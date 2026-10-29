package org.wishy.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/*
 * Downloads, in three small pieces:
 *   DlItem          one file and everything the screen shows about it
 *   DownloadHub     the queue and the transfer code (OkHttp, Apache-2.0); lives as long as the process
 *   DownloadService a foreground service so a transfer survives leaving the app, with a progress notification
 *
 * Why not Android's DownloadManager: it is missing or disabled on many TV boxes, and it cannot be told to
 * skip cookies, referrers or redirects the way the rest of this browser's privacy switches work.
 *
 * Safety rules: every file is written to a private temporary folder first and only copied into Downloads when it
 * is complete; the file name is cleaned of path tricks; cookies are read from the browser at request time and
 * are never written to disk; a redirect from https to http is refused; each redirect gets only its own cookies.
 */

enum class DlState { QUEUED, RUNNING, PAUSED, DONE, FAILED }

class DlItem(
    val id: Long,
    val url: String,
    /** Origin of the page that started the download (scheme + host only), or null. */
    val referer: String?,
    name: String,
    var mime: String?,
    val createdAt: Long,
) {
    var name by mutableStateOf(name)
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    var state by mutableStateOf(DlState.QUEUED)
    var error by mutableStateOf<String?>(null)
    /** Bytes per second while running. */
    var speed by mutableLongStateOf(0L)
    /** content:// (Android 10+) or file:// address of the finished file. */
    var savedUri by mutableStateOf<String?>(null)
    /** "Downloads" or the folder the file ended up in. */
    var savedWhere by mutableStateOf<String?>(null)
    /** ETag or Last-Modified, so a resume is refused by the server if the file changed. */
    var validator: String? = null

    /** Bumped by every start, pause and cancel: a worker whose number is out of date must stop at once. */
    @Volatile var runId = 0
    @Volatile var call: Call? = null
    /** Held for a whole transfer, so a new worker never starts while the previous one is still unwinding. */
    val lock = Any()
}

sealed interface DlEvent {
    class Done(val item: DlItem) : DlEvent
    class Failed(val item: DlItem) : DlEvent
}

private class Stale : IOException("stopped")
private class Fatal(message: String) : Exception(message)

object DownloadHub {
    val items: SnapshotStateList<DlItem> = mutableStateListOf()

    /** Called on the main thread when a download finishes or fails. Set by the controller while the screen is open. */
    var listener: ((DlEvent) -> Unit)? = null

    @Volatile var service: DownloadService? = null

    private lateinit var app: Context
    private var ready = false
    private var seq = 0L
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "wishy-dl").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)          // redirects are followed by hand, so cookies never travel to another host
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun post(block: () -> Unit) { main.post(block) }

    // ------------------------------------------------------------------ setup and storage

    /** Call once from the main thread. Safe to call again. */
    fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        ready = true
        load()
    }

    private val listFile: File get() = File(app.filesDir, "downloads.json")

    private fun partDir(): File = File(app.getExternalFilesDir(null) ?: app.filesDir, "dl").apply { mkdirs() }

    private fun partFile(d: DlItem) = File(partDir(), "${d.id}.part")

    private fun load() {
        runCatching {
            if (!listFile.exists()) return
            val arr = JSONArray(listFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = DlItem(
                    o.getLong("id"), o.getString("url"), o.optString("ref").ifEmpty { null },
                    o.optString("name", "download"), o.optString("mime").ifEmpty { null }, o.optLong("at")
                )
                d.total = o.optLong("total", -1L)
                d.done = o.optLong("done", 0L)
                val st = runCatching { DlState.valueOf(o.optString("state")) }.getOrDefault(DlState.PAUSED)
                // A transfer that was running when the app died has no worker any more: it waits to be resumed.
                d.state = if (st == DlState.RUNNING || st == DlState.QUEUED) DlState.PAUSED else st
                d.error = o.optString("err").ifEmpty { null }
                d.savedUri = o.optString("uri").ifEmpty { null }
                d.savedWhere = o.optString("where").ifEmpty { null }
                d.validator = o.optString("val").ifEmpty { null }
                items.add(d)
                if (d.id > seq) seq = d.id
            }
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            for (d in items) arr.put(
                JSONObject().put("id", d.id).put("url", d.url).put("ref", d.referer ?: "").put("name", d.name)
                    .put("mime", d.mime ?: "").put("total", d.total).put("done", d.done).put("state", d.state.name)
                    .put("err", d.error ?: "").put("uri", d.savedUri ?: "").put("where", d.savedWhere ?: "")
                    .put("val", d.validator ?: "").put("at", d.createdAt)
            )
            val tmp = File(listFile.path + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(listFile)) { listFile.delete(); tmp.renameTo(listFile) }
        }
    }

    // ------------------------------------------------------------------ what the screen calls (main thread)

    fun hasActive(): Boolean = items.any { it.state == DlState.QUEUED || it.state == DlState.RUNNING }

    fun guessName(url: String, contentDisposition: String?, mime: String?): String {
        if (url.startsWith("data:", ignoreCase = true)) {
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            return if (ext != null) "download.$ext" else "download"
        }
        return cleanName(runCatching { URLUtil.guessFileName(url, contentDisposition, mime) }.getOrDefault("download"))
    }

    /** Strips folders, control characters and anything a file system dislikes; never returns an empty name. */
    fun cleanName(raw: String): String {
        var n = raw.substringAfterLast('/').substringAfterLast('\\')
        n = n.replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim().trimStart('.')
        if (n.isEmpty()) n = "download"
        if (n.length > 120) {
            val ext = n.substringAfterLast('.', "")
            n = n.take(100) + if (ext.length in 1..10) ".$ext" else ""
        }
        return n
    }

    fun enqueue(url: String, referer: String?, name: String, mime: String?, size: Long): DlItem {
        val now = System.currentTimeMillis()
        seq = if (now > seq) now else seq + 1
        val d = DlItem(seq, url, referer, cleanName(name), mime?.takeIf { it.isNotBlank() }, now)
        if (size > 0) d.total = size
        items.add(d)
        start(d)
        return d
    }

    fun pause(d: DlItem) {
        if (d.state != DlState.RUNNING && d.state != DlState.QUEUED) return
        d.runId++
        d.call?.cancel()
        d.state = DlState.PAUSED
        d.speed = 0
        save(); refreshService()
    }

    fun resume(d: DlItem) {
        if (d.state == DlState.PAUSED || d.state == DlState.FAILED) start(d)
    }

    fun pauseAll() { for (d in items.toList()) pause(d) }

    /** Stops the transfer and deletes the partial file. For a finished item use [remove]. */
    fun cancel(d: DlItem) {
        d.runId++
        d.call?.cancel()
        items.remove(d)
        deletePartLater(d)
        save(); refreshService()
    }

    /** Takes a finished or failed item off the list. A finished file stays where it was saved. */
    fun remove(d: DlItem) {
        if (d.state == DlState.RUNNING || d.state == DlState.QUEUED) { cancel(d); return }
        items.remove(d)
        if (d.state != DlState.DONE) deletePartLater(d)
        save()
    }

    fun clearFinished() {
        for (d in items.toList()) if (d.state == DlState.DONE || d.state == DlState.FAILED) remove(d)
    }

    /** "Wipe data at exit": forget the list and delete half-finished files, unless a transfer is still running. */
    fun wipeIfIdle() {
        if (!ready || hasActive()) return
        items.clear()
        pool.execute { runCatching { partDir().listFiles()?.forEach { it.delete() } } }
        save()
    }

    /** An address another app may open (the file provider for files in the app's own folder). */
    fun shareUri(saved: String): Uri? = runCatching {
        val u = Uri.parse(saved)
        if (u.scheme == "file") FileProvider.getUriForFile(app, "${app.packageName}.files", File(u.path!!)) else u
    }.getOrNull()

    fun mimeOf(d: DlItem): String =
        d.mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(d.name.substringAfterLast('.', "").lowercase())
            ?: "*/*"

    // ------------------------------------------------------------------ queue

    private fun start(d: DlItem) {
        d.error = null
        d.state = DlState.QUEUED
        d.speed = 0
        val my = ++d.runId
        ensureService()
        pool.execute { runItem(d, my) }
        save()
    }

    private fun ensureService() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
    }

    private fun refreshService() { post { service?.refresh() } }

    private fun fail(d: DlItem, my: Int, message: String) {
        post {
            if (d.runId != my) return@post
            d.state = DlState.FAILED; d.error = message; d.speed = 0
            save(); listener?.invoke(DlEvent.Failed(d)); service?.refresh()
        }
    }

    private fun deletePartLater(d: DlItem) {
        pool.execute { synchronized(d.lock) { runCatching { partFile(d).delete() } } }
    }

    // ------------------------------------------------------------------ the transfer (worker threads)

    private fun runItem(d: DlItem, my: Int) {
        synchronized(d.lock) {
            if (d.runId != my) return
            post { if (d.runId == my) d.state = DlState.RUNNING; service?.refresh() }
            var attempt = 0
            while (true) {
                if (d.runId != my) return
                try {
                    transfer(d, my)
                    break
                } catch (e: Stale) {
                    return
                } catch (e: Fatal) {
                    fail(d, my, e.message ?: "Download failed"); return
                } catch (e: IOException) {
                    if (d.runId != my) return
                    attempt++
                    if (attempt > 4) { fail(d, my, friendly(e)); return }
                    // Wait 2, 4, 8, 16 seconds and try again from where the file stopped; pause and cancel are noticed at once.
                    val wait = 2000L shl (attempt - 1)
                    var waited = 0L
                    while (waited < wait) { Thread.sleep(250); waited += 250; if (d.runId != my) return }
                }
            }
            try {
                val (uri, where) = publish(d)
                post {
                    if (d.runId != my) return@post
                    d.savedUri = uri; d.savedWhere = where; d.state = DlState.DONE; d.speed = 0
                    save(); listener?.invoke(DlEvent.Done(d)); service?.refresh()
                }
            } catch (e: Exception) {
                fail(d, my, "Could not save the file: ${e.message ?: "unknown problem"}")
            }
        }
    }

    private fun friendly(e: IOException): String = when (e) {
        is UnknownHostException -> "Can't reach the server"
        is SocketTimeoutException -> "The server took too long to answer"
        is SSLException -> "The secure connection failed"
        else -> e.message ?: "Network problem"
    }

    private fun transfer(d: DlItem, my: Int) {
        val part = partFile(d)
        if (d.url.startsWith("data:", ignoreCase = true)) { transferData(d, my, part); return }

        var have = if (part.exists()) part.length() else 0L
        open(d, have).use { r ->
            when {
                r.code == 416 -> {
                    // "Range not satisfiable": either the file is already complete or it changed. Start over if it changed.
                    val total = r.header("Content-Range")?.substringAfter("*/", "")?.toLongOrNull()
                    if (have > 0 && total != null && total == have) {
                        post { if (d.runId == my) { d.total = total; d.done = have } }
                        return
                    }
                    part.delete()
                    throw IOException("The file changed; starting again")
                }
                r.code in 500..599 || r.code == 429 -> throw IOException("The server is busy (${r.code})")
                !r.isSuccessful -> throw Fatal(
                    when (r.code) {
                        401, 403 -> "Not allowed (${r.code}). The site may want you to sign in first."
                        404, 410 -> "The file is gone (${r.code})"
                        else -> "The server answered ${r.code}"
                    }
                )
            }
            val append = r.code == 206 && have > 0
            if (!append && have > 0) { part.delete(); have = 0 }
            val body = r.body ?: throw IOException("Empty answer")
            val len = body.contentLength()
            val total = if (len >= 0) len + (if (append) have else 0L) else -1L
            if (!append) d.validator = r.header("ETag") ?: r.header("Last-Modified")
            if (d.mime.isNullOrBlank()) d.mime = r.header("Content-Type")?.substringBefore(';')?.trim()

            val need = if (total >= 0) total - have else 0L
            if (need > 0 && StatFs(partDir().path).availableBytes < need + 8L * 1024 * 1024) throw Fatal("Not enough free space")
            post { if (d.runId == my) { d.total = total; d.done = have } }

            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var written = have
                    var windowStart = SystemClock.uptimeMillis()
                    var windowBytes = 0L
                    while (true) {
                        if (d.runId != my) throw Stale()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n; windowBytes += n
                        val now = SystemClock.uptimeMillis()
                        if (now - windowStart >= 400) {
                            val w = written
                            val speed = windowBytes * 1000 / (now - windowStart)
                            windowStart = now; windowBytes = 0
                            post { if (d.runId == my) { d.done = w; d.speed = speed; service?.refresh() } }
                        }
                    }
                    out.flush()
                    if (total >= 0 && written != total) throw IOException("The connection closed early")
                    post { if (d.runId == my) { d.done = written; if (d.total < 0) d.total = written } }
                }
            }
        }
    }

    /** data: addresses carry the file inside the address itself. */
    private fun transferData(d: DlItem, my: Int, part: File) {
        val raw = d.url
        val comma = raw.indexOf(',')
        if (comma < 0) throw Fatal("This address is not valid")
        val head = raw.substring(5, comma)
        val payload = raw.substring(comma + 1)
        val bytes = if (head.endsWith(";base64", ignoreCase = true)) Base64.decode(payload, Base64.DEFAULT)
        else Uri.decode(payload).toByteArray(Charsets.UTF_8)
        if (d.mime.isNullOrBlank()) d.mime = head.substringBefore(';').ifBlank { null }
        part.writeBytes(bytes)
        post { if (d.runId == my) { d.total = bytes.size.toLong(); d.done = bytes.size.toLong() } }
    }

    private fun headersFor(d: DlItem, url: HttpUrl, p: Prefs): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["User-Agent"] = Privacy.userAgent(app, p.desktopMode, p.opt(Opt.COMMON_IDENTITY))
        h["Accept"] = "*/*"
        h["Accept-Encoding"] = "identity"        // byte ranges and Content-Length stay honest
        d.referer?.let { h["Referer"] = it }
        runCatching { CookieManager.getInstance().getCookie(url.toString()) }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
        if (p.opt(Opt.GPC)) { h["Sec-GPC"] = "1"; h["DNT"] = "1" }
        return h
    }

    /**
     * Opens the file, following redirects by hand: each hop gets only the cookies for its own host, and a hop
     * from https to http is refused. Plain http addresses are tried over https first (the same switch the page loader uses).
     */
    private fun open(d: DlItem, have: Long): Response {
        val p = Prefs(app)
        var cur: HttpUrl = d.url.toHttpUrlOrNull() ?: throw Fatal("This address is not valid")
        var upgraded = false
        if (cur.scheme == "http" && p.opt(Opt.HTTPS_UPGRADE) && !Privacy.isLocal(cur.host)) {
            cur = cur.newBuilder().scheme("https").build(); upgraded = true
        }
        var hops = 0
        while (true) {
            if (++hops > 7) throw Fatal("Too many redirects")
            val rb = Request.Builder().url(cur)
            for ((k, v) in headersFor(d, cur, p)) rb.header(k, v)
            if (have > 0) { rb.header("Range", "bytes=$have-"); d.validator?.let { rb.header("If-Range", it) } }
            val call = client.newCall(rb.build())
            d.call = call
            val resp = try {
                call.execute()
            } catch (e: IOException) {
                if (upgraded) { upgraded = false; cur = d.url.toHttpUrlOrNull() ?: throw e; continue }
                throw e
            }
            upgraded = false
            if (resp.code == 301 || resp.code == 302 || resp.code == 303 || resp.code == 307 || resp.code == 308) {
                val loc = resp.header("Location")
                resp.close()
                val next = loc?.let { cur.resolve(it) } ?: throw Fatal("The server sent a bad redirect")
                if (cur.scheme == "https" && next.scheme == "http") throw Fatal("Refused: redirected to an insecure address")
                cur = next
                continue
            }
            return resp
        }
    }

    // ------------------------------------------------------------------ saving

    /** Copies the finished temporary file into the public Downloads folder. Returns (address, where). */
    @Suppress("DEPRECATION")
    private fun publish(d: DlItem): Pair<String, String> {
        val part = partFile(d)
        val mime = mimeOf(d).let { if (it == "*/*") "application/octet-stream" else it }
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val cr = app.contentResolver
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, d.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("no address")
                try {
                    (cr.openOutputStream(uri) ?: error("cannot write")).use { out ->
                        part.inputStream().use { it.copyTo(out, 64 * 1024) }
                    }
                    v.clear(); v.put(MediaStore.Downloads.IS_PENDING, 0)
                    cr.update(uri, v, null, null)
                } catch (e: Exception) {
                    runCatching { cr.delete(uri, null, null) }
                    throw e
                }
                part.delete()
                return uri.toString() to "Downloads"
            }
            // MediaStore refused (some TV builds): fall through to the app's own folder.
        }
        val publicOk = Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val dir = if (publicOk) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        else File(app.getExternalFilesDir(null) ?: app.filesDir, "Download")
        dir.mkdirs()
        val dest = unique(dir, d.name)
        if (!part.renameTo(dest)) {
            part.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            part.delete()
        }
        return Uri.fromFile(dest).toString() to (if (publicOk) "Downloads" else dest.parent ?: "the app folder")
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            if (!f.exists()) return f
            i++
        }
    }
}

/** Keeps the process alive while files download and shows one progress notification. Stops itself when the queue is empty. */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadHub.service = this
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be foreground within seconds of being started, whatever the queue looks like.
        runCatching { startFg(build(emptyList())) }
        if (intent?.action == ACTION_PAUSE_ALL) DownloadHub.pauseAll()
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (DownloadHub.service === this) DownloadHub.service = null
        super.onDestroy()
    }

    /** Android 15 caps how long a data-sync service may run: pause cleanly instead of being killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DownloadHub.pauseAll()
        stopSelf()
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
    }

    fun refresh() {
        val active = DownloadHub.items.filter { it.state == DlState.QUEUED || it.state == DlState.RUNNING }
        if (active.isEmpty()) {
            // The screen shows its own card when it is open; only speak up when nobody is looking.
            if (DownloadHub.listener == null) {
                val last = DownloadHub.items.lastOrNull { it.state == DlState.DONE || it.state == DlState.FAILED }
                if (last != null) runCatching {
                    getSystemService(NotificationManager::class.java)?.notify(
                        NOTIF_ID + 1,
                        NotificationCompat.Builder(this, CHANNEL)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle(if (last.state == DlState.DONE) "Download finished" else "Download failed")
                            .setContentText(last.name)
                            .setAutoCancel(true)
                            .setContentIntent(openApp())
                            .build()
                    )
                }
            }
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, build(active)) }
    }

    private fun openApp(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun build(active: List<DlItem>): Notification {
        val known = active.isNotEmpty() && active.all { it.total > 0 }
        val done = active.sumOf { it.done }
        val total = active.sumOf { maxOf(0L, it.total) }
        val pct = if (known && total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
        val title = when (active.size) { 0 -> "Downloads"; 1 -> active[0].name; else -> "${active.size} downloads" }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (known) "$pct%" else "Downloading")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct, !known)
            .setContentIntent(openApp())
            .addAction(
                0, "Pause all",
                PendingIntent.getService(
                    this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        return b.build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIF_ID = 4201
        const val ACTION_PAUSE_ALL = "org.wishy.browser.PAUSE_ALL"
    }
}

package org.wishy.browser.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

enum class DlState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELED }

const val APK_MIME = "application/vnd.android.package-archive"

class DownloadItem(
    val id: Long,
    val url: String,
    name: String,
    val mime: String?,
    val cookie: String?,
    val userAgent: String?,
    val referer: String?,
) {
    var name by mutableStateOf(name)
    var state by mutableStateOf(DlState.QUEUED)
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(-1L)
    var error by mutableStateOf<String?>(null)
    /** Final location once DONE. */
    var uri by mutableStateOf<Uri?>(null)
    /** Shown on the row when the file type looks risky or misleading. */
    var warning by mutableStateOf<String?>(null)
    @Volatile var pauseRequested = false
    @Volatile var cancelRequested = false
    val isApk: Boolean get() = name.lowercase(Locale.ROOT).endsWith(".apk")
}

class SavedFile(val name: String, val size: Long, val uri: Uri, val mime: String?)

/**
 * Own downloader (HttpURLConnection + Range) so pause and resume work; Android's DownloadManager cannot
 * pause. Partial files live in the app cache and are deleted at start. Finished files go to the public
 * Downloads folder through MediaStore on Android 10+ (no storage permission), or to the app's own
 * Download folder on Android 8-9. The list of downloads is memory only, like the rest of the browser.
 */
class DownloadsManager(private val ctx: Context, private val onEvent: (title: String, detail: String?, ok: Boolean) -> Unit) {

    val items = mutableStateListOf<DownloadItem>()
    private val ids = AtomicLong(1)
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val partDir = File(ctx.cacheDir, "dl").also { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }

    // ---------------------------------------------------------------- queue

    /** Returns the existing item when this URL is already being downloaded. */
    fun active(url: String): DownloadItem? =
        items.firstOrNull { it.url == url && (it.state == DlState.RUNNING || it.state == DlState.QUEUED || it.state == DlState.PAUSED) }

    fun enqueue(url: String, contentDisposition: String?, mime: String?, cookie: String?, ua: String?, referer: String?): DownloadItem? {
        val u = Uri.parse(url)
        if (u.scheme != "http" && u.scheme != "https") { onEvent("Can't download this", "Only http and https downloads are supported", false); return null }
        active(url)?.let { return it }
        val name = safeName(filenameFromDisposition(contentDisposition) ?: u.lastPathSegment)
        val item = DownloadItem(ids.getAndIncrement(), url, name, mime, cookie, ua, referer)
        item.warning = warningFor(name, mime)
        items.add(0, item)
        start(item)
        return item
    }

    private fun start(item: DownloadItem) {
        item.pauseRequested = false; item.cancelRequested = false
        item.error = null
        item.state = DlState.QUEUED
        pool.execute { runCatching { run(item) }.onFailure { fail(item, it.message ?: "Download failed") } }
    }

    fun pause(i: DownloadItem) { if (i.state == DlState.RUNNING || i.state == DlState.QUEUED) i.pauseRequested = true }
    fun resume(i: DownloadItem) { if (i.state == DlState.PAUSED) start(i) }
    /** Failed or canceled downloads continue from the partial file when there is one, else start again. */
    fun retry(i: DownloadItem) { if (i.state == DlState.FAILED || i.state == DlState.CANCELED || i.state == DlState.PAUSED) start(i) }

    fun cancel(i: DownloadItem) {
        i.cancelRequested = true
        if (i.state != DlState.RUNNING) { partFile(i).delete(); i.state = DlState.CANCELED }
    }

    fun remove(i: DownloadItem) {
        if (i.state == DlState.RUNNING || i.state == DlState.QUEUED) cancel(i)
        partFile(i).delete()
        items.remove(i)
    }

    fun clearFinished() { items.removeAll { it.state == DlState.DONE || it.state == DlState.CANCELED || it.state == DlState.FAILED } }

    fun shutdown() { items.forEach { it.cancelRequested = true }; pool.shutdownNow(); partDir.listFiles()?.forEach { it.delete() } }

    private fun partFile(i: DownloadItem) = File(partDir, "${i.id}.part")

    private fun post(block: () -> Unit) { main.post(block) }
    private fun fail(i: DownloadItem, msg: String) = post { i.state = DlState.FAILED; i.error = msg; onEvent("Download failed", "${i.name}: $msg", false) }

    // ---------------------------------------------------------------- worker

    private fun open(item: DownloadItem, from: Long): HttpURLConnection {
        var url = URL(item.url)
        var redirects = 0
        while (true) {
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept-Encoding", "identity")
            item.userAgent?.let { c.setRequestProperty("User-Agent", it) }
            if (url.host == URL(item.url).host) item.cookie?.let { c.setRequestProperty("Cookie", it) }
            item.referer?.let { if (it.startsWith("http")) c.setRequestProperty("Referer", it) }
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399 && code != 304) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                val next = URL(url, loc)
                c.disconnect()
                if (++redirects > 6) throw IOException("Too many redirects")
                if (url.protocol == "https" && next.protocol != "https") throw IOException("Blocked a redirect from HTTPS to HTTP")
                if (next.protocol != "http" && next.protocol != "https") throw IOException("Unsupported redirect")
                url = next
                continue
            }
            return c
        }
    }

    private fun run(item: DownloadItem) {
        val part = partFile(item)
        var from = if (part.exists()) part.length() else 0L
        post { item.state = DlState.RUNNING }
        var conn = open(item, from)
        var code = conn.responseCode
        if (code == 416) { conn.disconnect(); part.delete(); from = 0; conn = open(item, 0); code = conn.responseCode }
        if (code != 200 && code != 206) { conn.disconnect(); throw IOException("Server answered $code") }
        if (code == 200 && from > 0) { part.delete(); from = 0 }          // server ignored Range: start over
        val total = when {
            code == 206 -> conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            else -> conn.contentLengthLong
        }
        val serverMime = conn.contentType?.substringBefore(';')?.trim()
        filenameFromDisposition(conn.getHeaderField("Content-Disposition"))?.let { n ->
            val s = safeName(n)
            post { item.name = s; item.warning = warningFor(s, serverMime ?: item.mime) }
        } ?: post { item.warning = warningFor(item.name, serverMime ?: item.mime) }
        if (total > 0) {
            val free = StatFs(partDir.absolutePath).availableBytes
            if (free < total - from + 8L * 1024 * 1024) { conn.disconnect(); throw IOException("Not enough free storage") }
        }
        post { item.total = total; item.done = from }

        var done = from
        var lastPost = 0L
        conn.inputStream.use { input ->
            FileOutputStream(part, from > 0).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (item.cancelRequested) { conn.disconnect(); part.delete(); post { item.state = DlState.CANCELED }; return }
                    if (item.pauseRequested) { conn.disconnect(); post { item.state = DlState.PAUSED; item.done = done }; return }
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastPost > 300) { lastPost = now; val d = done; post { item.done = d } }
                }
            }
        }
        conn.disconnect()
        if (total > 0 && done != total) throw IOException("Connection ended early")
        val saved = finalizeFile(item, part, serverMime ?: item.mime)
        part.delete()
        post {
            item.done = done; item.total = done
            item.uri = saved.first; item.name = saved.second
            item.state = DlState.DONE
            onEvent("Download finished", saved.second, true)
        }
    }

    /** Moves the finished file to its public place. Returns its Uri and the final (possibly renamed) name. */
    private fun finalizeFile(item: DownloadItem, part: File, mime: String?): Pair<Uri, String> {
        val type = guessMime(item.name, mime)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, item.name)
                put(MediaStore.Downloads.MIME_TYPE, type)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create the file in Downloads")
            try {
                r.openOutputStream(uri)!!.use { o -> part.inputStream().use { it.copyTo(o) } }
                r.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { r.delete(uri, null, null); throw e }
            val finalName = r.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: item.name
            return uri to finalName
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("No storage available")
        dir.mkdirs()
        val target = uniqueFile(dir, item.name)
        part.inputStream().use { i -> FileOutputStream(target).use { i.copyTo(it) } }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", target) to target.name
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        return f
    }

    // ---------------------------------------------------------------- saved files (browse)

    /** Files this app saved to Downloads (Android 10+ lets an app list its own files without any permission). */
    fun savedFiles(): List<SavedFile> {
        val out = ArrayList<SavedFile>()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val proj = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.MIME_TYPE)
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, "${MediaStore.Downloads.OWNER_PACKAGE_NAME}=?", arrayOf(ctx.packageName),
                    "${MediaStore.Downloads.DATE_ADDED} DESC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                        out.add(SavedFile(c.getString(1) ?: "file", c.getLong(2), uri, c.getString(3)))
                    }
                }
            } else {
                ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                    out.add(SavedFile(f.name, f.length(), FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f), guessMime(f.name, null)))
                }
            }
        }
        return out
    }

    fun deleteSaved(f: SavedFile): Boolean = runCatching { ctx.contentResolver.delete(f.uri, null, null) > 0 }.getOrDefault(false)

    // ---------------------------------------------------------------- names and types

    companion object {
        fun filenameFromDisposition(cd: String?): String? {
            if (cd.isNullOrBlank()) return null
            Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let {
                return runCatching { Uri.decode(it.groupValues[2].trim().trim('"')) }.getOrNull()
            }
            Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.let { return it.groupValues[1].trim() }
            return null
        }

        /** No path parts, control characters, bidi tricks, reserved characters or leading dots; max 120 chars; never empty. */
        fun safeName(raw: String?): String {
            var n = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
            n = n.replace(Regex("[\\u202A-\\u202E\\u2066-\\u2069\\u200B-\\u200F]"), "")
            n = n.replace(Regex("[\\p{Cntrl}<>:\"|?*]"), "_").trim().trim('.')
            if (n.length > 120) {
                val ext = n.substringAfterLast('.', "").take(10)
                n = n.take(110 - ext.length) + if (ext.isEmpty()) "" else ".$ext"
            }
            return n.ifEmpty { "download" }
        }

        private val RISKY = setOf("apk", "exe", "bat", "cmd", "scr", "msi", "jar", "sh", "vbs", "ps1", "com", "pif", "dex", "xapk", "apks")
        private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "jpg", "jpeg", "png", "gif", "mp4", "mkv", "mp3", "zip", "rar", "7z")

        /** A short warning when the name or declared type could mislead, else null. */
        fun warningFor(name: String, mime: String?): String? {
            val parts = name.lowercase(Locale.ROOT).split('.')
            val ext = if (parts.size > 1) parts.last() else ""
            val inner = if (parts.size > 2) parts[parts.size - 2] else ""
            return when {
                ext in RISKY && inner in DOC -> "Disguised: looks like a .$inner file but is a .$ext program"
                ext == "apk" && mime != null && mime != APK_MIME && mime != "application/octet-stream" && mime != "binary/octet-stream" ->
                    "Server says this is $mime, but the name says it is an app"
                ext == "apk" -> "App installer: only install apps you trust"
                ext in RISKY -> "Program file: only open it if you trust the source"
                else -> null
            }
        }

        fun guessMime(name: String, declared: String?): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext == "apk") return APK_MIME
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            return declared?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: "application/octet-stream"
        }

        fun formatSize(b: Long): String = when {
            b < 0 -> "unknown size"
            b < 1024 -> "$b B"
            b < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
            b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
            else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        }
    }
}

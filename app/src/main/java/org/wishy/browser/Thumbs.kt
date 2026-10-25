package org.wishy.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Page previews.
 *
 * Privacy rule: the browser never writes browsing history. So a preview of an ordinary page lives
 * in memory only and disappears when the app closes. A preview of a page the user bookmarked is
 * written to disk (they explicitly kept that page) and deleted again when the bookmark is removed.
 */
class ThumbStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "thumbs").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Small LRU of recent previews (RGB_565, 480 px wide: about 240 KB each).
    private val mem = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val remove = size > MAX_IN_MEMORY
            if (remove && eldest != null) {
                val b = eldest.value
                if (!b.isRecycled) runCatching { b.recycle() }
            }
            return remove
        }
    }
    private val hostKeys = HashMap<String, String>()

    /** Bumped whenever a preview is added or removed, so the UI reloads it. */
    var version by mutableIntStateOf(0)
        private set

    fun key(url: String): String {
        val n = url.trim().substringBefore('#').trimEnd('/').lowercase()
        val d = MessageDigest.getInstance("SHA-1").digest(n.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun hostOf(url: String): String? =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()?.removePrefix("www.")?.removePrefix("m.")

    /** Shrinks and stores a screenshot. Safe to call from the UI thread; the work happens on a worker. */
    fun put(url: String, shot: Bitmap, persist: Boolean) {
        io.execute {
            try {
                val small = runCatching { shrink(shot) }.getOrNull()
                if (small == null) return@execute
                val k = key(url)
                val old: Bitmap?
                synchronized(mem) {
                    old = mem.put(k, small)
                    hostOf(url)?.let { hostKeys[it] = k }
                }
                if (old != null && old !== small && !old.isRecycled) runCatching { old.recycle() }
                if (persist) writeDisk(k, small)
                main.post { version++ }
            } catch (_: Throwable) {
                // Ignore OOM or error during thumbnail processing
            } finally {
                if (!shot.isRecycled) runCatching { shot.recycle() }
            }
        }
    }

    /** Memory first, then disk. Call off the UI thread. */
    fun get(url: String): Bitmap? {
        val k = key(url)
        synchronized(mem) { mem[k] }?.let { return it }
        val f = File(dir, "$k.jpg")
        if (!f.exists()) return null
        val b = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        synchronized(mem) { mem[k] = b }
        return b
    }

    /** The newest in-memory preview for a site (used by the home screen shortcuts). */
    fun getForHost(host: String): Bitmap? = synchronized(mem) {
        val k = hostKeys[host.removePrefix("www.").removePrefix("m.")] ?: return null
        mem[k]
    }

    /** Called when the user bookmarks a page: keep its preview on disk. */
    fun persist(url: String) {
        val b = synchronized(mem) { mem[key(url)] } ?: return
        io.execute { writeDisk(key(url), b) }
    }

    fun hasPreview(url: String): Boolean = synchronized(mem) { mem.containsKey(key(url)) } || File(dir, "${key(url)}.jpg").exists()

    fun delete(url: String) {
        val k = key(url)
        synchronized(mem) {
            val b = mem.remove(k)
            if (b != null && !b.isRecycled) runCatching { b.recycle() }
        }
        io.execute { runCatching { File(dir, "$k.jpg").delete() }; main.post { version++ } }
    }

    /** Forgets everything in memory and, with [includeBookmarks], on disk too. */
    fun clear(includeBookmarks: Boolean) {
        synchronized(mem) {
            mem.values.forEach { if (!it.isRecycled) runCatching { it.recycle() } }
            mem.clear()
            hostKeys.clear()
        }
        if (includeBookmarks) io.execute { dir.listFiles()?.forEach { runCatching { it.delete() } } }
        version++
    }

    /** Drops previews of pages that are no longer bookmarked (e.g. after an import or a crash). */
    fun prune(keepUrls: List<String>) {
        val keep = keepUrls.map { "${key(it)}.jpg" }.toSet()
        io.execute { dir.listFiles()?.forEach { if (it.name !in keep) runCatching { it.delete() } } }
    }

    private fun writeDisk(k: String, b: Bitmap) {
        runCatching { File(dir, "$k.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
    }

    /** Crops to 16:9 from the top of the page and scales to 480 px wide. */
    private fun shrink(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val targetH = (w * 9f / 16f).toInt().coerceAtLeast(1)
        val cropped = if (h >= targetH) Bitmap.createBitmap(src, 0, 0, w, targetH)
        else {
            val cw = (h * 16f / 9f).toInt().coerceIn(1, w)
            Bitmap.createBitmap(src, (w - cw) / 2, 0, cw, h)
        }
        val scaled = Bitmap.createScaledBitmap(cropped, OUT_W, OUT_W * 9 / 16, true)
        if (cropped !== src && cropped !== scaled && !cropped.isRecycled) runCatching { cropped.recycle() }
        val out = scaled.copy(Bitmap.Config.RGB_565, false) ?: scaled
        if (out !== scaled && !scaled.isRecycled) runCatching { scaled.recycle() }
        return out
    }

    companion object {
        private const val MAX_IN_MEMORY = 14
        private const val OUT_W = 480
    }
}

/** A page opened this session. In memory only; never written to disk. */
class Recent(val url: String, val title: String)

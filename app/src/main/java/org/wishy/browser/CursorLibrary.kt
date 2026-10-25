package org.wishy.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/** A pointer made from a picture. [hotX]/[hotY] are the click point as a fraction of the picture. */
class CursorImage(
    val id: String,
    val name: String,
    val pack: String,
    val file: String,
    val hotX: Float,
    val hotY: Float,
    val aspect: Float,
    val custom: Boolean,
)

class CursorPack(val name: String, val author: String, val license: String, val url: String)

/**
 * Pictures a pointer can use:
 *  - open-source packs shipped in assets/cursors (see index.json there for credits and licences)
 *  - the user's own cursors, added from a file or a web address and kept in the app's private storage.
 */
class CursorLibrary(context: Context, private val prefs: Prefs) {

    private val app = context.applicationContext
    private val customDir = File(app.filesDir, "cursors").apply { mkdirs() }
    private val cache = HashMap<String, ImageBitmap>()

    val packInfo: List<CursorPack>
    val packed: List<CursorImage>
    val custom = mutableStateListOf<CursorImage>()

    init {
        val infos = ArrayList<CursorPack>()
        val imgs = ArrayList<CursorImage>()
        runCatching {
            val text = app.assets.open("cursors/index.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val ps = root.optJSONArray("packs") ?: JSONArray()
            for (i in 0 until ps.length()) {
                val o = ps.getJSONObject(i)
                infos.add(CursorPack(o.optString("name"), o.optString("author"), o.optString("license"), o.optString("url")))
            }
            val cs = root.optJSONArray("cursors") ?: JSONArray()
            for (i in 0 until cs.length()) {
                val o = cs.getJSONObject(i)
                imgs.add(
                    CursorImage(
                        o.getString("id"), o.getString("name"), o.optString("pack"), o.getString("file"),
                        o.optDouble("hotX", 0.5).toFloat(), o.optDouble("hotY", 0.5).toFloat(),
                        o.optDouble("aspect", 1.0).toFloat(), false
                    )
                )
            }
        }
        packInfo = infos
        packed = imgs
        runCatching {
            val arr = JSONArray(prefs.customCursors)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val file = o.getString("file")
                if (File(customDir, file).exists()) custom.add(
                    CursorImage(
                        o.getString("id"), o.getString("name"), "Yours", file,
                        o.optDouble("hotX", 0.1).toFloat(), o.optDouble("hotY", 0.1).toFloat(),
                        o.optDouble("aspect", 1.0).toFloat(), true
                    )
                )
            }
        }
    }

    fun find(id: String): CursorImage? = packed.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }

    /** Decoded picture, cached. Small (at most 128 px), so decoding on the UI thread is fine. */
    fun bitmap(img: CursorImage): ImageBitmap? {
        synchronized(cache) { cache[img.id] }?.let { return it }
        val bmp = runCatching {
            if (img.custom) BitmapFactory.decodeFile(File(customDir, img.file).absolutePath)
            else app.assets.open("cursors/${img.file}").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap() ?: return null
        synchronized(cache) { cache[img.id] = bmp }
        return bmp
    }

    // ---- adding your own -------------------------------------------------

    /** Reads at most [MAX_BYTES] from a web address. Only https is accepted. Call off the UI thread. */
    fun download(address: String): ByteArray {
        val u = URL(address.trim())
        require(u.protocol == "https") { "Use an https:// address" }
        val c = u.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "WishyBrowser")
            if (c.responseCode !in 200..299) error("The server answered ${c.responseCode}")
            return readLimited(c.inputStream)
        } finally {
            c.disconnect()
        }
    }

    fun readLimited(input: java.io.InputStream): ByteArray = input.use { s ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) error("That picture is too large (limit 3 MB)")
        }
        out.toByteArray()
    }

    /**
     * Turns picture bytes into a stored cursor. [tipTopLeft] = the click point is the top-left tip
     * (arrows); otherwise the picture's centre (crosshairs, dots). Call off the UI thread, then
     * pass the result to [register] on the UI thread.
     */
    fun create(bytes: ByteArray, rawName: String, tipTopLeft: Boolean): CursorImage {
        val bmp = decodeScaled(bytes) ?: error("That file is not a picture Wishy can read")
        val id = "custom_" + System.currentTimeMillis()
        val file = "$id.png"
        File(customDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val clean = rawName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifBlank { "My cursor" }
        return CursorImage(
            id, clean.take(28), "Yours", file,
            if (tipTopLeft) 0.06f else 0.5f, if (tipTopLeft) 0.05f else 0.5f,
            bmp.width.toFloat() / bmp.height.toFloat(), true
        )
    }

    fun register(img: CursorImage) {
        custom.add(0, img)
        save()
    }

    fun remove(img: CursorImage) {
        if (!img.custom) return
        custom.removeAll { it.id == img.id }
        synchronized(cache) { cache.remove(img.id) }
        runCatching { File(customDir, img.file).delete() }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (c in custom) arr.put(
            JSONObject().put("id", c.id).put("name", c.name).put("file", c.file)
                .put("hotX", c.hotX.toDouble()).put("hotY", c.hotY.toDouble()).put("aspect", c.aspect.toDouble())
        )
        prefs.customCursors = arr.toString()
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val m = max(bmp.width, bmp.height)
        if (m <= MAX_SIDE) return bmp
        val k = MAX_SIDE.toFloat() / m
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * k).roundToInt().coerceAtLeast(1), (bmp.height * k).roundToInt().coerceAtLeast(1), true
        )
    }

    companion object {
        private const val MAX_BYTES = 3 * 1024 * 1024
        private const val MAX_SIDE = 128
    }
}

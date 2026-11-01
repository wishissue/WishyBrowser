package org.wishy.browser.engine

import android.content.Context

/**
 * Native request blocker: a sorted list of ad/tracker domains (built from EasyList and EasyPrivacy by
 * tools/build_blocklist.py) kept as one byte array and searched by binary search, so ~100,000 domains
 * cost about 2.5 MB of RAM instead of ~10 MB for a HashSet.
 *
 * What it does: blocks sub-resource requests (scripts, images, frames, XHR...) to listed domains when
 * they are third-party to the page. What it does NOT do: URL-path rules, $script/$image options, scriptlets,
 * procedural cosmetic filters, dynamic filtering. It is not uBlock Origin.
 */
class Blocker(private val context: Context) {

    private class Index(val data: ByteArray, val start: IntArray, val end: IntArray)

    private val index: Index by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { load() }

    val domainCount: Int get() = index.start.size

    /** CSS that hides generic ad containers. Built once, injected after each page loads. */
    val cosmeticCss: String by lazy {
        runCatching {
            val sels = context.assets.open("cosmetic.txt").bufferedReader().useLines { it.filter { s -> s.isNotBlank() }.toList() }
            if (sels.isEmpty()) "" else sels.joinToString(",") + "{display:none!important}"
        }.getOrDefault("")
    }

    private fun load(): Index {
        val data = runCatching { context.assets.open("blocklist.txt").use { it.readBytes() } }.getOrDefault(ByteArray(0))
        var n = 0
        for (b in data) if (b == '\n'.code.toByte()) n++
        val start = IntArray(n)
        val end = IntArray(n)
        var line = 0
        var s = 0
        for (i in data.indices) {
            if (data[i] == '\n'.code.toByte()) {
                start[line] = s; end[line] = i; line++; s = i + 1
            }
        }
        return Index(data, start, end)
    }

    /** True when [host] or any parent domain of it is on the list. */
    fun blocks(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        var from = 0
        while (true) {
            if (contains(h, from)) return true
            val dot = h.indexOf('.', from)
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false // never test a bare TLD
            from = dot + 1
        }
    }

    private fun contains(h: String, from: Int): Boolean {
        val ix = index
        var lo = 0
        var hi = ix.start.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(ix, mid, h, from)
            if (c == 0) return true
            if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        return false
    }

    /** Compares list line [i] with h[from..]; <0 when the line sorts before. */
    private fun compare(ix: Index, i: Int, h: String, from: Int): Int {
        var a = ix.start[i]
        val aEnd = ix.end[i]
        var b = from
        while (a < aEnd && b < h.length) {
            val x = ix.data[a].toInt() and 0xFF
            val y = h[b].code
            if (x != y) return x - y
            a++; b++
        }
        return (aEnd - a) - (h.length - b)
    }
}

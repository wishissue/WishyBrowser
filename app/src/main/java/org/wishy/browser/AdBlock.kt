package org.wishy.browser

/**
 * Ad requests that live on hosts too useful to block outright (YouTube, Google, Facebook, X), so the host blocklist
 * cannot catch them. Matched by host and path. Anything uncertain is left out: a wrong rule breaks a page, a missing
 * one only lets a tracker through (the page-side layer in adblock.js still catches the visible ad).
 *
 * Plain Kotlin on purpose (strings in, boolean out, no android.net.Uri): tools/adblock-test checks it on a normal JVM.
 */
object AdRules {
    private class P(val path: String, val query: String? = null, val exact: Boolean = false) {
        fun hit(p: String, q: String?): Boolean {
            val pathOk = if (exact) p == path || p == "$path/" else p.contains(path)
            return pathOk && (query == null || (q != null && q.contains(query)))
        }
    }

    private val YT_HOSTS = listOf("youtube.com", "youtube-nocookie.com", "youtubekids.com")

    /** YouTube's own ad and ad-measurement endpoints. */
    private val YT = listOf(
        P("/api/stats/ads"), P("/pagead/"), P("/ptracking"), P("/get_midroll_"),
        P("/youtubei/v1/player/ad_break"), P("/api/stats/qoe", query = "adformat="),
    )

    private val BY_HOST: Map<String, List<P>> = mapOf(
        "google.com" to listOf(P("/pagead/"), P("/ads/ga-audiences"), P("/ads/measurement")),
        "facebook.com" to listOf(P("/tr", exact = true)),                 // the Meta pixel
        "connect.facebook.net" to listOf(P("/fbevents.js")),
        "twitter.com" to listOf(P("/i/adsct")),
        "x.com" to listOf(P("/i/adsct")),
    )

    /** Google's ad paths, wherever they are served from. */
    private val ANYWHERE = listOf(
        P("/pagead/"), P("/gampad/ads"), P("/pcs/activeview"), P("/gpt/pubads_impl"), P("/tag/js/gpt.js"),
    )

    private fun isYouTube(host: String) = YT_HOSTS.any { host == it || host.endsWith(".$it") }

    /**
     * @param host the request's host, @param encodedPath and @param encodedQuery as Uri.getEncodedPath()/getEncodedQuery().
     * @param youtube the "Block YouTube ads" switch, @param other the "Block ads and trackers" switch.
     */
    fun matches(host: String?, encodedPath: String?, encodedQuery: String?, youtube: Boolean, other: Boolean): Boolean {
        val hostLc = host?.lowercase() ?: return false
        val path = encodedPath ?: ""
        if (youtube && isYouTube(hostLc) && YT.any { it.hit(path, encodedQuery) }) return true
        if (!other) return false
        var h = hostLc
        while (true) {
            BY_HOST[h]?.let { rules -> if (rules.any { it.hit(path, encodedQuery) }) return true }
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return ANYWHERE.any { it.hit(path, encodedQuery) }
    }

    /** YouTube's JSON endpoints get "{}" back instead of nothing, so its player does not stall on a parse error. */
    fun wantsJson(encodedPath: String?): Boolean = (encodedPath ?: "").startsWith("/youtubei/")
}

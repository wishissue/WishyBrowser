package org.wishy.browser.engine

import android.net.Uri

/** Pure URL rules: tracking-parameter removal, HTTPS upgrade, third-party test. */
object UrlPolicy {
    private val TRACKING = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "igshid",
        "mc_eid", "mc_cid", "_hsenc", "_hsmi", "mkt_tok", "vero_id", "oly_enc_id", "oly_anon_id",
        "_openstat", "__s", "ttclid", "li_fat_id",
    )

    private fun isTracking(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("utm_") || n in TRACKING
    }

    /** Returns [u] without known tracking parameters (same instance when there was nothing to remove). */
    fun stripTracking(u: Uri): Uri {
        if (!u.isHierarchical) return u
        val names = u.queryParameterNames
        if (names.none { isTracking(it) }) return u
        val b = u.buildUpon().clearQuery()
        for (name in names) {
            if (isTracking(name)) continue
            for (v in u.getQueryParameters(name)) b.appendQueryParameter(name, v)
        }
        return b.build()
    }

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Public-looking http host on the default port. Never upgrades IPs, localhost or LAN names. */
    fun canUpgrade(u: Uri): Boolean {
        if (!u.scheme.equals("http", true)) return false
        val h = u.host?.lowercase() ?: return false
        if (u.port != -1 && u.port != 80) return false
        if (h == "localhost" || IPV4.matches(h) || h.contains(':')) return false
        if (!h.contains('.')) return false
        return !(h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".internal") || h.endsWith(".home.arpa"))
    }

    fun upgrade(u: Uri): Uri = u.buildUpon().scheme("https").build()

    /** Rough same-site test (compares the last two labels; wrong for co.uk-style suffixes, which only makes blocking slightly less strict). */
    fun isThirdParty(pageHost: String?, reqHost: String?): Boolean {
        if (pageHost.isNullOrEmpty() || reqHost.isNullOrEmpty()) return false
        val a = pageHost.lowercase(); val b = reqHost.lowercase()
        if (a == b) return false
        return site(a) != site(b)
    }

    private fun site(h: String): String {
        val p = h.split('.')
        return if (p.size <= 2) h else p.takeLast(2).joinToString(".")
    }
}

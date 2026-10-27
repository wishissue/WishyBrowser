package org.wishy.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import androidx.compose.ui.graphics.vector.ImageVector
import org.wishy.browser.ui.Sym
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Domain blocklist (Peter Lowe's ad servers + EasyPrivacy domain rules, ~46k hosts, ~1 MB in assets,
 * ~250 KB inside the APK). Lookup walks up the host: a.b.example.com -> b.example.com -> example.com.
 */
object Blocklist {
    @Volatile private var set: HashSet<String> = HashSet()
    @Volatile var loaded = false
        private set

    fun load(ctx: Context) {
        val s = HashSet<String>(64_000)
        runCatching {
            ctx.assets.open("blocklist.txt").bufferedReader().useLines { lines -> lines.forEach { if (it.isNotEmpty()) s.add(it) } }
        }
        set = s
        loaded = s.isNotEmpty()
        // Compose state must be written on the main thread.
        android.os.Handler(android.os.Looper.getMainLooper()).post { Shields.domains = s.size; Shields.ready = true }
    }

    fun isBlocked(host: String?): Boolean {
        if (!loaded || host.isNullOrEmpty()) return false
        val s = set
        var h: String = host.lowercase()
        while (true) {
            if (s.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false // never test a bare TLD
            h = h.substring(dot + 1)
        }
    }
}

/** What the engine layer is allowed to do, kept in one place so the privacy story is easy to audit. */
object Privacy {
    val EMPTY = ByteArray(0)
    fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(EMPTY))

    /** Request headers added to every page load we start. (WebView cannot add headers to sub-requests.) */
    val HEADERS = mapOf("Sec-GPC" to "1", "DNT" to "1")

    private val TRACKING = Regex("^(utm_[a-z]+|fbclid|gclid|gclsrc|dclid|msclkid|mc_eid|mc_cid|igshid|yclid|_hsenc|_hsmi|vero_id|oly_enc_id|oly_anon_id|ref_src|ref_url|twclid|wickedid|spm|s_cid)$", RegexOption.IGNORE_CASE)

    /** Returns the URL without tracking parameters, or the same string when nothing was removed. */
    fun clean(url: String): String {
        val u = Uri.parse(url)
        if (u.isOpaque || u.query == null) return url
        val names = u.queryParameterNames
        if (names.none { TRACKING.matches(it) }) return url
        val b = u.buildUpon().clearQuery()
        for (n in names) if (!TRACKING.matches(n)) for (v in u.getQueryParameters(n)) b.appendQueryParameter(n, v)
        return b.build().toString()
    }

    private val IP = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Hosts that are never upgraded to https: LAN addresses, localhost and single-label names (routers, NAS, Plex). */
    fun isLocal(host: String?): Boolean {
        if (host == null) return true
        if (IP.matches(host) || host.contains(':')) return true
        return host == "localhost" || !host.contains('.') || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")
    }

    /**
     * Same UA as the system WebView with the device model and the "wv" marker removed, so pages see one common
     * Chrome instead of a rare "Android TV model X". Keeps the real Chrome version (sites check it).
     */
    fun userAgent(ctx: Context, desktop: Boolean, common: Boolean = true): String {
        val def = WebSettings.getDefaultUserAgent(ctx)
        if (!common && !desktop) return def
        val chrome = Regex("Chrome/([\\d.]+)").find(def)?.groupValues?.get(1) ?: "120.0.0.0"
        return if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
        else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36"
    }
}

/** Where typed words go. DuckDuckGo is the default; the rest are privacy-minded alternatives plus the big two. */
enum class SearchEngine(val id: String, val label: String, private val template: String) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    DUCKDUCKGO_LITE("duckduckgo_lite", "DuckDuckGo Lite", "https://lite.duckduckgo.com/lite/?q=%s"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    QWANT("qwant", "Qwant", "https://www.qwant.com/?q=%s"),
    MOJEEK("mojeek", "Mojeek", "https://www.mojeek.com/search?q=%s"),
    ECOSIA("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s");

    fun searchUrl(query: String): String = template.replace("%s", Uri.encode(query))

    companion object {
        fun byId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

/**
 * Every switch on the Privacy page. All protections are on by default; each can be turned off by the user.
 * [default] is the value used before the user has chosen.
 */
enum class Opt(val key: String, val group: String, val title: String, val desc: String, val default: Boolean = true) {
    TRACKERS("trackers", "Blocking", "Block ads and trackers", "Drops requests to about 46,000 ad and tracking hosts"),
    COOKIE_BANNERS("banners", "Blocking", "Hide cookie pop-ups", "Hides common consent banners and unlocks scrolling"),
    POPUPS("popups", "Blocking", "Block pop-ups", "New windows open only when you click, always in the same tab"),
    AUTOPLAY("autoplay", "Blocking", "Block autoplay", "Video and audio start only after you press OK"),
    THIRD_PARTY_COOKIES("tp_cookies", "Tracking", "Block third-party cookies", "Stops sites following you across other sites"),
    CLEAN_LINKS("clean_links", "Tracking", "Clean tracking links", "Removes utm_, fbclid, gclid and similar parameters"),
    REFERRER("referrer", "Tracking", "Hide where you came from", "Links to other sites are opened without a referrer"),
    GPC("gpc", "Tracking", "Send Global Privacy Control", "Tells sites not to sell or share your data (also Do Not Track)"),
    FINGERPRINT("fingerprint", "Identity", "Fingerprint protection", "Adds noise to canvas and hides battery, memory and CPU details"),
    COMMON_IDENTITY("identity", "Identity", "Common browser identity", "Hides your TV model and WebView build from sites"),
    HTTPS_UPGRADE("https", "Connection", "Upgrade to HTTPS", "Opens http:// pages over https when the site supports it"),
    LOCATION("location", "Device", "Block location", "Sites can never ask for your position"),
    FORMS("forms", "Device", "Don't save passwords or forms", "Nothing you type into a page is remembered"),
    WIPE_EXIT("wipe_exit", "Device", "Wipe data at exit", "Cookies, cache and history are erased when the app closes and starts"),
    LITE("lite", "Speed", "Lite mode (no images)", "Loads pages much faster on weak TVs; off by default", default = false);

    val icon: ImageVector
        get() = when (this) {
            TRACKERS -> Sym.AdOff
            COOKIE_BANNERS -> Sym.Cookie
            POPUPS -> Sym.Block
            AUTOPLAY -> Sym.PlayArrow
            THIRD_PARTY_COOKIES -> Sym.Cookie
            CLEAN_LINKS -> Sym.LinkOff
            REFERRER -> Sym.VisibilityOff
            GPC -> Sym.Policy
            FINGERPRINT -> Sym.Fingerprint
            COMMON_IDENTITY -> Sym.Person
            HTTPS_UPGRADE -> Sym.Lock
            LOCATION -> Sym.PublicOff
            FORMS -> Sym.Key
            WIPE_EXIT -> Sym.DeleteSweep
            LITE -> Sym.Speed
        }
}

/** Counts blocked requests across threads; the controller reads and resets it. */
class BlockCounter { val n = AtomicInteger() }

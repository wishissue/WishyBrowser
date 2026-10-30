import org.wishy.browser.AdRules
import java.net.URI
import kotlin.system.exitProcess

var pass = 0; var fail = 0
fun m(url: String, yt: Boolean = true, other: Boolean = true): Boolean {
    val u = URI(url)
    return AdRules.matches(u.host, u.rawPath, u.rawQuery, yt, other)
}
fun check(name: String, ok: Boolean) { if (ok) { pass++; println("  ok    $name") } else { fail++; println("  FAIL  $name") } }

fun main() {
    println("\nMust be blocked")
    for (u in listOf(
        "https://www.youtube.com/api/stats/ads?ver=2&ad_cpn=x", "https://www.youtube.com/pagead/viewthroughconversion/1/",
        "https://m.youtube.com/ptracking?pltype=adhost", "https://www.youtube.com/get_midroll_info?x=1",
        "https://www.youtube.com/youtubei/v1/player/ad_break?key=1", "https://www.youtube.com/api/stats/qoe?adformat=15_2_1&cpn=a",
        "https://www.youtube-nocookie.com/api/stats/ads", "https://music.youtube.com/api/stats/ads",
        "https://www.google.com/pagead/1p-user-list/123", "https://www.google.com/ads/ga-audiences?v=1",
        "https://www.facebook.com/tr?id=1&ev=PageView", "https://www.facebook.com/tr/", "https://connect.facebook.net/en_US/fbevents.js",
        "https://x.com/i/adsct?p_id=1", "https://twitter.com/i/adsct",
        "https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-1", "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
        "https://securepubads.g.doubleclick.net/gampad/ads?iu=/1/x", "https://www.googletagservices.com/tag/js/gpt.js",
    )) check(u, m(u))

    println("\nMust NOT be blocked (blocking these breaks pages)")
    for (u in listOf(
        "https://www.youtube.com/watch?v=abc", "https://www.youtube.com/youtubei/v1/player?key=1", "https://www.youtube.com/youtubei/v1/next",
        "https://www.youtube.com/youtubei/v1/browse", "https://www.youtube.com/youtubei/v1/search", "https://www.youtube.com/youtubei/v1/log_event",
        "https://www.youtube.com/api/stats/playback?cpn=1", "https://www.youtube.com/api/stats/watchtime?cpn=1",
        "https://www.youtube.com/api/stats/qoe?cpn=1&fmt=18", "https://www.youtube.com/s/player/abc/player_ias.vflset/en_US/base.js",
        "https://rr1---sn-abc.googlevideo.com/videoplayback?id=1", "https://www.youtube.com/generate_204",
        "https://www.google.com/search?q=cats", "https://www.google.com/maps", "https://accounts.google.com/signin",
        "https://www.facebook.com/", "https://www.facebook.com/track", "https://www.facebook.com/trending", "https://www.facebook.com/tr/x/y",
        "https://x.com/home", "https://example.com/pages/about", "https://example.com/advert-policy",
    )) check(u, !m(u))

    println("\nSwitches")
    check("YouTube switch off + trackers off: nothing blocked", !m("https://www.youtube.com/api/stats/ads", yt = false, other = false))
    check("YouTube switch off, trackers on: YouTube ad endpoint left to host list", !m("https://www.youtube.com/api/stats/ads", yt = false, other = true))
    check("YouTube switch on, trackers off: YouTube ad endpoint blocked", m("https://www.youtube.com/api/stats/ads", yt = true, other = false))
    check("trackers off: google /pagead/ path not blocked by path rules", !m("https://www.google.com/pagead/x", yt = true, other = false))
    check("trackers off: facebook pixel not blocked", !m("https://www.facebook.com/tr?id=1", yt = true, other = false))

    println("\nEdge cases")
    check("null host is never blocked", !AdRules.matches(null, "/pagead/", null, true, true))
    check("null path and query do not crash", !AdRules.matches("example.com", null, null, true, true))
    check("host match is case-insensitive", AdRules.matches("WWW.YOUTUBE.COM", "/api/stats/ads", null, true, false))
    check("lookalike host evilyoutube.com is not YouTube", !AdRules.matches("evilyoutube.com", "/api/stats/ads", null, true, true))
    check("youtubei paths get JSON back", AdRules.wantsJson("/youtubei/v1/player/ad_break"))
    check("other paths get an empty body", !AdRules.wantsJson("/api/stats/ads") && !AdRules.wantsJson(null))

    println("\n$pass passed, $fail failed")
    exitProcess(if (fail == 0) 0 else 1)
}

package org.wishy.browser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What the full-screen card on top of the browser is saying. */
sealed interface Promo {
    /** A newer version exists. The card only informs; nothing is downloaded. */
    class Update(val current: String, val version: String, val notes: String) : Promo

    /** The "buy me a coffee" reminder. [months] is how long the app has been in use. */
    class Donate(val months: Int) : Promo
}

/**
 * Looks for a newer release. One plain HTTPS GET to the project's public GitHub release list, no cookies, no device
 * or user information, at most once a day, and it can be switched off on the Privacy page. Nothing is downloaded.
 */
object UpdateCheck {
    private const val API = "https://api.github.com/repos/wishissue/WishyBrowser/releases/latest"

    class Release(val version: String, val notes: String)

    /** Blocking: call off the UI thread. Throws on any network or parse problem. */
    fun fetchLatest(): Release {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "WishyBrowser")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode !in 200..299) error("GitHub answered ${c.responseCode}")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val tag = o.getString("tag_name").trim().removePrefix("v").removePrefix("V")
            return Release(tag, o.optString("body", "").trim())
        } finally {
            c.disconnect()
        }
    }

    /** "4.2.0" is newer than "4.1.0"; compares number by number, ignores any text. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = Regex("\\d+").findAll(remote).map { it.value.toIntOrNull() ?: 0 }.toList()
        val l = Regex("\\d+").findAll(local).map { it.value.toIntOrNull() ?: 0 }.toList()
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

/** When the coffee reminder is due: 1 month after first use, then every 3 months. */
object DonateSchedule {
    private const val DAY = 24L * 60 * 60 * 1000
    const val FIRST_AFTER_DAYS = 30
    const val REPEAT_AFTER_DAYS = 91

    fun isDue(now: Long, firstRun: Long, lastAsk: Long): Boolean =
        if (lastAsk <= 0L) now - firstRun >= FIRST_AFTER_DAYS * DAY
        else now - lastAsk >= REPEAT_AFTER_DAYS * DAY

    fun monthsUsed(now: Long, firstRun: Long): Int = ((now - firstRun) / (30 * DAY)).toInt().coerceAtLeast(1)
}

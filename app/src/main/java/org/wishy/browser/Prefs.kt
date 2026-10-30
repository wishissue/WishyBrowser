package org.wishy.browser

import android.content.Context

/**
 * The few user choices the app remembers (all local, all optional).
 */
class Prefs(context: Context) {

    private val appCtx = context.applicationContext
    private val sp = context.getSharedPreferences("wishy_prefs", Context.MODE_PRIVATE)

    var homeUrl: String?
        get() = sp.getString("home_url", null)
        set(value) = sp.edit().putString("home_url", value).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop_mode", true)
        set(value) = sp.edit().putBoolean("desktop_mode", value).apply()

    /** 1.0 = normal. Larger values make page text bigger for couch-distance reading. */
    var textScale: Float
        get() = sp.getFloat("text_scale", 1.0f)
        set(value) = sp.edit().putFloat("text_scale", value).apply()

    var onboardingShown: Boolean
        get() = sp.getBoolean("onboarding_shown", false)
        set(value) = sp.edit().putBoolean("onboarding_shown", value).apply()

    /** One of the 45 palette ids in ui/theme/Palettes.kt. */
    var themeId: String
        get() = sp.getString("theme_id", "wishy") ?: "wishy"
        set(value) = sp.edit().putString("theme_id", value).apply()

    /** "dark", "light", "dusk" or "oled". */
    var themeMode: String
        get() = sp.getString("theme_mode", "dark") ?: "dark"
        set(value) = sp.edit().putString("theme_mode", value).apply()

    var cursorId: String
        get() = sp.getString("cursor_id", "") ?: ""
        set(value) = sp.edit().putString("cursor_id", value).apply()

    /** Turns every animation into an instant change (for very weak hardware). */
    var reduceMotion: Boolean
        // On by default: fewer animations, faster page loads on weak TVs.
        get() = sp.getBoolean("reduce_motion", true)
        set(value) = sp.edit().putBoolean("reduce_motion", value).apply()

    /** Timestamp of the newest process exit already reported by ExitReport. */
    var lastExitSeen: Long
        get() = sp.getLong("last_exit_seen", 0L)
        set(value) = sp.edit().putLong("last_exit_seen", value).apply()

    /** Pointer size multiplier (see CURSOR_SCALES). */
    var cursorScale: Float
        get() = sp.getFloat("cursor_scale", 1.0f)
        set(value) = sp.edit().putFloat("cursor_scale", value).apply()

    /** Show a notification card when ads/trackers were blocked on a page. */
    var notifyBlocked: Boolean
        get() = sp.getBoolean("notify_blocked", true)
        set(value) = sp.edit().putBoolean("notify_blocked", value).apply()

    /** Keep page previews (thumbnails). Bookmarks keep theirs on disk; everything else stays in memory only. */
    var thumbsEnabled: Boolean
        get() = sp.getBoolean("thumbs_enabled", true)
        set(value) = sp.edit().putBoolean("thumbs_enabled", value).apply()

    /** Which blocker configuration was last installed. Bumped when the default filter lists change. */
    var shieldsProfile: Int
        get() = sp.getInt("shields_profile", 0)
        set(value) = sp.edit().putInt("shields_profile", value).apply()

    /** JSON list of the user's own cursors (see CursorLibrary). */
    var customCursors: String
        get() = sp.getString("custom_cursors", "[]") ?: "[]"
        set(value) = sp.edit().putString("custom_cursors", value).apply()

    /** Id of the search engine typed words go to (see SearchEngine). DuckDuckGo until the user picks another. */
    var searchEngine: String
        get() = sp.getString("search_engine", SearchEngine.DUCKDUCKGO.id) ?: SearchEngine.DUCKDUCKGO.id
        set(value) = sp.edit().putString("search_engine", value).apply()

    /** First time the app was opened; the donation reminders count from here. */
    var firstRunAt: Long
        get() = sp.getLong("first_run_at", 0L)
        set(value) = sp.edit().putLong("first_run_at", value).apply()

    /** When the coffee reminder was last shown (0 = never). */
    var lastDonateAsk: Long
        get() = sp.getLong("last_donate_ask", 0L)
        set(value) = sp.edit().putLong("last_donate_ask", value).apply()

    /** Ask GitHub once a day whether a newer version exists. Only informs; nothing is downloaded. */
    var updateCheck: Boolean
        get() = sp.getBoolean("update_check", true)
        set(value) = sp.edit().putBoolean("update_check", value).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    /** The newest version the user was already told about, so each version is announced once. */
    var lastUpdateNotified: String
        get() = sp.getString("last_update_notified", "") ?: ""
        set(value) = sp.edit().putString("last_update_notified", value).apply()

    /** Where the toolbar sits: "top", "bottom", "left" or "right" (see BarSide). */
    var barSide: String
        get() = sp.getString("bar_side", "top") ?: "top"
        set(value) = sp.edit().putString("bar_side", value).apply()

    /** Full-screen mode: no toolbar and no system bars until you ask for them. */
    var hideBar: Boolean
        get() = sp.getBoolean("hide_bar", false)
        set(value) = sp.edit().putBoolean("hide_bar", value).apply()

    /** The notification permission is asked for once, when the first download starts. */
    var notifAsked: Boolean
        get() = sp.getBoolean("notif_asked", false)
        set(value) = sp.edit().putBoolean("notif_asked", value).apply()

    /** Sites where the ad layer is switched off by the user (for pages that break). Comma-separated hosts. */
    var allowSites: Set<String>
        get() = (sp.getString("allow_sites", "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
        set(value) = sp.edit().putString("allow_sites", value.joinToString(",")).apply()

    fun opt(o: Opt): Boolean = sp.getBoolean("opt_" + o.key, o.default)
    fun setOpt(o: Opt, value: Boolean) = sp.edit().putBoolean("opt_" + o.key, value).apply()

    companion object {
        /** About 2 GB of RAM or less. */
        fun isWeakDevice(ctx: Context): Boolean {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return false
            val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return am.isLowRamDevice || mi.totalMem <= 2_300L * 1024 * 1024
        }

        val TEXT_SCALES = floatArrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val CURSOR_SCALES = floatArrayOf(0.8f, 1.0f, 1.3f, 1.7f)
        val CURSOR_SCALE_LABELS = listOf("Small", "Medium", "Large", "Extra large")
    }
}

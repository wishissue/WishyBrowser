package org.wishy.browser

import android.content.Context

/**
 * The few user choices the app remembers (all local, all optional):
 * a custom home page, desktop-site mode and text size.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("wishy_prefs", Context.MODE_PRIVATE)

    var homeUrl: String?
        get() = sp.getString("home_url", null)
        set(value) = sp.edit().putString("home_url", value).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop_mode", false)
        set(value) = sp.edit().putBoolean("desktop_mode", value).apply()

    /** 1.0 = normal. Larger values make page text bigger for couch-distance reading. */
    var textScale: Float
        get() = sp.getFloat("text_scale", 1.0f)
        set(value) = sp.edit().putFloat("text_scale", value).apply()

    /** True once the one-time "how this works" explainer has been shown. */
    var onboardingShown: Boolean
        get() = sp.getBoolean("onboarding_shown", false)
        set(value) = sp.edit().putBoolean("onboarding_shown", value).apply()

    companion object {
        val TEXT_SCALES = floatArrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}

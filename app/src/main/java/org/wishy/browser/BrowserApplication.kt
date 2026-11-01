package org.wishy.browser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import org.wishy.browser.engine.Blocker

/**
 * No engine runtime to start any more: pages are drawn by the device's own Android System WebView.
 * This class only owns the blocker and reads why the previous run ended.
 *
 * No analytics, crash reporter, accounts or background network work exist in this app.
 */
class BrowserApplication : Application() {

    val blocker: Blocker by lazy { Blocker(this) }

    override fun onCreate() {
        super.onCreate()
        readLastExit()
    }

    /**
     * Reads Android's own process-exit history (API 30+) and keeps a one-line summary of the newest
     * real problem (out of memory, native crash, ANR...) for [ExitReport]. No logcat or adb needed.
     */
    private fun readLastExit() {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return
            val prefs = Prefs(this)
            val fresh = am.getHistoricalProcessExitReasons(packageName, 0, 10).filter { it.timestamp > prefs.lastExitSeen }
            if (fresh.isEmpty()) return
            prefs.lastExitSeen = fresh.maxOf { it.timestamp }
            val problems = setOf(
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY, android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE, android.app.ApplicationExitInfo.REASON_ANR,
                android.app.ApplicationExitInfo.REASON_SIGNALED, android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            )
            val worst = fresh.firstOrNull { it.reason in problems } ?: return
            val what = when (worst.reason) {
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android ran out of memory"
                android.app.ApplicationExitInfo.REASON_CRASH -> "App crash"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                android.app.ApplicationExitInfo.REASON_ANR -> "App stopped responding"
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Used too many resources"
                else -> "Stopped by Android"
            }
            val summary = "$what (${worst.rss / 1024} MB)"
            Log.w(TAG, "Previous exit: $summary ${worst.description.orEmpty()}")
            ExitReport.pending = summary
        }.onFailure { Log.w(TAG, "Could not read exit history", it) }
    }

    companion object {
        private const val TAG = "WishyBrowser"
        fun from(app: Application): BrowserApplication = app as BrowserApplication
    }
}

/** Why the previous run ended, shown once as a notification card. */
object ExitReport {
    @Volatile var pending: String? = null
    fun take(): String? { val t = pending; pending = null; return t }
}

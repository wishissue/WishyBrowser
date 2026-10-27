package org.wishy.browser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Tiny on purpose: no engine to boot. Loads the blocklist on a background thread and reads the last exit reason. */
class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The system WebView runs its own renderer process; this app has no helper processes of its own.
        readLastExit()
        Thread({ Blocklist.load(this) }, "wishy-blocklist").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Android keeps the process-exit history (API 30+); one line of it is shown on the next launch. */
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
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            )
            val worst = fresh.firstOrNull { it.reason in problems } ?: return
            val what = when (worst.reason) {
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android ran out of memory"
                android.app.ApplicationExitInfo.REASON_CRASH -> "App crash"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                android.app.ApplicationExitInfo.REASON_ANR -> "App stopped responding"
                else -> "Used too many resources"
            }
            ExitReport.pending = "$what (${worst.rss / 1024} MB)"
        }.onFailure { Log.w("Wishy", "Could not read exit history", it) }
    }

    companion object {
        fun from(app: Application): BrowserApplication = app as BrowserApplication
    }
}

/** Why the previous run ended, shown once as a notification card. */
object ExitReport {
    @Volatile var pending: String? = null
    fun take(): String? { val t = pending; pending = null; return t }
}

/** Live state of the built-in blocker, read by the UI. */
object Shields {
    /** True once the blocklist is parsed. */
    var ready by mutableStateOf(false)
    var domains by mutableIntStateOf(0)
    val blockerActive: Boolean get() = ready && domains > 0
}

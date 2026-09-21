package org.wishy.browser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Owns the single [GeckoRuntime] for the process and configures every
 * privacy-related setting GeckoView exposes. This is the one place all of
 * the "block trackers / block cookies / resist fingerprinting / no
 * telemetry" requirements are wired up.
 *
 * We deliberately do NOT link Mozilla's Glean telemetry SDK, crash
 * reporter, Nimbus experiments/"sponsored content" library, or any
 * Firefox Account / Sync component. GeckoView itself does not phone home
 * on its own — those pieces are separate optional libraries that Firefox
 * (the product) adds on top, and this app simply never depends on them.
 */
class BrowserApplication : Application() {

    lateinit var runtime: GeckoRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        // Application.onCreate() runs in EVERY process this app owns, and
        // GeckoView spawns its own helper processes (":tab17", ":tab39", ...).
        // Creating a GeckoRuntime inside those helpers starts a whole extra
        // browser engine per helper, which piles up processes until Android
        // runs out of memory and kills the app. Only the main process may
        // create the runtime; helpers must do nothing here.
        if (!isMainProcess()) return

        @Suppress("WrongConstant")
        val contentBlocking = ContentBlocking.Settings.Builder()
            // Block trackers, cryptominers, fingerprinters and social trackers.
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            // Third-party cookies are isolated per site (Firefox's "Total
            // Cookie Protection", the behavior behind Strict ETP).
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .strictSocialTrackingProtection(true)
            // Remove tracking parameters (utm_*, fbclid, ...) from links.
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .emailTrackerBlockingPrivateMode(true)
            .cookiePurging(true)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(contentBlocking)
            .consoleOutput(false)
            .aboutConfigEnabled(false)
            .build()

        runtime = GeckoRuntime.create(this, settings)

        runtime.settings.apply {
            // Fingerprinting protection (Resist Fingerprinting / RFP)
            // requires GeckoView 130+.
            setFingerprintingProtection(true)
            setFingerprintingProtectionPrivateBrowsing(true)

            // Tell every site not to sell or share the visit (Global Privacy Control).
            setGlobalPrivacyControl(true)

            // Remembered text size (Menu -> Text size), for reading from the couch.
            setAutomaticFontSizeAdjustment(false)
            setFontSizeFactor(Prefs(this@BrowserApplication).textScale)
        }

        applyPrivacyPrefs()
    }

    /**
     * Engine preferences that have no dedicated GeckoView setting. Each one
     * switches off a feature that would otherwise contact a third party or
     * that this app cannot support anyway. Failures are logged, never fatal.
     */
    private fun applyPrivacyPrefs() {
        val off = listOf(
            // Telemetry, studies and experiments: nothing is collected or uploaded.
            "toolkit.telemetry.enabled",
            "datareporting.healthreport.uploadEnabled",
            "datareporting.policy.dataSubmissionEnabled",
            "app.shield.optoutstudies.enabled",
            "app.normandy.enabled",
            // Background network checks to Mozilla servers.
            "network.captive-portal-service.enabled",
            "network.connectivity-service.enabled",
            // Push messaging would keep a connection open to a push server.
            "dom.push.enabled",
            // Web notifications and location: not supported by this app.
            "dom.webnotifications.enabled",
            "geo.enabled",
            // Passkeys / security keys need Google Play Services (proprietary),
            // which this build leaves out. Sites fall back to passwords.
            "security.webauth.webauthn"
        )
        for (pref in off) {
            try {
                GeckoPreferenceController
                    .setGeckoPref(pref, false, GeckoPreferenceController.PREF_BRANCH_USER)
                    .accept({ }, { e -> Log.w(TAG, "Could not set $pref", e) })
            } catch (e: Exception) {
                Log.w(TAG, "Could not set $pref", e)
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val pid = Process.myPid()
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
        return name == packageName
    }

    companion object {
        private const val TAG = "WishyBrowser"

        fun from(app: Application): BrowserApplication = app as BrowserApplication
    }
}

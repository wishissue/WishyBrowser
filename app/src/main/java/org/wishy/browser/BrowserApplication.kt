package org.wishy.browser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension

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

    @ExperimentalGeckoViewApi
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
            // Anti-tracking & cookie isolation (Total Cookie Protection)
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
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

        // uBlock Origin + Wishy Core. ensureBuiltIn (not installBuiltIn) keeps them
        // installed between launches, so uBlock keeps its compiled filter lists
        // instead of rebuilding them from scratch every time the app starts, and
        // each one is explicitly allowed to run in the always-private session.
        startExtensions()

        runtime.settings.apply {
            // Disable full RFP (Resist Fingerprinting) so modern web apps (GitHub, React, WASM)
            // don't enter infinite loops or break on timer/worker restrictions.
            setFingerprintingProtection(false)
            setFingerprintingProtectionPrivateBrowsing(false)

            // Tell every site not to sell or share the visit (Global Privacy Control).
            setGlobalPrivacyControl(true)

            // Remembered text size (Menu -> Text size), for reading from the couch.
            setAutomaticFontSizeAdjustment(false)
            setFontSizeFactor(Prefs(this@BrowserApplication).textScale)
        }

        applyPrivacyPrefs()
        applySecurityPrefs()
        applyMemoryPrefs()
    }

    private fun startExtensions() {
        var pending = 2
        fun finished() {
            if (--pending == 0) {
                Shields.ready = true
                // Some results are stubs without metadata; list() fills in the real state.
                refreshShields()
            }
        }
        // When the default filter lists change (SHIELDS_PROFILE goes up) the blocker is installed
        // fresh once, so the new defaults apply even to people who already had the app.
        val prefs = Prefs(this)
        val installBlocker = {
            ensureAllowed(UBLOCK_URI, UBLOCK_ID) { ext ->
                Shields.ublock = ExtInfo.of(ext)
                Shields.ublockExt = ext
                finished()
            }
        }
        if (prefs.shieldsProfile < SHIELDS_PROFILE) {
            prefs.shieldsProfile = SHIELDS_PROFILE
            removeBlocker { installBlocker() }
        } else installBlocker()
        ensureAllowed(CORE_URI, CORE_ID) { ext ->
            Shields.core = ext
            Shields.coreInfo = ExtInfo.of(ext)
            finished()
        }
    }

    /**
     * Installs the built-in extension if needed, then opts it in to private
     * browsing. Gecko does NOT run extensions in private windows unless the
     * embedder does this, and every session here is private. Verified: with
     * this step missing, uBlock blocks nothing in a private window.
     */
    private fun ensureAllowed(uri: String, id: String, done: (WebExtension?) -> Unit) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(uri, id).accept({ ext ->
            if (ext == null) {
                Log.w(TAG, "ensureBuiltIn($id) returned null")
                done(null)
            } else {
                controller.setAllowedInPrivateBrowsing(ext, true).accept({ updated ->
                    Log.i(TAG, "Extension $id allowed in private browsing")
                    done(updated ?: ext)
                }, { e ->
                    Log.w(TAG, "Could not allow $id in private browsing", e)
                    done(ext)
                })
            }
        }, { e ->
            Log.w(TAG, "Could not install built-in extension $id", e)
            Shields.error = e?.message ?: "install failed"
            done(null)
        })
    }

    /** Re-reads the extension states for the Shields page. */
    fun refreshShields() {
        runtime.webExtensionController.list().accept({ list ->
            Shields.ublock = list?.firstOrNull { it.id == UBLOCK_ID }?.let { ExtInfo.of(it) }
            Shields.coreInfo = list?.firstOrNull { it.id == CORE_ID }?.let { ExtInfo.of(it) }
        }, { e -> Log.w(TAG, "Could not list extensions", e) })
    }

    /** Removes uBlock if it is installed, then calls [then] (also when it was not installed or the lookup failed). */
    private fun removeBlocker(then: () -> Unit) {
        val controller = runtime.webExtensionController
        controller.list().accept({ list ->
            val old = list?.firstOrNull { it.id == UBLOCK_ID }
            if (old == null) then()
            else controller.uninstall(old).accept({ then() }, { then() })
        }, { e ->
            Log.w(TAG, "Could not list extensions", e)
            then()
        })
    }

    /** Recovery action: remove uBlock completely and install it again, allowed in private mode. */
    fun resetShields() {
        Shields.ublock = null
        Shields.ublockExt = null
        removeBlocker {
            ensureAllowed(UBLOCK_URI, UBLOCK_ID) {
                Shields.ublock = ExtInfo.of(it)
                Shields.ublockExt = it
            }
        }
    }

    /**
     * Network & engine security preferences. Disabled forced DoH (TRR mode 0) to use fast Android system DNS
     * without DoH TLS connection timeouts on Google DNS servers.
     */
    @ExperimentalGeckoViewApi
    private fun applySecurityPrefs() {
        try {
            // DNS: Use Android native system DNS for fast, non-blocking resolution
            GeckoPreferenceController.setGeckoPref("network.trr.mode", 0, GeckoPreferenceController.PREF_BRANCH_USER).accept({ }, { })
            GeckoPreferenceController.setGeckoPref("dom.security.https_only_mode", false, GeckoPreferenceController.PREF_BRANCH_USER).accept({ }, { })
            // Enable IndexedDB in private browsing mode for modern web apps
            GeckoPreferenceController.setGeckoPref("dom.indexedDB.privateBrowsing.enabled", true, GeckoPreferenceController.PREF_BRANCH_USER).accept({ }, { })
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply security preferences", e)
        }
    }

    /**
     * Engine preferences that have no dedicated GeckoView setting. Each one
     * switches off a feature that would otherwise contact a third party or
     * that this app cannot support anyway. Failures are logged, never fatal.
     */
    @ExperimentalGeckoViewApi
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

    /**
     * Engine performance & memory tuning for smooth rendering on modern sites like GitHub.
     */
    @ExperimentalGeckoViewApi
    private fun applyMemoryPrefs() {
        try {
            // One history viewer: Back-Forward Cache (bfcache) kept to 1 page.
            GeckoPreferenceController.setGeckoPref("browser.sessionhistory.max_total_viewers", 1, GeckoPreferenceController.PREF_BRANCH_USER)
            // No spare process: Gecko only launches a content process when navigation starts.
            GeckoPreferenceController.setGeckoPref("dom.ipc.processPrelaunch.enabled", false, GeckoPreferenceController.PREF_BRANCH_USER)
            // 32MB memory cache: allows complex web apps (like GitHub) to cache scripts and styles without thrashing.
            GeckoPreferenceController.setGeckoPref("browser.cache.memory.capacity", 32768, GeckoPreferenceController.PREF_BRANCH_USER)

            // One content process: matches the single-tab browser design.
            GeckoPreferenceController.setGeckoPref("dom.ipc.processCount", 1, GeckoPreferenceController.PREF_BRANCH_USER)

            // Enable WebRender GPU acceleration for hardware-accelerated web page layout
            GeckoPreferenceController.setGeckoPref("gfx.webrender.all", true, GeckoPreferenceController.PREF_BRANCH_USER)

            // Decoded image cache (24 MB) to prevent image decoding overhead
            GeckoPreferenceController.setGeckoPref("image.mem.max_decoded_image_kb", 24576, GeckoPreferenceController.PREF_BRANCH_USER)

            // Script timeout: prevent unresponsive scripts from freezing the browser engine
            GeckoPreferenceController.setGeckoPref("dom.max_script_run_time", 15, GeckoPreferenceController.PREF_BRANCH_USER)

            // APZ smooth scrolling
            GeckoPreferenceController.setGeckoPref("apz.force_disable_autoscroll", true, GeckoPreferenceController.PREF_BRANCH_USER)
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply memory preferences", e)
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
        const val UBLOCK_ID = "uBlock0@raymondhill.net"
        const val CORE_ID = "core@wishy.org"
        /** Bump this when assets.json changes which filter lists are on by default. */
        private const val SHIELDS_PROFILE = 3
        private const val UBLOCK_URI = "resource://android/assets/extensions/ublock/"
        private const val CORE_URI = "resource://android/assets/extensions/wishy/"

        fun from(app: Application): BrowserApplication = app as BrowserApplication
    }
}

/** What the Shields page shows about one extension. */
class ExtInfo(val enabled: Boolean, val allowedPrivate: Boolean, val version: String) {
    companion object {
        fun of(ext: WebExtension?): ExtInfo? = ext?.let {
            val m = it.metaData
            ExtInfo(m?.enabled ?: false, m?.allowedInPrivateBrowsing ?: false, m?.version ?: "?")
        }
    }
}

/** Live state of the ad/tracker blocker and the Wishy Core extension. Read by the UI. */
object Shields {
    /** True once both extensions are installed and allowed (or have failed), so the first page can load. */
    var ready by mutableStateOf(false)
    var ublock by mutableStateOf<ExtInfo?>(null)
    /** The live uBlock extension; the browser reads its per-tab badge to count blocked requests. */
    var ublockExt by mutableStateOf<WebExtension?>(null)
    var coreInfo by mutableStateOf<ExtInfo?>(null)
    var core by mutableStateOf<WebExtension?>(null)
    var error by mutableStateOf<String?>(null)

    val blockerActive: Boolean get() = ublock?.let { it.enabled && it.allowedPrivate } == true
}

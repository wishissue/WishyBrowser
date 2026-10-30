package org.wishy.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.wishy.browser.ui.Sym
import org.wishy.browser.ui.pages.DONATE_URL
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/** The pages the menu can open. The back stack lives in [BrowserController.stack]. */
enum class Page { MENU, DOWNLOADS, BOOKMARKS, THEMES, CURSORS, SETTINGS, SHIELDS, PRIVACY, NOTIFICATIONS, DEVELOPER, CREDITS, DONATE }

/** A dialog a web page asked for, shown by the Compose PromptHost. */
sealed interface PromptRequest {
    class Alert(val title: String?, val message: String?, val done: () -> Unit) : PromptRequest
    class Confirm(val title: String?, val message: String?, val done: (Boolean) -> Unit) : PromptRequest
    class TextInput(val title: String?, val message: String?, val initial: String, val done: (String?) -> Unit) : PromptRequest
    class Permission(val host: String, val done: (Boolean) -> Unit) : PromptRequest
}

/** The few things only an Activity can do. */
interface ActivityBridge {
    fun pickFiles(intent: Intent, onResult: (Array<Uri>?) -> Unit)
    fun requestPermissions(perms: Array<String>, onResult: (Boolean) -> Unit)
    fun finishApp()
}

/**
 * Owns the single WebView, the D-pad pointer and every piece of state the Compose UI
 * shows. The UI only reads these fields and calls these functions.
 *
 * Remote cheat-sheet:
 *   MENU ......... toggle pointer      BACK ......... fullscreen -> page back -> toolbar -> exit
 *   SEARCH ....... address bar         BOOKMARK ..... bookmarks     CH+/CH- ... page down / up
 */
class BrowserController(
    private val activity: ComponentActivity,
    private val bridge: ActivityBridge,
) {
    val prefs = Prefs(activity.applicationContext)
    private val store = BookmarkStore(activity.applicationContext)
    val bookmarks: SnapshotStateList<Bookmark> = store.all().toMutableStateList()
    private val ui = Handler(Looper.getMainLooper())

    /** Page previews (see Thumbs.kt) and the pointer pictures (see CursorLibrary.kt). */
    val thumbs = ThumbStore(activity.applicationContext)
    val cursorLib = CursorLibrary(activity.applicationContext, prefs)

    /** Pages opened since the app started. Memory only: this browser keeps no history. */
    val recents: SnapshotStateList<Recent> = mutableStateListOf()
    private var webRef: WebView? = null
    /** The page view. Bumped when the renderer died and the view had to be rebuilt (the UI re-hosts it). */
    val web: WebView get() = webRef!!
    var webGen by mutableIntStateOf(0)
        private set
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var promptCancel: (() -> Unit)? = null
    private var upgradedTo: String? = null
    private var fallbackHttp: String? = null

    // ---- page state ----
    var url by mutableStateOf("about:blank")
        private set
    var title by mutableStateOf("")
        private set
    var progress by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var isFullScreen by mutableStateOf(false)
        private set
    var readerable by mutableStateOf(false)
        private set
    private var pageScrollY = 0

    val atHome: Boolean get() = url.isBlank() || (url.startsWith("about:") && !url.startsWith("about:reader"))

    // ---- appearance / settings (mirrors Prefs so Compose recomposes) ----
    var themeId by mutableStateOf(prefs.themeId)
        private set
    @set:JvmName("internalSetThemeMode")
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var cursorId by mutableStateOf(prefs.cursorId)
        private set
    @set:JvmName("internalSetReduceMotion")
    var reduceMotion by mutableStateOf(prefs.reduceMotion)
        private set
    var desktopMode by mutableStateOf(prefs.desktopMode)
        private set
    var textScale by mutableFloatStateOf(prefs.textScale)
        private set
    var homeUrl by mutableStateOf(prefs.homeUrl)
        private set
    var cursorScale by mutableFloatStateOf(prefs.cursorScale)
        private set
    var notifyBlocked by mutableStateOf(prefs.notifyBlocked)
        private set
    var thumbsEnabled by mutableStateOf(prefs.thumbsEnabled)
        private set
    @set:JvmName("internalSetSearchEngine")
    var searchEngine by mutableStateOf(SearchEngine.byId(prefs.searchEngine))
        private set

    // ---- privacy switches (see Opt in Engine.kt) ----
    /** The switches that are on. Replaced as a whole, so request threads can read it without a lock. */
    @Volatile private var enabledOpts: Set<Opt> = Opt.entries.filter { prefs.opt(it) }.toSet()
    private var optTick by mutableIntStateOf(0)
    private fun has(o: Opt) = o in enabledOpts
    /** For the UI: reading this makes Compose redraw when a switch changes. */
    fun isOn(o: Opt): Boolean { optTick; return o in enabledOpts }
    val protectionsOn: Int get() { optTick; return Opt.entries.count { it != Opt.LITE && it in enabledOpts } }
    val protectionsTotal: Int get() = Opt.entries.count { it != Opt.LITE }

    // ---- update notice and donation reminder (see Updates.kt) ----
    var promo by mutableStateOf<Promo?>(null)
        private set
    var updateCheckOn by mutableStateOf(prefs.updateCheck)
        private set
    private var pendingUpdate: Promo.Update? = null

    // ---- find in page ----
    var findOpen by mutableStateOf(false)
        private set
    var findTotal by mutableIntStateOf(0)
        private set
    var findIndex by mutableIntStateOf(0)
        private set

    // ---- blocked ads and trackers ----
    /** Requests blocked on the current page. */
    var blockedNow by mutableIntStateOf(0)
        private set
    /** Blocked on every page since the app opened. */
    var blockedSession by mutableIntStateOf(0)
        private set
    private val blockedReq = AtomicInteger()
    private val publishQueued = AtomicBoolean(false)
    private var pageMax = 0
    private var sessionBase = 0
    private var noticeFor: String? = null
    private var lastNoticedUrl: String? = null

    // ---- overlays, prompts, toasts ----
    val stack: SnapshotStateList<Page> = mutableListOf<Page>().toMutableStateList()
    val overlay: Page? get() = stack.lastOrNull()
    var prompt by mutableStateOf<PromptRequest?>(null)

    /** Notification cards on screen now (at most 3), and the newest-first list the Notifications page shows. */
    val notes: SnapshotStateList<Note> = mutableStateListOf()
    val noteHistory: SnapshotStateList<Note> = mutableStateListOf()
    var unreadNotes by mutableIntStateOf(0)
        private set
    private var noteSeq = 0L
    var showOnboarding by mutableStateOf(!prefs.onboardingShown)
        private set

    // ---- pointer ----
    var pointerOn by mutableStateOf(true)
        private set
    var cursorX = 0f
        private set
    var cursorY = 0f
        private set
    var clickTick by mutableIntStateOf(0)
        private set
    var chromeFocused by mutableStateOf(false)
    var viewW = 0f
        private set
    var viewH = 0f
        private set
    private var cursorPlaced = false
    private val heldKeys = LinkedHashSet<Int>()
    private var holdStartMs = 0L
    private var lastTickMs = 0L
    private var exitArmed = false
    private var menuLong = false
    private var hintsShown = false

    /** Short key-hint strip shown once after the first page loads. */
    var showHints by mutableStateOf(false)
        private set
    private var snapBusy = false
    /** The native pointer view; the UI hosts it on top of the page and only tells it which glyph to use. */
    val pointer = PointerView(activity)
    private val snapRequest = Runnable { if (heldKeys.isEmpty()) requestClosest() }
    private var scrollRemainder = 0f

    fun placeCursor() = pointer.moveTo(cursorX, cursorY)

    /** The URL pill in the toolbar and the search pill on the home screen take this focus. */
    val urlFocus = FocusRequester()

    val pointerVisible: Boolean
        get() = pointerOn && !atHome && !chromeFocused && !isFullScreen && overlay == null && prompt == null && !findOpen && promo == null && !addressOpen

    // ------------------------------------------------------------------
    // The WebView and every privacy switch it has
    // ------------------------------------------------------------------

    /** Cookies, DOM storage, cache, form data and history are wiped at launch and at exit: nothing is kept. */
    private fun wipePrivateData(force: Boolean = false) {
        if (!force && !has(Opt.WIPE_EXIT)) return
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null); cm.flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createWeb(): WebView {
        val w = WebView(activity)
        w.setBackgroundColor(Color.BLACK)          // no white flash while a page loads
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.isSaveEnabled = false
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true                  // modern sites break without it; wiped at exit
        s.databaseEnabled = false
        // Privacy: nothing local, nothing remembered, nothing shared with Google.
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.allowFileAccessFromFileURLs = false
        s.allowUniversalAccessFromFileURLs = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= 26) s.safeBrowsingEnabled = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.setSupportMultipleWindows(true)           // pop-ups are intercepted in onCreateWindow
        s.javaScriptCanOpenWindowsAutomatically = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = prefs.desktopMode
        s.textZoom = (prefs.textScale * 100f).roundToInt()
        WebView.setWebContentsDebuggingEnabled(false)
        CookieManager.getInstance().setAcceptCookie(true)
        w.setDownloadListener { u, _, cd, mime, len -> onDownloadRequested(u, cd, mime, len) }
        w.setOnScrollChangeListener { _, _, y, _, _ -> pageScrollY = y }
        w.webViewClient = client
        w.webChromeClient = chrome
        w.setFindListener { active, total, _ -> findIndex = if (total == 0) 0 else active + 1; findTotal = total }
        applyOpts(w)
        return w
    }

    private var docScript: ScriptHandler? = null
    private var adDocScript: ScriptHandler? = null
    private val docStartOk: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val privacyJs: String by lazy { runCatching { activity.assets.open("privacy.js").bufferedReader().use { it.readText() } }.getOrDefault("") }

    /** The privacy script with this moment's switches in front of it. Empty when nothing needs injecting. */
    private fun privacyScript(): String {
        val fp = has(Opt.FINGERPRINT); val ref = has(Opt.REFERRER); val ban = has(Opt.COOKIE_BANNERS)
        val rtc = has(Opt.WEBRTC); val ping = has(Opt.CLEAN_LINKS)
        if (!(fp || ref || ban || rtc || ping) || privacyJs.isEmpty()) return ""
        return "window.__wyCfg={fp:$fp,ref:$ref,banners:$ban,rtc:$rtc,ping:$ping};" + privacyJs
    }

    private val adJs: String by lazy { runCatching { activity.assets.open("adblock.js").bufferedReader().use { it.readText() } }.getOrDefault("") }

    /** Hosts where the user switched shields off (kept in memory; saved in Prefs). */
    @Volatile private var allowedHosts: Set<String> = prefs.allowSites

    /** The page being shown, for the per-site list. */
    private val pageHost: String get() = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: ""

    /** The ad script with this moment's switches in front of it. Empty when every ad switch is off. */
    private fun adScript(): String {
        val yt = has(Opt.YOUTUBE_ADS); val cos = has(Opt.AD_COSMETIC)
        if (!(yt || cos) || adJs.isEmpty()) return ""
        val allow = allowedHosts.joinToString(",") { "\"" + it.replace("\"", "") + "\"" }
        return "window.__wyAd={yt:$yt,cos:$cos,allow:[$allow]};" + adJs
    }

    /** Shields for the page on screen: on unless the user switched them off for this site. */
    val siteShieldsOn: Boolean get() = !allowedHosts.contains(pageHost)
    val canToggleSiteShields: Boolean get() = !atHome && pageHost.isNotEmpty()

    fun toggleSiteShields() {
        val h = pageHost
        if (h.isEmpty()) return
        allowedHosts = if (allowedHosts.contains(h)) allowedHosts - h else allowedHosts + h
        prefs.allowSites = allowedHosts
        optTick++
        applyOpts(web)
        val on = !allowedHosts.contains(h)
        notify(if (on) NoteKind.SUCCESS else NoteKind.WARNING, if (on) "Shields on for $h" else "Shields off for $h", "Reloading the page", Sym.ShieldFilled)
        reload()
    }

    /** Pushes every switch into the WebView. Runs for each new WebView and whenever a switch changes. */
    @Suppress("DEPRECATION")
    private fun applyOpts(w: WebView) {
        val s = w.settings
        s.saveFormData = !has(Opt.FORMS)
        s.savePassword = !has(Opt.FORMS)
        s.setGeolocationEnabled(!has(Opt.LOCATION))
        s.mediaPlaybackRequiresUserGesture = has(Opt.AUTOPLAY)
        s.blockNetworkImage = has(Opt.LITE)
        s.userAgentString = Privacy.userAgent(activity, prefs.desktopMode, has(Opt.COMMON_IDENTITY))
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, !has(Opt.THIRD_PARTY_COOKIES))
        runCatching { docScript?.remove() }
        runCatching { adDocScript?.remove() }
        docScript = null; adDocScript = null
        val js = privacyScript()
        if (js.isNotEmpty() && docStartOk) docScript = runCatching { WebViewCompat.addDocumentStartJavaScript(w, js, setOf("*")) }.getOrNull()
        val ads = adScript()
        if (ads.isNotEmpty() && docStartOk) adDocScript = runCatching { WebViewCompat.addDocumentStartJavaScript(w, ads, setOf("*")) }.getOrNull()
    }

    fun setOpt(o: Opt, on: Boolean) {
        prefs.setOpt(o, on)
        enabledOpts = if (on) enabledOpts + o else enabledOpts - o
        optTick++
        applyOpts(web)
        val later = o == Opt.FINGERPRINT || o == Opt.REFERRER || o == Opt.COOKIE_BANNERS || o == Opt.COMMON_IDENTITY || o == Opt.LITE ||
            o == Opt.YOUTUBE_ADS || o == Opt.AD_COSMETIC || o == Opt.WEBRTC || o == Opt.CLEAN_LINKS
        notify(if (on) NoteKind.SUCCESS else NoteKind.WARNING, "${o.title}: ${if (on) "on" else "off"}", if (later && !atHome) "Applies to the next page you open" else null, o.icon)
        if (o == Opt.LITE && !atHome) web.reload()
    }

    fun turnAllProtectionsOn() {
        for (o in Opt.entries) if (o != Opt.LITE) { prefs.setOpt(o, true) }
        enabledOpts = enabledOpts + Opt.entries.filter { it != Opt.LITE }
        optTick++
        applyOpts(web)
        notify(NoteKind.SUCCESS, "Every protection is on", "$protectionsTotal of $protectionsTotal", Sym.ShieldFilled)
    }

    /** The "fire button": cookies, storage, cache, history, form data and previews are erased now, then Home opens. */
    fun clearBrowsingData() {
        wipePrivateData(force = true)
        runCatching { web.clearCache(true); web.clearHistory(); web.clearFormData() }
        thumbs.clear(includeBookmarks = false)
        recents.clear()
        if (DownloadHub.hasActive()) DownloadHub.clearFinished() else DownloadHub.wipeIfIdle()
        goHome()
        notify(NoteKind.SUCCESS, "Browsing data cleared", "Cookies, cache, history and download list", Sym.DeleteSweep)
    }

    fun setSearchEngine(e: SearchEngine) {
        prefs.searchEngine = e.id; searchEngine = e
        notify(NoteKind.INFO, "Search with ${e.label}", null, Sym.Search)
    }

    // ---- find in page ----
    fun openFind() {
        if (atHome) { notify(NoteKind.WARNING, "Open a page first", "Then you can search inside it", Sym.Search); return }
        stack.clear(); stopCursor()
        findTotal = 0; findIndex = 0
        findOpen = true
    }

    fun findInPage(query: String) {
        if (query.isBlank()) { runCatching { web.clearMatches() }; findTotal = 0; findIndex = 0 } else web.findAllAsync(query)
    }

    fun findNext(forward: Boolean) { if (findTotal > 0) web.findNext(forward) }

    fun closeFind() {
        findOpen = false
        runCatching { web.clearMatches() }
        findTotal = 0; findIndex = 0
        focusPage()
    }

    fun copyLink() {
        if (atHome) { notify(NoteKind.WARNING, "Open a page first", null, Sym.ContentCopy); return }
        val cm = activity.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
        notify(NoteKind.SUCCESS, "Link copied", hostOf(url), Sym.ContentCopy)
    }

    /** The renderer died or was killed: a WebView in that state cannot be reused, so build a fresh one. */
    private fun rebuildWeb(target: String) {
        val old = webRef
        webRef = createWeb()
        webGen++
        loadUri(target)
        old?.let {
            it.stopLoading()
            // Compose removes it from its parent when webGen changes; destroy a moment later.
            ui.postDelayed({ runCatching { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }, 600L)
        }
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    fun homeTarget(): String = homeUrl ?: activity.getString(R.string.home_url)

    /** Turns whatever was typed into a URL or a search. */
    fun resolveInput(input: String): String {
        val s = input.trim()
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(s)) return s
        val hasSpace = s.contains(' ')
        if (!hasSpace && Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(s)) return "http://$s"
        if (!hasSpace && s.contains('.')) return "https://$s"
        return searchEngine.searchUrl(s)
    }

    fun open(input: String) {
        if (input.isBlank()) return
        exitArmed = false
        addressOpen = false
        stack.clear()
        loadUri(resolveInput(input))
        focusPage()
    }

    fun goHome() {
        exitArmed = false
        stack.clear()
        loadUri(homeTarget())
    }

    /** Every navigation we start goes through here: tracking parameters removed, http upgraded to https (not on LAN hosts). */
    fun loadUri(raw: String) {
        var u = if (has(Opt.CLEAN_LINKS)) Privacy.clean(raw) else raw
        fallbackHttp = null; upgradedTo = null
        val p = Uri.parse(u)
        if (has(Opt.HTTPS_UPGRADE) && p.scheme == "http" && !Privacy.isLocal(p.host)) {
            fallbackHttp = u
            u = p.buildUpon().scheme("https").build().toString()
            upgradedTo = u
        }
        web.loadUrl(u, pageHeaders())
    }

    private fun pageHeaders(): Map<String, String> = if (has(Opt.GPC)) Privacy.HEADERS else emptyMap()

    fun back() { web.goBack() }
    fun forward() { web.goForward() }
    fun reload() { web.reload() }
    fun stop() { web.stopLoading() }

    fun focusPage() {
        chromeFocused = false
        if (hideBar) barRevealed = false          // full-screen mode: the toolbar goes away when you go back to the page
        runCatching { web.requestFocus() }
    }

    fun focusToolbar() {
        stopCursor()
        if (hideBar && !barRevealed) { focusOnReveal = true; revealBar() }   // not composed yet: the toolbar takes focus itself when it appears
        runCatching { urlFocus.requestFocus() }
    }

    /** Called by Compose when the app opens or something closes, to give focus back to the page. */
    fun settleFocus() {
        if (overlay == null && prompt == null && !atHome && !findOpen && promo == null) focusPage()
    }

    /** Shows a notification card and keeps it in the Notifications page. */
    fun notify(kind: NoteKind, title: String, message: String? = null, icon: ImageVector? = null, durationMs: Long = 3800L) {
        val n = Note(++noteSeq, kind, title, message, icon, durationMs, System.currentTimeMillis())
        // The same headline twice in a row replaces the old card instead of stacking.
        notes.removeAll { it.title == title && it.message == message }
        notes.add(n)
        while (notes.size > 3) notes.removeAt(0)
        noteHistory.add(0, n)
        while (noteHistory.size > 40) noteHistory.removeAt(noteHistory.lastIndex)
        if (overlay != Page.NOTIFICATIONS) unreadNotes++
    }

    /** Plain one-line message. */
    fun toast(text: String) = notify(NoteKind.INFO, text)

    fun dismissNote(n: Note) { notes.remove(n) }

    fun clearNoteHistory() { noteHistory.clear(); notes.clear(); unreadNotes = 0 }

    // ------------------------------------------------------------------
    // Menu pages
    // ------------------------------------------------------------------

    fun openPage(p: Page) {
        stopCursor()
        // Grab a fresh picture of the page before the menu covers it, for the preview card.
        if (p == Page.NOTIFICATIONS) unreadNotes = 0
        if (overlay != p) stack.add(p)
    }

    fun popPage() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun closeAll() = stack.clear()

    fun dismissOnboarding() {
        prefs.onboardingShown = true
        showOnboarding = false
        ui.postDelayed({ tryShowPromo() }, 1500L)
    }

    // ------------------------------------------------------------------
    // Update notice and "buy me a coffee" reminder
    // ------------------------------------------------------------------

    val versionName: String get() = currentVersion()

    private fun currentVersion(): String =
        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "0"

    fun setUpdateCheck(on: Boolean) {
        prefs.updateCheck = on; updateCheckOn = on
        notify(NoteKind.INFO, if (on) "Update notices on" else "Update notices off", if (on) "Wishy asks GitHub once a day. Nothing is downloaded." else null, Sym.TipsAndUpdates)
    }

    /** Called once after start-up. Asks about updates (at most daily), then shows whatever is due. */
    private fun startPromos() {
        val now = System.currentTimeMillis()
        if (prefs.firstRunAt <= 0L || prefs.firstRunAt > now) prefs.firstRunAt = now
        if (prefs.updateCheck && now - prefs.lastUpdateCheck > 20L * 60 * 60 * 1000) checkForUpdate(manual = false) else tryShowPromo()
    }

    /** [manual]: from Settings, so it always answers, even when this version was already announced. */
    fun checkForUpdate(manual: Boolean) {
        if (manual) notify(NoteKind.INFO, "Checking for a new version", null, Sym.Refresh, 1800L)
        Thread({
            val r = runCatching { UpdateCheck.fetchLatest() }
            ui.post {
                r.onSuccess { rel ->
                    prefs.lastUpdateCheck = System.currentTimeMillis()
                    val cur = currentVersion()
                    if (UpdateCheck.isNewer(rel.version, cur) && (manual || prefs.lastUpdateNotified != rel.version)) {
                        pendingUpdate = Promo.Update(cur, rel.version, rel.notes)
                    } else if (manual) {
                        notify(NoteKind.SUCCESS, "You have the newest version", cur, Sym.CheckCircleFilled)
                    }
                }.onFailure {
                    if (manual) notify(NoteKind.WARNING, "Could not check for updates", "Check your connection and try again", Sym.Warning, 4500L)
                }
                tryShowPromo()
            }
        }, "wishy-update").start()
    }

    /** Shows one card at a time, and only when nothing else needs the screen. An update comes before a coffee. */
    private fun tryShowPromo() {
        if (promo != null) return
        if (prompt != null || showOnboarding || overlay != null || findOpen || isFullScreen || loading) {
            ui.removeCallbacks(promoRetry)
            ui.postDelayed(promoRetry, 15_000L)
            return
        }
        pendingUpdate?.let {
            pendingUpdate = null
            prefs.lastUpdateNotified = it.version
            stopCursor()
            promo = it
            return
        }
        val now = System.currentTimeMillis()
        if (DonateSchedule.isDue(now, prefs.firstRunAt, prefs.lastDonateAsk)) {
            prefs.lastDonateAsk = now
            stopCursor()
            promo = Promo.Donate(DonateSchedule.monthsUsed(now, prefs.firstRunAt))
        }
    }

    private val promoRetry = Runnable { tryShowPromo() }

    fun dismissPromo() { promo = null; focusPageIfBrowsing() }

    fun openDonateFromPromo() { promo = null; open(DONATE_URL) }

    fun openDonateQrFromPromo() { promo = null; stack.clear(); openPage(Page.DONATE) }

    private fun focusPageIfBrowsing() { if (overlay == null && prompt == null && !atHome && !findOpen) focusPage() }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    private fun same(a: String, b: String) = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

    val isBookmarked: Boolean get() = bookmarks.any { same(it.url, url) }

    fun isBookmarkedUrl(u: String): Boolean = bookmarks.any { same(it.url, u) }

    fun toggleBookmark() {
        if (atHome) { notify(NoteKind.WARNING, "Open a page first", "Then you can bookmark it", Sym.Bookmark); return }
        val u = url
        val label = title.ifBlank { hostOf(u) }
        val added = store.toggle(title, u)
        bookmarks.clear(); bookmarks.addAll(store.all())
        if (added) {
            // A bookmark keeps its preview on disk (the user chose to keep this page).
            thumbs.persist(u)
            captureNow()
            notify(NoteKind.SUCCESS, "Bookmarked", label, Sym.BookmarkAdded)
        } else {
            thumbs.delete(u)
            notify(NoteKind.INFO, "Bookmark removed", label, Sym.BookmarkRemove)
        }
    }

    fun removeBookmark(b: Bookmark) {
        store.remove(b.url)
        thumbs.delete(b.url)
        bookmarks.clear(); bookmarks.addAll(store.all())
        notify(NoteKind.INFO, "Bookmark removed", b.title, Sym.BookmarkRemove)
    }

    fun hostOf(u: String): String =
        (runCatching { Uri.parse(u).host }.getOrNull() ?: u).removePrefix("www.")

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun setTheme(id: String) { prefs.themeId = id; themeId = id }
    fun setThemeMode(mode: String) { prefs.themeMode = mode; themeMode = mode }
    fun setCursor(id: String) { prefs.cursorId = id; cursorId = id }
    fun setReduceMotion(on: Boolean) { prefs.reduceMotion = on; reduceMotion = on }

    fun setCursorScale(index: Int) {
        val v = Prefs.CURSOR_SCALES[index.coerceIn(0, Prefs.CURSOR_SCALES.lastIndex)]
        prefs.cursorScale = v; cursorScale = v
    }

    fun toggleNotifyBlocked() {
        val on = !notifyBlocked
        prefs.notifyBlocked = on; notifyBlocked = on
        notify(NoteKind.INFO, if (on) "Block notices on" else "Block notices off", null, Sym.Notifications)
    }

    fun toggleThumbs() {
        val on = !thumbsEnabled
        prefs.thumbsEnabled = on; thumbsEnabled = on
        if (!on) { thumbs.clear(includeBookmarks = false); recents.clear() }
        notify(NoteKind.INFO, if (on) "Page previews on" else "Page previews off", if (on) null else "Bookmarks keep the previews they have", Sym.Image)
    }

    fun clearPreviews() {
        thumbs.clear(includeBookmarks = true)
        recents.clear()
        notify(NoteKind.SUCCESS, "Previews cleared", "Bookmarks will get new ones the next time you visit them", Sym.CleaningServices)
    }

    fun setHomeToCurrentPage() {
        if (atHome) { notify(NoteKind.WARNING, "Open a page first", null, Sym.Home); return }
        prefs.homeUrl = url; homeUrl = url
        notify(NoteKind.SUCCESS, "Home page saved", hostOf(url), Sym.Home)
    }

    fun clearHomePage() { prefs.homeUrl = null; homeUrl = null; notify(NoteKind.INFO, "Home page reset", "Back to the start screen", Sym.Home) }

    fun toggleDesktopMode() {
        val on = !desktopMode
        prefs.desktopMode = on; desktopMode = on
        web.settings.userAgentString = Privacy.userAgent(activity, on, has(Opt.COMMON_IDENTITY))
        web.settings.loadWithOverviewMode = on
        web.reload()
        notify(NoteKind.INFO, if (on) "Desktop site on" else "Mobile site on", "Reloading the page", Sym.DesktopWindows)
    }

    fun cycleTextSize() {
        val scales = Prefs.TEXT_SCALES
        val i = scales.indexOfFirst { it >= textScale - 0.01f }.coerceAtLeast(0)
        val next = scales[(i + 1) % scales.size]
        prefs.textScale = next; textScale = next
        web.settings.textZoom = (next * 100f).roundToInt()
        notify(NoteKind.INFO, "Text size ${(next * 100).roundToInt()}%", null, Sym.TextIncrease)
    }

    fun openReader() {
        if (!atHome) web.evaluateJavascript(READER_JS, null)
    }

    fun openDefaultBrowserSettings() {
        val ok = runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }.isSuccess
        if (!ok) runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", activity.packageName, null))
            )
        }
    }

    fun exitApp() = bridge.finishApp()

    // ------------------------------------------------------------------
    // Layout: where the toolbar sits, and full-screen mode
    // ------------------------------------------------------------------

    private var _barSide by mutableStateOf(BarSide.byId(prefs.barSide))
    val barSide: BarSide get() = _barSide
    /** Full-screen mode: the toolbar and the system bars stay hidden until asked for. */
    private var _hideBar by mutableStateOf(prefs.hideBar)
    val hideBar: Boolean get() = _hideBar
    /** In full-screen mode: the toolbar is showing for a moment. */
    var barRevealed by mutableStateOf(false)
        private set
    var barTick by mutableIntStateOf(0)
        private set
    /** The address sheet used by the left and right toolbars (their column has no room for the address). */
    var addressOpen by mutableStateOf(false)

    val barShown: Boolean get() = !atHome && !isFullScreen && (!hideBar || barRevealed)

    fun setBarSide(side: BarSide) {
        prefs.barSide = side.id; _barSide = side
        notify(NoteKind.INFO, "Toolbar on the ${side.label.lowercase()}", null, Sym.ViewAgenda)
    }

    fun setHideBar(on: Boolean) {
        prefs.hideBar = on; _hideBar = on; barRevealed = false
        notify(
            NoteKind.INFO, if (on) "Full-screen mode on" else "Full-screen mode off",
            if (on) "Toolbar back: ${revealHint()}, press SEARCH, or hold MENU" else null, Sym.Fullscreen, if (on) 8000L else 3800L
        )
    }

    private fun revealHint() = when (barSide) {
        BarSide.TOP -> "pointer up at the top edge"
        BarSide.BOTTOM -> "pointer down at the bottom edge"
        BarSide.LEFT -> "pointer left at the left edge"
        BarSide.RIGHT -> "pointer right at the right edge"
    }

    /** Short name of the push that opens the toolbar, for the key hints. */
    val revealKeyHint: String get() = when (barSide) {
        BarSide.TOP -> "Up at top"; BarSide.BOTTOM -> "Down at bottom"; BarSide.LEFT -> "Left at edge"; BarSide.RIGHT -> "Right at edge"
    }

    fun revealBar() { if (hideBar) { barRevealed = true; barTick++ } }

    /** Full-screen mode: put the toolbar away again (used by the idle timer). */
    fun hideBarNow() { if (hideBar && barRevealed && !chromeFocused) barRevealed = false }

    private var focusOnReveal = false

    /** True once after the toolbar was brought back by a key: it should take the remote's focus. */
    fun takeFocusOnReveal(): Boolean { val v = focusOnReveal; focusOnReveal = false; return v }

    fun leaveToolbar() = focusPage()

    /** The pointer is against the toolbar's edge and the user pushed on towards it. */
    private fun edgePush(code: Int): Boolean = when (barSide) {
        BarSide.TOP -> code == KeyEvent.KEYCODE_DPAD_UP && cursorY <= 2f && pageScrollY <= 0
        BarSide.BOTTOM -> code == KeyEvent.KEYCODE_DPAD_DOWN && cursorY >= viewH - 2f && !web.canScrollVertically(1)
        BarSide.LEFT -> code == KeyEvent.KEYCODE_DPAD_LEFT && cursorX <= 2f
        BarSide.RIGHT -> code == KeyEvent.KEYCODE_DPAD_RIGHT && cursorX >= viewW - 2f
    }

    /** SEARCH, F6, Ctrl+L: the address pill (top and bottom toolbars) or the address sheet (left and right). */
    fun openAddress() {
        if (!atHome && barSide.vertical) { stopCursor(); addressOpen = true } else focusToolbar()
    }

    fun closeAddress() { addressOpen = false; focusPage() }



    // ------------------------------------------------------------------
    // Downloads (the engine is in Downloads.kt)
    // ------------------------------------------------------------------

    val downloads: SnapshotStateList<DlItem> get() = DownloadHub.items

    private fun originOf(u: String): String? =
        runCatching { Uri.parse(u).let { if (it.scheme != null && it.host != null) "${it.scheme}://${it.host}/" else null } }.getOrNull()

    /** The page started a download. Ask first (unless that switch is off), then hand it to the download manager. */
    private fun onDownloadRequested(u: String, cd: String?, mime: String?, len: Long) {
        ui.post {
            if (prompt != null) return@post
            val scheme = runCatching { Uri.parse(u).scheme?.lowercase() }.getOrNull()
            if (scheme == "blob") {
                notify(NoteKind.WARNING, "Can't download this yet", "This site builds the file inside the page", Sym.Download, 5200L)
                return@post
            }
            if (scheme != "http" && scheme != "https" && scheme != "data") {
                notify(NoteKind.WARNING, "Can't download this", "Unsupported address type", Sym.Download, 4500L)
                return@post
            }
            val name = DownloadHub.guessName(u, cd, mime)
            val pageHost = runCatching { Uri.parse(url).host }.getOrNull()
            val fileHost = runCatching { Uri.parse(u).host }.getOrNull()
            // "Hide where you came from": a file on another site gets no referrer at all.
            val referer = if (has(Opt.REFERRER) && pageHost != fileHost) null else originOf(url)
            val go = { startDownload(u, referer, name, mime, len) }
            if (has(Opt.ASK_DOWNLOAD)) {
                stopCursor()
                promptCancel = { }
                val size = if (len > 0) " (${android.text.format.Formatter.formatShortFileSize(activity, len)})" else ""
                prompt = PromptRequest.Confirm(hostOf(url), "Download $name$size?") { ok ->
                    prompt = null; promptCancel = null
                    if (ok) go()
                }
            } else go()
        }
    }

    private fun startDownload(u: String, referer: String?, name: String, mime: String?, len: Long) {
        ensureDownloadPermissions {
            DownloadHub.enqueue(u, referer, name, mime, len)
            notify(NoteKind.INFO, "Download started", name, Sym.Download, 3200L)
        }
    }

    /** Android 9 and older need storage permission to write into Downloads; Android 13+ asks once for notifications. */
    private fun ensureDownloadPermissions(then: () -> Unit) {
        val needStorage = Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        val needNotif = Build.VERSION.SDK_INT >= 33 && !prefs.notifAsked &&
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val afterStorage = {
            if (needNotif) {
                prefs.notifAsked = true
                runCatching { bridge.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)) { then() } }.onFailure { then() }
            } else then()
        }
        // A denied permission is fine: the file then goes to the app's own folder, and progress only shows in the app.
        if (needStorage) runCatching { bridge.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) { afterStorage() } }.onFailure { afterStorage() }
        else afterStorage()
    }

    fun openDownload(d: DlItem) {
        val saved = d.savedUri ?: return
        val uri = DownloadHub.shareUri(saved)
        if (uri == null) { notify(NoteKind.WARNING, "Can't find that file", d.savedWhere, Sym.Warning); return }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DownloadHub.mimeOf(d))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(i) }.onFailure {
            notify(NoteKind.WARNING, "No app can open this file", "It is in ${d.savedWhere ?: "Downloads"}", Sym.Warning, 5200L)
        }
    }

    // ------------------------------------------------------------------
    // Back button
    // ------------------------------------------------------------------

    /** Handle Back button: closes prompts and overlays first, then page navigation. */
    fun handleBack() {
        when {
            prompt != null -> { val c = promptCancel; promptCancel = null; prompt = null; c?.invoke() }
            promo != null -> dismissPromo()
            findOpen -> closeFind()
            addressOpen -> closeAddress()
            stack.isNotEmpty() -> popPage()
            hideBar && barRevealed -> { barRevealed = false; focusPage() }
            isFullScreen -> chrome.onHideCustomView()
            atHome -> if (exitArmed) bridge.finishApp() else { exitArmed = true; notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
            chromeFocused -> if (exitArmed) bridge.finishApp() else focusPage()
            canGoBack -> web.goBack()
            else -> { exitArmed = true; focusToolbar(); notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
        }
    }

    // ------------------------------------------------------------------
    // Delegates
    // ------------------------------------------------------------------

    /** Page lifecycle, request blocking, https upgrade and tracking-parameter removal. */
    private val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            val trackers = has(Opt.TRACKERS)
            val yt = has(Opt.YOUTUBE_ADS)
            if (!trackers && !yt) return null
            val target = request.url
            // Shields off for this site: leave its requests alone.
            if (allowedHosts.isNotEmpty() && allowedHosts.contains(pageHost)) return null
            val hit = (trackers && Blocklist.isBlocked(target.host)) || AdRules.matches(target.host, target.encodedPath, target.encodedQuery, yt, trackers)
            if (!hit) return null
            blockedReq.incrementAndGet()
            if (publishQueued.compareAndSet(false, true)) ui.postDelayed({ publishQueued.set(false); publishBlocked() }, 350L)
            return if (AdRules.wantsJson(target.encodedPath)) Privacy.emptyJson() else Privacy.emptyResponse()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme
            if (scheme != "http" && scheme != "https") return scheme != "about" && scheme != "blob" && scheme != "data" // no intent:/market: hand-offs
            if (!request.isForMainFrame) return false
            val u = request.url.toString()
            val cleaned = if (has(Opt.CLEAN_LINKS)) Privacy.clean(u) else u
            val upgrade = has(Opt.HTTPS_UPGRADE) && scheme == "http" && !Privacy.isLocal(request.url.host)
            if (cleaned == u && !upgrade) return false
            ui.post { loadUri(cleaned) }
            return true
        }

        override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
            this@BrowserController.url = u ?: "about:blank"
            title = ""
            progress = 0; loading = true
            ui.removeCallbacks(captureRunnable)
            ui.removeCallbacks(blockedNotice)
            sessionBase += pageMax
            pageMax = 0; blockedReq.set(0)
            blockedNow = 0; blockedSession = sessionBase
            readerable = false
            inject(view)
        }

        override fun onPageCommitVisible(view: WebView, u: String?) = inject(view)

        override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
            if (u != null) this@BrowserController.url = u
            canGoBack = view.canGoBack()
            canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, u: String?) {
            inject(view)
            loading = false; progress = 100
            canGoBack = view.canGoBack(); canGoForward = view.canGoForward()
            if (u == null || u.startsWith("about:")) return
            ui.postDelayed({ runCatching { view.evaluateJavascript(READABLE_JS) { r -> readerable = r == "true" } } }, 1200L)
            rememberRecent()
            scheduleCapture()
            if (!hintsShown && !atHome) {
                hintsShown = true; showHints = true
                ui.postDelayed({ showHints = false }, 7000L)
            }
            noticeFor = this@BrowserController.url
            ui.removeCallbacks(blockedNotice)
            ui.postDelayed(blockedNotice, 2200L)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            loading = false
            // https upgrade failed (site has no https): retry the original http address once.
            val http = fallbackHttp
            if (http != null && request.url.toString() == upgradedTo) {
                fallbackHttp = null; upgradedTo = null
                web.loadUrl(http, pageHeaders())
                notify(NoteKind.WARNING, "No secure version of this site", "Opened over http", Sym.Warning)
                return
            }
            notify(NoteKind.WARNING, "Can't open this page", hostOf(request.url.toString()), Sym.Warning, 5000L)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()     // never "proceed anyway"
            loading = false
            notify(NoteKind.WARNING, "Connection is not private", hostOf(error.url ?: url), Sym.Lock, 6000L)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recoverSession(if (detail.didCrash()) "crashed" else "was stopped by Android (low memory)")
            return true
        }
    }

    /** Pointer snapping, D-pad link navigation, ad-frame cleanup and the YouTube ad skipper (assets/wishy.js). */
    private val wishyJs: String by lazy { runCatching { activity.assets.open("wishy.js").bufferedReader().use { it.readText() } }.getOrDefault("") }

    private fun inject(view: WebView) {
        val u = view.url ?: return
        if (!u.startsWith("http")) return
        view.evaluateJavascript(wishyJs, null)
        // Without document-start scripts the privacy layer runs as early as it can (it guards against running twice).
        if (!docStartOk) {
            privacyScript().let { if (it.isNotEmpty()) view.evaluateJavascript(it, null) }
            adScript().let { if (it.isNotEmpty()) view.evaluateJavascript(it, null) }
        }
    }

    /** Dialogs, permissions, pop-ups, file upload and full-screen video. */
    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) { if (newProgress != progress) progress = newProgress }

        override fun onReceivedTitle(view: WebView, t: String?) {
            title = t ?: ""
            updateRecentTitle()
        }

        override fun onConsoleMessage(m: ConsoleMessage?): Boolean = true

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view; customCb = callback
            view.setBackgroundColor(Color.BLACK)
            activity.addContentView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            isFullScreen = true
        }

        override fun onHideCustomView() {
            val v = customView ?: return
            (v.parent as? ViewGroup)?.removeView(v)
            customView = null
            customCb?.onCustomViewHidden(); customCb = null
            isFullScreen = false
            focusPage()
        }

        // Pop-ups: only when the user caused it, and always in this same tab.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (has(Opt.POPUPS) && !isUserGesture) return false
            val tmp = WebView(activity)
            tmp.settings.javaScriptEnabled = false
            tmp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val target = r.url.toString()
                    ui.post { loadUri(target); runCatching { tmp.destroy() } }
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = tmp
            resultMsg.sendToTarget()
            return true
        }

        override fun onJsAlert(view: WebView, u: String?, message: String?, result: JsResult): Boolean {
            promptCancel = { result.cancel() }
            prompt = PromptRequest.Alert(hostOf(u ?: url), message) { prompt = null; promptCancel = null; result.confirm() }
            return true
        }

        override fun onJsConfirm(view: WebView, u: String?, message: String?, result: JsResult): Boolean {
            promptCancel = { result.cancel() }
            prompt = PromptRequest.Confirm(hostOf(u ?: url), message) { ok ->
                prompt = null; promptCancel = null
                if (ok) result.confirm() else result.cancel()
            }
            return true
        }

        override fun onJsPrompt(view: WebView, u: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
            promptCancel = { result.cancel() }
            prompt = PromptRequest.TextInput(hostOf(u ?: url), message, defaultValue ?: "") { text ->
                prompt = null; promptCancel = null
                if (text != null) result.confirm(text) else result.cancel()
            }
            return true
        }

        override fun onJsBeforeUnload(view: WebView, u: String?, message: String?, result: JsResult): Boolean {
            result.confirm(); return true     // never trap the user on a page
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = request.resources.filter {
                it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
            }
            // Anything else (DRM device id, MIDI) is refused.
            if (wanted.isEmpty()) { request.deny(); return }
            val host = request.origin.host ?: request.origin.toString()
            promptCancel = { request.deny() }
            prompt = PromptRequest.Permission(host) { allow ->
                prompt = null; promptCancel = null
                if (!allow) {
                    request.deny()
                } else {
                    val needed = wanted.map {
                        if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) android.Manifest.permission.CAMERA else android.Manifest.permission.RECORD_AUDIO
                    }.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
                    if (needed.isEmpty()) request.grant(wanted.toTypedArray())
                    else bridge.requestPermissions(needed.toTypedArray()) { ok -> if (ok) request.grant(wanted.toTypedArray()) else request.deny() }
                }
            }
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = callback
            val types = params.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray().orEmpty()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (types.size == 1) types[0] else "*/*"
                if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            runCatching {
                bridge.pickFiles(Intent.createChooser(intent, "Choose file")) { uris -> fileCb?.onReceiveValue(uris); fileCb = null }
            }.onFailure { fileCb?.onReceiveValue(null); fileCb = null }
            return true
        }
    }

    // ------------------------------------------------------------------
    // Crash recovery and memory pressure
    // ------------------------------------------------------------------

    private val crashTimes = ArrayDeque<Long>()

    /** A WebView whose renderer is gone cannot be reused: build a new one and reload. Three crashes in a minute goes home. */
    private fun recoverSession(why: String) {
        loading = false
        val now = SystemClock.uptimeMillis()
        crashTimes.addLast(now)
        while (crashTimes.isNotEmpty() && now - crashTimes.first() > 60_000L) crashTimes.removeFirst()
        val looping = crashTimes.size >= 3
        val target = if (looping || !url.startsWith("http")) homeTarget() else url
        ui.post { rebuildWeb(target) }
        if (looping) {
            crashTimes.clear()
            notify(NoteKind.WARNING, "This page keeps crashing", "Went home. Try Mobile site or Reader view.", Sym.Refresh, 6000L)
        } else {
            notify(NoteKind.WARNING, "Page $why", "Reloading...", Sym.Refresh)
        }
    }

    /** About 2 GB of RAM or less: a full-resolution screenshot there can tip the app into an out-of-memory kill. */
    private val lowRam: Boolean by lazy {
        val am = activity.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || mi.totalMem <= 2_300L * 1024 * 1024
    }

    private fun systemLowOnMemory(): Boolean {
        val am = activity.getSystemService(android.app.ActivityManager::class.java)
        return android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.lowMemory
    }

    /** Android asks apps to let go of memory: drop the in-memory page previews first. */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        val pressure = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure) {
            ui.removeCallbacks(captureRunnable)
            thumbs.clear(includeBookmarks = false)
        }
    }

    // ------------------------------------------------------------------
    // Ads and trackers blocked, page previews, recent pages
    // ------------------------------------------------------------------


    private fun publishBlocked() {
        val total = blockedReq.get()
        pageMax = max(pageMax, total)
        blockedNow = pageMax
        blockedSession = sessionBase + pageMax
    }

    /** A moment after a page finishes: say how much was blocked, once per page. */
    private val blockedNotice = Runnable {
        publishBlocked()
        val u = url
        val n = blockedNow
        if (notifyBlocked && n > 0 && !atHome && u == noticeFor && u != lastNoticedUrl) {
            lastNoticedUrl = u
            notify(
                NoteKind.SHIELD,
                if (n == 1) "Blocked 1 ad or tracker" else "Blocked $n ads and trackers",
                hostOf(u), Sym.ShieldFilled
            )
        }
    }

    private val captureRunnable = Runnable { captureNow() }

    /** Takes a preview a moment after the page settles (images and fonts need a beat to draw). */
    fun scheduleCapture(delayMs: Long = 3500L) {
        if (!thumbsEnabled || Build.VERSION.SDK_INT < 26) return
        ui.removeCallbacks(captureRunnable)
        ui.postDelayed(captureRunnable, delayMs)
    }

    /**
     * Snapshot of the visible page, taken with PixelCopy: the GPU copies the finished frame in the background.
     * (The old version drew the whole WebView into a bitmap on the UI thread, which froze the page and made it
     * flash about a second after every load.) Only runs when nothing is on top of the page and the pointer is idle.
     */
    @android.annotation.SuppressLint("NewApi")
    fun captureNow() {
        if (!thumbsEnabled || Build.VERSION.SDK_INT < 26 || atHome || isFullScreen || overlay != null || prompt != null) return
        if (heldKeys.isNotEmpty() || loading || systemLowOnMemory()) return
        val gv = web
        if (gv.width <= 0 || gv.height <= 0) return
        val u = url
        if (!u.startsWith("http")) return
        runCatching {
            val loc = IntArray(2)
            gv.getLocationInWindow(loc)
            val bmp = Bitmap.createBitmap(gv.width / 2, gv.height / 2, Bitmap.Config.ARGB_8888)
            val src = android.graphics.Rect(loc[0], loc[1], loc[0] + gv.width, loc[1] + gv.height)
            pointer.alpha = 0f   // keep the pointer out of the picture (restored right after)
            android.view.PixelCopy.request(activity.window, src, bmp, { result ->
                pointer.alpha = 1f
                if (result == android.view.PixelCopy.SUCCESS && u == url) thumbs.put(u, bmp, isBookmarkedUrl(u)) else bmp.recycle()
            }, ui)
        }.onFailure { pointer.alpha = 1f }
    }

    private fun rememberRecent() {
        if (!thumbsEnabled) return
        val u = url
        if (!u.startsWith("http")) return
        val i = recents.indexOfFirst { same(it.url, u) }
        if (i >= 0) recents.removeAt(i)
        recents.add(0, Recent(u, title.ifBlank { hostOf(u) }))
        while (recents.size > 10) recents.removeAt(recents.lastIndex)
    }

    private fun updateRecentTitle() {
        val u = url
        val i = recents.indexOfFirst { same(it.url, u) }
        if (i >= 0 && title.isNotBlank() && recents[i].title != title) recents[i] = Recent(recents[i].url, title)
    }

    // ------------------------------------------------------------------
    // Your own cursors
    // ------------------------------------------------------------------

    val cursorSpec: org.wishy.browser.ui.CursorSpec
        get() = org.wishy.browser.ui.Cursors.resolve(cursorId, cursorLib)

    /** Picks a picture with the system file chooser. [tipTopLeft]: the click point is the top-left tip. */
    fun addCursorFromFile(tipTopLeft: Boolean) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        runCatching {
            bridge.pickFiles(Intent.createChooser(intent, "Choose a picture")) { uris ->
                val uri = uris?.firstOrNull() ?: return@pickFiles
                val name = runCatching {
                    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull() ?: "My cursor"
                addCursorAsync(tipTopLeft, name) {
                    cursorLib.readLimited(activity.contentResolver.openInputStream(uri) ?: error("Could not open that file"))
                }
            }
        }.onFailure {
            notify(NoteKind.ERROR, "No file chooser on this device", "Use a web address instead", Sym.Image)
        }
    }

    fun addCursorFromUrl(address: String, tipTopLeft: Boolean) {
        val a = address.trim()
        if (a.isEmpty()) return
        val name = a.substringBefore('?').substringAfterLast('/')
        addCursorAsync(tipTopLeft, name) { cursorLib.download(a) }
    }

    private fun addCursorAsync(tipTopLeft: Boolean, name: String, load: () -> ByteArray) {
        notify(NoteKind.INFO, "Adding your cursor", "One moment", Sym.Download, 1600L)
        Thread {
            val r = runCatching { cursorLib.create(load(), name, tipTopLeft) }
            ui.post {
                r.onSuccess {
                    cursorLib.register(it)
                    setCursor(it.id)
                    notify(NoteKind.SUCCESS, "Cursor added", "${it.name} is now your pointer", Sym.AdsClick)
                }.onFailure {
                    notify(NoteKind.ERROR, "Could not add that cursor", it.message ?: "Unknown problem", Sym.Error, 5200L)
                }
            }
        }.start()
    }

    fun removeCursor(img: CursorImage) {
        cursorLib.remove(img)
        if (cursorId == img.id) setCursor("")
        notify(NoteKind.INFO, "Cursor removed", img.name, Sym.DeleteOutline)
    }

    // ------------------------------------------------------------------
    // Keys and the D-pad pointer
    // ------------------------------------------------------------------

    /** True while Compose should own the keys: a page/menu/dialog is open, the home screen is showing, or the toolbar has focus. */
    private val uiOwnsKeys: Boolean get() = overlay != null || prompt != null || atHome || chromeFocused || findOpen || promo != null || addressOpen

    fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        if (!down && heldKeys.remove(event.keyCode)) {
            if (heldKeys.isEmpty()) { ui.removeCallbacks(snapRequest); ui.postDelayed(snapRequest, 260L) }
            return true
        }
        if (prompt != null) return false

        if (event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_D -> { if (first) toggleBookmark(); return true }
                KeyEvent.KEYCODE_B -> { if (first) openPage(Page.BOOKMARKS); return true }
                KeyEvent.KEYCODE_L -> { if (first) openAddress(); return true }
                KeyEvent.KEYCODE_J -> { if (first) openPage(Page.DOWNLOADS); return true }
                KeyEvent.KEYCODE_R -> { if (first) reload(); return true }
                KeyEvent.KEYCODE_F -> { if (first) openFind(); return true }
                KeyEvent.KEYCODE_C -> if (event.isShiftPressed) { if (first) copyLink(); return true }
            }
        }
        if (event.isAltPressed && !uiOwnsKeys) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (first) back(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (first) forward(); return true }
            }
        }
        when (event.keyCode) {
            // Tap MENU: pointer on/off (decided when the key is released). Hold MENU: open the menu.
            KeyEvent.KEYCODE_MENU -> {
                if (down) {
                    if (event.repeatCount == 0) menuLong = false
                    else if (!menuLong) { menuLong = true; if (overlay == null) openPage(Page.MENU) }
                } else if (!menuLong && overlay == null && !atHome) togglePointer()
                return true
            }
            KeyEvent.KEYCODE_BOOKMARK -> { if (first) openPage(Page.BOOKMARKS); return true }
            KeyEvent.KEYCODE_SEARCH -> { if (first) openAddress(); return true }
            KeyEvent.KEYCODE_F5 -> { if (first) reload(); return true }
            KeyEvent.KEYCODE_F6 -> { if (first) openAddress(); return true }
        }
        if (isFullScreen && overlay == null) return fullscreenKey(event)
        if (uiOwnsKeys) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { if (down) scrollPageBy(viewH * 0.85f); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { if (down) scrollPageBy(-viewH * 0.85f); return true }
            KeyEvent.KEYCODE_MOVE_HOME -> { if (first) web.pageUp(true); return true }
            KeyEvent.KEYCODE_MOVE_END -> { if (first) web.pageDown(true); return true }
        }
        val dpad = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (dpad && !pointerOn) {
            // Pointer off: the page's own focus navigation (assets/wishy.js) moves between links.
            if (first) {
                val dir = when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> "Up"; KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "Left"; else -> "Right"
                }
                web.evaluateJavascript("window.__wyNav&&__wyNav('$dir')", null)
            }
            return true
        }
        if (pointerOn) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (first) {
                        if (edgePush(event.keyCode)) { focusToolbar(); return true }
                        startCursorKey(event.keyCode)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    if (first) clickAtCursor()
                    return true
                }
            }
        }
        return false
    }

    fun togglePointer() {
        pointerOn = !pointerOn
        stopCursor()
        if (pointerOn) notify(NoteKind.INFO, "Pointer on", "OK clicks. MENU switches it off.", Sym.AdsClick)
        else notify(NoteKind.INFO, "Pointer off", "Pages get the raw D-pad keys", Sym.ArrowSelectorTool)
    }

    /** Called when the page area is measured. Keeps the pointer inside it. */
    fun onViewSized(w: Float, h: Float) {
        viewW = w; viewH = h
        if (!cursorPlaced && w > 0f) { cursorX = w / 2f; cursorY = h / 2f; cursorPlaced = true }
        cursorX = cursorX.coerceIn(0f, w); cursorY = cursorY.coerceIn(0f, h)
        placeCursor()
    }

    private val density get() = activity.resources.displayMetrics.density

    private val ticker = object : Runnable {
        override fun run() {
            if (heldKeys.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(1L, 48L) / 1000f
            lastTickMs = now
            val t = ((now - holdStartMs) / 700f).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val speed = (520f + (1900f - 520f) * eased) * density
            var dx = 0f; var dy = 0f
            for (k in heldKeys) when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> dy -= 1f
                KeyEvent.KEYCODE_DPAD_DOWN -> dy += 1f
                KeyEvent.KEYCODE_DPAD_LEFT -> dx -= 1f
                KeyEvent.KEYCODE_DPAD_RIGHT -> dx += 1f
            }
            if (dx != 0f || dy != 0f) moveCursorBy(dx * speed * dt, dy * speed * dt)
            activity.window.decorView.postOnAnimation(this)
        }
    }

    private fun startCursorKey(code: Int) {
        if (heldKeys.isEmpty()) { holdStartMs = SystemClock.uptimeMillis(); lastTickMs = holdStartMs }
        heldKeys.add(code)
        val step = 14f * density
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursorBy(0f, -step)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursorBy(0f, step)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursorBy(-step, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursorBy(step, 0f)
        }
        activity.window.decorView.removeCallbacks(ticker)
        activity.window.decorView.postOnAnimation(ticker)
    }

    private var lastClosestPostMs = 0L

    fun stopCursor() {
        heldKeys.clear()
        activity.window.decorView.removeCallbacks(ticker)
    }

    private var lastScrollPostMs = 0L

    private fun moveCursorBy(dx: Float, dy: Float) {
        val edge = 64f * density
        var ny = dy
        // At the top/bottom edge the pointer stays put and the page scrolls instead.
        if (dy > 0f && cursorY >= viewH - edge && web.canScrollVertically(1)) { scrollPageBy(dy * 1.6f); ny = 0f }
        else if (dy < 0f && cursorY <= edge && pageScrollY > 0) { scrollPageBy(dy * 1.6f); ny = 0f }
        cursorY = (cursorY + ny).coerceIn(0f, viewH)
        cursorX = (cursorX + dx).coerceIn(0f, viewW)
        placeCursor()
    }

    // Magnetic snap to the nearest link when the pointer is idle (assets/wishy.js tells us where).
    private var snapTarget: Pair<Float, Float>? = null
    private val snapper = object : Runnable {
        override fun run() {
            val t = snapTarget ?: return
            if (heldKeys.isNotEmpty()) { snapTarget = null; return }
            val k = 0.35f
            cursorX += (t.first - cursorX) * k
            cursorY += (t.second - cursorY) * k
            placeCursor()
            if (kotlin.math.abs(t.first - cursorX) < 0.6f && kotlin.math.abs(t.second - cursorY) < 0.6f) {
                cursorX = t.first; cursorY = t.second; snapTarget = null; placeCursor()
            } else activity.window.decorView.postOnAnimation(this)
        }
    }

    private fun snapCursorTo(x: Float, y: Float) {
        if (heldKeys.isNotEmpty()) return
        snapTarget = x to y
        activity.window.decorView.removeCallbacks(snapper)
        activity.window.decorView.postOnAnimation(snapper)
    }

    private fun scrollPageBy(px: Float) {
        scrollRemainder += px
        val whole = scrollRemainder.toInt()
        if (whole != 0) { scrollRemainder -= whole; web.scrollBy(0, whole) }
    }

    /** Asks the page for the nearest link; at most one question in flight so the UI thread never queues up work. */
    private fun requestClosest() {
        if (snapBusy || atHome) return
        snapBusy = true
        web.evaluateJavascript("window.__wyClosest&&__wyClosest($cursorX,$cursorY,$viewW)") { r ->
            snapBusy = false
            if (r.isNullOrEmpty() || r == "null") return@evaluateJavascript
            runCatching {
                val o = JSONObject(r)
                snapCursorTo(o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
            }
        }
    }

    /** Full-screen video: OK plays/pauses, Left/Right seek 10 s. Everything else goes to the system. */
    private fun fullscreenKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val js = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                "var v=document.querySelector('video');if(v){v.paused?v.play():v.pause()}"
            KeyEvent.KEYCODE_DPAD_LEFT -> "var v=document.querySelector('video');if(v)v.currentTime-=10"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "var v=document.querySelector('video');if(v)v.currentTime+=10"
            else -> return false
        }
        web.evaluateJavascript("(function(){$js})()", null)
        return true
    }

    private fun clickAtCursor() {
        snapTarget?.let { (x, y) ->
            cursorX = x
            cursorY = y
            placeCursor()
            snapTarget = null
            activity.window.decorView.removeCallbacks(snapper)
        }
        clickTick++
        pointer.pulse()
        val gv = web
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        gv.dispatchTouchEvent(down)
        gv.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
    }

    fun onPause() { stopCursor(); webRef?.onPause() }

    fun onResume() { webRef?.onResume() }

    fun onDestroy() {
        stopCursor()
        DownloadHub.listener = null
        if (has(Opt.WIPE_EXIT)) DownloadHub.wipeIfIdle()
        ui.removeCallbacksAndMessages(null)
        fileCb?.onReceiveValue(null); fileCb = null
        customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        webRef?.let {
            runCatching {
                it.stopLoading()
                if (has(Opt.WIPE_EXIT)) { it.clearCache(true); it.clearHistory(); it.clearFormData() }
                (it.parent as? ViewGroup)?.removeView(it); it.destroy()
            }
        }
        wipePrivateData()
        webRef = null
    }

    // Last on purpose: the clients above are property initialisers and must exist before the first WebView is built.
    init {
        wipePrivateData()
        DownloadHub.init(activity.applicationContext)
        if (has(Opt.WIPE_EXIT)) DownloadHub.wipeIfIdle()
        DownloadHub.listener = { ev ->
            when (ev) {
                is DlEvent.Done -> notify(NoteKind.SUCCESS, "Download finished", ev.item.name, Sym.Download, 5200L)
                is DlEvent.Failed -> notify(NoteKind.ERROR, "Download failed", "${ev.item.name}: ${ev.item.error ?: "unknown problem"}", Sym.Error, 6500L)
            }
        }
        webRef = createWeb()
        thumbs.prune(bookmarks.map { it.url })
        ui.postDelayed({ startPromos() }, 3500L)
    }

    private companion object {
        /** A page worth the reader view: it has an <article> or <main> with real text. */
        const val READABLE_JS = "(function(){var a=document.querySelector('article')||document.querySelector('main');return !!a&&a.innerText.length>1500})()"

        /** Rebuilds the page as plain large text on a dark background (done in the page, nothing is sent anywhere). */
        const val READER_JS = "(function(){var a=document.querySelector('article')||document.querySelector('main');if(!a)return;" +
            "var c=a.cloneNode(true);c.querySelectorAll('script,style,iframe,nav,aside,form,button,svg,noscript,video,[aria-hidden=true]').forEach(function(e){e.remove()});" +
            "var t=document.title;document.head.innerHTML='<meta name=viewport content=\"width=device-width,initial-scale=1\"><style>html{background:#111}" +
            "body{max-width:46em;margin:0 auto;padding:6vh 5vw;font:1.35em/1.7 Georgia,serif;color:#eee}a{color:#7cc4ff}img{max-width:100%;height:auto}" +
            "h1,h2,h3{line-height:1.25;font-family:sans-serif}</style>';document.body.innerHTML='';var h=document.createElement('h1');h.textContent=t;" +
            "document.body.appendChild(h);document.body.appendChild(c);window.scrollTo(0,0)})()"
    }
}

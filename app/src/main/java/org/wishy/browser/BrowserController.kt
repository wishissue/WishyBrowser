package org.wishy.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebExtension
import org.wishy.browser.ui.Sym
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.roundToInt

/** The pages the menu can open. The back stack lives in [BrowserController.stack]. */
enum class Page { MENU, BOOKMARKS, THEMES, CURSORS, SETTINGS, SHIELDS, NOTIFICATIONS, DEVELOPER, CREDITS, DONATE }

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
 * Owns the single GeckoSession, the D-pad pointer and every piece of state the Compose UI
 * shows. The UI only reads these fields and calls these functions; it never touches Gecko.
 *
 * Remote cheat-sheet:
 *   MENU ......... toggle pointer      BACK ......... fullscreen -> page back -> toolbar -> exit
 *   SEARCH ....... address bar         BOOKMARK ..... bookmarks     CH+/CH- ... page down / up
 */
class BrowserController(
    private val activity: ComponentActivity,
    val runtime: GeckoRuntime,
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
    val session: GeckoSession
    var geckoView: GeckoView? = null

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

    // ---- blocked ads and trackers ----
    /** Blocked on the current page: uBlock Origin's count plus Gecko's own tracker blocking. */
    var blockedNow by mutableIntStateOf(0)
        private set
    /** Blocked on every page since the app opened. */
    var blockedSession by mutableIntStateOf(0)
        private set
    private var ubBlocked = 0
    private var etpBlocked = 0
    private var pageMax = 0
    private var sessionBase = 0
    private var publishPending = false
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
    var cursorX by mutableFloatStateOf(0f)
        private set
    var cursorY by mutableFloatStateOf(0f)
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
    private var corePort: WebExtension.Port? = null

    /** The URL pill in the toolbar and the search pill on the home screen take this focus. */
    val urlFocus = FocusRequester()

    val pointerVisible: Boolean
        get() = pointerOn && !atHome && !chromeFocused && overlay == null && prompt == null

    init {
        val desktop = prefs.desktopMode
        // usePrivateMode = true: Gecko never writes history, form data or persistent
        // cookies to disk. Extensions are explicitly allowed in private mode (BrowserApplication).
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            // Without this flag the Strict tracking protection set in BrowserApplication is not applied to the session.
            .useTrackingProtection(true)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        session = GeckoSession(settings)
        session.open(runtime)
        installDelegates()
        thumbs.prune(bookmarks.map { it.url })
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
        return "https://duckduckgo.com/html/?q=" + Uri.encode(s)
    }

    fun open(input: String) {
        if (input.isBlank()) return
        exitArmed = false
        stack.clear()
        session.loadUri(resolveInput(input))
        focusPage()
    }

    fun goHome() {
        exitArmed = false
        stack.clear()
        session.loadUri(homeTarget())
    }

    fun back() { session.goBack() }
    fun forward() { session.goForward() }
    fun reload() { session.reload() }
    fun stop() { session.stop() }

    fun focusPage() {
        chromeFocused = false
        runCatching { geckoView?.requestFocus() }
    }

    fun focusToolbar() {
        stopCursor()
        runCatching { urlFocus.requestFocus() }
    }

    /** Called by Compose when the app opens or something closes, to give focus back to the page. */
    fun settleFocus() {
        if (overlay == null && prompt == null && !atHome) focusPage()
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
        if (p == Page.MENU) captureNow()
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
    }

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
        session.settings.setUserAgentMode(if (on) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        session.settings.setViewportMode(if (on) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        session.reload()
        notify(NoteKind.INFO, if (on) "Desktop site on" else "Mobile site on", "Reloading the page", Sym.DesktopWindows)
    }

    fun cycleTextSize() {
        val scales = Prefs.TEXT_SCALES
        val i = scales.indexOfFirst { it >= textScale - 0.01f }.coerceAtLeast(0)
        val next = scales[(i + 1) % scales.size]
        prefs.textScale = next; textScale = next
        runtime.settings.setFontSizeFactor(next)
        notify(NoteKind.INFO, "Text size ${(next * 100).roundToInt()}%", null, Sym.TextIncrease)
    }

    fun openReader() {
        if (!atHome) session.loadUri("about:reader?url=$url")
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
    // Back button
    // ------------------------------------------------------------------

    /** Handle Back button: closes prompts and overlays first, then page navigation. */
    fun handleBack() {
        when {
            prompt != null -> prompt = null
            stack.isNotEmpty() -> popPage()
            isFullScreen -> session.exitFullScreen()
            atHome -> if (exitArmed) bridge.finishApp() else { exitArmed = true; notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
            chromeFocused -> if (exitArmed) bridge.finishApp() else focusPage()
            canGoBack -> session.goBack()
            else -> { exitArmed = true; focusToolbar(); notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
        }
    }

    // ------------------------------------------------------------------
    // Delegates
    // ------------------------------------------------------------------

    fun attachCore(ext: WebExtension) {
        ext.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                corePort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        if (message is JSONObject && message.optString("type") == "find_closest_result") {
                            val r = message.optJSONObject("result") ?: return
                            snapCursorTo(r.optDouble("x").toFloat(), r.optDouble("y").toFloat())
                        }
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        if (corePort == port) corePort = null
                    }
                })
            }
        }, "wishy_core")
    }

    private fun installDelegates() {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession, url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean
            ) {
                this@BrowserController.url = url ?: "about:blank"
                title = ""
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { this@BrowserController.canGoBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { this@BrowserController.canGoForward = canGoForward }
            // target=_blank / window.open would be dropped in a single-tab browser: open here instead.
            override fun onLoadRequest(
                session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    session.loadUri(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@BrowserController.title = title ?: ""
                updateRecentTitle()
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                isFullScreen = fullScreen
                if (fullScreen) focusPage()
            }
            override fun onCrash(session: GeckoSession) {
                loading = false
                notify(NoteKind.WARNING, "Web process recovered", "Reloading page...", Sym.Refresh)
                session.reload()
            }
            override fun onKill(session: GeckoSession) {
                loading = false
                notify(NoteKind.WARNING, "Web process restored", "Reloading page...", Sym.Refresh)
                session.reload()
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progress = 0; loading = true
                // New page: bank the last page's blocked count into the session total and start over.
                ui.removeCallbacks(captureRunnable)
                ui.removeCallbacks(blockedNotice)
                sessionBase += pageMax
                pageMax = 0; ubBlocked = 0; etpBlocked = 0
                blockedNow = 0; blockedSession = sessionBase
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) { this@BrowserController.progress = progress }
            @ExperimentalGeckoViewApi
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                loading = false
                if (success) {
                    session.sessionPageExtractor.getPageMetadata().accept { m -> readerable = m?.isReaderable ?: false }
                    rememberRecent()
                    scheduleCapture()
                    if (!hintsShown && !atHome) {
                        hintsShown = true; showHints = true
                        ui.postDelayed({ showHints = false }, 7000L)
                    }
                    noticeFor = this@BrowserController.url
                    ui.removeCallbacks(blockedNotice)
                    ui.postDelayed(blockedNotice, 2200L)
                } else readerable = false
            }
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) { pageScrollY = scrollY }
        }
        session.promptDelegate = promptDelegate()
        session.permissionDelegate = permissionDelegate()
        // Gecko's own tracker blocking (Strict protection). uBlock's count arrives through attachBlocker().
        session.setContentBlockingDelegate(object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (event.isBlocking) { etpBlocked++; schedulePublish() }
            }
        })
    }

    // ------------------------------------------------------------------
    // Ads and trackers blocked, page previews, recent pages
    // ------------------------------------------------------------------

    /**
     * uBlock Origin shows how many requests it blocked as the text on its toolbar button. Nothing
     * draws that button here, so this reads the text for the current tab and counts with it.
     */
    fun attachBlocker(ext: WebExtension) {
        val d = object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                if (session == null) return // the default (not per-tab) action carries no count
                ubBlocked = parseBadge(action.badgeText)
                schedulePublish()
            }
        }
        runCatching { session.webExtensionController.setActionDelegate(ext, d) }
    }

    /** "12" -> 12, "1.2k" -> 1200, "" -> 0. */
    private fun parseBadge(text: String?): Int {
        val t = text?.trim()?.lowercase().orEmpty()
        return when {
            t.isEmpty() -> 0
            t.endsWith("k") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000).toInt()
            t.endsWith("m") -> ((t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000).toInt()
            else -> t.toIntOrNull() ?: 0
        }
    }

    /** Counts can change many times a second while a page loads; publish at most every 350 ms. */
    private fun schedulePublish() {
        if (publishPending) return
        publishPending = true
        ui.postDelayed({ publishPending = false; publishBlocked() }, 350L)
    }

    private fun publishBlocked() {
        val total = ubBlocked + etpBlocked
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
    fun scheduleCapture(delayMs: Long = 1500L) {
        if (!thumbsEnabled) return
        ui.removeCallbacks(captureRunnable)
        ui.postDelayed(captureRunnable, delayMs)
    }

    /** Snapshot of the visible page. Ordinary pages stay in memory; bookmarked pages are also saved. */
    fun captureNow() {
        if (!thumbsEnabled || atHome || isFullScreen) return
        val gv = geckoView ?: return
        if (gv.width <= 0 || gv.height <= 0) return
        val u = url
        if (!u.startsWith("http")) return
        runCatching {
            gv.capturePixels().accept({ shot ->
                if (shot != null) {
                    if (u == url) {
                        thumbs.put(u, shot, isBookmarkedUrl(u))
                    } else if (!shot.isRecycled) {
                        runCatching { shot.recycle() }
                    }
                }
            }, { })
        }.onFailure {
            // Ignore capture failure under memory pressure
        }
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
        if (cursorId == img.id) setCursor("arrow_pulse")
        notify(NoteKind.INFO, "Cursor removed", img.name, Sym.DeleteOutline)
    }

    private fun promptDelegate() = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(session: GeckoSession, p: GeckoSession.PromptDelegate.AlertPrompt):
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            prompt = PromptRequest.Alert(p.title, p.message) { prompt = null; r.complete(p.dismiss()) }
            return r
        }

        override fun onButtonPrompt(session: GeckoSession, p: GeckoSession.PromptDelegate.ButtonPrompt):
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            prompt = PromptRequest.Confirm(p.title, p.message) { ok ->
                prompt = null
                r.complete(if (ok) p.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) else p.dismiss())
            }
            return r
        }

        override fun onTextPrompt(session: GeckoSession, p: GeckoSession.PromptDelegate.TextPrompt):
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            prompt = PromptRequest.TextInput(p.title, p.message, p.defaultValue ?: "") { text ->
                prompt = null
                r.complete(if (text != null) p.confirm(text) else p.dismiss())
            }
            return r
        }

        override fun onPopupPrompt(session: GeckoSession, p: GeckoSession.PromptDelegate.PopupPrompt):
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            GeckoResult.fromValue(p.confirm(AllowOrDeny.DENY))

        override fun onFilePrompt(session: GeckoSession, p: GeckoSession.PromptDelegate.FilePrompt):
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val mime = p.mimeTypes?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
            val multiple = p.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mime.size == 1) mime[0] else "*/*"
                if (mime.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mime)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }
            runCatching {
                bridge.pickFiles(Intent.createChooser(intent, "Choose file")) { uris ->
                    when {
                        uris.isNullOrEmpty() -> r.complete(p.dismiss())
                        multiple -> r.complete(p.confirm(activity, uris))
                        else -> r.complete(p.confirm(activity, uris[0]))
                    }
                }
            }.onFailure { r.complete(p.dismiss()) }
            return r
        }
    }

    private fun permissionDelegate() = object : GeckoSession.PermissionDelegate {
        override fun onAndroidPermissionsRequest(
            session: GeckoSession, permissions: Array<out String>?, callback: GeckoSession.PermissionDelegate.Callback
        ) {
            val needed = permissions?.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            } ?: emptyList()
            if (needed.isEmpty()) { callback.grant(); return }
            bridge.requestPermissions(needed.toTypedArray()) { granted -> if (granted) callback.grant() else callback.reject() }
        }

        override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int>? =
            GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

        override fun onMediaPermissionRequest(
            session: GeckoSession, uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) {
            val host = runCatching { Uri.parse(uri).host }.getOrNull() ?: uri
            prompt = PromptRequest.Permission(host) { allow ->
                prompt = null
                if (allow) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
            }
        }
    }

    // ------------------------------------------------------------------
    // Keys and the D-pad pointer
    // ------------------------------------------------------------------

    /** True while Compose should own the keys: a page/menu/dialog is open, the home screen is showing, or the toolbar has focus. */
    private val uiOwnsKeys: Boolean get() = overlay != null || prompt != null || atHome || chromeFocused

    fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        if (!down && heldKeys.remove(event.keyCode)) return true
        if (prompt != null) return false

        if (event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_D -> { if (first) toggleBookmark(); return true }
                KeyEvent.KEYCODE_B -> { if (first) openPage(Page.BOOKMARKS); return true }
                KeyEvent.KEYCODE_L -> { if (first) focusToolbar(); return true }
                KeyEvent.KEYCODE_R -> { if (first) reload(); return true }
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
            KeyEvent.KEYCODE_SEARCH -> { if (first) focusToolbar(); return true }
            KeyEvent.KEYCODE_F5 -> { if (first) reload(); return true }
        }
        if (uiOwnsKeys) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { if (down) scrollPageBy(viewH * 0.85f); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { if (down) scrollPageBy(-viewH * 0.85f); return true }
        }
        val dpad = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (dpad && !pointerOn) {
            // Pointer off: the page's own focus navigation (Wishy Core) moves between links.
            if (first) {
                val dir = when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> "Up"; KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "Left"; else -> "Right"
                }
                corePort?.postMessage(JSONObject().apply { put("type", "navigate"); put("direction", dir) })
            }
            return true
        }
        if (pointerOn) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (first) {
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && cursorY <= 2f && pageScrollY <= 0) {
                            focusToolbar(); return true
                        }
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
    }

    private val density get() = activity.resources.displayMetrics.density

    private val ticker = object : Runnable {
        override fun run() {
            if (heldKeys.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(1L, 48L) / 1000f
            lastTickMs = now
            val t = ((now - holdStartMs) / 900f).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val speed = (320f + (1500f - 320f) * eased) * density
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
        if (cursorPlaced) {
            corePort?.postMessage(JSONObject().apply { put("type", "find_closest"); put("x", cursorX); put("y", cursorY) })
        }
    }

    private var lastScrollPostMs = 0L

    private fun moveCursorBy(dx: Float, dy: Float) {
        val edge = 64f * density
        if (dy > 0f && cursorY >= viewH - edge) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScrollPostMs >= 24L) {
                lastScrollPostMs = now
                scrollPageBy(dy * 1.6f)
            }
        } else if (dy < 0f && cursorY <= edge && pageScrollY > 0) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScrollPostMs >= 24L) {
                lastScrollPostMs = now
                scrollPageBy(dy * 1.6f)
            }
        } else {
            cursorY = (cursorY + dy).coerceIn(0f, viewH)
        }
        cursorX = (cursorX + dx).coerceIn(0f, viewW)

        val now = SystemClock.uptimeMillis()
        if (now - lastClosestPostMs >= 150L) {
            lastClosestPostMs = now
            corePort?.postMessage(JSONObject().apply { put("type", "find_closest"); put("x", cursorX); put("y", cursorY) })
        }
    }

    // Magnetic snap to the nearest link when the pointer is idle (Wishy Core tells us where).
    private var snapTarget: Pair<Float, Float>? = null
    private val snapper = object : Runnable {
        override fun run() {
            val t = snapTarget ?: return
            if (heldKeys.isNotEmpty()) { snapTarget = null; return }
            val k = 0.35f
            cursorX += (t.first - cursorX) * k
            cursorY += (t.second - cursorY) * k
            if (kotlin.math.abs(t.first - cursorX) < 0.6f && kotlin.math.abs(t.second - cursorY) < 0.6f) {
                cursorX = t.first; cursorY = t.second; snapTarget = null
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
        session.panZoomController.scrollBy(ScreenLength.fromPixels(0.0), ScreenLength.fromPixels(px.toDouble()))
    }

    private fun clickAtCursor() {
        snapTarget?.let { (x, y) ->
            cursorX = x
            cursorY = y
            snapTarget = null
            activity.window.decorView.removeCallbacks(snapper)
        }
        clickTick++
        val gv = geckoView ?: return
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        gv.dispatchTouchEvent(down)
        gv.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
    }

    fun onPause() = stopCursor()

    fun onDestroy() {
        stopCursor()
        ui.removeCallbacksAndMessages(null)
        session.close()
        geckoView = null
    }
}

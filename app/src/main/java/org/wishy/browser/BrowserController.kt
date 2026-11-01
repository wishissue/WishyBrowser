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
import android.graphics.Bitmap
import android.view.View
import org.wishy.browser.downloads.DlState
import org.wishy.browser.downloads.DownloadItem
import org.wishy.browser.downloads.DownloadsManager
import org.wishy.browser.downloads.SavedFile
import org.wishy.browser.downloads.APK_MIME
import org.wishy.browser.engine.Blocker
import org.wishy.browser.engine.BrowserEngine
import org.wishy.browser.engine.DownloadRequest
import org.wishy.browser.engine.EngineListener
import org.wishy.browser.engine.Notice
import org.wishy.browser.engine.WebViewEngine
import org.wishy.browser.ui.Sym
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.roundToInt

/** What a site may do with the camera and microphone. */
enum class SiteChoice { ASK, ALLOW, BLOCK }

/** The pages the menu can open. The back stack lives in [BrowserController.stack]. */
enum class Page { MENU, BOOKMARKS, THEMES, CURSORS, SETTINGS, SHIELDS, DOWNLOADS, NOTIFICATIONS, DEVELOPER, CREDITS, DONATE }

/** A dialog a web page asked for, shown by the Compose PromptHost. */
sealed interface PromptRequest {
    class Alert(val title: String?, val message: String?, val done: () -> Unit) : PromptRequest
    class Confirm(val title: String?, val message: String?, val done: (Boolean) -> Unit) : PromptRequest
    class TextInput(val title: String?, val message: String?, val initial: String, val done: (String?) -> Unit) : PromptRequest
    class Permission(val host: String, val done: (Boolean) -> Unit) : PromptRequest
    /** Asks before opening a file that could be harmful (an app installer, a program). */
    class OpenFile(val name: String, val warning: String?, val isApk: Boolean, val done: (Boolean) -> Unit) : PromptRequest
}

/** The few things only an Activity can do. */
interface ActivityBridge {
    fun pickFiles(intent: Intent, onResult: (Array<Uri>?) -> Unit)
    fun requestPermissions(perms: Array<String>, onResult: (Boolean) -> Unit)
    fun finishApp()
}

/**
 * Owns the single browser engine, the D-pad pointer and every piece of state the Compose UI
 * shows. The UI only reads these fields and calls these functions; it never touches the engine's WebView directly.
 *
 * Remote cheat-sheet:
 *   MENU ......... toggle pointer      BACK ......... fullscreen -> page back -> toolbar -> exit
 *   SEARCH ....... address bar         BOOKMARK ..... bookmarks     CH+/CH- ... page down / up
 */
class BrowserController(
    private val activity: ComponentActivity,
    private val blocker: Blocker,
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
    lateinit var engine: BrowserEngine
        private set
    /** Bumps when the engine had to build a new view (renderer crash); the UI re-attaches the view. */
    var engineGen by mutableIntStateOf(0)
        private set
    /** A video asked for fullscreen: this is the view to show on top of everything. */
    var fullScreenView by mutableStateOf<View?>(null)
        private set

    val downloads = DownloadsManager(activity.applicationContext) { title, detail, ok ->
        ui.post { notify(if (ok) NoteKind.SUCCESS else NoteKind.ERROR, title, detail, Sym.Download) }
    }

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
    /** Requests the native blocker stopped on the current page. */
    var blockedNow by mutableIntStateOf(0)
        private set
    /** Blocked on every page since the app opened. */
    var blockedSession by mutableIntStateOf(0)
        private set
    private var pageBlocked = 0
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

    /** The URL pill in the toolbar and the search pill on the home screen take this focus. */
    val urlFocus = FocusRequester()

    val pointerVisible: Boolean
        get() = pointerOn && !atHome && !chromeFocused && overlay == null && prompt == null

    private val listener = object : EngineListener {
        override fun onUrl(url: String) { this@BrowserController.url = url }
        override fun onTitle(title: String) { this@BrowserController.title = title; updateRecentTitle() }
        override fun onProgress(percent: Int) { progress = percent }
        override fun onPageStarted(url: String) {
            progress = 0; loading = true; readerable = false; pageScrollY = 0
            // New page: bank the last page's blocked count into the session total and start over.
            ui.removeCallbacks(captureRunnable)
            ui.removeCallbacks(blockedNotice)
            sessionBase += pageMax
            pageMax = 0; pageBlocked = 0
            blockedNow = 0; blockedSession = sessionBase
            title = ""
        }
        override fun onPageFinished(success: Boolean) {
            loading = false
            if (!success) { readerable = false; return }
            rememberRecent()
            scheduleCapture()
            if (!hintsShown && !atHome) {
                hintsShown = true; showHints = true
                ui.postDelayed({ showHints = false }, 7000L)
            }
            noticeFor = url
            ui.removeCallbacks(blockedNotice)
            ui.postDelayed(blockedNotice, 2200L)
        }
        override fun onNavState(canBack: Boolean, canForward: Boolean) { canGoBack = canBack; canGoForward = canForward }
        override fun onScroll(y: Int) { pageScrollY = y }
        override fun onFullScreen(view: View?) {
            fullScreenView = view
            isFullScreen = view != null
            if (view != null) focusPage()
        }
        override fun onBlocked(countOnPage: Int) { pageBlocked = countOnPage; schedulePublish() }
        override fun onReaderable(yes: Boolean) { readerable = yes }
        override fun onRenderGone(crashed: Boolean) = recoverSession(if (crashed) "crashed" else "was stopped by Android (low memory)")
        override fun onViewReplaced() { engineGen++ }
        override fun onNotice(kind: Notice, detail: String) {
            when (kind) {
                Notice.UPGRADE_FAILED -> notify(NoteKind.WARNING, "Not secure", "$detail does not support HTTPS. Opened over HTTP.", Sym.LockOpen, 6000L)
                Notice.TRACKING_STRIPPED -> Unit
                Notice.CERT_ERROR -> notify(NoteKind.ERROR, "Blocked: invalid certificate", detail, Sym.Lock, 6000L)
                Notice.EXTERNAL_BLOCKED -> notify(NoteKind.INFO, "Blocked a link to another app", "Scheme: $detail", Sym.Block)
                Notice.POPUP_BLOCKED -> notify(NoteKind.INFO, "Pop-up blocked", detail, Sym.Block)
                Notice.LOAD_ERROR -> Unit
            }
        }

        override fun onAlert(message: String?, done: () -> Unit) {
            prompt = PromptRequest.Alert(null, message) { prompt = null; done() }
        }
        override fun onConfirm(message: String?, done: (Boolean) -> Unit) {
            prompt = PromptRequest.Confirm(null, message) { ok -> prompt = null; done(ok) }
        }
        override fun onPrompt(message: String?, default: String?, done: (String?) -> Unit) {
            prompt = PromptRequest.TextInput(null, message, default ?: "") { text -> prompt = null; done(text) }
        }
        override fun onFileChooser(mimeTypes: Array<String>, multiple: Boolean, done: (Array<Uri>?) -> Unit) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
                if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }
            runCatching { bridge.pickFiles(Intent.createChooser(intent, "Choose file"), done) }.onFailure { done(null) }
        }
        override fun onPermission(host: String, resources: Array<String>, respond: (Boolean) -> Unit) {
            val perms = ArrayList<String>()
            for (r in resources) when (r) {
                android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> perms.add(android.Manifest.permission.CAMERA)
                android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> perms.add(android.Manifest.permission.RECORD_AUDIO)
            }
            // DRM playback (EME) alone is harmless and needed by streaming sites; camera and microphone follow the site's setting.
            if (perms.isEmpty()) { respond(resources.all { it == android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }); return }
            fun finish(allow: Boolean) {
                val needed = perms.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
                if (!allow) respond(false)
                else if (needed.isEmpty()) respond(true)
                else bridge.requestPermissions(needed.toTypedArray()) { granted -> respond(granted) }
            }
            when (siteMedia(host)) {
                SiteChoice.ALLOW -> finish(true)
                SiteChoice.BLOCK -> respond(false)
                SiteChoice.ASK -> prompt = PromptRequest.Permission(host) { allow -> prompt = null; finish(allow) }
            }
        }
        override fun onDownload(req: DownloadRequest) { startDownload(req) }
    }

    init {
        // Private by design: wipe anything a previous run left in WebView's storage before the first page loads.
        WebViewEngine.wipe(activity, null)
        engine = WebViewEngine(activity, listener, blocker, prefs.desktopMode, prefs.textScale)
        engine.scriptPolicy = { host -> host == null || siteKey(host) !in prefs.jsOffHosts }
        thumbs.prune(bookmarks.map { it.url })
    }

    fun loadInitial(url: String?) = engine.load(url ?: homeTarget())

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
        engine.load(resolveInput(input))
        focusPage()
    }

    fun goHome() {
        exitArmed = false
        stack.clear()
        engine.load(homeTarget())
    }

    fun back() { engine.goBack() }
    fun forward() { engine.goForward() }
    fun reload() { engine.reload() }
    fun stop() { engine.stop() }

    fun focusPage() {
        chromeFocused = false
        runCatching { engine.view.requestFocus() }
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
        val existing = notes.firstOrNull { it.title == title && it.message == message }
        val id = existing?.id ?: ++noteSeq
        val n = Note(id, kind, title, message, icon, durationMs, System.currentTimeMillis())

        val idx = notes.indexOfFirst { it.id == id }
        if (idx >= 0) {
            notes[idx] = n
        } else {
            notes.add(n)
        }
        while (notes.size > 3) notes.removeAt(0)

        val histIdx = noteHistory.indexOfFirst { it.title == title && it.message == message }
        if (histIdx >= 0) noteHistory.removeAt(histIdx)
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
        engine.setDesktopMode(on)
        notify(NoteKind.INFO, if (on) "Desktop site on" else "Mobile site on", "Reloading the page", Sym.DesktopWindows)
    }

    fun cycleTextSize() {
        val scales = Prefs.TEXT_SCALES
        val i = scales.indexOfFirst { it >= textScale - 0.01f }.coerceAtLeast(0)
        val next = scales[(i + 1) % scales.size]
        prefs.textScale = next; textScale = next
        engine.setTextScale(next)
        notify(NoteKind.INFO, "Text size ${(next * 100).roundToInt()}%", null, Sym.TextIncrease)
    }

    fun openReader() {
        if (!atHome) engine.showReader()
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

    /** Answers the open dialog with "no", so the page's JavaScript is never left waiting for it. */
    private fun dismissPrompt() {
        when (val p = prompt) {
            is PromptRequest.Alert -> p.done()
            is PromptRequest.Confirm -> p.done(false)
            is PromptRequest.TextInput -> p.done(null)
            is PromptRequest.Permission -> p.done(false)
            is PromptRequest.OpenFile -> p.done(false)
            null -> Unit
        }
        prompt = null
    }

    /** Handle Back button: closes prompts and overlays first, then page navigation. */
    fun handleBack() {
        when {
            prompt != null -> dismissPrompt()
            stack.isNotEmpty() -> popPage()
            isFullScreen -> engine.exitFullScreen()
            atHome -> if (exitArmed) bridge.finishApp() else { exitArmed = true; notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
            chromeFocused -> if (exitArmed) bridge.finishApp() else focusPage()
            canGoBack -> engine.goBack()
            else -> { exitArmed = true; focusToolbar(); notify(NoteKind.INFO, "Press Back again to exit", null, Sym.Close, 2600L) }
        }
    }

    // ------------------------------------------------------------------
    // Crash recovery and memory pressure
    // ------------------------------------------------------------------

    private val crashTimes = ArrayDeque<Long>()

    /**
     * The engine has already built a fresh WebView and reloaded the page. If the same thing happens 3 times
     * in a minute we go home instead, so a page that is too heavy for the device cannot trap the app in a crash loop.
     */
    private fun recoverSession(why: String) {
        loading = false
        val now = SystemClock.uptimeMillis()
        crashTimes.addLast(now)
        while (crashTimes.isNotEmpty() && now - crashTimes.first() > 60_000L) crashTimes.removeFirst()
        val looping = crashTimes.size >= 3
        if (looping) {
            crashTimes.clear()
            engine.load(homeTarget())
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

    /** Counts can change many times a second while a page loads; publish at most every 350 ms. */
    private fun schedulePublish() {
        if (publishPending) return
        publishPending = true
        ui.postDelayed({ publishPending = false; publishBlocked() }, 350L)
    }

    private fun publishBlocked() {
        pageMax = max(pageMax, pageBlocked)
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
        // On low-RAM TVs the automatic screenshot is what pushed heavy pages (GitHub) over the edge: it
        // grabs a full-resolution frame right when the page is at its largest. Previews still happen
        // when the menu opens or a page is bookmarked.
        if (!thumbsEnabled || lowRam) return
        ui.removeCallbacks(captureRunnable)
        ui.postDelayed(captureRunnable, delayMs)
    }

    /** Snapshot of the visible page. Ordinary pages stay in memory; bookmarked pages are also saved. */
    fun captureNow() {
        if (!thumbsEnabled || atHome || isFullScreen || systemLowOnMemory()) return
        val u = url
        if (!u.startsWith("http")) return
        runCatching {
            engine.capture { shot ->
                if (shot != null) {
                    if (u == url) thumbs.put(u, shot, isBookmarkedUrl(u))
                    else if (!shot.isRecycled) runCatching { shot.recycle() }
                }
            }
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
        if (cursorId == img.id) setCursor(org.wishy.browser.ui.DEFAULT_CURSOR_ID)
        notify(NoteKind.INFO, "Cursor removed", img.name, Sym.DeleteOutline)
    }

    // ------------------------------------------------------------------
    // Keys and the D-pad pointer
    // ------------------------------------------------------------------

    /** True while Compose should own the keys: a page/menu/dialog is open, the home screen is showing, or the toolbar has focus. */
    private val uiOwnsKeys: Boolean get() = overlay != null || prompt != null || atHome || chromeFocused

    fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0

        if (!down) {
            val removed = heldKeys.remove(event.keyCode)
            if (heldKeys.isEmpty()) {
                stopCursor()
                scheduleSnap()
            }
            if (removed) return true
        }

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

        if (uiOwnsKeys) {
            stopCursor()
            return false
        }

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
                engine.navigate(dir)
            }
            return true
        }
        if (pointerOn) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (first) {
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && cursorY <= 2f && engine.view.scrollY <= 0) {
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
        if (w <= 0f || h <= 0f) return
        viewW = w; viewH = h
        if (!cursorPlaced) {
            cursorX = w / 2f
            cursorY = h / 2f
            cursorPlaced = true
        } else {
            cursorX = cursorX.coerceIn(0f, w)
            cursorY = cursorY.coerceIn(0f, h)
        }
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
        activity.window.decorView.removeCallbacks(snapRequest)
        snapTarget = null
        activity.window.decorView.removeCallbacks(snapper)
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


    fun stopCursor() {
        heldKeys.clear()
        activity.window.decorView.removeCallbacks(ticker)
        activity.window.decorView.removeCallbacks(snapRequest)
    }

    /** Asks the page for the nearest link. The answer is dropped if the pointer has moved since (no yanking back). */
    private fun requestSnap() {
        val sx = cursorX; val sy = cursorY
        engine.findClosest(sx, sy, viewW) { r ->
            if (r != null && heldKeys.isEmpty() &&
                kotlin.math.abs(cursorX - sx) < 3f && kotlin.math.abs(cursorY - sy) < 3f
            ) snapCursorTo(r.first, r.second)
        }
    }

    private val snapRequest = Runnable { if (heldKeys.isEmpty() && cursorPlaced && pointerVisible) requestSnap() }

    /** Magnetic snap only once the pointer has been still for a moment, so single taps can nudge it off a link. */
    private fun scheduleSnap() {
        val d = activity.window.decorView
        d.removeCallbacks(snapRequest)
        d.postDelayed(snapRequest, 300L)
    }

    private var lastScrollPostMs = 0L

    private fun moveCursorBy(dx: Float, dy: Float) {
        val edge = 64f * density
        cursorX = (cursorX + dx).coerceIn(0f, viewW)
        cursorY = (cursorY + dy).coerceIn(0f, viewH)

        if (dy > 0f && cursorY >= viewH - edge) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScrollPostMs >= 24L) {
                lastScrollPostMs = now
                scrollPageBy(dy * 1.6f)
            }
        } else if (dy < 0f && cursorY <= edge && engine.view.scrollY > 0) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScrollPostMs >= 24L) {
                lastScrollPostMs = now
                scrollPageBy(dy * 1.6f)
            }
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
        engine.scrollBy(px)
    }

    private fun clickAtCursor() {
        snapTarget?.let { (x, y) ->
            cursorX = x
            cursorY = y
            snapTarget = null
            activity.window.decorView.removeCallbacks(snapper)
        }
        clickTick++
        val gv = engine.view
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
        downloads.shutdown()
        engine.destroy()
    }

    // ------------------------------------------------------------------
    // Per-site settings
    // ------------------------------------------------------------------

    private fun siteKey(host: String) = host.lowercase().removePrefix("www.")

    /** The site the page shows, or null on the start screen. */
    val siteHost: String? get() = if (atHome) null else Uri.parse(url).host?.let { siteKey(it) }
    private var siteTick by mutableIntStateOf(0)

    fun siteJsAllowed(): Boolean { siteTick; val h = siteHost ?: return true; return h !in prefs.jsOffHosts }
    fun siteMedia(host: String? = siteHost): SiteChoice {
        siteTick
        val h = host?.let { siteKey(it) } ?: return SiteChoice.ASK
        return when { h in prefs.mediaAllowHosts -> SiteChoice.ALLOW; h in prefs.mediaDenyHosts -> SiteChoice.BLOCK; else -> SiteChoice.ASK }
    }

    fun toggleSiteJs() {
        val h = siteHost ?: return
        val off = prefs.jsOffHosts.toMutableSet()
        val nowOff = if (h in off) { off.remove(h); false } else { off.add(h); true }
        prefs.jsOffHosts = off; siteTick++
        engine.applyScriptPolicy(); engine.reload()
        notify(NoteKind.INFO, if (nowOff) "JavaScript off for $h" else "JavaScript on for $h", "Reloading the page", Sym.Code)
    }

    /** Ask -> Allow -> Block -> Ask. */
    fun cycleSiteMedia() {
        val h = siteHost ?: return
        val a = prefs.mediaAllowHosts.toMutableSet(); val d = prefs.mediaDenyHosts.toMutableSet()
        val next = when (siteMedia(h)) { SiteChoice.ASK -> SiteChoice.ALLOW; SiteChoice.ALLOW -> SiteChoice.BLOCK; SiteChoice.BLOCK -> SiteChoice.ASK }
        a.remove(h); d.remove(h)
        if (next == SiteChoice.ALLOW) a.add(h) else if (next == SiteChoice.BLOCK) d.add(h)
        prefs.mediaAllowHosts = a; prefs.mediaDenyHosts = d; siteTick++
    }

    fun resetSiteSettings() {
        prefs.resetSites(); siteTick++
        engine.applyScriptPolicy(); engine.reload()
        notify(NoteKind.SUCCESS, "Site settings reset", "JavaScript and camera/microphone choices", Sym.CleaningServices)
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    private fun startDownload(r: DownloadRequest) {
        val existing = downloads.active(r.url)
        if (existing != null) { notify(NoteKind.INFO, "Already downloading", existing.name, Sym.Download); return }
        val item = downloads.enqueue(r.url, r.contentDisposition, r.mimeType, r.cookie, r.userAgent, r.referer) ?: return
        notify(NoteKind.INFO, "Download started", item.name, Sym.Download)
        if (r.url.startsWith("http://")) notify(NoteKind.WARNING, "Not encrypted", "This file is downloaded over HTTP", Sym.LockOpen, 5000L)
    }

    fun downloadsSummary(): String {
        val running = downloads.items.count { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
        return if (running > 0) "$running active" else if (downloads.items.isEmpty()) "Nothing yet" else "${downloads.items.size} this session"
    }

    val blockerDomains: Int get() = blocker.domainCount

    fun openDownloadsFolder() {
        val ok = runCatching { activity.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) }.isSuccess
        if (!ok) notify(NoteKind.INFO, "No file manager on this device", "Your downloaded files are listed on this page", Sym.Folder)
    }

    fun pauseDownload(i: DownloadItem) = downloads.pause(i)
    fun resumeDownload(i: DownloadItem) = downloads.resume(i)
    fun cancelDownload(i: DownloadItem) = downloads.cancel(i)
    fun removeDownload(i: DownloadItem) = downloads.remove(i)

    /** Opens a finished file. App installers and program files always ask first. */
    fun openDownload(i: DownloadItem) { i.uri?.let { openFile(i.name, it, i.warning) } }
    fun openSaved(f: SavedFile) = openFile(f.name, f.uri, DownloadsManager.warningFor(f.name, f.mime))

    private fun openFile(name: String, uri: Uri, warning: String?) {
        val apk = name.lowercase().endsWith(".apk")
        if (apk || warning != null) {
            prompt = PromptRequest.OpenFile(name, warning, apk) { ok ->
                prompt = null
                if (ok) { if (apk) installApk(uri) else launchView(name, uri) }
            }
        } else launchView(name, uri)
    }

    private fun launchView(name: String, uri: Uri) {
        val mime = DownloadsManager.guessMime(name, null)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(intent) }.onFailure {
            notify(NoteKind.WARNING, "No app can open this", name, Sym.Description)
        }
    }

    /** Hands the file to Android's own package installer. Android shows its own confirmation; nothing installs silently. */
    private fun installApk(uri: Uri) {
        val pm = activity.packageManager
        if (!pm.canRequestPackageInstalls()) {
            notify(NoteKind.INFO, "Allow installs first", "Turn on \"Allow from this source\" for Wishy, then open the file again", Sym.Security, 8000L)
            val ok = runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            }.isSuccess
            if (!ok) openDefaultBrowserSettings()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(intent) }.onFailure {
            notify(NoteKind.ERROR, "This device has no package installer", null, Sym.Error)
        }
    }

    /** Clears cookies, site storage and cache now. */
    fun clearBrowsingData() {
        engine.clearData()
        notify(NoteKind.SUCCESS, "Browsing data cleared", "Cookies, site storage and cache", Sym.CleaningServices)
    }
}

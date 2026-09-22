package org.wishy.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.animation.ValueAnimator
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.card.MaterialCardView
import org.wishy.browser.databinding.ItemTileBinding
import java.util.Calendar
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.ScreenLength
import org.wishy.browser.databinding.ActivityMainBinding
import kotlin.math.roundToInt

/**
 * Single-tab, single-screen browser for Android TV.
 *
 * What's here:
 *   - a URL/search bar with bookmark star, bookmarks list and a small menu
 *   - the page itself (with a slim load-progress line)
 *   - a D-pad-driven pointer (with edge scrolling) for pages that need a mouse
 *   - fullscreen video support
 *
 * Remote cheat-sheet:
 *   MENU ............ toggle pointer mode
 *   BACK ............ close pointer/fullscreen -> page back -> address bar -> exit
 *   BOOKMARK / Ctrl+B  open bookmarks        Ctrl+D ... star this page
 *   SEARCH / Ctrl+L .. jump to address bar   CH+/CH- ... page down / up
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: GeckoSession
    private lateinit var runtime: GeckoRuntime
    private lateinit var bookmarks: BookmarkStore
    private lateinit var prefs: Prefs

    // Current page info (used for bookmarking).
    private var currentUrl: String? = null
    private var currentTitle: String? = null

    private var canGoBack = false
    private var isFullScreen = false
    private var pageScrollY = 0
    private var isReaderable = false

    private var wishyCorePort: WebExtension.Port? = null

    private val wishyMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            wishyCorePort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message is JSONObject && message.optString("type") == "find_closest_result") {
                        val result = message.optJSONObject("result")
                        if (result != null) {
                            val sx = result.optDouble("x").toFloat()
                            val sy = result.optDouble("y").toFloat()
                            snapCursorTo(sx, sy)
                        }
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    if (wishyCorePort == port) wishyCorePort = null
                }
            })
        }
    }

    private var snapAnimatorX: ValueAnimator? = null
    private var snapAnimatorY: ValueAnimator? = null

    private fun snapCursorTo(sx: Float, sy: Float) {
        // Only snap if we are NOT currently holding keys (moving)
        if (heldKeys.isNotEmpty()) return
        
        snapAnimatorX?.cancel()
        snapAnimatorY?.cancel()

        snapAnimatorX = ValueAnimator.ofFloat(cursorX, sx).apply {
            duration = 150L
            interpolator = DecelerateInterpolator()
            addUpdateListener { 
                cursorX = it.animatedValue as Float
                positionCursorOverlay()
            }
            start()
        }
        snapAnimatorY = ValueAnimator.ofFloat(cursorY, sy).apply {
            duration = 150L
            interpolator = DecelerateInterpolator()
            addUpdateListener { 
                cursorY = it.animatedValue as Float
                positionCursorOverlay()
            }
            start()
        }
    }

    // Set when Back moved focus to the address bar because there was
    // nothing left to go back to; the next Back then exits the app.
    private var exitArmed = false

    // Pointer/cursor mode state for D-pad navigation on non-TV-optimized pages.
    // Tracked in GeckoView's own local coordinate space (0,0 = top-left of
    // the page viewport, NOT the top-left of the screen) since that's what
    // both the touch-dispatch target and the overlay positioning need.
    private var cursorMode = true // pointer is ON by default
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorPlaced = false

    // Start screen + smooth cursor state
    private var startVisible = true
    private var cursorShown = false
    private val heldKeys = LinkedHashSet<Int>()
    private var holdStartMs = 0L
    private var lastTickMs = 0L

    /** Runs every frame while a D-pad direction is held: ramps speed up smoothly. */
    private val cursorTicker = object : Runnable {
        override fun run() {
            if (heldKeys.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(1L, 48L) / 1000f
            lastTickMs = now
            val t = ((now - holdStartMs) / 900f).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val d = resources.displayMetrics.density
            val speed = (320f + (1500f - 320f) * eased) * d // px per second
            var dx = 0f
            var dy = 0f
            for (k in heldKeys) when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> dy -= 1f
                KeyEvent.KEYCODE_DPAD_DOWN -> dy += 1f
                KeyEvent.KEYCODE_DPAD_LEFT -> dx -= 1f
                KeyEvent.KEYCODE_DPAD_RIGHT -> dx += 1f
            }
            if (dx != 0f || dy != 0f) moveCursorBy(dx * speed * dt, dy * speed * dt)
            binding.root.postOnAnimation(this)
        }
    }

    // ------------------------------------------------------------------
    // Activity-result launchers for file pickers and Android runtime
    // permissions. These must be registered unconditionally as field
    // initializers (i.e. before onCreate/onStart run) or the platform
    // throws at launch time.
    // ------------------------------------------------------------------

    /** Set right before launching the picker; consumed exactly once when it returns. */
    private var filePromptCallback: ((Array<Uri>?) -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePromptCallback
        filePromptCallback = null
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback?.invoke(uris)
    }

    /** Set right before launching the request; consumed exactly once when it returns. */
    private var pendingAndroidPermissionGrant: ((Boolean) -> Unit)? = null

    private val androidPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val callback = pendingAndroidPermissionGrant
        pendingAndroidPermissionGrant = null
        callback?.invoke(grants.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15+ draws apps edge to edge. Keep the UI clear of the system
        // bars and display cutouts (TVs usually report none, some boxes do).
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Android 13+ delivers Back through the predictive-back dispatcher and
        // (targeting Android 16) never sends it as a key event. Older versions
        // still go through dispatchKeyEvent below.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        runtime = BrowserApplication.from(application).runtime
        bookmarks = BookmarkStore(applicationContext)
        prefs = Prefs(applicationContext)

        // usePrivateMode = true => Gecko never writes history, form data,
        // or persistent cookies to disk for this session. Bookmarks and the
        // three small preferences live in our own app-private storage and
        // exist only because the user chose to save them.
        val desktop = prefs.desktopMode
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            .userAgentMode(
                if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .build()

        session = GeckoSession(sessionSettings)
        session.open(runtime)
        binding.geckoView.setSession(session)

        setUpSessionDelegates()
        setUpBar()
        setUpCursorLayout()
        buildTiles()
        FocusAnim.attach(binding.root)
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, _ -> updateCursorVisibility() }
        updateGreeting()
        staggerIn(listOf(binding.clockBlock, binding.startSearchCard, binding.tilesScroll))

        // If we were launched to open a link (from another app, or by being
        // the default browser), load that instead of the home page.
        val viewUrl = viewIntentUrl(intent)
        session.loadUri(viewUrl ?: homeUrl())

        // Opening the app: land on the search bar with the keyboard up so
        // you can just type. If we were launched to open a link, the page
        // gets focus instead so the D-pad works on it right away.
        binding.startEditText.post { restoreFocus(showKeyboard = true) }
        updateStar()
        maybeShowOnboarding()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // launchMode="singleTask": a link opened from another app while
        // we're already running arrives here instead of a fresh onCreate.
        viewIntentUrl(intent)?.let { url ->
            exitArmed = false
            session.loadUri(url)
            binding.geckoView.requestFocus()
        }
    }

    private fun viewIntentUrl(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()

    /** Shown exactly once, ever, the first time the app is opened. */
    private fun maybeShowOnboarding() {
        if (prefs.onboardingShown) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.onboarding_title)
            .setMessage(R.string.onboarding_message)
            .setCancelable(false)
            .setPositiveButton(R.string.got_it) { _, _ ->
                prefs.onboardingShown = true
                restoreFocus(showKeyboard = true)
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private fun setUpSessionDelegates() {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                currentUrl = url
                currentTitle = null // the new page's title arrives via onTitleChange
                // Don't overwrite what the user is typing.
                if (url != null && !binding.urlEditText.hasFocus()) {
                    binding.urlEditText.setText(if (url.startsWith("about:")) "" else url)
                }
                if (url != null) showStart(url.startsWith("about:"))
                updateStar()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }

            // Links that ask for a new window/tab (target="_blank",
            // window.open from a click) would otherwise be silently dropped
            // in a single-tab browser. Open them in this tab instead.
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
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
                currentTitle = title
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                setFullScreen(fullScreen)
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                binding.progressBar.setProgressCompat(0, false)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                binding.progressBar.setProgressCompat(progress, true)
            }

            @ExperimentalGeckoViewApi
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                binding.progressBar.visibility = View.INVISIBLE
                if (success) {
                    session.sessionPageExtractor.getPageMetadata().accept { metadata ->
                        isReaderable = metadata?.isReaderable ?: false
                    }
                } else {
                    isReaderable = false
                }
            }
        }

        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                pageScrollY = scrollY
            }
        }

        session.promptDelegate = pageDialogDelegate()
        session.permissionDelegate = pagePermissionDelegate()

        runtime.webExtensionController.list().accept { extensions ->
            extensions?.find { it.id == "core@wishy.org" }?.let { core ->
                core.setMessageDelegate(wishyMessageDelegate, "wishy_core")
            }
        }
    }

    // ------------------------------------------------------------------
    // JS alerts/confirms/prompts and file pickers.
    // ------------------------------------------------------------------

    private fun pageDialogDelegate() = object : GeckoSession.PromptDelegate {

        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(prompt.title)
                .setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .setOnDismissListener { restoreFocus() }
                .show()
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(prompt.title)
                .setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .setOnDismissListener { restoreFocus() }
                .show()
            return result
        }

        override fun onTextPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.TextPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val input = EditText(this@MainActivity).apply {
                setText(prompt.defaultValue)
                setSingleLine()
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(prompt.title)
                .setMessage(prompt.message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.complete(prompt.confirm(input.text.toString()))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .setOnDismissListener { restoreFocus() }
                .show()
            return result
        }

        override fun onPopupPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.PopupPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            // Real popup windows have nowhere to go in a single-tab browser;
            // target="_blank" links are already redirected in onLoadRequest.
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

        override fun onFilePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.FilePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val mimeTypes = prompt.mimeTypes?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
            val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
                if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }

            filePromptCallback = { uris ->
                when {
                    uris.isNullOrEmpty() -> result.complete(prompt.dismiss())
                    multiple -> result.complete(prompt.confirm(this@MainActivity, uris))
                    else -> result.complete(prompt.confirm(this@MainActivity, uris[0]))
                }
            }
            runCatching {
                filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.choose_file)))
            }.onFailure {
                filePromptCallback = null
                result.complete(prompt.dismiss())
            }
            return result
        }
    }

    // ------------------------------------------------------------------
    // Permission prompts (geolocation/notifications/camera/mic), plus the
    // Android-level runtime permission dialog camera/mic need on top of it.
    // ------------------------------------------------------------------

    private fun pagePermissionDelegate() = object : GeckoSession.PermissionDelegate {

        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<out String>?,
            callback: GeckoSession.PermissionDelegate.Callback
        ) {
            val needed = permissions?.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            } ?: emptyList()
            if (needed.isEmpty()) {
                callback.grant()
                return
            }
            pendingAndroidPermissionGrant = { granted -> if (granted) callback.grant() else callback.reject() }
            androidPermissionLauncher.launch(needed.toTypedArray())
        }

        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int>? =
            // Location and notifications can never work in this app: the
            // manifest declares no location permission (Android would refuse
            // it even after the user tapped Allow) and no web-notification
            // delegate is installed. So deny everything silently instead of
            // showing a dialog whose "Allow" button does nothing. Storage
            // access, autoplay, DRM, etc. keep the strict-privacy default too.
            GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) {
            val host = runCatching { Uri.parse(uri).host }.getOrNull() ?: uri
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(R.string.permission_title, host))
                .setMessage(R.string.permission_camera_mic)
                .setPositiveButton(R.string.permission_allow) { _, _ ->
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                }
                .setNegativeButton(R.string.permission_deny) { _, _ -> callback.reject() }
                .setOnCancelListener { callback.reject() }
                .setOnDismissListener { restoreFocus() }
                .show()
        }
    }

    private fun setUpBar() {
        binding.goButton.setOnClickListener { loadFromBar() }
        binding.startGoButton.setOnClickListener { loadFromBar() }
        binding.bookmarkButton.setOnClickListener { toggleBookmark() }
        binding.bookmarksListButton.setOnClickListener { showBookmarks() }
        binding.menuButton.setOnClickListener { showMenu() }
        binding.pointerToggleButton.setOnClickListener { toggleCursorMode() }
        binding.backButton.setOnClickListener { session.goBack() }
        binding.homeButton.setOnClickListener { goHome() }

        setUpInput(binding.urlEditText, binding.urlBarCard)
        setUpInput(binding.startEditText, binding.startSearchCard)

        binding.geckoView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) exitArmed = false
        }
        updatePointerTint()
    }

    /** Enter/Go submits, and the whole bar glows + lifts while it has focus. */
    private fun setUpInput(edit: EditText, card: MaterialCardView) {
        edit.setOnEditorActionListener { _, actionId, event ->
            val enterKey = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                enterKey
            ) {
                loadFromBar()
                true
            } else {
                false
            }
        }
        edit.setOnFocusChangeListener { _, hasFocus ->
            val d = resources.displayMetrics.density
            card.strokeWidth = ((if (hasFocus) 3 else 1) * d).toInt()
            card.setStrokeColor(
                ColorStateList.valueOf(getColor(if (hasFocus) R.color.neon_cyan else R.color.glass_border))
            )
            val scale = if (hasFocus) 1.02f else 1f
            card.animate().scaleX(scale).scaleY(scale).setDuration(220L).start()
        }
    }

    private fun updatePointerTint() {
        binding.pointerToggleButton.iconTint = ColorStateList.valueOf(
            getColor(if (cursorMode) R.color.accent else R.color.icon_default)
        )
    }

    private fun setUpCursorLayout() {
        // Keeps the cursor valid when the page area changes size.
        binding.geckoView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!cursorPlaced && v.width > 0) {
                cursorX = v.width / 2f
                cursorY = v.height / 2f
                cursorPlaced = true
            }
            cursorX = cursorX.coerceIn(0f, v.width.toFloat())
            cursorY = cursorY.coerceIn(0f, v.height.toFloat())
            positionCursorOverlay()
        }
    }

    // ------------------------------------------------------------------
    // Address bar / navigation
    // ------------------------------------------------------------------

    private fun homeUrl(): String = prefs.homeUrl ?: getString(R.string.home_url)

    private fun loadFromBar() {
        val edit = activeInput()
        val input = edit.text.toString().trim()
        if (input.isEmpty()) return

        exitArmed = false
        session.loadUri(resolveInput(input))
        edit.clearFocus()
        binding.startEditText.setText("")
        showStart(false)
        hideKeyboardAndFocusPage()
    }

    /** Turns whatever was typed into a URL or a search. */
    private fun resolveInput(input: String): String {
        // Already has a scheme (https://, http://, file://, ...): use as typed.
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(input)) return input

        val hasSpace = input.contains(' ')
        // A bare LAN address such as 192.168.1.10:8080 -> plain http (routers, NAS, Plex, ...).
        val lanAddress = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
        if (!hasSpace && lanAddress.matches(input)) return "http://$input"

        // Looks like a domain (contains a dot, no spaces) -> https.
        if (!hasSpace && input.contains('.')) return "https://$input"

        return "https://duckduckgo.com/html/?q=" + Uri.encode(input)
    }

    private fun hideKeyboardAndFocusPage() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.geckoView.requestFocus()
    }

    private fun focusAddressBar(showKeyboard: Boolean = false) {
        stopCursor()
        val edit = activeInput()
        edit.requestFocus()
        edit.selectAll()
        if (showKeyboard) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Where focus should go after a popup closes or the app opens: the
     * search bar while the page is blank (so you can just type), otherwise
     * the page so the D-pad works on it.
     */
    private fun restoreFocus(showKeyboard: Boolean = false) {
        val blank = currentUrl.isNullOrBlank() || currentUrl!!.startsWith("about:")
        val openedLink = viewIntentUrl(intent) != null
        if (blank && !openedLink) focusAddressBar(showKeyboard)
        else binding.geckoView.requestFocus()
    }

    // ------------------------------------------------------------------
    // Start screen, home, and animation helpers
    // ------------------------------------------------------------------

    private fun activeInput(): EditText =
        if (startVisible) binding.startEditText else binding.urlEditText

    private fun goHome() {
        exitArmed = false
        val home = homeUrl()
        session.loadUri(home)
        if (home.startsWith("about:")) {
            showStart(true)
            binding.startEditText.post { focusAddressBar() }
        }
    }

    private fun openUrl(url: String) {
        exitArmed = false
        showStart(false)
        session.loadUri(url)
        hideKeyboardAndFocusPage()
    }

    /** Cross-fades between the centered start screen and the browsing bar. */
    private fun showStart(show: Boolean) {
        if (show == startVisible) return
        startVisible = show
        val d = resources.displayMetrics.density
        if (show) {
            updateGreeting()
            binding.topBar.visibility = View.GONE
            binding.startPage.alpha = 0f
            binding.startPage.visibility = View.VISIBLE
            binding.startPage.animate().alpha(1f).setDuration(280L).start()
            staggerIn(listOf(binding.clockBlock, binding.startSearchCard, binding.tilesScroll))
        } else {
            binding.startPage.animate().alpha(0f).setDuration(220L).withEndAction {
                if (!startVisible) binding.startPage.visibility = View.GONE
            }.start()
            if (!isFullScreen) {
                binding.topBar.visibility = View.VISIBLE
                binding.topBar.alpha = 0f
                binding.topBar.translationY = -18f * d
                binding.topBar.animate().alpha(1f).translationY(0f).setDuration(320L)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
        updateCursorVisibility()
    }

    /** Items float up one after another (optimized for weak TV hardware). */
    private fun staggerIn(views: List<View>) {
        val d = resources.displayMetrics.density
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 16f * d
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(40L * i).setDuration(250L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { v.animate().setStartDelay(0L) }
                .start()
        }
    }

    private fun updateGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.greetingText.setText(
            when {
                h < 5 -> R.string.greeting_night
                h < 12 -> R.string.greeting_morning
                h < 17 -> R.string.greeting_afternoon
                h < 22 -> R.string.greeting_evening
                else -> R.string.greeting_night
            }
        )
    }

    private data class Shortcut(val labelRes: Int, val glyph: String, val color: String, val url: String?)

    /** Edit this list to change the start-screen shortcuts (url = null opens Saved). */
    private fun buildTiles() {
        val shortcuts = listOf(
            Shortcut(R.string.tile_weather, "W", "#4C9BFF", "https://duckduckgo.com/?q=weather&ia=weather"),
            Shortcut(R.string.tile_maps, "M", "#2FBF71", "https://www.google.com/maps"),
            Shortcut(R.string.tile_amazon, "a", "#FF9900", "https://www.amazon.com"),
            Shortcut(R.string.tile_gmail, "G", "#EA4335", "https://mail.google.com"),
            Shortcut(R.string.tile_youtube, "\u25B6", "#FF1F3D", "https://www.youtube.com"),
            Shortcut(R.string.tile_facebook, "f", "#1877F2", "https://www.facebook.com"),
            Shortcut(R.string.tile_saved, "\u2605", "#F5B301", null)
        )
        for (sc in shortcuts) {
            val tile = ItemTileBinding.inflate(LayoutInflater.from(this), binding.tilesRow, false)
            tile.glyph.text = sc.glyph
            tile.label.setText(sc.labelRes)
            tile.iconWrap.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(sc.color))
            }
            tile.root.setOnClickListener {
                val url = sc.url
                if (url == null) showBookmarks() else openUrl(url)
            }
            binding.tilesRow.addView(tile.root)
        }
    }

    /** Playful pop used when something is toggled. */
    private fun bounce(v: View) {
        val rest = if (v.isFocused) FocusAnim.scaleFor(v) else 1f
        v.animate().cancel()
        v.scaleX = 0.7f
        v.scaleY = 0.7f
        v.animate().scaleX(rest).scaleY(rest).setDuration(420L)
            .setInterpolator(OvershootInterpolator(3f)).start()
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    private fun toggleBookmark() {
        val url = currentUrl
        if (url.isNullOrBlank() || url.startsWith("about:")) {
            toast(getString(R.string.nothing_to_bookmark))
            return
        }
        val added = bookmarks.toggle(currentTitle, url)
        if (added) {
            val label = currentTitle?.takeIf { it.isNotBlank() } ?: Uri.parse(url).host ?: url
            toast(getString(R.string.bookmark_added, label))
        } else {
            toast(getString(R.string.bookmark_removed))
        }
        updateStar()
        bounce(binding.bookmarkButton)
    }

    /** Star is filled (and yellow) when the current page is bookmarked. */
    private fun updateStar() {
        val on = currentUrl?.let { bookmarks.contains(it) } == true
        binding.bookmarkButton.setIconResource(
            if (on) R.drawable.ic_star_filled_24dp else R.drawable.ic_star_24dp
        )
        binding.bookmarkButton.iconTint = ColorStateList.valueOf(
            getColor(if (on) R.color.md_star else R.color.icon_default)
        )
        binding.bookmarkButton.contentDescription =
            getString(if (on) R.string.remove_bookmark else R.string.add_bookmark)
    }

    private fun showBookmarks() {
        BookmarksDialog(
            activity = this,
            store = bookmarks,
            onOpen = { bookmark ->
                exitArmed = false
                session.loadUri(bookmark.url)
            },
            onClosed = {
                updateStar() // a bookmark may have been deleted
                restoreFocus()
            }
        ).show()
    }

    // ------------------------------------------------------------------
    // Menu (back / forward / reload / home / desktop site / text size)
    // ------------------------------------------------------------------

    private fun showMenu() {
        val desktop = prefs.desktopMode
        val scalePercent = (prefs.textScale * 100).roundToInt()
        val labels = mutableListOf(
            getString(R.string.menu_back),
            getString(R.string.menu_forward),
            getString(R.string.menu_reload),
            getString(R.string.menu_home),
            getString(R.string.menu_set_home),
            getString(if (desktop) R.string.menu_desktop_on else R.string.menu_desktop_off),
            getString(R.string.menu_text_size, scalePercent),
            getString(R.string.menu_default_browser)
        )
        if (isReaderable) {
            labels.add(getString(R.string.menu_reader_view))
        }
        labels.add(getString(R.string.menu_exit))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setItems(labels.toTypedArray()) { _, which ->
                val label = labels[which]
                when {
                    label == getString(R.string.menu_back) -> session.goBack()
                    label == getString(R.string.menu_forward) -> session.goForward()
                    label == getString(R.string.menu_reload) -> session.reload()
                    label == getString(R.string.menu_home) -> goHome()
                    label == getString(R.string.menu_set_home) -> setHomeToCurrentPage()
                    label == getString(if (desktop) R.string.menu_desktop_on else R.string.menu_desktop_off) -> toggleDesktopMode()
                    label == getString(R.string.menu_text_size, scalePercent) -> cycleTextSize()
                    label == getString(R.string.menu_default_browser) -> openDefaultBrowserSettings()
                    label == getString(R.string.menu_reader_view) -> {
                        currentUrl?.let { session.loadUri("about:reader?url=$it") }
                    }
                    label == getString(R.string.menu_exit) -> finish()
                }
            }
            .setOnDismissListener { restoreFocus() }
            .show()
    }

    /** Best-effort: takes the user to the system screen for choosing a default browser. */
    private fun openDefaultBrowserSettings() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.isSuccess
        if (!opened) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
        }
    }

    private fun setHomeToCurrentPage() {
        val url = currentUrl
        if (url.isNullOrBlank() || url.startsWith("about:")) {
            toast(getString(R.string.nothing_to_bookmark))
            return
        }
        prefs.homeUrl = url
        toast(getString(R.string.home_set))
    }

    private fun toggleDesktopMode() {
        val desktop = !prefs.desktopMode
        prefs.desktopMode = desktop
        session.settings.setUserAgentMode(
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        )
        session.settings.setViewportMode(
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        )
        session.reload()
        toast(getString(if (desktop) R.string.desktop_on_toast else R.string.desktop_off_toast))
    }

    private fun cycleTextSize() {
        val scales = Prefs.TEXT_SCALES
        val currentIndex = scales.indexOfFirst { it >= prefs.textScale - 0.01f }.coerceAtLeast(0)
        val next = scales[(currentIndex + 1) % scales.size]
        prefs.textScale = next
        runtime.settings.setFontSizeFactor(next)
        toast(getString(R.string.text_size_toast, (next * 100).roundToInt()))
    }

    // ------------------------------------------------------------------
    // Fullscreen video
    // ------------------------------------------------------------------

    private fun setFullScreen(on: Boolean) {
        isFullScreen = on
        if (on) binding.geckoView.requestFocus()
        binding.rail.visibility = if (on) View.GONE else View.VISIBLE
        binding.topBar.visibility = if (on || startVisible) View.GONE else View.VISIBLE

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ------------------------------------------------------------------
    // Remote control / keyboard handling
    // ------------------------------------------------------------------
    //
    // GeckoView consumes D-pad/Back before onKeyDown, so everything is
    // handled here in dispatchKeyEvent(), before any view sees the key.

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleKey(event) || super.dispatchKeyEvent(event)

    /** True while the remote is on the sidebar, the address bar or the start screen. */
    private fun inChrome(): Boolean =
        binding.topBar.hasFocus() || binding.startPage.hasFocus() || binding.rail.hasFocus()

    /** @return true if the key was ours (both DOWN and UP are swallowed). */
    private fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        val chrome = inChrome()

        // Releasing a direction we were gliding the cursor with.
        if (!down && heldKeys.remove(event.keyCode)) return true

        if (event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_D -> { if (first) toggleBookmark(); return true }
                KeyEvent.KEYCODE_B -> { if (first) showBookmarks(); return true }
                KeyEvent.KEYCODE_L -> { if (first) focusAddressBar(); return true }
                KeyEvent.KEYCODE_R -> { if (first) session.reload(); return true }
            }
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> { if (first) toggleCursorMode(); return true }
            KeyEvent.KEYCODE_BOOKMARK -> { if (first) showBookmarks(); return true }
            KeyEvent.KEYCODE_SEARCH -> { if (first) focusAddressBar(); return true }
            KeyEvent.KEYCODE_F5 -> { if (first) session.reload(); return true }
            KeyEvent.KEYCODE_BACK -> {
                // Android 13+ uses the OnBackPressedCallback registered in onCreate.
                if (Build.VERSION.SDK_INT < 33) {
                    if (first) handleBack()
                    return true
                }
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down && !chrome) scrollPageBy(binding.geckoView.height * 0.85f)
                return !chrome
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down && !chrome) scrollPageBy(-binding.geckoView.height * 0.85f)
                return !chrome
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!cursorMode && !chrome && first) {
                    val direction = when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> "Up"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
                        else -> ""
                    }
                    wishyCorePort?.postMessage(JSONObject().apply {
                        put("type", "navigate")
                        put("direction", direction)
                    })
                    return true
                }
            }
        }

        // Pointer mode: hold a direction to glide, OK clicks.
        if (cursorMode && !chrome) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (first) {
                        // Pushing against the top / left edge hops into the chrome.
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && cursorY <= 2f && pageScrollY <= 0) {
                            focusAddressBar()
                            return true
                        }
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && cursorX <= 2f) {
                            stopCursor()
                            binding.homeButton.requestFocus()
                            return true
                        }
                        startCursorKey(event.keyCode)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (first) synthesizeClickAtCursor()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Back walks outwards one step at a time, and never toggles the pointer:
     * fullscreen -> chrome back to page -> page history -> exit.
     */
    private fun handleBack() {
        when {
            isFullScreen -> session.exitFullScreen()
            startVisible -> {
                if (exitArmed) finish() else {
                    exitArmed = true
                    toast(getString(R.string.press_back_again))
                }
            }
            inChrome() -> {
                if (exitArmed) finish() else hideKeyboardAndFocusPage()
            }
            canGoBack -> session.goBack()
            else -> {
                exitArmed = true
                focusAddressBar()
                toast(getString(R.string.press_back_again))
            }
        }
    }

    // ------------------------------------------------------------------
    // Pointer mode
    // ------------------------------------------------------------------

    private fun toggleCursorMode() {
        cursorMode = !cursorMode
        stopCursor()
        updatePointerTint()
        if (cursorMode) {
            if (!startVisible) binding.geckoView.requestFocus()
            toast(getString(R.string.pointer_on))
        } else {
            toast(getString(R.string.pointer_off))
        }
        updateCursorVisibility()
    }

    /** The pointer only shows while it's on AND the page (not the chrome) has focus. */
    private fun updateCursorVisibility() {
        val show = cursorMode && !startVisible && !inChrome()
        if (show == cursorShown) return
        cursorShown = show
        val v = binding.dpadCursor
        if (show) {
            positionCursorOverlay()
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start()
        } else {
            stopCursor()
            v.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(160L)
                .withEndAction { if (!cursorShown) v.visibility = View.GONE }.start()
        }
    }

    private fun startCursorKey(keyCode: Int) {
        val d = resources.displayMetrics.density
        if (heldKeys.isEmpty()) {
            holdStartMs = SystemClock.uptimeMillis()
            lastTickMs = holdStartMs
        }
        heldKeys.add(keyCode)
        // Immediate nudge so a quick tap still moves a little.
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursorBy(0f, -14f * d)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursorBy(0f, 14f * d)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursorBy(-14f * d, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursorBy(14f * d, 0f)
        }
        binding.root.removeCallbacks(cursorTicker)
        binding.root.postOnAnimation(cursorTicker)
    }

    private fun stopCursor() {
        heldKeys.clear()
        binding.root.removeCallbacks(cursorTicker)
    }

    /** Where the arrow's tip is, in the root layout's coordinate space. */
    private fun cursorTipInRoot(): Pair<Float, Float> {
        val g = IntArray(2)
        val r = IntArray(2)
        binding.geckoView.getLocationInWindow(g)
        binding.root.getLocationInWindow(r)
        return Pair(cursorX + (g[0] - r[0]), cursorY + (g[1] - r[1]))
    }

    private fun positionCursorOverlay() {
        val d = resources.displayMetrics.density
        val (tx, ty) = cursorTipInRoot()
        binding.dpadCursor.x = tx - CURSOR_TIP_X_DP * d
        binding.dpadCursor.y = ty - CURSOR_TIP_Y_DP * d
    }

    /**
     * Moves the pointer by (dx, dy) pixels. Near the bottom edge (or the top
     * edge when the page is scrolled) the *page scrolls* instead, so the
     * pointer glides into scrolling without any mode switch.
     */
    private fun moveCursorBy(dx: Float, dy: Float) {
        val d = resources.displayMetrics.density
        val w = binding.geckoView.width.toFloat()
        val h = binding.geckoView.height.toFloat()
        val edge = 64f * d

        if (dy > 0f && cursorY >= h - edge) {
            scrollPageBy(dy * 1.6f)
        } else if (dy < 0f && cursorY <= edge && pageScrollY > 0) {
            scrollPageBy(dy * 1.6f)
        } else {
            cursorY = (cursorY + dy).coerceIn(0f, h)
        }
        cursorX = (cursorX + dx).coerceIn(0f, w)
        positionCursorOverlay()

        // Check for magnetic snap
        wishyCorePort?.postMessage(JSONObject().apply {
            put("type", "find_closest")
            put("x", cursorX)
            put("y", cursorY)
        })
    }

    private fun scrollPageBy(pixels: Float) {
        session.panZoomController.scrollBy(
            ScreenLength.fromPixels(0.0),
            ScreenLength.fromPixels(pixels.toDouble())
        )
    }

    private fun synthesizeClickAtCursor() {
        pulseCursor()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0
        )
        val up = MotionEvent.obtain(
            downTime, downTime + 50, MotionEvent.ACTION_UP, cursorX, cursorY, 0
        )
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        binding.geckoView.dispatchTouchEvent(down)
        binding.geckoView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /** Ring ripple at the tip + a little press squish on the arrow. */
    private fun pulseCursor() {
        val d = resources.displayMetrics.density
        val (tx, ty) = cursorTipInRoot()
        val p = binding.cursorPulse
        p.animate().cancel()
        p.x = tx - 24f * d
        p.y = ty - 24f * d
        p.alpha = 0.9f
        p.scaleX = 0.25f
        p.scaleY = 0.25f
        p.animate().alpha(0f).scaleX(1.6f).scaleY(1.6f).setDuration(430L)
            .setInterpolator(DecelerateInterpolator()).start()

        val c = binding.dpadCursor
        c.animate().cancel()
        c.scaleX = 0.78f
        c.scaleY = 0.78f
        c.animate().scaleX(1f).scaleY(1f).setDuration(260L)
            .setInterpolator(OvershootInterpolator(3f)).start()
    }

    override fun onPause() {
        stopCursor()
        super.onPause()
    }

    // ------------------------------------------------------------------

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun readAsset(path: String): String? = try {
        assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }

    private companion object {
        // Where the arrow's tip sits inside the 26dp cursor icon (4,3 of 24 -> scaled to 26).
        const val CURSOR_TIP_X_DP = 5f
        const val CURSOR_TIP_Y_DP = 3.9f
    }
}

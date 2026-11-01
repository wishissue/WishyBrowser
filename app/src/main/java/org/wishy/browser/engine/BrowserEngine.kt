package org.wishy.browser.engine

import android.graphics.Bitmap
import android.view.View

/**
 * Everything the UI needs from a browser engine. The Compose UI and BrowserController only ever
 * talk to this interface, so the engine (today: Android System WebView) can be swapped again.
 */
interface BrowserEngine {
    /** The view that draws the page. Replaced after a renderer crash (see [EngineListener.onViewReplaced]). */
    val view: View

    fun load(input: String)
    fun reload()
    fun stop()
    fun goBack()
    fun goForward()
    fun scrollBy(px: Float)
    fun exitFullScreen()

    /** Decides, per host, whether JavaScript may run. Checked before each top-level load. */
    var scriptPolicy: (host: String?) -> Boolean
    /** Re-applies [scriptPolicy] to the current page's host (call before reload after a change). */
    fun applyScriptPolicy()

    fun setDesktopMode(on: Boolean)
    fun setTextScale(scale: Float)

    /** D-pad navigation between links when the pointer is off. */
    fun navigate(direction: String)
    /** Nearest clickable element to (x, y) in view pixels, or null. Result is in view pixels. */
    fun findClosest(x: Float, y: Float, viewW: Float, done: (Pair<Float, Float>?) -> Unit)
    fun checkReaderable(done: (Boolean) -> Unit)
    fun showReader()

    /** Half-resolution snapshot of the visible page (best effort), or null. */
    fun capture(done: (Bitmap?) -> Unit)

    /** Cookies, DOM storage, cache and form data. */
    fun clearData()
    fun destroy()
}

interface EngineListener {
    fun onUrl(url: String)
    fun onTitle(title: String)
    fun onProgress(percent: Int)
    fun onPageStarted(url: String)
    fun onPageFinished(success: Boolean)
    fun onNavState(canBack: Boolean, canForward: Boolean)
    fun onScroll(y: Int)
    /** Non-null when a page asked for fullscreen (video); null when it ended. */
    fun onFullScreen(view: View?)
    /** Number of requests blocked on the current page so far. */
    fun onBlocked(countOnPage: Int)
    fun onReaderable(yes: Boolean)
    fun onRenderGone(crashed: Boolean)
    /** The engine had to create a new view (after a renderer crash). */
    fun onViewReplaced()
    fun onNotice(kind: Notice, detail: String)

    fun onAlert(message: String?, done: () -> Unit)
    fun onConfirm(message: String?, done: (Boolean) -> Unit)
    fun onPrompt(message: String?, default: String?, done: (String?) -> Unit)
    fun onFileChooser(mimeTypes: Array<String>, multiple: Boolean, done: (Array<android.net.Uri>?) -> Unit)
    /** [resources] are android.webkit.PermissionRequest.RESOURCE_* values. */
    fun onPermission(host: String, resources: Array<String>, respond: (Boolean) -> Unit)
    fun onDownload(req: DownloadRequest)
}

enum class Notice { UPGRADE_FAILED, TRACKING_STRIPPED, CERT_ERROR, EXTERNAL_BLOCKED, POPUP_BLOCKED, LOAD_ERROR }

class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val length: Long,
    val cookie: String?,
    val referer: String?,
)

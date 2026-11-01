package org.wishy.browser.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.CookieManager
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
import android.webkit.WebViewDatabase
import android.webkit.GeolocationPermissions
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android System WebView backend. Security defaults: no file/content access, no mixed content, no
 * JavaScript bridge objects (addJavascriptInterface is never called), no geolocation, third-party
 * cookies off, every SSL error cancels the load, unknown URL schemes are blocked.
 */
class WebViewEngine(
    private val activity: Activity,
    private val l: EngineListener,
    private val blocker: Blocker,
    private var desktop: Boolean,
    private var textScale: Float,
) : BrowserEngine {

    private val main = Handler(Looper.getMainLooper())
    private val blockedCount = AtomicInteger(0)
    private val coreJs: String by lazy { activity.assets.open("wishy-core.js").bufferedReader().use { it.readText() } }
    private val cosmeticJs: String by lazy {
        val css = blocker.cosmeticCss
        if (css.isEmpty()) "" else
            "(function(){if(document.getElementById('__wishy_cos'))return;var s=document.createElement('style');" +
                "s.id='__wishy_cos';s.textContent=" + JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s)})();"
    }

    private lateinit var web: WebView
    override val view: View get() = web

    private var pageHost: String? = null
    override var scriptPolicy: (String?) -> Boolean = { true }
    private var upgradedFrom: String? = null
    private var upgradedTo: String? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var pageFailed = false
    private var lastUrl = ""

    // ------------------------------------------------------------------ setup

    private fun userAgent(): String {
        var ua = WebSettings.getDefaultUserAgent(activity)
        ua = ua.replace("; wv", "").replace(" Version/4.0", "")
        if (desktop) ua = ua.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        return ua
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun create(): WebView {
        val w = WebView(activity)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            saveFormData = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = desktop
            textZoom = (textScale * 100f).toInt()
            userAgentString = userAgent()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, false)
        }
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        w.webViewClient = client
        w.webChromeClient = chrome
        w.setDownloadListener { url, ua, cd, mime, len ->
            l.onDownload(DownloadRequest(url, ua, cd, mime, len, CookieManager.getInstance().getCookie(url), w.url))
        }
        w.setOnScrollChangeListener { _, _, y, _, _ -> l.onScroll(y) }
        return w
    }

    // ------------------------------------------------------------------ navigation

    private val gpcHeaders = mapOf("Sec-GPC" to "1")

    /** Applies tracking-parameter stripping and the HTTPS upgrade, then loads. */
    private fun loadSafe(raw: String) {
        if (raw.startsWith("about:")) { web.loadUrl("about:blank"); return }
        var u = Uri.parse(raw)
        val stripped = UrlPolicy.stripTracking(u)
        if (stripped !== u) { u = stripped; l.onNotice(Notice.TRACKING_STRIPPED, u.host ?: "") }
        upgradedFrom = null; upgradedTo = null
        if (UrlPolicy.canUpgrade(u)) {
            upgradedFrom = u.toString()
            u = UrlPolicy.upgrade(u)
            upgradedTo = u.toString()
        }
        pageFailed = false
        web.settings.javaScriptEnabled = scriptPolicy(u.host)
        web.loadUrl(u.toString(), gpcHeaders)
    }

    override fun applyScriptPolicy() { web.settings.javaScriptEnabled = scriptPolicy(pageHost) }

    override fun load(input: String) = loadSafe(input)
    override fun reload() { pageFailed = false; web.reload() }
    override fun stop() = web.stopLoading()
    override fun goBack() { if (web.canGoBack()) web.goBack() }
    override fun goForward() { if (web.canGoForward()) web.goForward() }
    override fun scrollBy(px: Float) { web.scrollBy(0, px.toInt()) }
    override fun exitFullScreen() { customCallback?.onCustomViewHidden(); customCallback = null; l.onFullScreen(null) }

    override fun setDesktopMode(on: Boolean) {
        desktop = on
        web.settings.userAgentString = userAgent()
        web.settings.loadWithOverviewMode = on
        web.reload()
    }

    override fun setTextScale(scale: Float) {
        textScale = scale
        web.settings.textZoom = (scale * 100f).toInt()
    }

    private fun navState() = l.onNavState(web.canGoBack(), web.canGoForward())

    // ------------------------------------------------------------------ scripts

    private fun inject() {
        web.evaluateJavascript(coreJs, null)
        if (cosmeticJs.isNotEmpty()) web.evaluateJavascript(cosmeticJs, null)
    }

    override fun navigate(direction: String) {
        web.evaluateJavascript("window.__wishy?__wishy.nav('$direction'):false", null)
    }

    override fun findClosest(x: Float, y: Float, viewW: Float, done: (Pair<Float, Float>?) -> Unit) {
        web.evaluateJavascript("window.__wishy?__wishy.closest($x,$y,$viewW):null") { r ->
            val res = runCatching {
                if (r != null && r.startsWith("{")) JSONObject(r).let { it.getDouble("x").toFloat() to it.getDouble("y").toFloat() } else null
            }.getOrNull()
            done(res)
        }
    }

    override fun checkReaderable(done: (Boolean) -> Unit) {
        web.evaluateJavascript("window.__wishy?__wishy.readerable():false") { done(it == "true") }
    }

    override fun showReader() { web.evaluateJavascript("window.__wishy?__wishy.reader():false", null) }

    override fun capture(done: (Bitmap?) -> Unit) {
        val w = web.width; val h = web.height
        if (w <= 0 || h <= 0) { done(null); return }
        val bmp = runCatching { Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.RGB_565) }.getOrNull()
        if (bmp == null) { done(null); return }
        runCatching { val c = Canvas(bmp); c.scale(0.5f, 0.5f); web.draw(c) }.onFailure { bmp.recycle(); done(null); return }
        done(bmp)
    }

    // ------------------------------------------------------------------ data

    override fun clearData() = wipe(activity, web)

    override fun destroy() {
        main.removeCallbacksAndMessages(null)
        customCallback?.onCustomViewHidden()
        wipe(activity, web)
        web.stopLoading()
        web.webChromeClient = null
        web.destroy()
    }

    // ------------------------------------------------------------------ clients

    private fun errorPage(title: String, detail: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><body style=" +
            "'margin:0;background:#111;color:#eee;font:28px sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center'>" +
            "<div style='max-width:760px;padding:32px'><h1 style='margin:0 0 16px'>${esc(title)}</h1><p style='color:#bbb'>${esc(detail)}</p></div>"
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
            val u = req.url
            val scheme = u.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                if (!req.isForMainFrame) return false
                val clean = UrlPolicy.stripTracking(u)
                if (clean !== u || UrlPolicy.canUpgrade(u)) { loadSafe(u.toString()); return true }
                v.settings.javaScriptEnabled = scriptPolicy(u.host)
                return false
            }
            if (scheme == "about" || scheme == "blob") return false
            l.onNotice(Notice.EXTERNAL_BLOCKED, scheme ?: "")
            return true
        }

        override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
            if (req.isForMainFrame) return null
            val host = req.url.host ?: return null
            val scheme = req.url.scheme
            if (scheme != "http" && scheme != "https") return null
            if (!UrlPolicy.isThirdParty(pageHost, host)) return null
            if (!blocker.blocks(host)) return null
            val n = blockedCount.incrementAndGet()
            main.post { l.onBlocked(n) }
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
            val u = url ?: return
            blockedCount.set(0)
            pageHost = Uri.parse(u).host
            lastUrl = u
            // Back/forward skip the checks above: if this page's policy differs from the current setting, fix it and reload once.
            val want = if (u.startsWith("http")) scriptPolicy(pageHost) else true
            if (want != v.settings.javaScriptEnabled) { v.settings.javaScriptEnabled = want; v.reload(); return }
            l.onBlocked(0)
            l.onPageStarted(u)
            l.onUrl(u)
            navState()
        }

        override fun onPageCommitVisible(v: WebView, url: String?) { inject() }

        override fun onPageFinished(v: WebView, url: String?) {
            inject()
            l.onPageFinished(!pageFailed)
            navState()
            v.evaluateJavascript("window.__wishy?__wishy.readerable():false") { l.onReaderable(it == "true") }
        }

        override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
            if (url != null) { lastUrl = url; l.onUrl(url) }
            navState()
        }

        override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
            if (!req.isForMainFrame) return
            val failed = req.url.toString()
            // The HTTPS upgrade failed: fall back to the address the user asked for, and say so.
            if (failed == upgradedTo && upgradedFrom != null) {
                val back = upgradedFrom!!
                upgradedFrom = null; upgradedTo = null
                l.onNotice(Notice.UPGRADE_FAILED, Uri.parse(back).host ?: "")
                web.loadUrl(back, gpcHeaders)
                return
            }
            pageFailed = true
            l.onNotice(Notice.LOAD_ERROR, err.description?.toString() ?: "")
            web.loadDataWithBaseURL(failed, errorPage("Can't open this page", "${err.description}\n$failed"), "text/html", "utf-8", failed)
        }

        // Never proceed: a certificate error always ends the load.
        override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            pageFailed = true
            val host = Uri.parse(error.url ?: "").host ?: ""
            l.onNotice(Notice.CERT_ERROR, host)
            web.loadDataWithBaseURL(error.url, errorPage("Connection not private", "The certificate for $host is not valid. Wishy will not load this page."), "text/html", "utf-8", error.url)
        }

        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            val crashed = detail.didCrash()
            val url = lastUrl
            runCatching { (v.parent as? android.view.ViewGroup)?.removeView(v); v.destroy() }
            web = create()
            l.onViewReplaced()
            l.onRenderGone(crashed)
            if (url.startsWith("http")) loadSafe(url)
            return true
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) = l.onProgress(p)
        override fun onReceivedTitle(v: WebView, t: String?) = l.onTitle(t ?: "")

        override fun onShowCustomView(v: View, cb: CustomViewCallback) {
            customCallback?.onCustomViewHidden()
            customCallback = cb
            l.onFullScreen(v)
        }
        override fun onHideCustomView() { customCallback = null; l.onFullScreen(null) }

        override fun onJsAlert(v: WebView, url: String?, message: String?, r: JsResult): Boolean {
            l.onAlert(message) { r.confirm() }; return true
        }
        override fun onJsConfirm(v: WebView, url: String?, message: String?, r: JsResult): Boolean {
            l.onConfirm(message) { ok -> if (ok) r.confirm() else r.cancel() }; return true
        }
        override fun onJsPrompt(v: WebView, url: String?, message: String?, def: String?, r: JsPromptResult): Boolean {
            l.onPrompt(message, def) { text -> if (text != null) r.confirm(text) else r.cancel() }; return true
        }
        override fun onJsBeforeUnload(v: WebView, url: String?, message: String?, r: JsResult): Boolean { r.confirm(); return true }

        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
            val types = p.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray() ?: emptyArray()
            l.onFileChooser(types, p.mode == FileChooserParams.MODE_OPEN_MULTIPLE) { uris -> cb.onReceiveValue(uris) }
            return true
        }

        override fun onPermissionRequest(req: PermissionRequest) {
            val host = req.origin?.host ?: ""
            l.onPermission(host, req.resources) { ok -> if (ok) req.grant(req.resources) else req.deny() }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, cb: GeolocationPermissions.Callback) { cb.invoke(origin, false, false) }

        // window.open / target=_blank: only with a user gesture, and it opens in this single tab.
        override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) { l.onNotice(Notice.POPUP_BLOCKED, pageHost ?: ""); return false }
            val tmp = WebView(activity)
            tmp.webViewClient = object : WebViewClient() {
                private var done = false
                private fun take(url: String?) {
                    if (done) return
                    done = true
                    if (!url.isNullOrEmpty() && url != "about:blank") main.post { loadSafe(url) }
                    main.post { tmp.stopLoading(); tmp.destroy() }
                }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { take(r.url.toString()); return true }
                override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) { take(url) }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = tmp
            resultMsg.sendToTarget()
            return true
        }
    }

    init { web = create() }

    companion object {
        /** Wipes everything WebView keeps on disk. Called when the app starts, when it closes, and from "Clear data". */
        fun wipe(activity: Activity, web: WebView?) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(activity).clearHttpAuthUsernamePassword()
                web?.clearCache(true)
                web?.clearFormData()
                web?.clearHistory()
            }
        }
    }
}

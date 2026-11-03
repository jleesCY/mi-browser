// Port of legacy/src/components/BrowserWebView.tsx + legacy/src/navigationUtils.ts, plus the
// react-native-webview 13.15 Android defaults the app relied on (downloads, uploads, permissions, fullscreen).
package com.jlees.mibrowser.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.graphics.asImageBitmap
import com.jlees.mibrowser.ui.common.cacheImage
import android.view.ContextThemeWrapper
import com.jlees.mibrowser.data.Blocklist
import java.io.ByteArrayInputStream
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintManager
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ContextMenuData
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.isIpOrLocalhost
import java.io.File
import java.net.URL

data class WebError(val code: Int, val desc: String, val failingUrl: String?)

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
private val MOBILE_UA = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

private const val READER_JS = """(function() {
  var style = document.createElement('style');
  style.type = 'text/css';
  style.appendChild(document.createTextNode('body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; color: #333; background: #fff; } img { max-width: 100%; height: auto; } nav, header, footer, .ad, .advertisement, .sidebar { display: none !important; }'));
  document.head.appendChild(style);
})();"""

/** Live WebViews kept in memory; older tabs are rebuilt from saved state when opened. */
const val MAX_LIVE_TABS = 5

class WebViewHost(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit,
) {
    private val views = HashMap<String, WebView>()
    private val loaded = HashMap<String, Pair<String?, Int>>()
    /** Live WebViews are capped (each is a full page renderer): least recently viewed tabs are evicted, keeping their
     *  back/forward state here so they come back where they were when opened again. */
    private val lastUsed = HashMap<String, Long>()
    private val saved = HashMap<String, Bundle>()
    val liveCount get() = views.size
    /** Bumped when all WebViews are rebuilt, so the visible AndroidView re-attaches a fresh one. */
    var generation by mutableStateOf(0)

    /** "host|resource" pairs the user allowed this session (camera, microphone, location). */
    private val sitePermissions = HashSet<String>()

    /** Error overlay per tab (renderError). */
    val errors = mutableStateMapOf<String, WebError>()
    /** Fullscreen video view from onShowCustomView. */
    var customView by mutableStateOf<View?>(null)
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val density get() = activity.resources.displayMetrics.density

    fun has(id: String) = views.containsKey(id)

    /** Get or create the tab's WebView, apply settings and perform any requested load. */
    fun sync(tab: TabItem): WebView {
        val fresh = !views.containsKey(tab.id)
        val wv = views.getOrPut(tab.id) { create(tab.id) }
        if (tab.id == vm.activeTabId) lastUsed[tab.id] = System.nanoTime()
        if (fresh) {
            // a new WebView for an existing tab (evicted, theme change, activity recreated): restore or reopen its page
            val restored = saved.remove(tab.id)?.let { wv.restoreState(it) != null } == true
            if (restored || tab.url != null) {
                loaded[tab.id] = tab.requestedUrl to tab.loadNonce
                if (!restored) wv.loadUrl(tab.url!!)
            }
            trimTo(MAX_LIVE_TABS)
        }
        val s = vm.settings
        wv.settings.javaScriptEnabled = s.jsEnabled
        val ua = if (tab.desktopMode ?: s.desktopMode) DESKTOP_UA else MOBILE_UA
        if (wv.settings.userAgentString != ua) wv.settings.userAgentString = ua
        CookieManager.getInstance().setAcceptCookie(!s.blockCookies)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !s.blockCookies && !s.blockThirdPartyCookies)
        val req = tab.requestedUrl
        if (req != null && loaded[tab.id] != (req to tab.loadNonce)) {
            loaded[tab.id] = req to tab.loadNonce
            errors.remove(tab.id)
            wv.loadUrl(req)
        }
        return wv
    }

    fun destroy(id: String) {
        views.remove(id)?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        loaded.remove(id); errors.remove(id); lastUsed.remove(id); saved.remove(id)
    }

    /** Evicts least recently viewed WebViews (never the active one) until at most [max] are live. */
    fun trimTo(max: Int) {
        while (views.size > max) {
            val id = views.keys.filter { it != vm.activeTabId }.minByOrNull { lastUsed[it] ?: 0L } ?: return
            val state = Bundle().also { views[id]?.saveState(it) }
            destroy(id)
            saved[id] = state
        }
    }

    /** Activity teardown: release every WebView (they hold the Activity). */
    fun destroyAll() = views.keys.toList().forEach { destroy(it) }

    fun handle(cmd: WebCmd) {
        when (cmd) {
            is WebCmd.Reload -> { errors.remove(cmd.tabId); views[cmd.tabId]?.reload() }
            is WebCmd.Stop -> views[cmd.tabId]?.stopLoading()
            is WebCmd.GoBack -> views[cmd.tabId]?.goBack()
            is WebCmd.GoForward -> views[cmd.tabId]?.goForward()
            WebCmd.Print -> print()
            is WebCmd.CapturePreview -> capturePreview(cmd.tabId)
            is WebCmd.Destroy -> destroy(cmd.tabId)
            WebCmd.WipeWebData -> {
                clearBrowsingData()
                views.keys.toList().forEach { destroy(it) }
            }
            WebCmd.ClearBrowsingData -> clearBrowsingData()
            WebCmd.RecreateAll -> { views.keys.toList().forEach { destroy(it) }; generation++ }
        }
    }

    /** Cookies, site storage (localStorage, IndexedDB…), HTTP cache and saved form/auth data. */
    private fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        views.values.forEach { it.clearCache(true); it.clearFormData() }
        @Suppress("DEPRECATION") WebViewDatabase.getInstance(activity).apply { clearHttpAuthUsernamePassword(); clearFormData() }
    }

    fun findNext(forward: Boolean) { views[vm.activeTabId]?.findNext(forward) }

    /** New search text: highlight all matches and jump to the first (findAllAsync); empty clears. */
    fun findAll(query: String) {
        val wv = views[vm.activeTabId] ?: return
        if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
    }

    fun exitFullscreen() { customViewCallback?.onCustomViewHidden() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(id: String): WebView {
        // Pages see prefers-color-scheme from the creating context's theme, so match the app's light/dark setting.
        val wv = WebView(ContextThemeWrapper(activity, if (isDark()) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar))
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.WHITE)
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        with(wv.settings) {
            domStorageEnabled = true
            @Suppress("DEPRECATION") databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION") saveFormData = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            safeBrowsingEnabled = true
        }
        wv.webViewClient = Client(id)
        wv.webChromeClient = Chrome(id)
        wv.setOnScrollChangeListener { _, _, y, _, _ -> if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.Scroll(y / density)) }
        wv.setOnLongClickListener { onLongPress(wv) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, length -> download(url, userAgent, contentDisposition, mimetype, length) }
        return wv
    }

    private fun isDark() = when (vm.settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val emptyResponse get() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun nav(id: String, view: WebView, url: String?, loading: Boolean) =
        vm.onWebNav(id, url, view.title, view.canGoBack(), view.canGoForward(), loading)

    private inner class Client(val id: String) : WebViewClient() {
        /** Ad/tracker blocking: drop sub-resource requests to blocklisted hosts (runs on a WebView thread). */
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            if (vm.settings.blockTrackers && !request.isForMainFrame && Blocklist.isBlocked(request.url.host)) emptyResponse else null

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val gesture = request.hasGesture()
            if (url.startsWith("blob:") || url.startsWith("data:")) return false
            if (url.startsWith("mibrowser:")) { if (gesture) vm.handleIncomingUrl(url); return true }
            if (vm.settings.httpsOnly && url.startsWith("http://") && request.isForMainFrame && !isIpOrLocalhost(displayHost(url))) {
                vm.loadInTab(id, url.replaceFirst("http://", "https://"))
                return true
            }
            if (url.startsWith("googleapp://")) return true
            val standard = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")
            if (standard) {
                if (url.contains("intent://") || url.contains("#Intent;")) { openExternal(url, gesture); return true }
                return false
            }
            openExternal(url, gesture)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            errors.remove(id)
            nav(id, view, url, true)
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadStart)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val t = vm.tabs.firstOrNull { it.id == id }
            if (t != null && (t.readerMode ?: vm.settings.readerModeEnabled)) view.evaluateJavascript(READER_JS, null)
            nav(id, view, url, false)
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadEnd)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            nav(id, view, url, vm.tabs.firstOrNull { it.id == id }?.loading ?: false)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            errors[id] = WebError(error.errorCode, error.description?.toString() ?: "Unknown error", request.url.toString())
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadEnd)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = displayHost(error.url)
            if (host in vm.ignoredHosts) { handler.proceed(); return }
            handler.cancel()
            if (view.url.equals(error.url, ignoreCase = true) || view.url == null) {
                errors[id] = WebError(-1200, "ERR_SSL_PROTOCOL_ERROR", error.url)
                if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadEnd)
            }
        }
    }

    private inner class Chrome(val id: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadProgress(newProgress / 100f))
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            nav(id, view, view.url, vm.tabs.firstOrNull { it.id == id }?.loading ?: false)
        }

        /** target=_blank / window.open → new mi tab. */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false // pop-up blocking: only windows opened by a tap/click
            val temp = WebView(activity)
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val url = r.url.toString()
                    if (url.startsWith("http://") || url.startsWith("https://")) vm.addNewTab(url) else openExternal(url, gesture = true)
                    v.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = temp
            resultMsg.sendToTarget()
            // a pop-up that never navigates (about:blank + script) would otherwise keep this WebView forever
            temp.postDelayed({ runCatching { temp.destroy() } }, 10_000)
            return true
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback?.onCustomViewHidden()
            customViewCallback = callback
            customView = view
        }

        override fun onHideCustomView() {
            customView = null
            customViewCallback = null
        }

        /** Camera/mic: ask the user per site (remembered for this session), then the Android runtime permission. */
        override fun onPermissionRequest(request: PermissionRequest) {
            val host = request.origin?.host ?: run { request.deny(); return }
            val wanted = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
            if (wanted.isEmpty()) { request.deny(); return }
            if (wanted.all { "$host|$it" in sitePermissions }) { grantMedia(request); return }
            val what = wanted.joinToString(" and ") { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }
            vm.prompt(
                "Allow $what?", "$host wants to use your $what.",
                listOf(
                    AlertButton("Block", "cancel") { runCatching { request.deny() } },
                    AlertButton("Allow") { wanted.forEach { sitePermissions += "$host|$it" }; grantMedia(request) },
                ),
                onCancel = { runCatching { request.deny() } },
            )
        }

        private fun grantMedia(request: PermissionRequest) {
            val map = mapOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO, PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA)
            val granted = mutableListOf<String>()
            val needed = mutableListOf<String>()
            for (r in request.resources) {
                val perm = map[r] ?: continue
                if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) granted += r else needed += perm
            }
            val grant = { g: List<String> -> runCatching { if (g.isEmpty()) request.deny() else request.grant(g.distinct().toTypedArray()) } }
            if (needed.isEmpty()) { grant(granted); return }
            requestPermissions(needed.toTypedArray()) { result ->
                for ((res, perm) in map) if (res in request.resources && result[perm] == true) granted += res
                grant(granted)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) { vm.alert = null }

        /** Location: ask the user per site (remembered for this session), then the Android runtime permission. */
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val host = Uri.parse(origin).host ?: origin
            val grantLocation = {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) callback.invoke(origin, true, false)
                else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { r -> callback.invoke(origin, r.values.any { it }, false) }
            }
            if ("$host|location" in sitePermissions) { grantLocation(); return }
            vm.prompt(
                "Allow location?", "$host wants to know your location.",
                listOf(
                    AlertButton("Block", "cancel") { callback.invoke(origin, false, false) },
                    AlertButton("Allow") { sitePermissions += "$host|location"; grantLocation() },
                ),
                onCancel = { callback.invoke(origin, false, false) },
            )
        }

        override fun onGeolocationPermissionsHidePrompt() { vm.alert = null }

        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            val content = params.createIntent().apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            val accept = params.acceptTypes.joinToString(",")
            val wantsImage = accept.isBlank() || accept.contains("image")
            var captured: Uri? = null
            val extras = mutableListOf<Intent>()
            if (wantsImage && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val file = File(activity.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                captured = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                extras += Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, captured)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(content, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
            try {
                launchForResult(chooser) { res ->
                    val uris = FileChooserParams.parseResult(res.resultCode, res.data)
                        ?: res.data?.clipData?.let { cd -> Array(cd.itemCount) { cd.getItemAt(it).uri } }
                        ?: if (res.resultCode == Activity.RESULT_OK && captured != null) arrayOf(captured) else null
                    callback.onReceiveValue(uris)
                }
            } catch (_: ActivityNotFoundException) {
                callback.onReceiveValue(null)
                return false
            }
            return true
        }
    }

    // ---------------------------------------------------------------- context menu (native hit test)
    private fun onLongPress(wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val type = hit.type
        if (type != WebView.HitTestResult.SRC_ANCHOR_TYPE && type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE && type != WebView.HitTestResult.IMAGE_TYPE) return false
        val msg = Handler(Looper.getMainLooper()) { m ->
            val d = m.data
            val link = when (type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> hit.extra
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> d.getString("url")
                else -> null
            }
            val img = if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) null else (d.getString("src") ?: hit.extra)
            val text = if (link != null) (d.getString("title") ?: "").trim().take(50) else "Image"
            vm.contextMenu = ContextMenuData(link, img, text)
            true
        }.obtainMessage()
        wv.requestFocusNodeHref(msg)
        return true
    }

    // ---------------------------------------------------------------- external links (navigationUtils.ts)
    /**
     * Links to other apps (tel:, mailto:, market:, intent://…). Pages can only trigger this from a user tap, and the
     * user confirms before another app opens. Without a tap, an intent:// link may still fall back to its web URL.
     */
    fun openExternal(url: String, gesture: Boolean) {
        val isIntent = url.startsWith("intent:") || url.contains("#Intent;")
        if (!gesture) {
            if (isIntent) fallbackUrl(url)?.let { vm.loadInTab(vm.activeTabId, it) }
            return
        }
        val target = if (isIntent) Regex("package=([^;]+)").find(url)?.groupValues?.get(1) ?: "another app"
        else Uri.parse(url).scheme?.let { "a $it: link" } ?: "another app"
        vm.prompt(
            "Open in another app?", "This page wants to open $target.",
            listOf(
                AlertButton("Cancel", "cancel"),
                AlertButton("Open") {
                    try {
                        if (isIntent) handleIntent(url)
                        else activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e: Exception) {
                        vm.showAlert("Link Error", "Could not open this link.\n\nError: ${e.message ?: "Unknown error"}\n\nURL: $url")
                    }
                },
            ),
            onCancel = {},
        )
    }

    private fun fallbackUrl(intentUrl: String) = Regex("browser_fallback_url=([^;]+)").find(intentUrl)
        ?.let { Uri.decode(it.groupValues[1]) }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun handleIntent(intentUrl: String) {
        // Attempt 1/2: launch the intent (component/selector stripped for safety)
        try {
            val intent = Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null
            }
            activity.startActivity(intent)
            return
        } catch (_: Exception) {}
        // Attempt 3: browser_fallback_url
        Regex("browser_fallback_url=([^;]+)").find(intentUrl)?.let {
            val fallback = Uri.decode(it.groupValues[1])
            if (!fallback.startsWith("http://") && !fallback.startsWith("https://")) {
                vm.showAlert("Invalid Fallback URL", "The fallback URL provided is not a valid web address."); return
            }
            vm.loadInTab(vm.activeTabId, fallback)
            vm.inputUrl = displayHost(fallback)
            return
        }
        // Attempt 4: package → store
        Regex("package=([^;]+)").find(intentUrl)?.let {
            val pkg = it.groupValues[1]
            vm.showAlert("App Required", "This feature requires an external app ($pkg). Would you like to view it in the store?", listOf(
                AlertButton("Cancel", "cancel"),
                AlertButton("View Store") {
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
                },
            ))
            return
        }
        vm.showAlert("Action Failed", "Could not handle this action and no fallback was provided by the website.")
    }

    // ---------------------------------------------------------------- error page actions
    fun retry(id: String) { errors.remove(id); views[id]?.reload() }

    fun proceedUnsafe(id: String) {
        val target = errors[id]?.failingUrl ?: vm.tabs.firstOrNull { it.id == id }?.url
        val host = displayHost(target)
        if (host.isNotEmpty()) vm.trustHost(host)
        errors.remove(id)
        views[id]?.reload()
    }

    // ---------------------------------------------------------------- downloads (RNWV DownloadListener)
    private fun download(url: String, userAgent: String, contentDisposition: String?, mimetype: String?, length: Long) {
        val req = try { DownloadManager.Request(Uri.parse(url)) } catch (_: IllegalArgumentException) { return }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype).replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        try {
            val u = URL(url)
            CookieManager.getInstance().getCookie("${u.protocol}://${u.host}")?.let { req.addRequestHeader("Cookie", it) }
        } catch (_: Exception) {}
        req.addRequestHeader("User-Agent", userAgent)
        req.setTitle(fileName)
        req.setDescription("Downloading $fileName")
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val go = {
            try {
                (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(activity, "Downloading $fileName", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
        val start = {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) go()
            else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if (it.values.all { g -> g }) go() }
        }
        val size = if (length > 0) " (" + android.text.format.Formatter.formatShortFileSize(activity, length) + ")" else ""
        vm.prompt(
            "Download file?", "$fileName$size\nfrom ${displayHost(url)}",
            listOf(AlertButton("Cancel", "cancel"), AlertButton("Download") { start() }),
            onCancel = {},
        )
    }

    // ---------------------------------------------------------------- print / previews
    private fun print() {
        val wv = views[vm.activeTabId] ?: return
        val title = wv.title ?: "mi"
        (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, wv.createPrintDocumentAdapter(title), null)
    }

    fun capturePreview(id: String) {
        val wv = views[id]
        val tab = vm.tabs.firstOrNull { it.id == id }
        if (wv != null && tab != null && wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow) {
            try {
                val bmp = Bitmap.createBitmap(wv.width / 2, wv.height / 2, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply { scale(0.5f, 0.5f); translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat()) }.also { wv.draw(it) }
                // shown from memory right away; the file (for after a restart) is written off the main thread
                val file = File(activity.cacheDir, "preview_${id}_${System.currentTimeMillis()}.jpg")
                cacheImage(file.path, bmp.asImageBitmap())
                val old = tab.previewImage
                vm.updateTab(id) { copy(previewImage = file.path) }
                Thread {
                    runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                    old?.let { File(it).delete() }
                }.start()
            } catch (_: Exception) {}
        }
        if (vm.tabs.none { it.url != null }) {
            activity.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
        }
    }
}

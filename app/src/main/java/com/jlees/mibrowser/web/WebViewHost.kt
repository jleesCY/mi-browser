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
import android.graphics.Bitmap
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

class WebViewHost(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit,
) {
    private val views = HashMap<String, WebView>()
    private val loaded = HashMap<String, Pair<String?, Int>>()
    private val pendingAllowedHost = HashMap<String, String>()

    /** Error overlay per tab (renderError). */
    val errors = mutableStateMapOf<String, WebError>()
    /** Fullscreen video view from onShowCustomView. */
    var customView by mutableStateOf<View?>(null)
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val density get() = activity.resources.displayMetrics.density

    fun has(id: String) = views.containsKey(id)

    /** Get or create the tab's WebView, apply settings and perform any requested load. */
    fun sync(tab: TabItem): WebView {
        val wv = views.getOrPut(tab.id) { create(tab.id) }
        val s = vm.settings
        wv.settings.javaScriptEnabled = s.jsEnabled
        val ua = if (tab.desktopMode ?: s.desktopMode) DESKTOP_UA else MOBILE_UA
        if (wv.settings.userAgentString != ua) wv.settings.userAgentString = ua
        CookieManager.getInstance().setAcceptCookie(!s.blockCookies)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !s.blockCookies)
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
        loaded.remove(id); errors.remove(id)
    }

    fun handle(cmd: WebCmd) {
        when (cmd) {
            is WebCmd.Reload -> { errors.remove(cmd.tabId); views[cmd.tabId]?.reload() }
            is WebCmd.Stop -> views[cmd.tabId]?.stopLoading()
            is WebCmd.GoBack -> views[cmd.tabId]?.goBack()
            is WebCmd.GoForward -> views[cmd.tabId]?.goForward()
            is WebCmd.Find -> views[vm.activeTabId]?.findNext(cmd.forward)
            WebCmd.ClearFind -> views[vm.activeTabId]?.clearMatches()
            WebCmd.Print -> print()
            is WebCmd.CapturePreview -> capturePreview(cmd.tabId)
            is WebCmd.Destroy -> destroy(cmd.tabId)
            WebCmd.WipeWebData -> {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                views.keys.toList().forEach { destroy(it) }
            }
        }
    }

    /** New search text: highlight all matches and jump to the first (findAllAsync). */
    fun findAll(query: String) {
        val wv = views[vm.activeTabId] ?: return
        if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
    }

    fun exitFullscreen() { customViewCallback?.onCustomViewHidden() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(id: String): WebView {
        val wv = WebView(activity)
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
        }
        wv.webViewClient = Client(id)
        wv.webChromeClient = Chrome(id)
        wv.setOnScrollChangeListener { _, _, y, _, _ -> if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.Scroll(y / density)) }
        wv.setOnLongClickListener { onLongPress(wv) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ -> download(url, userAgent, contentDisposition, mimetype) }
        return wv
    }

    private fun nav(id: String, view: WebView, url: String?, loading: Boolean) =
        vm.onWebNav(id, url, view.title, view.canGoBack(), view.canGoForward(), loading)

    private inner class Client(val id: String) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("blob:") || url.startsWith("data:")) return false
            if (url.startsWith("mibrowser:")) { vm.handleIncomingUrl(url); return true }
            if (vm.settings.httpsOnly && url.startsWith("http://") && request.isForMainFrame && !isIpOrLocalhost(displayHost(url))) {
                vm.loadInTab(id, url.replaceFirst("http://", "https://"))
                return true
            }
            if (url.startsWith("googleapp://")) return true
            val standard = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")
            if (standard) {
                if (url.contains("intent://") || url.contains("#Intent;")) { openExternal(url); return true }
                return false
            }
            openExternal(url)
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
            if (host in vm.ignoredHosts || pendingAllowedHost[id] == host) { handler.proceed(); return }
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
            val temp = WebView(activity)
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val url = r.url.toString()
                    if (url.startsWith("http://") || url.startsWith("https://")) vm.addNewTab(url) else openExternal(url)
                    v.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = temp
            resultMsg.sendToTarget()
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

        override fun onPermissionRequest(request: PermissionRequest) {
            val map = mapOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO, PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA)
            val granted = mutableListOf<String>()
            val needed = mutableListOf<String>()
            for (r in request.resources) {
                val perm = map[r] ?: continue
                if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) granted += r else needed += perm
            }
            if (needed.isEmpty()) { if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray()); return }
            requestPermissions(needed.toTypedArray()) { result ->
                for ((res, perm) in map) if (res in request.resources && result[perm] == true) granted += res
                if (granted.isEmpty()) request.deny() else request.grant(granted.distinct().toTypedArray())
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false); return
            }
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { r ->
                callback.invoke(origin, r.values.any { it }, false)
            }
        }

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
    fun openExternal(url: String) {
        try {
            if (url.startsWith("intent:") || url.contains("#Intent;")) { handleIntent(url); return }
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            activity.startActivity(i)
        } catch (e: Exception) {
            vm.showAlert("Link Error", "Could not open this link.\n\nError: ${e.message ?: "Unknown error"}\n\nURL: $url")
        }
    }

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
                AlertButton("View Store") { openExternal("market://details?id=$pkg") },
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
        if (host.isNotEmpty()) { pendingAllowedHost[id] = host; vm.trustHost(host) }
        errors.remove(id)
        views[id]?.reload()
    }

    // ---------------------------------------------------------------- downloads (RNWV DownloadListener)
    private fun download(url: String, userAgent: String, contentDisposition: String?, mimetype: String?) {
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
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) go()
        else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if (it.values.all { g -> g }) go() }
    }

    // ---------------------------------------------------------------- print / previews
    private fun print() {
        val wv = views[vm.activeTabId] ?: return
        val title = wv.title ?: "mi"
        (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, wv.createPrintDocumentAdapter(title), null)
    }

    private fun capturePreview(id: String) {
        val wv = views[id]
        val tab = vm.tabs.firstOrNull { it.id == id }
        if (wv != null && tab != null && wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow) {
            try {
                val bmp = Bitmap.createBitmap(wv.width / 2, wv.height / 2, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply { scale(0.5f, 0.5f); translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat()) }.also { wv.draw(it) }
                val file = File(activity.cacheDir, "preview_${id}_${System.currentTimeMillis()}.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 50, it) }
                val old = tab.previewImage
                vm.updateTab(id) { copy(previewImage = file.path) }
                old?.let { File(it).delete() }
            } catch (_: Exception) {}
        }
        if (vm.tabs.none { it.url != null }) {
            activity.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
        }
    }
}

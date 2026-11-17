// A tab's page events: what may load (blocking, HTTPS Only, other apps), progress, errors and certificates.
package com.jlees.mibrowser.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.adblock.stripTracking
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconHost

/** Tab [id]'s WebViewClient; the jobs behind it are [webHost]'s (ContentBlocker, SignInChallenges, HttpsOnly…). */
class TabWebClient(private val webHost: WebViewHost, private val id: String) : WebViewClient() {

    /** Ad, tracker and cookie-banner blocking of sub-resources (runs on a WebView thread). */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = webHost.blocker.intercept(id, request)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val original = request.url.toString()
        // a link carrying tracking parameters (utm_*, fbclid…) is opened without them
        val url = if (request.isForMainFrame && webHost.vm.settings.stripTrackingParams && request.method == "GET") stripTracking(original) else original
        if (request.isForMainFrame) webHost.signIn.navigated(id, url)
        val gesture = request.hasGesture()
        if (url.startsWith("blob:") || url.startsWith("data:")) return false
        if (url.startsWith("mibrowser:")) { if (gesture) webHost.vm.handleIncomingUrl(url, incognito = webHost.isIncognito(id)); return true }
        if (webHost.vm.settings.httpsOnly && url.startsWith("http://") && request.isForMainFrame && !webHost.httpsOnly.letsThrough(id, displayHost(url))) {
            webHost.vm.loadInTab(id, url.replaceFirst("http://", "https://"))
            return true
        }
        if (url.startsWith("googleapp://")) return true
        val standard = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")
        if (standard) {
            if (url.contains("intent://") || url.contains("#Intent;")) { webHost.externalApps.open(url, gesture); return true }
            if (url != original) { view.loadUrl(url); return true }
            return false
        }
        webHost.externalApps.open(url, gesture)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        webHost.blocker.pageStarted(id, url?.let { Uri.parse(it).host })
        // HTTPS Only: the insecure site this tab continued to stays allowed only while the tab stays on it
        webHost.httpsOnly.pageStarted(id, url)
        webHost.errors.remove(id)
        webHost.dialogLimit.reset(id)
        webHost.media.forget(id) // the page that was playing is gone
        webHost.topInset.reset(id); webHost.topInset.apply(id, view)
        webHost.nav(id, view, url, true)
        if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadStart)
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) =
        webHost.signIn.httpAuth(id, webHost.scope(id), view.url, handler, host, realm)
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = webHost.signIn.clientCertificate(request)

    override fun onPageFinished(view: WebView, url: String?) {
        if (id == webHost.vm.tabs.activeId) webHost.vm.find.refresh() // the page searched while loading may have more now
        webHost.topInset.reset(id); webHost.topInset.apply(id, view) // again, now the page's own head is in place
        fetchTouchIcon(view, url)
        webHost.signIn.pageFinished()
        if (webHost.errors[id] == null) webHost.vm.clearHttpFallback(id) // https worked: later failures in this tab are real errors
        val t = webHost.vm.tabs.list.firstOrNull { it.id == id }
        if (t != null && (t.readerMode ?: webHost.vm.settings.readerModeEnabled)) view.evaluateJavascript(webHost.scripts.reader, null)
        webHost.nav(id, view, url, false)
        if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadEnd)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        webHost.nav(id, view, url, webHost.vm.tabs.list.firstOrNull { it.id == id }?.loading ?: false)
    }

    /**
     * The page renderer died (crashed, or Android reclaimed its memory while mi was in the background): the tabs'
     * pages are rebuilt (they share the one renderer) instead of mi going down with it.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { webHost.rendererGone(view, detail.didCrash()); return true }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        // like Chrome's HTTPS upgrades: any failure of the automatic https attempt retries over http, except when
        // the name doesn't resolve (http would fail too) or Safe Browsing blocked the page (never routed around)
        val httpMightWork = error.errorCode != ERROR_HOST_LOOKUP && error.errorCode != ERROR_UNSAFE_RESOURCE
        if (httpMightWork && fallBackToHttp(request.url.toString())) return
        // HTTPS Only: the secure connection failed; warn, with a way to continue over http (like Chrome and Firefox)
        val failed = request.url.toString()
        if (httpMightWork && webHost.vm.settings.httpsOnly && failed.startsWith("https://")) {
            webHost.errors[id] = WebError(WebError.HTTPS_ONLY, "HTTPS_ONLY", failed)
            if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadEnd)
            return
        }
        webHost.errors[id] = WebError(error.errorCode, error.description?.toString() ?: "Unknown error", request.url.toString())
        if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadEnd)
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val host = displayHost(error.url)
        if (webHost.vm.certExceptions.has(webHost.profileOf(id), host)) { handler.proceed(); return }
        handler.cancel()
        val mainFrame = view.url == null || view.url.equals(error.url, ignoreCase = true)
        if (mainFrame && fallBackToHttp(error.url)) return
        if (view.url.equals(error.url, ignoreCase = true) || view.url == null) {
            webHost.errors[id] = WebError(-1200, "ERR_SSL_PROTOCOL_ERROR", error.url)
            if (id == webHost.vm.tabs.activeId) webHost.vm.ui.tryEmit(UiEvent.LoadEnd)
        }
    }

    /**
     * The page's high-resolution icon (apple-touch-icon / sized icon links), read from its own HTML once it has
     * loaded. The site it is stored for comes from WebView ([url]), never from the page's script, and the result is
     * dropped if the tab has navigated away meanwhile, so a page can only ever set its own icon.
     */
    private fun fetchTouchIcon(view: WebView, url: String?) {
        if (webHost.isIncognito(id)) return
        val host = faviconHost(url) ?: return
        if (view.url != url) return
        if (!webHost.vm.settings.jsEnabled) { Favicons.upgrade(host); return }
        view.evaluateJavascript(webHost.scripts.touchIcon) { result ->
            if (view.url != url) return@evaluateJavascript
            val href = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
            if (href != null) Favicons.fetchFor(host, href) else Favicons.upgrade(host)
        }
    }

    /** A typed address (no scheme) failed over https: retry it over http, once, unless HTTPS-Only is on. */
    private fun fallBackToHttp(url: String?): Boolean {
        val t = webHost.vm.tabs.list.firstOrNull { it.id == id } ?: return false
        if (!t.httpFallback || webHost.vm.settings.httpsOnly || url == null || !url.startsWith("https://")) return false
        webHost.vm.loadInTab(id, "http://" + url.removePrefix("https://")) // also clears the fallback flag: only once
        return true
    }
}

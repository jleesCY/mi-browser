// The WebView pool: one WebView per open tab (least recently used ones evicted), their settings, and the WebView
// callbacks. Each job behind a callback is its own class (SitePermissions, Downloads, ContentBlocker…).
package com.jlees.mibrowser.web

import com.jlees.mibrowser.data.clearBrowsingTraces

import android.webkit.CookieManager

import android.webkit.WebStorage

import com.jlees.mibrowser.adblock.AdBlock

import com.jlees.mibrowser.ui.common.Haptics

import com.jlees.mibrowser.downloads.DownloadPrompt

import com.jlees.mibrowser.tabs.TabPreviews

import com.jlees.mibrowser.adblock.ContentBlocker


import com.jlees.mibrowser.incognito.IncognitoProfiles

import com.jlees.mibrowser.data.ThemeMode

import com.jlees.mibrowser.adblock.stripTracking
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ContextMenuData
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconHost
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.uniqueId
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

data class WebError(val code: Int, val desc: String, val failingUrl: String?) {
    companion object {
        /** HTTPS Only couldn't connect securely (mi's own code; WebView's are small negatives). */
        const val HTTPS_ONLY = -3001
    }
}


/** Live WebViews kept in memory: the active tab and three in the background. Older tabs are rebuilt from saved state
 *  when opened (each live WebView holds tens of MB; background ones are paused, so they cost memory, not battery). */
const val MAX_LIVE_TABS = 4

class WebViewHost(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit,
) {
    private val scripts = PageScripts(activity)
    private val topInset by lazy { TopInset(scripts.topInset) { vm.settings.showStatusBar } }
    private val haptics = Haptics(activity.window.decorView) { vm.settings.hapticFeedback }
    // incognito tabs add their own (in-memory) sites with blocking off: never the saved list
    private val blocker: ContentBlocker = ContentBlocker({ vm.settings }) { id -> if (isIncognito(id)) vm.incognitoAdAllowlist else emptySet() }
    private val userAgents = UserAgents(activity)
    private val permissions = SitePermissions(activity, vm, requestPermissions)
    private val popups = PopupPolicy(vm)
    /** Audio and video that keep playing in the background, with the media notification. */
    val media = com.jlees.mibrowser.media.BackgroundMedia(activity.applicationContext, { vm.settings.backgroundAudio }, ::isIncognito,
        { id -> vm.tabs.list.firstOrNull { it.id == id }?.url }) { applyActivity() }
    private val downloads = DownloadPrompt(activity, vm, requestPermissions, launchForResult)
    private val printer = Printer(activity) { id -> destroy(id) }
    private val externalApps = ExternalApps(activity, vm)
    private val signIn = SignInChallenges(activity, vm)
    private val dialogLimit = DialogLimit()
    private val fileChooser = FileChooser(activity, launchForResult)
    private val previews = TabPreviews(activity, vm)
    private val bridge = PageBridge(activity) { id, wv -> printer.print(id, wv) }
    private val views = HashMap<String, WebView>()
    private val loaded = HashMap<String, Pair<String?, Int>>()
    /** Live WebViews are capped (each is a full page renderer): least recently viewed tabs are evicted, keeping their
     *  back/forward state here so they come back where they were when opened again. */
    private val lastUsed = HashMap<String, Long>()
    private val saved = HashMap<String, Bundle>()
    val liveCount get() = views.size
    /** Bumped when all WebViews are rebuilt, so the visible AndroidView re-attaches a fresh one. */
    var generation by mutableStateOf(0)

    /** The error page shown over each tab whose page failed to load. */
    val errors = mutableStateMapOf<String, WebError>()
    /** Fullscreen video view from onShowCustomView. */
    var customView by mutableStateOf<View?>(null)
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val density get() = activity.resources.displayMetrics.density

    // ---- incognito: each incognito tab's storage is its own WebView profile (see IncognitoProfiles)
    val incognito = IncognitoProfiles(
        tabProfiles = { vm.tabs.list.mapNotNull { it.profile } },
        onEnded = { name ->
            // session state kept per profile goes with it
            popups.forget(name)
            signIn.forget(name)
            permissions.forget(name)
            vm.certExceptions.forget(name); vm.httpAllowed.forget(name)
        },
        serviceWorkers = blocker.profileWorkers,
    )
    /** Tab [id]'s profile (null: a normal tab); also for Site settings. */
    fun profileOf(id: String) = vm.tabs.list.firstOrNull { it.id == id }?.profile ?: incognito.profileOfView(id)
    private fun isIncognito(id: String) = profileOf(id) != null
    /** Prefix for session state (permissions, sign-ins, pop-up allowances) so it stays inside one profile. */
    private fun scope(id: String) = profileOf(id).orEmpty()

    /** Per-tab settings that can change (JS, desktop mode, cookies); applied before any load. */
    private fun configure(wv: WebView, desktop: Boolean) {
        val s = vm.settings
        // only what changed: this runs on every recomposition, and re-applying a setting can make the page re-lay out
        if (wv.settings.javaScriptEnabled != s.jsEnabled) wv.settings.javaScriptEnabled = s.jsEnabled
        userAgents.apply(wv.settings, desktop)
        incognito.cookies(wv).setAcceptCookie(!s.blockCookies)
        incognito.cookies(wv).setAcceptThirdPartyCookies(wv, !s.blockCookies && !s.blockThirdPartyCookies)
        if (wv.settings.textZoom != s.textZoom) wv.settings.textZoom = s.textZoom
        // only has an effect while the WebView's theme is dark (it follows the app theme, see create)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && WebSettingsCompat.isAlgorithmicDarkeningAllowed(wv.settings) != s.darkenPages)
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, s.darkenPages)
        if (wv.settings.mediaPlaybackRequiresUserGesture != s.blockAutoplay) wv.settings.mediaPlaybackRequiresUserGesture = s.blockAutoplay
    }

    fun noteUserTouch() = bridge.noteUserTouch()

    // ---- battery: only the visible tab runs freely; everything pauses while mi is in the background
    private var appVisible = true
    private var timersPaused = false
    private val pausedViews = HashSet<String>()

    /**
     * Pauses animations/geolocation in every WebView but the active one (and that one too while mi isn't visible) and
     * the tab playing media. With mi in the background and nothing playing, JS timers stop too (process-wide).
     */
    fun applyActivity() {
        val playing = media.playingTab
        for ((id, wv) in views) {
            val run = com.jlees.mibrowser.media.keepsRunning(id, vm.tabs.activeId, appVisible, playing)
            if (run && id in pausedViews) { wv.onResume(); pausedViews -= id }
            else if (!run && id !in pausedViews) { wv.onPause(); pausedViews += id }
        }
        // instance methods, but they act on every WebView in the process; with none alive, create() catches up
        val timers = appVisible || playing != null
        if (timers == timersPaused) views.values.firstOrNull()?.let { if (timers) it.resumeTimers() else it.pauseTimers(); timersPaused = !timers }
    }

    /** Activity started/stopped. */
    fun setAppVisible(visible: Boolean) {
        if (visible == appVisible) return
        appVisible = visible
        applyActivity()
    }

    fun has(id: String) = views.containsKey(id)

    /** Get or create the tab's WebView, apply settings and perform any requested load. */
    fun sync(tab: TabItem): WebView {
        val fresh = !views.containsKey(tab.id)
        val wv = views.getOrPut(tab.id) { create(tab.id, tab.profile) }
        if (tab.id == vm.tabs.activeId) lastUsed[tab.id] = System.nanoTime()
        if (fresh) {
            configure(wv, tab.desktopMode ?: vm.settings.desktopMode)
            // a new WebView for an existing tab (evicted, theme change, activity recreated): restore or reopen its page
            val restored = saved.remove(tab.id)?.let { wv.restoreState(it) != null } == true
            if (restored || tab.url != null) {
                loaded[tab.id] = tab.requestedUrl to tab.loadNonce
                if (!restored) wv.loadUrl(tab.url!!)
            }
            trimTo(MAX_LIVE_TABS)
            applyActivity()
        }
        configure(wv, tab.desktopMode ?: vm.settings.desktopMode)
        topInset.apply(tab.id, wv) // follows the status bar setting while the page is open
        val req = tab.requestedUrl
        if (req != null && loaded[tab.id] != (req to tab.loadNonce)) {
            loaded[tab.id] = req to tab.loadNonce
            errors.remove(tab.id)
            signIn.navigated(tab.id, req)
            wv.loadUrl(req)
        }
        return wv
    }

    fun destroy(id: String) {
        if (printer.isPrinting(id)) { printer.closeWhenDone(id); views[id]?.let { (it.parent as? ViewGroup)?.removeView(it) }; return }
        blocker.forget(id)
        var wasIncognito = false
        views.remove(id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wasIncognito = incognito.detach(id, wv)
            wv.destroy()
        }
        loaded.remove(id); errors.remove(id); lastUsed.remove(id); saved.remove(id); topInset.reset(id)
        pausedViews.remove(id); dialogLimit.reset(id); media.forget(id); signIn.forgetTab(id); httpContinued.remove(id)
        // posted: callers destroy a tab's WebView before (or while) removing the tab, and the tab list must be final
        if (wasIncognito || incognito.detach(id, null) || vm.tabs.list.none { it.id == id }) Handler(Looper.getMainLooper()).post { incognito.release() }
    }

    /** Evicts least recently viewed WebViews (never the active one) until at most [max] are live. */
    fun trimTo(max: Int) {
        while (views.size > max) {
            // never a popup or its opener: the page relies on window.opener / postMessage between the two
            val linked = vm.tabs.list.filter { it.openerId != null }.flatMap { listOf(it.id, it.openerId!!) }.toSet()
            val id = views.keys.filter { it != vm.tabs.activeId && it != media.tabId && it !in linked && !printer.isPrinting(it) }.minByOrNull { lastUsed[it] ?: 0L } ?: return
            val state = Bundle().also { views[id]?.saveState(it) }
            destroy(id)
            saved[id] = state
        }
    }

    /** Activity teardown: release every WebView (they hold the Activity). [finishing]: incognito ends with the app. */
    fun destroyAll(finishing: Boolean) {
        views.keys.toList().forEach { destroy(it) }
        media.release()
        if (finishing) incognito.release(all = true) // wipe now; folders go at the next launch
    }

    fun handle(cmd: WebCmd) {
        when (cmd) {
            is WebCmd.Reload -> { errors.remove(cmd.tabId); views[cmd.tabId]?.reload() }
            is WebCmd.Stop -> views[cmd.tabId]?.stopLoading()
            is WebCmd.GoBack -> views[cmd.tabId]?.goBack()
            is WebCmd.GoForward -> views[cmd.tabId]?.goForward()
            WebCmd.Print -> views[vm.tabs.activeId]?.let { printer.print(vm.tabs.activeId, it) }
            is WebCmd.CapturePreview -> capturePreview(cmd.tabId)
            is WebCmd.Destroy -> cmd.tabIds.forEach(::destroy)
            WebCmd.WipeWebData -> {
                clearBrowsingData()
                views.keys.toList().forEach { destroy(it) }
            }
            WebCmd.ClearBrowsingData -> clearBrowsingData()
            WebCmd.RecreateAll -> { views.keys.toList().forEach { destroy(it) }; generation++ }
            is WebCmd.Find -> views[vm.tabs.activeId]?.let { if (cmd.query.isEmpty()) it.clearMatches() else it.findAllAsync(cmd.query) }
            is WebCmd.FindNext -> views[vm.tabs.activeId]?.findNext(cmd.forward)
        }
    }

    /** Cookies, site storage (localStorage, IndexedDB…), HTTP cache and saved form/auth data. */
    private var cacheClearPending = false

    private fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        views.values.forEach { it.clearCache(true); it.clearFormData() }
        // the HTTP cache is cleared through a WebView: with none open (e.g. at startup), the next one created does it
        if (views.isEmpty()) cacheClearPending = true
        @Suppress("DEPRECATION") WebViewDatabase.getInstance(activity).apply { clearHttpAuthUsernamePassword(); clearFormData() }
        // and what was granted this session: sign-ins, camera/mic/location, certificate exceptions
        signIn.forget(); permissions.forget()
        vm.clearBrowsingTraces() // tab snapshots, site icons, mi's own HTTP cache
    }

    fun exitFullscreen() { customViewCallback?.onCustomViewHidden() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(id: String, profile: String?): WebView {
        // Pages see prefers-color-scheme from the creating context's theme, so match the app's light/dark setting.
        val themed = ContextThemeWrapper(activity, if (isDark()) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        val wv = MiWebView(themed, incognito = profile != null, pullEnabled = { vm.settings.pullToRefresh },
            onPull = { if (id == vm.tabs.activeId) vm.pullProgress = it },
            onRefresh = { errors.remove(id); views[id]?.reload() },
            onThreshold = { haptics.tick() })
        if (cacheClearPending && profile == null) { wv.clearCache(true); cacheClearPending = false }
        if (profile != null) incognito.attach(id, wv, profile)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.WHITE)
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        with(wv.settings) {
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION")
            saveFormData = true
            setGeolocationEnabled(true)
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
        val ws = wv.settings
        // No "X-Requested-With: <app id>" header where WebView allows turning it off (it marks requests as coming
        // from an embedded WebView). WebView 155 doesn't offer this, so there the header is still sent. Deprecated, but
        // still what removes the header where it's supported: kept on purpose.
        @Suppress("DEPRECATION")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) WebSettingsCompat.setRequestedWithHeaderOriginAllowList(ws, emptySet())
        // Passkeys / security keys (WebAuthn) through Android's Credential Manager, as a browser.
        // Incognito: signing in with a passkey works, creating one doesn't (it would be saved to the password manager
        // for good). Without document-start scripts that can't be enforced, so passkeys stay off there.
        val incognitoTab = isIncognito(id)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION) && (!incognitoTab || WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))) {
            WebSettingsCompat.setWebAuthenticationSupport(ws, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
            if (incognitoTab) WebViewCompat.addDocumentStartJavaScript(wv, scripts.noPasskeyCreation, setOf("*"))
        }
        // Payment Request API (e.g. Google Pay on checkout pages).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) WebSettingsCompat.setPaymentRequestEnabled(ws, true)
        // window.print() and navigator.share for pages (assets/js/bridge.js).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(wv, "miBridge", setOf("*"), bridge.listener(id))
            blocker.attach(wv, id, scripts.cosmeticFilter)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.bridge, setOf("*"))
            // what plays, for background playback and picture-in-picture (assets/js/media.js)
            WebViewCompat.addWebMessageListener(wv, "miMedia", setOf("*"), media.listener(id))
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.media, setOf("*"))
        }
        if (timersPaused && appVisible) { wv.resumeTimers(); timersPaused = false } // paused while every WebView was gone
        wv.webViewClient = Client(id)
        wv.webChromeClient = Chrome(id)
        wv.setOnScrollChangeListener { _, _, y, _, _ -> if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.Scroll(y / density)) }
        wv.setOnLongClickListener { onLongPress(wv) }
        // match count and position for the find bar (only the visible tab's search is shown)
        wv.setFindListener { ordinal, matches, done -> if (done && id == vm.tabs.activeId) vm.find.onResult(ordinal, matches) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, length -> downloads.start(incognito.cookies(wv), isIncognito(id), url, userAgent, contentDisposition, mimetype, length) }
        return wv
    }

    private fun isDark() = when (vm.settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun nav(id: String, view: WebView, url: String?, loading: Boolean) =
        vm.onWebNav(id, url, view.title, view.canGoBack(), view.canGoForward(), loading)

    private inner class Client(val id: String) : WebViewClient() {
        /** Ad, tracker and cookie-banner blocking of sub-resources (runs on a WebView thread). */
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = blocker.intercept(id, request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val original = request.url.toString()
            // a link carrying tracking parameters (utm_*, fbclid…) is opened without them
            val url = if (request.isForMainFrame && vm.settings.stripTrackingParams && request.method == "GET") stripTracking(original) else original
            if (request.isForMainFrame) signIn.navigated(id, url)
            val gesture = request.hasGesture()
            if (url.startsWith("blob:") || url.startsWith("data:")) return false
            if (url.startsWith("mibrowser:")) { if (gesture) vm.handleIncomingUrl(url, incognito = isIncognito(id)); return true }
            if (vm.settings.httpsOnly && url.startsWith("http://") && request.isForMainFrame && !httpLetThrough(id, displayHost(url))) {
                vm.loadInTab(id, url.replaceFirst("http://", "https://"))
                return true
            }
            if (url.startsWith("googleapp://")) return true
            val standard = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")
            if (standard) {
                if (url.contains("intent://") || url.contains("#Intent;")) { externalApps.open(url, gesture); return true }
                if (url != original) { view.loadUrl(url); return true }
                return false
            }
            externalApps.open(url, gesture)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            blocker.pageStarted(id, url?.let { Uri.parse(it).host })
            // HTTPS Only: the insecure site this tab continued to stays allowed only while the tab stays on it
            if (httpContinued[id].let { it != null && it != displayHost(url) }) httpContinued.remove(id)
            errors.remove(id)
            dialogLimit.reset(id)
            media.forget(id) // the page that was playing is gone
            topInset.reset(id); topInset.apply(id, view)
            nav(id, view, url, true)
            if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadStart)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) =
            signIn.httpAuth(id, scope(id), view.url, handler, host, realm)
        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = signIn.clientCertificate(request)

        override fun onPageFinished(view: WebView, url: String?) {
            if (id == vm.tabs.activeId) vm.find.refresh() // the page searched while loading may have more now
            topInset.reset(id); topInset.apply(id, view) // again, now the page's own head is in place
            fetchTouchIcon(view, url)
            signIn.pageFinished()
            if (errors[id] == null) vm.clearHttpFallback(id) // https worked: later failures in this tab are real errors
            val t = vm.tabs.list.firstOrNull { it.id == id }
            if (t != null && (t.readerMode ?: vm.settings.readerModeEnabled)) view.evaluateJavascript(scripts.reader, null)
            nav(id, view, url, false)
            if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadEnd)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            nav(id, view, url, vm.tabs.list.firstOrNull { it.id == id }?.loading ?: false)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            // like Chrome's HTTPS upgrades: any failure of the automatic https attempt retries over http, except when
            // the name doesn't resolve (http would fail too) or Safe Browsing blocked the page (never routed around)
            val httpMightWork = error.errorCode != ERROR_HOST_LOOKUP && error.errorCode != ERROR_UNSAFE_RESOURCE
            if (httpMightWork && fallBackToHttp(request.url.toString())) return
            // HTTPS Only: the secure connection failed; warn, with a way to continue over http (like Chrome and Firefox)
            val failed = request.url.toString()
            if (httpMightWork && vm.settings.httpsOnly && failed.startsWith("https://")) {
                errors[id] = WebError(WebError.HTTPS_ONLY, "HTTPS_ONLY", failed)
                if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadEnd)
                return
            }
            errors[id] = WebError(error.errorCode, error.description?.toString() ?: "Unknown error", request.url.toString())
            if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadEnd)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = displayHost(error.url)
            if (vm.certExceptions.has(profileOf(id), host)) { handler.proceed(); return }
            handler.cancel()
            val mainFrame = view.url == null || view.url.equals(error.url, ignoreCase = true)
            if (mainFrame && fallBackToHttp(error.url)) return
            if (view.url.equals(error.url, ignoreCase = true) || view.url == null) {
                errors[id] = WebError(-1200, "ERR_SSL_PROTOCOL_ERROR", error.url)
                if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadEnd)
            }
        }

        /**
         * The page's high-resolution icon (apple-touch-icon / sized icon links), read from its own HTML once it has
         * loaded. The site it is stored for comes from WebView ([url]), never from the page's script, and the result is
         * dropped if the tab has navigated away meanwhile, so a page can only ever set its own icon.
         */
        private fun fetchTouchIcon(view: WebView, url: String?) {
            if (isIncognito(id)) return
            val host = faviconHost(url) ?: return
            if (view.url != url) return
            if (!vm.settings.jsEnabled) { Favicons.upgrade(host); return }
            view.evaluateJavascript(scripts.touchIcon) { result ->
                if (view.url != url) return@evaluateJavascript
                val href = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
                if (href != null) Favicons.fetchFor(host, href) else Favicons.upgrade(host)
            }
        }

        /** A typed address (no scheme) failed over https: retry it over http, once, unless HTTPS-Only is on. */
        private fun fallBackToHttp(url: String?): Boolean {
            val t = vm.tabs.list.firstOrNull { it.id == id } ?: return false
            if (!t.httpFallback || vm.settings.httpsOnly || url == null || !url.startsWith("https://")) return false
            vm.loadInTab(id, "http://" + url.removePrefix("https://")) // also clears the fallback flag: only once
            return true
        }

    }

    private inner class Chrome(val id: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.LoadProgress(newProgress / 100f))
        }

        /**
         * Site icons come from the page itself and are kept on the device (see Favicons). The icon is paired with the
         * page through WebView's history entry, not view.url: around a navigation the previous page's icon can arrive
         * after the address has already changed.
         */
        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if (isIncognito(id)) return
            val item = view.copyBackForwardList().currentItem ?: return
            val host = faviconHost(item.url) ?: return
            item.favicon?.let { Favicons.save(host, it) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            nav(id, view, view.url, vm.tabs.list.firstOrNull { it.id == id }?.loading ?: false)
        }

        /**
         * window.open / target=_blank: a real window, shown as a new tab linked to this one. The page keeps
         * window.opener and postMessage to it (sign-in and payment pop-ups such as PayPal depend on that).
         * Pop-ups without a tap are blocked unless the user allowed the site.
         */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val site = displayHost(view.url)
            if (!isUserGesture && !popups.allowed(profileOf(id), site)) {
                if (id == vm.tabs.activeId) popups.blocked(profileOf(id), site, view.url)
                return false
            }
            val opener = vm.tabs.list.firstOrNull { it.id == id }
            val desktop = opener?.desktopMode
            val popupId = uniqueId()
            val wv = create(popupId, opener?.profile) // same profile as the opener
            configure(wv, desktop ?: vm.settings.desktopMode)
            views[popupId] = wv
            lastUsed[popupId] = System.nanoTime()
            (resultMsg.obj as WebView.WebViewTransport).webView = wv
            resultMsg.sendToTarget()
            vm.addPopupTab(popupId, openerId = id, desktopMode = desktop)
            return true
        }

        /** The page called window.close() (only windows opened by script may): back to the opener. */
        override fun onCloseWindow(window: WebView) { vm.closeWindow(id) }

        // JS dialogs use WebView's own dialogs (return false) unless the page is spamming them (see dialogAllowed)
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (dialogLimit.allow(id)) return false
            result.confirm(); return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (dialogLimit.allow(id)) return false
            result.cancel(); return true
        }

        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
            if (dialogLimit.allow(id)) return false
            result.cancel(); return true
        }

        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) =
            fileChooser.show(callback, params)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback?.onCustomViewHidden()
            customViewCallback = callback
            customView = view
        }

        override fun onHideCustomView() {
            customView = null
            customViewCallback = null
        }

        override fun onPermissionRequest(request: PermissionRequest) = permissions.media(scope(id), request)
        override fun onPermissionRequestCanceled(request: PermissionRequest) { vm.alert = null }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
            permissions.location(scope(id), origin, callback)
        override fun onGeolocationPermissionsHidePrompt() { vm.alert = null }

    }

    // ---------------------------------------------------------------- context menu (native hit test)
    private fun onLongPress(wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val type = hit.type
        if (type != WebView.HitTestResult.SRC_ANCHOR_TYPE && type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE && type != WebView.HitTestResult.IMAGE_TYPE) return false
        haptics.longPress()
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

    // ---------------------------------------------------------------- error page actions
    fun retry(id: String) { errors.remove(id); views[id]?.reload() }

    /**
     * HTTPS Only, insecure site the tab continued to (tab → host): its links stay on http while the tab stays on that
     * site. Leaving the site or closing the tab forgets it, so the next visit asks again; nothing is kept anywhere else.
     */
    private val httpContinued = HashMap<String, String>()

    /** Whether [host] may load over plain http in tab [id]: continued to on this visit, or (Warn Every Time off) this session. */
    private fun httpLetThrough(id: String, host: String) =
        httpContinued[id] == host || (!vm.settings.httpsWarnEveryTime && vm.httpAllowed.has(profileOf(id), host))

    /** HTTPS Only's warning: continue to the site over plain http (for this visit, or the session; see Warn Every Time). */
    fun continueOverHttp(id: String) {
        val target = errors[id]?.failingUrl ?: return
        val host = displayHost(target)
        // Warn Every Time: this visit only; off: the site is let through for the session (in this profile)
        if (vm.settings.httpsWarnEveryTime) httpContinued[id] = host else vm.httpAllowed.trust(profileOf(id), host)
        errors.remove(id)
        vm.loadInTab(id, target.replaceFirst("https://", "http://"))
    }

    fun proceedUnsafe(id: String) {
        val target = errors[id]?.failingUrl ?: vm.tabs.list.firstOrNull { it.id == id }?.url
        val host = displayHost(target)
        if (host.isNotEmpty()) vm.certExceptions.trust(profileOf(id), host)
        errors.remove(id)
        views[id]?.reload()
    }

    // ---------------------------------------------------------------- site settings (sites/SiteSettingsView)
    // all in tab [tabId]'s profile, so an incognito tab's site page only touches that incognito tab

    fun siteAccess(tabId: String, host: String) = permissions.allowedFor(scope(tabId), host)
    fun revokeSiteAccess(tabId: String, host: String, access: SiteAccess) { permissions.revoke(scope(tabId), host, access); views[tabId]?.reload() }
    fun popupsAllowed(tabId: String, site: String) = popups.allowed(profileOf(tabId), site)
    fun setPopupsAllowed(tabId: String, site: String, allow: Boolean) = popups.setAllowed(profileOf(tabId), site, allow)

    /**
     * Clears [url]'s site: its cookies (WebView can't delete one site's cookies directly, so each is expired, for the
     * host and its registrable domain) and its storage (local storage, IndexedDB, caches, service workers), then
     * reloads. Cookies set on other paths than / can survive.
     */
    fun clearSiteData(tabId: String, url: String) {
        val wv = views[tabId]
        val u = Uri.parse(url)
        val host = u.host ?: return
        val origin = "${u.scheme}://$host"
        val cm = wv?.let { incognito.cookies(it) } ?: CookieManager.getInstance()
        val domains = listOf(host, AdBlock.registrable(host)).distinct()
        cm.getCookie(origin)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            cm.setCookie(origin, "$name=; Max-Age=0; Path=/")
            domains.forEach { d -> cm.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$d") }
        }
        cm.flush()
        val storage = if (wv != null && profileOf(tabId) != null) WebViewCompat.getProfile(wv).webStorage else WebStorage.getInstance()
        storage.deleteOrigin(origin)
        wv?.reload()
    }

    // ---------------------------------------------------------------- previews
    /** Snapshots tab [id]'s page for its switcher card (see TabPreviews). */
    fun capturePreview(id: String) {
        // a page that failed shows mi's error page over WebView's own: a snapshot would catch WebView's (the card draws
        // mi's error page itself instead, see TabSwitcher)
        if (errors[id] != null) return
        previews.capture(id, views[id])
    }

    init {
        incognito.release() // leftovers from a crash
        blocker.filterServiceWorkers()
    }
}

// The WebView pool: one WebView per open tab (least recently used ones evicted) and their settings. The WebView
// callbacks are TabWebClient and TabChromeClient; each job behind them is its own class (SitePermissions, HttpsOnly…).
package com.jlees.mibrowser.web

import com.jlees.mibrowser.data.darkFor
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.extensions.fetchExtension
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.adblock.ContentBlocker
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.downloads.DownloadPrompt
import com.jlees.mibrowser.incognito.IncognitoProfiles
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.uniqueId
import com.jlees.mibrowser.tabs.TabPreviews
import com.jlees.mibrowser.ui.common.Haptics

data class WebError(val code: Int, val desc: String, val failingUrl: String?) {
    companion object {
        /** HTTPS Only couldn't connect securely (mi's own code; WebView's are small negatives). */
        const val HTTPS_ONLY = -3001
        /** The page renderer crashed while this tab was showing. */
        const val CRASHED = -3002
    }
}


/** After a page crashed: reopen it by itself, unless it already did so in the last minute ([lastReload], elapsed ms). */
fun reloadAfterCrash(lastReload: Long?, now: Long) = lastReload == null || now - lastReload > 60_000

/** Live WebViews kept in memory: the active tab and three in the background. Older tabs are rebuilt from saved state
 *  when opened (each live WebView holds tens of MB; background ones are paused, so they cost memory, not battery). */
const val MAX_LIVE_TABS = 4

class WebViewHost(
    private val activity: Activity,
    internal val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit,
) {
    internal val scripts = PageScripts(activity)
    internal val topInset by lazy { TopInset(scripts.topInset) { vm.settings.showStatusBar } }
    private val haptics = Haptics(activity.window.decorView) { vm.settings.hapticFeedback }
    // incognito tabs add their own (in-memory) sites with blocking off: never the saved list
    internal val blocker: ContentBlocker = ContentBlocker({ vm.settings }) { id -> if (isIncognito(id)) vm.incognitoAdAllowlist else emptySet() }
    private val userAgents = UserAgents(activity)
    internal val permissions = SitePermissions(activity, vm, requestPermissions)
    internal val popups = PopupPolicy(vm)
    /** Audio and video that keep playing in the background, with the media notification. */
    val media = com.jlees.mibrowser.media.BackgroundMedia(activity.applicationContext, { vm.settings.backgroundAudio }, ::isIncognito,
        { id -> vm.tabs.list.firstOrNull { it.id == id }?.url }) { applyActivity() }
    private val downloads = DownloadPrompt(activity, vm, requestPermissions, launchForResult)
    /** Files a page makes itself (blob:/data:): saved into Downloads and listed on the Downloads page. */
    private val blobDownloads = com.jlees.mibrowser.downloads.BlobDownloads(activity, { vm.settings.downloadFolder },
        onSaved = { item -> vm.downloads.add(item); com.jlees.mibrowser.ui.common.Notices.show("Saved ${item.fileName}", done = true) },
        onFailed = { name -> com.jlees.mibrowser.ui.common.Notices.show("Couldn't save $name") })
    private val printer = Printer(activity) { id -> destroy(id) }
    internal val externalApps = ExternalApps(activity, vm)
    internal val signIn = SignInChallenges(activity, vm)
    internal val dialogLimit = DialogLimit()
    internal val fileChooser = FileChooser(activity, launchForResult)
    private val previews = TabPreviews(activity, vm)
    private val bridge = PageBridge(activity) { id, wv -> printer.print(id, wv) }
    private val linkMenu = LinkMenu(vm, haptics)
    internal val httpsOnly = HttpsOnly(vm, ::profileOf)
    private val views = HashMap<String, WebView>()
    /**
     * Installed extensions, running in tabs and in pages of their own (extensions/). One engine for as long as the
     * ViewModel lives: a window rebuilt (rotation, a theme change) takes the running one over rather than starting
     * extensions a second time; it only reaches this window's tabs through [ExtensionEngine.tabView], let go in destroyAll.
     */
    val extensions = (com.jlees.mibrowser.extensions.ExtensionFlow.engine?.takeIf { it.vm === vm }
        ?: com.jlees.mibrowser.extensions.ExtensionEngine(activity.applicationContext, vm).also {
            com.jlees.mibrowser.extensions.ExtensionFlow.engine?.shutdown() // one left from a ViewModel that's gone
            com.jlees.mibrowser.extensions.ExtensionFlow.engine = it
        }).also { it.tabView = { id -> views[id] } }
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
            vm.extensions.forgetScope(name)
            // session state kept per profile goes with it
            popups.forget(name)
            signIn.forget(name)
            permissions.forget(name)
            vm.certExceptions.forget(name); vm.httpAllowed.forget(name)
        },
        serviceWorkers = blocker.profileWorkers,
    )
    private val browsingData by lazy { BrowsingData(activity, vm, incognito) { signIn.forget(); permissions.forget() } }
    /** Tab [id]'s profile (null: a normal tab); also for Site settings. */
    fun profileOf(id: String) = vm.tabs.list.firstOrNull { it.id == id }?.profile ?: incognito.profileOfView(id)
    internal fun isIncognito(id: String) = profileOf(id) != null
    /** Prefix for session state (permissions, sign-ins, pop-up allowances) so it stays inside one profile. */
    internal fun scope(id: String) = profileOf(id).orEmpty()

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
        // process-wide (app windows' pages too: PageTimers); with no WebView alive, create() catches up
        PageTimers.mainNeeds = appVisible || playing != null
        views.values.firstOrNull()?.let(PageTimers::apply)
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
            if (errors[tab.id]?.code == WebError.CRASHED) loaded[tab.id] = tab.requestedUrl to tab.loadNonce // waits for Try Again
            else if (restored || tab.url != null) {
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
        extensions.tabs.detach(id)
        var wasIncognito = false
        views.remove(id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wasIncognito = incognito.detach(id, wv)
            wv.destroy()
        }
        loaded.remove(id); errors.remove(id); lastUsed.remove(id); saved.remove(id); topInset.reset(id); com.jlees.mibrowser.follow.FollowPage.detach(id)
        pausedViews.remove(id); dialogLimit.reset(id); media.forget(id); signIn.forgetTab(id); httpsOnly.forget(id)
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
        // the engine outlives this window (unless mi is closing): it lets go of this window's tabs
        if (finishing) extensions.shutdown() else extensions.tabView = { null }
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
                browsingData.clearAll(views.values)
                views.keys.toList().forEach { destroy(it) }
            }
            WebCmd.ClearBrowsingData -> browsingData.clearAll(views.values)
            WebCmd.RecreateAll -> { views.keys.toList().forEach { destroy(it) }; generation++ }
            is WebCmd.Find -> views[vm.tabs.activeId]?.let { if (cmd.query.isEmpty()) it.clearMatches() else it.findAllAsync(cmd.query) }
            is WebCmd.FindNext -> views[vm.tabs.activeId]?.findNext(cmd.forward)
        }
    }

    fun exitFullscreen() { customViewCallback?.onCustomViewHidden() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(id: String, profile: String?): WebView {
        // Pages see prefers-color-scheme from the creating context's theme, so match the app's light/dark setting.
        val themed = ContextThemeWrapper(activity, if (vm.settings.darkFor(activity.resources.configuration)) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        val wv = MiWebView(themed, incognito = profile != null, pullEnabled = { vm.settings.pullToRefresh },
            onPull = { if (id == vm.tabs.activeId) vm.pullProgress = it },
            onRefresh = { errors.remove(id); views[id]?.reload() },
            onThreshold = { haptics.tick() })
        if (profile == null) browsingData.created(wv)
        if (profile != null) incognito.attach(id, wv, profile)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.WHITE)
        // a tab out of sight lets Android reclaim its page's memory first (the visible one keeps the renderer
        // important); if the renderer is reclaimed, the pages are rebuilt (TabWebClient.onRenderProcessGone)
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
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
            blocker.attach(wv, id, scripts.cosmeticFilter, scripts.scriptlets)
            blobDownloads.attach(wv, scripts.blobDownload)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.bridge, setOf("*"))
            // what plays, for background playback and picture-in-picture (assets/js/media.js)
            WebViewCompat.addWebMessageListener(wv, "miMedia", setOf("*"), media.listener(id))
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.media, setOf("*"))
            extensions.tabs.attach(wv, id, profile, incognito.cookies(wv))
        }
        PageTimers.mainNeeds = appVisible || media.playingTab != null
        PageTimers.apply(wv) // paused while every WebView was gone
        wv.webViewClient = TabWebClient(this, id)
        wv.webChromeClient = TabChromeClient(this, id)
        wv.setOnScrollChangeListener { _, _, y, _, _ -> if (id == vm.tabs.activeId) vm.ui.tryEmit(UiEvent.Scroll(y / density)) }
        wv.setOnLongClickListener { linkMenu.onLongPress(wv) }
        wv.selectionItems = { extensions.menus.selectionItems(id, wv) } // extensions' items for selected text
        if (profile == null) com.jlees.mibrowser.follow.FollowPage.attach(id, wv) // following prices (never from incognito)
        // match count and position for the find bar (only the visible tab's search is shown)
        wv.setFindListener { ordinal, matches, done -> if (done && id == vm.tabs.activeId) vm.find.onResult(ordinal, matches) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, length ->
            // a file the page made itself: the page hands it over (BlobDownloads); anything else, mi's downloader
            if (url.startsWith("blob:") || url.startsWith("data:")) blobDownloads.fetch(wv, url, isIncognito(id))
            // an extension package (a Firefox .xpi): offered to install, not saved as a file
            else if (mimetype == "application/x-xpinstall" || Regex("""\.xpi([?#]|$)""").containsMatchIn(url))
                vm.fetchExtension(com.jlees.mibrowser.extensions.InstallSource.File(url), android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype).substringBeforeLast('.'))
            else downloads.start(incognito.cookies(wv), isIncognito(id), url, userAgent, contentDisposition, mimetype, length)
        }
        return wv
    }

    internal fun nav(id: String, view: WebView, url: String?, loading: Boolean) =
        vm.onWebNav(id, url, view.title, view.canGoBack(), view.canGoForward(), loading)

    /** A window the page in tab [openerId] opened (allowed): a new tab linked to it, in the same profile. */
    internal fun openPopup(openerId: String, resultMsg: Message): Boolean {
        val opener = vm.tabs.list.firstOrNull { it.id == openerId }
        val desktop = opener?.desktopMode
        val popupId = uniqueId()
        val wv = create(popupId, opener?.profile) // same profile as the opener
        configure(wv, desktop ?: vm.settings.desktopMode)
        views[popupId] = wv
        lastUsed[popupId] = System.nanoTime()
        (resultMsg.obj as WebView.WebViewTransport).webView = wv
        resultMsg.sendToTarget()
        vm.addPopupTab(popupId, openerId = openerId, desktopMode = desktop)
        extensions.navigation.createdTarget(openerId, popupId, opener?.url.orEmpty())
        return true
    }

    /** Fullscreen video (onShowCustomView), shown over everything until the page or Back ends it. */
    internal fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = callback
        customView = view
    }

    internal fun hideCustomView() { customView = null; customViewCallback = null }

    /**
     * The renderer every tab shares died (TabWebClient.onRenderProcessGone, once for each WebView): their WebViews are
     * rebuilt. Reclaimed by Android, the pages simply load again; crashed, the visible tab shows an error page rather
     * than reloading what may well crash it again (and again).
     */
    /** When each tab last reopened its page after a crash (elapsed ms). */
    private val crashReloads = HashMap<String, Long>()

    internal fun rendererGone(view: WebView, crashed: Boolean) {
        if (view !in views.values) return // its WebView is already gone (an earlier callback rebuilt them all)
        val active = vm.tabs.activeId
        views.keys.toList().forEach { destroy(it) }
        // a crash reopens the page by itself (the rebuilt WebView loads it: sync); a second within a minute shows the
        // error page instead, so a page that keeps crashing can't loop
        if (crashed) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (reloadAfterCrash(crashReloads[active], now)) crashReloads[active] = now
            else errors[active] = WebError(WebError.CRASHED, "CRASHED", vm.tabs.list.firstOrNull { it.id == active }?.url)
        }
        generation++
    }

    // ---------------------------------------------------------------- error page actions
    fun retry(id: String) {
        val crashed = errors.remove(id)?.code == WebError.CRASHED
        // after a crash the rebuilt WebView has nothing to reload: it opens the tab's page
        if (crashed) vm.tabs.list.firstOrNull { it.id == id }?.url?.let { views[id]?.loadUrl(it) } else views[id]?.reload()
    }

    /** HTTPS Only's warning: continue to the site over plain http (for this visit, or the session; see Warn Every Time). */
    fun continueOverHttp(id: String) {
        val target = errors[id]?.failingUrl ?: return
        httpsOnly.continueTo(id, displayHost(target))
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

    /** Clears [url]'s site in tab [tabId]'s profile, then reloads (see BrowsingData.clearSite). */
    fun clearSiteData(tabId: String, url: String) = browsingData.clearSite(views[tabId], profileOf(tabId) != null, url)

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

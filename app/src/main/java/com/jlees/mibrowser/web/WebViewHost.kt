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
import com.jlees.mibrowser.logic.isLocalHost
import com.jlees.mibrowser.logic.isLoopback
import com.jlees.mibrowser.logic.faviconHost
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.uniqueId
import com.jlees.mibrowser.AuthRequest
import com.jlees.mibrowser.ui.shareText
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.security.KeyChain
import androidx.webkit.ProfileStore
import com.jlees.mibrowser.data.INCOGNITO_PROFILE_PREFIX
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import java.net.URL

data class WebError(val code: Int, val desc: String, val failingUrl: String?)

/**
 * Gives pages what Chrome gives them, where WebView doesn't: window.print() (a no-op in WebView) and navigator.share
 * (missing in WebView), both routed to the app through the "miBridge" message listener (main frame only).
 * Share keeps the Web Share rule that it needs a user gesture.
 */
/**
 * Incognito: navigator.credentials.create({publicKey}) is refused, so no passkey is saved from an incognito tab.
 * ponytail: a page could reach an unpatched create() through a fresh about:blank iframe; Android still shows its own
 * "Create passkey?" sheet then, so nothing is saved without the user confirming.
 */
private const val NO_PASSKEY_CREATION_JS = """(() => {
  const proto = window.CredentialsContainer && CredentialsContainer.prototype;
  if (!proto || !proto.create || proto.__miNoCreate) return;
  const create = proto.create;
  Object.defineProperty(proto, '__miNoCreate', { value: true });
  Object.defineProperty(proto, 'create', { configurable: false, writable: false, value: function (options) {
    if (options && options.publicKey) {
      try { window.miBridge && window.miBridge.postMessage(JSON.stringify({ type: 'passkeyBlocked' })); } catch (e) {}
      return Promise.reject(new DOMException("Passkeys can't be created in incognito", 'NotAllowedError'));
    }
    return create.apply(this, arguments);
  } });
})();"""

private const val BRIDGE_JS = """(() => {
  const bridge = window.miBridge;
  if (!bridge || window.__miBridgeShim) return;
  window.__miBridgeShim = true;
  const send = (m) => bridge.postMessage(JSON.stringify(m));
  window.print = () => send({ type: 'print' });
  if (!navigator.share) {
    navigator.share = (d) => {
      if (navigator.userActivation && !navigator.userActivation.isActive)
        return Promise.reject(new DOMException('Sharing requires a user gesture', 'NotAllowedError'));
      d = d || {};
      if (d.files && d.files.length) return Promise.reject(new DOMException('Sharing files is not supported', 'NotAllowedError'));
      let url = '';
      try { url = d.url ? new URL(d.url, location.href).href : ''; } catch (e) { return Promise.reject(new TypeError('Invalid URL')); }
      send({ type: 'share', title: String(d.title || ''), text: String(d.text || ''), url });
      return Promise.resolve();
    };
    navigator.canShare = (d) => !!d && !(d.files && d.files.length) && !!(d.url || d.text || d.title);
  }
})();"""

private const val READER_JS = """(function() {
  var style = document.createElement('style');
  style.type = 'text/css';
  style.appendChild(document.createTextNode('body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; color: #333; background: #fff; } img { max-width: 100%; height: auto; } nav, header, footer, .ad, .advertisement, .sidebar { display: none !important; }'));
  document.head.appendChild(style);
})();"""

/** The page's largest declared icon (touch icons count as 180 px when unsized), as an absolute URL, or null. */
private const val TOUCH_ICON_JS = """(() => {
  let best = null, size = 0;
  for (const e of document.querySelectorAll('link[rel~="apple-touch-icon"],link[rel~="apple-touch-icon-precomposed"],link[rel~="icon"]')) {
    const s = parseInt(((e.sizes && e.sizes.value) || '').split(/x/i)[0]) || (/apple/.test(e.rel) ? 180 : 16);
    if (s > size && e.href) { size = s; best = e.href; }
  }
  return size >= 48 ? best : null;
})()"""

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

    // ---- incognito: every incognito tab (with its pop-ups) has its own WebView profile, so cookies, storage, cache,
    // service workers and permissions never cross between it and normal tabs or other incognito tabs. A profile is
    // deleted from disk as soon as nothing uses it, and leftovers (after a crash) are deleted at startup.
    private val viewProfile = HashMap<String, String>()
    private fun profileOf(id: String) = vm.tabs.firstOrNull { it.id == id }?.profile ?: viewProfile[id]
    private fun isIncognito(id: String) = profileOf(id) != null
    /** Prefix for session state (permissions, sign-ins, pop-up allowances) so it stays inside one profile. */
    private fun scope(id: String) = profileOf(id).orEmpty()
    /** The tab's cookie store: its profile's (an incognito tab never reads or writes the normal cookie jar). */
    private fun cookies(wv: WebView): CookieManager =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.getProfile(wv).cookieManager else CookieManager.getInstance()
    /** Pop-ups allowed in an incognito tab ("<profile>|<site>"); session only, never added to the saved list. */
    private val incognitoPopupSites = HashSet<String>()

    /**
     * Ends every incognito profile no tab uses (all of them when [all]). WebView can only delete a profile that wasn't
     * loaded in this process, so a profile used this session is wiped through its own APIs instead (cookies, all site
     * storage incl. service workers and IndexedDB, location permissions; its HTTP cache was cleared with its last
     * WebView) and its folder is deleted at the next launch (see [endProcessIfIncognitoUsed]).
     */
    fun releaseProfiles(all: Boolean = false) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val inUse = if (all) emptySet() else vm.tabs.mapNotNull { it.profile }.toSet() + viewProfile.values
        val store = ProfileStore.getInstance()
        store.allProfileNames.filter { it.startsWith(INCOGNITO_PROFILE_PREFIX) && it !in inUse && it !in wipedProfiles }.forEach { name ->
            if (name in loadedProfiles) {
                store.getProfile(name)?.run {
                    cookieManager.removeAllCookies(null); cookieManager.flush()
                    webStorage.deleteAllData()
                    geolocationPermissions.clearAll()
                }
                wipedProfiles += name
            } else runCatching { store.deleteProfile(name) }
            incognitoPopupSites.removeAll { it.startsWith("$name|") }
            httpAuth.keys.removeAll { it.startsWith("$name|") }
            sitePermissions.removeAll { it.startsWith("$name|") }
            vm.forgetProfile(name)
            lastPopupNotice = null
        }
    }

    /** Incognito profiles loaded in this process (they can't be deleted until it ends) and those already wiped. */
    private val loadedProfiles = HashSet<String>()
    private val wipedProfiles = HashSet<String>()

    /**
     * Leaving the app after using incognito: end the process, so the next launch starts fresh and deletes the wiped
     * profile folders (WebView keeps a profile loaded for the life of the process). [flush] saves normal data first.
     */
    fun endProcessIfIncognitoUsed(flush: () -> Unit) {
        if (loadedProfiles.isEmpty()) return
        flush()
        android.os.Process.killProcess(android.os.Process.myPid())
    }


    // ---- identity: present as Chrome of the same version (what Chrome sends), not as an embedded WebView.
    // WebView's own UA ends in "; wv) ... Version/4.0" and its client hints list an "Android WebView" brand; sites
    // like PayPal and Google sign-in refuse embedded WebViews, so neither is sent.
    private val chromeVersion: String by lazy {
        runCatching { Regex("Chrome/([\\d.]+)").find(WebSettings.getDefaultUserAgent(activity))?.groupValues?.get(1) }.getOrNull() ?: "140.0.0.0"
    }
    private val chromeMajor get() = chromeVersion.substringBefore('.')
    private val mobileUa by lazy { "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Mobile Safari/537.36" }
    private val desktopUa by lazy { "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Safari/537.36" }

    private fun uaMetadata(desktop: Boolean): UserAgentMetadata {
        fun brand(name: String, major: String, full: String) =
            UserAgentMetadata.BrandVersion.Builder().setBrand(name).setMajorVersion(major).setFullVersion(full).build()
        return UserAgentMetadata.Builder()
            .setBrandVersionList(listOf(brand("Chromium", chromeMajor, chromeVersion), brand("Not_A Brand", "24", "24.0.0.0")))
            .setFullVersion(chromeVersion)
            .setPlatform(if (desktop) "Linux" else "Android")
            .setPlatformVersion(if (desktop) "" else Build.VERSION.RELEASE)
            .setModel(if (desktop) "" else Build.MODEL)
            .setMobile(!desktop)
            .build()
    }

    /** Per-tab settings that can change (JS, desktop mode, cookies); applied before any load. */
    private fun configure(wv: WebView, desktop: Boolean) {
        val s = vm.settings
        wv.settings.javaScriptEnabled = s.jsEnabled
        val ua = if (desktop) desktopUa else mobileUa
        if (wv.settings.userAgentString != ua) {
            wv.settings.userAgentString = ua
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) WebSettingsCompat.setUserAgentMetadata(wv.settings, uaMetadata(desktop))
        }
        cookies(wv).setAcceptCookie(!s.blockCookies)
        cookies(wv).setAcceptThirdPartyCookies(wv, !s.blockCookies && !s.blockThirdPartyCookies)
        if (wv.settings.textZoom != s.textZoom) wv.settings.textZoom = s.textZoom
        // only has an effect while the WebView's theme is dark (it follows the app theme, see create)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, s.darkenPages)
    }

    /** Last "pop-up blocked" notice (site|page), so a page that keeps trying doesn't stack dialogs. */
    private var lastPopupNotice: String? = null

    /** Each tab's latest main-frame navigation (including server redirects): the scheme of a sign-in challenge. */
    private val mainNav = HashMap<String, String>()

    /** HTTP authentication typed this session ("host|realm" → user, password); never written to disk. */
    private val httpAuth = HashMap<String, Pair<String, String>>()
    /** Challenges already answered with remembered credentials: if the server asks again, they were wrong. */
    private val authAnswered = HashSet<String>()

    /** Tabs whose page is being printed: their WebView must outlive window.close() until the print job is done. */
    private val printing = HashSet<String>()
    private val closeAfterPrint = HashSet<String>()
    private var lastBridgeAction = 0L
    /** Last time the user touched mi (any screen); page-triggered print/share must follow a real touch. */
    private var lastUserTouch = 0L
    fun noteUserTouch() { lastUserTouch = SystemClock.uptimeMillis() }

    // ---- battery: only the visible tab runs freely; everything pauses while mi is in the background
    private var appVisible = true
    private var timersPaused = false
    private val pausedViews = HashSet<String>()

    /** Pauses animations/geolocation in every WebView but the active one (and that one too while mi isn't visible). */
    fun applyActivity() {
        for ((id, wv) in views) {
            val run = appVisible && id == vm.activeTabId
            if (run && id in pausedViews) { wv.onResume(); pausedViews -= id }
            else if (!run && id !in pausedViews) { wv.onPause(); pausedViews += id }
        }
    }

    /** Activity started/stopped. Stopped: JS timers in all pages stop too (WebView.pauseTimers is process-wide). */
    fun setAppVisible(visible: Boolean) {
        if (visible == appVisible) return
        appVisible = visible
        // instance methods, but they act on every WebView in the process; with none alive, create() catches up
        if (visible) { views.values.firstOrNull()?.let { it.resumeTimers(); timersPaused = false } }
        else views.values.firstOrNull()?.let { it.pauseTimers(); timersPaused = true }
        applyActivity()
    }

    /** Pages that opened 3 JS dialogs within 10 s: further dialogs are dismissed until the page navigates. */
    private val dialogTimes = HashMap<String, ArrayDeque<Long>>()
    private val dialogsBlocked = HashSet<String>()
    private fun dialogAllowed(id: String): Boolean {
        if (id in dialogsBlocked) return false
        val now = SystemClock.uptimeMillis()
        val times = dialogTimes.getOrPut(id) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() > 10_000) times.removeFirst()
        if (times.size >= 3) { dialogsBlocked += id; return false }
        times.addLast(now)
        return true
    }

    fun has(id: String) = views.containsKey(id)

    /** Get or create the tab's WebView, apply settings and perform any requested load. */
    fun sync(tab: TabItem): WebView {
        val fresh = !views.containsKey(tab.id)
        val wv = views.getOrPut(tab.id) { create(tab.id, tab.profile) }
        if (tab.id == vm.activeTabId) lastUsed[tab.id] = System.nanoTime()
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
        val req = tab.requestedUrl
        if (req != null && loaded[tab.id] != (req to tab.loadNonce)) {
            loaded[tab.id] = req to tab.loadNonce
            errors.remove(tab.id)
            mainNav[tab.id] = req
            wv.loadUrl(req)
        }
        return wv
    }

    fun destroy(id: String) {
        if (id in printing) { closeAfterPrint += id; views[id]?.let { (it.parent as? ViewGroup)?.removeView(it) }; return }
        views.remove(id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            // an incognito profile's HTTP cache is cleared with its last WebView (clearCache needs a live one)
            val p = viewProfile[id]
            if (p != null && viewProfile.count { it.value == p } == 1) wv.clearCache(true)
            wv.destroy()
        }
        loaded.remove(id); errors.remove(id); lastUsed.remove(id); saved.remove(id)
        pausedViews.remove(id); dialogTimes.remove(id); dialogsBlocked.remove(id); mainNav.remove(id)
        // posted: callers destroy a tab's WebView before (or while) removing the tab, and the tab list must be final
        if (viewProfile.remove(id) != null || vm.tabs.none { it.id == id }) Handler(Looper.getMainLooper()).post { releaseProfiles() }
    }

    /** Evicts least recently viewed WebViews (never the active one) until at most [max] are live. */
    fun trimTo(max: Int) {
        while (views.size > max) {
            // never a popup or its opener: the page relies on window.opener / postMessage between the two
            val linked = vm.tabs.filter { it.openerId != null }.flatMap { listOf(it.id, it.openerId!!) }.toSet()
            val id = views.keys.filter { it != vm.activeTabId && it !in linked && it !in printing }.minByOrNull { lastUsed[it] ?: 0L } ?: return
            val state = Bundle().also { views[id]?.saveState(it) }
            destroy(id)
            saved[id] = state
        }
    }

    /** Activity teardown: release every WebView (they hold the Activity). [finishing]: incognito ends with the app. */
    fun destroyAll(finishing: Boolean) {
        views.keys.toList().forEach { destroy(it) }
        if (finishing) releaseProfiles(all = true) // wipe now; folders go at the next launch
    }

    fun handle(cmd: WebCmd) {
        when (cmd) {
            is WebCmd.Reload -> { errors.remove(cmd.tabId); views[cmd.tabId]?.reload() }
            is WebCmd.Stop -> views[cmd.tabId]?.stopLoading()
            is WebCmd.GoBack -> views[cmd.tabId]?.goBack()
            is WebCmd.GoForward -> views[cmd.tabId]?.goForward()
            WebCmd.Print -> views[vm.activeTabId]?.let { print(vm.activeTabId, it) }
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
        // and what was granted this session: sign-ins, camera/mic/location, certificate exceptions
        httpAuth.clear(); authAnswered.clear(); sitePermissions.clear()
        vm.clearBrowsingTraces() // tab snapshots, site icons, mi's own HTTP cache
    }

    fun findNext(forward: Boolean) { views[vm.activeTabId]?.findNext(forward) }

    /** New search text: highlight all matches and jump to the first (findAllAsync); empty clears. */
    fun findAll(query: String) {
        val wv = views[vm.activeTabId] ?: return
        if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
    }

    fun exitFullscreen() { customViewCallback?.onCustomViewHidden() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(id: String, profile: String?): WebView {
        // Pages see prefers-color-scheme from the creating context's theme, so match the app's light/dark setting.
        val themed = ContextThemeWrapper(activity, if (isDark()) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        val wv = MiWebView(themed, incognito = profile != null, pullEnabled = { vm.settings.pullToRefresh },
            onPull = { if (id == vm.activeTabId) vm.pullProgress = it },
            onRefresh = { errors.remove(id); views[id]?.reload() })
        if (profile != null) {
            // before anything else touches the WebView; never fall back to the shared profile
            check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { "incognito needs WebView profiles" }
            ProfileStore.getInstance().getOrCreateProfile(profile)
            WebViewCompat.setProfile(wv, profile)
            loadedProfiles += profile
            viewProfile[id] = profile
            // no autofill service (password managers): nothing typed in incognito is saved or filled from outside
            wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
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
        val ws = wv.settings
        // No "X-Requested-With: <app id>" header where WebView allows turning it off (it marks requests as coming
        // from an embedded WebView). WebView 155 doesn't offer this, so there the header is still sent.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) WebSettingsCompat.setRequestedWithHeaderOriginAllowList(ws, emptySet())
        // Passkeys / security keys (WebAuthn) through Android's Credential Manager, as a browser.
        // Incognito: signing in with a passkey works, creating one doesn't (it would be saved to the password manager
        // for good). Without document-start scripts that can't be enforced, so passkeys stay off there.
        val incognito = isIncognito(id)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION) && (!incognito || WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))) {
            WebSettingsCompat.setWebAuthenticationSupport(ws, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
            if (incognito) WebViewCompat.addDocumentStartJavaScript(wv, NO_PASSKEY_CREATION_JS, setOf("*"))
        }
        // Payment Request API (e.g. Google Pay on checkout pages).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) WebSettingsCompat.setPaymentRequestEnabled(ws, true)
        // window.print() and navigator.share for pages (see BRIDGE_JS).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(wv, "miBridge", setOf("*"), bridge(id))
            WebViewCompat.addDocumentStartJavaScript(wv, BRIDGE_JS, setOf("*"))
        }
        if (timersPaused && appVisible) { wv.resumeTimers(); timersPaused = false } // paused while every WebView was gone
        wv.webViewClient = Client(id)
        wv.webChromeClient = Chrome(id)
        wv.setOnScrollChangeListener { _, _, y, _, _ -> if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.Scroll(y / density)) }
        wv.setOnLongClickListener { onLongPress(wv) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, length -> download(wv, id, url, userAgent, contentDisposition, mimetype, length) }
        return wv
    }

    /** Messages from BRIDGE_JS. Only the main frame may print or share, at most once every 1.5 s. */
    private fun bridge(id: String) = WebViewCompat.WebMessageListener { view, message, _, isMainFrame, _ ->
        if (!isMainFrame) return@WebMessageListener
        val now = SystemClock.uptimeMillis()
        if (now - lastBridgeAction < 1500) return@WebMessageListener
        // the page-side gesture check can be bypassed by calling miBridge directly; require a real touch in mi
        if (now - lastUserTouch > 5000) return@WebMessageListener
        val msg = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return@WebMessageListener
        lastBridgeAction = now
        when (msg.optString("type")) {
            "print" -> print(id, view)
            "passkeyBlocked" -> Toast.makeText(activity, "Passkeys can't be created in incognito", Toast.LENGTH_LONG).show()
            "share" -> {
                val text = listOf(msg.optString("text"), msg.optString("url")).filter { it.isNotBlank() }.joinToString("\n")
                if (text.isNotEmpty()) shareText(activity, text, msg.optString("title").ifBlank { null })
            }
        }
    }

    private fun print(id: String, wv: WebView) {
        if (id in printing) return
        val title = wv.title?.takeIf { it.isNotBlank() } ?: "mi"
        printing += id
        val adapter = KeepAlivePrintAdapter(wv.createPrintDocumentAdapter(title)) {
            printing -= id
            if (closeAfterPrint.remove(id)) destroy(id) // the page closed its window while printing
        }
        runCatching { (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, adapter, null) }
            .onFailure { printing -= id }
    }

    /** Delegates to WebView's adapter and reports when the print job no longer needs the WebView (onFinish). */
    private class KeepAlivePrintAdapter(private val inner: PrintDocumentAdapter, private val done: () -> Unit) : PrintDocumentAdapter() {
        override fun onStart() = inner.onStart()
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) =
            inner.onLayout(old, new, signal, cb, extras)
        override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, signal: CancellationSignal?, cb: WriteResultCallback) =
            inner.onWrite(pages, dest, signal, cb)
        override fun onFinish() { inner.onFinish(); done() }
    }

    /** A page tried to open a pop-up without a tap: blocked, with a way to allow the site (then the user retries). */
    private fun popupBlocked(id: String, site: String, page: String?) {
        if (site.isEmpty() || id != vm.activeTabId || "$site|$page" == lastPopupNotice) return
        lastPopupNotice = "$site|$page"
        vm.showAlert(
            "Pop-up blocked",
            "$site tried to open a new window without a tap. If you were expecting one (a sign-in or payment window), allow pop-ups for this site and try again.",
            if (isIncognito(id)) listOf(AlertButton("Keep blocking", "cancel"), AlertButton("Allow in this tab") { incognitoPopupSites += "${scope(id)}|$site" })
            else listOf(AlertButton("Keep blocking", "cancel"), AlertButton("Always allow") { vm.allowPopups(site) }),
            onCancel = {},
        )
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
            if (request.isForMainFrame) mainNav[id] = url
            val gesture = request.hasGesture()
            if (url.startsWith("blob:") || url.startsWith("data:")) return false
            if (url.startsWith("mibrowser:")) { if (gesture) vm.handleIncomingUrl(url, incognito = isIncognito(id)); return true }
            if (vm.settings.httpsOnly && url.startsWith("http://") && request.isForMainFrame && !isLocalHost(request.url.host)) {
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
            dialogsBlocked.remove(id); dialogTimes.remove(id)
            nav(id, view, url, true)
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadStart)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            fetchTouchIcon(view, url)
            authAnswered.removeAll { true }
            if (errors[id] == null) vm.clearHttpFallback(id) // https worked: later failures in this tab are real errors
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
            // like Chrome's HTTPS upgrades: any failure of the automatic https attempt retries over http, except when
            // the name doesn't resolve (http would fail too) or Safe Browsing blocked the page (never routed around)
            val httpMightWork = error.errorCode != ERROR_HOST_LOOKUP && error.errorCode != ERROR_UNSAFE_RESOURCE
            if (httpMightWork && fallBackToHttp(request.url.toString())) return
            errors[id] = WebError(error.errorCode, error.description?.toString() ?: "Unknown error", request.url.toString())
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadEnd)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = displayHost(error.url)
            if (vm.isCertException(profileOf(id), host)) { handler.proceed(); return }
            handler.cancel()
            val mainFrame = view.url == null || view.url.equals(error.url, ignoreCase = true)
            if (mainFrame && fallBackToHttp(error.url)) return
            if (view.url.equals(error.url, ignoreCase = true) || view.url == null) {
                errors[id] = WebError(-1200, "ERR_SSL_PROTOCOL_ERROR", error.url)
                if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadEnd)
            }
        }

        /**
         * The page's high-resolution icon (apple-touch-icon / sized icon links), read from its own HTML once it has
         * loaded. The site it is stored for comes from WebView ([url]), never from the page's script, and the result is
         * dropped if the tab has navigated away meanwhile, so a page can only ever set its own icon.
         */
        private fun fetchTouchIcon(view: WebView, url: String?) {
            if (isIncognito(id)) return // nothing from incognito is stored
            val host = faviconHost(url) ?: return
            if (view.url != url) return
            if (!vm.settings.jsEnabled) { Favicons.upgrade(host); return }
            view.evaluateJavascript(TOUCH_ICON_JS) { result ->
                if (view.url != url) return@evaluateJavascript
                val href = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
                if (href != null) Favicons.fetchFor(host, href) else Favicons.upgrade(host)
            }
        }

        /** A typed address (no scheme) failed over https: retry it over http, once, unless HTTPS-Only is on. */
        private fun fallBackToHttp(url: String?): Boolean {
            val t = vm.tabs.firstOrNull { it.id == id } ?: return false
            if (!t.httpFallback || vm.settings.httpsOnly || url == null || !url.startsWith("https://")) return false
            vm.loadInTab(id, "http://" + url.removePrefix("https://")) // also clears the fallback flag: only once
            return true
        }

        /** HTTP authentication (Basic/Digest): ask for credentials; remembered for this session, re-asked if rejected. */
        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            val key = "${scope(id)}|$host|$realm"
            val known = httpAuth[key]
            if (known != null && key !in authAnswered) { authAnswered += key; handler.proceed(known.first, known.second); return }
            httpAuth.remove(key)
            // the challenge doesn't say http or https: use this tab's navigation to that host (else the current page)
            val nav = mainNav[id]?.takeIf { displayHost(it).equals(host, ignoreCase = true) } ?: view.url
            val insecure = nav?.startsWith("http://", ignoreCase = true) == true && !isLoopback(host)
            vm.authRequest = AuthRequest(
                host, realm, insecure,
                onSubmit = { user, pass -> httpAuth[key] = user to pass; authAnswered += key; handler.proceed(user, pass) },
                onCancel = { handler.cancel() },
            )
        }

        /** A site asked for a client certificate (corporate / development servers): let the user pick one from the device. */
        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            KeyChain.choosePrivateKeyAlias(activity, { alias ->
                if (alias == null) { request.cancel(); return@choosePrivateKeyAlias }
                Thread {
                    runCatching {
                        val key = KeyChain.getPrivateKey(activity, alias)
                        val chain = KeyChain.getCertificateChain(activity, alias)
                        if (key != null && chain != null) request.proceed(key, chain) else request.cancel()
                    }.onFailure { request.cancel() }
                }.start()
            }, request.keyTypes, request.principals, request.host, request.port, null)
        }
    }

    private inner class Chrome(val id: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (id == vm.activeTabId) vm.ui.tryEmit(UiEvent.LoadProgress(newProgress / 100f))
        }

        /**
         * Site icons come from the page itself and are kept on the device (see Favicons). The icon is paired with the
         * page through WebView's history entry, not view.url: around a navigation the previous page's icon can arrive
         * after the address has already changed.
         */
        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if (isIncognito(id)) return // nothing from incognito is stored
            val item = view.copyBackForwardList().currentItem ?: return
            val host = faviconHost(item.url) ?: return
            item.favicon?.let { Favicons.save(host, it) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            nav(id, view, view.url, vm.tabs.firstOrNull { it.id == id }?.loading ?: false)
        }

        /**
         * window.open / target=_blank: a real window, shown as a new tab linked to this one. The page keeps
         * window.opener and postMessage to it (sign-in and payment pop-ups such as PayPal depend on that).
         * Pop-ups without a tap are blocked unless the user allowed the site.
         */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val site = displayHost(view.url)
            // the saved allow-list is normal-mode data: incognito tabs only use what was allowed in them
            val allowed = if (isIncognito(id)) "${scope(id)}|$site" in incognitoPopupSites else site in vm.settings.popupSites
            if (!isUserGesture && !allowed) { popupBlocked(id, site, view.url); return false }
            val opener = vm.tabs.firstOrNull { it.id == id }
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
            if (dialogAllowed(id)) return false
            result.confirm(); return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (dialogAllowed(id)) return false
            result.cancel(); return true
        }

        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
            if (dialogAllowed(id)) return false
            result.cancel(); return true
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
            if (wanted.all { "${scope(id)}|$host|$it" in sitePermissions }) { grantMedia(request); return }
            val what = wanted.joinToString(" and ") { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }
            vm.showAlert(
                "Allow $what?", "$host wants to use your $what.",
                listOf(
                    AlertButton("Block", "cancel") { runCatching { request.deny() } },
                    AlertButton("Allow") { wanted.forEach { sitePermissions += "${scope(id)}|$host|$it" }; grantMedia(request) },
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
            if ("${scope(id)}|$host|location" in sitePermissions) { grantLocation(); return }
            vm.showAlert(
                "Allow location?", "$host wants to know your location.",
                listOf(
                    AlertButton("Block", "cancel") { callback.invoke(origin, false, false) },
                    AlertButton("Allow") { sitePermissions += "${scope(id)}|$host|location"; grantLocation() },
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
        vm.showAlert(
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
        if (host.isNotEmpty()) vm.trustHost(profileOf(id), host)
        errors.remove(id)
        views[id]?.reload()
    }

    // ---------------------------------------------------------------- downloads (RNWV DownloadListener)
    private fun download(wv: WebView, id: String, url: String, userAgent: String, contentDisposition: String?, mimetype: String?, length: Long) {
        val req = try { DownloadManager.Request(Uri.parse(url)) } catch (_: IllegalArgumentException) { return }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype).replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        try {
            val u = URL(url)
            cookies(wv).getCookie("${u.protocol}://${u.host}")?.let { req.addRequestHeader("Cookie", it) }
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
        vm.showAlert(
            "Download file?", "$fileName$size\nfrom ${displayHost(url)}" +
                if (isIncognito(id)) "\n\nThe file is saved to your Downloads folder and stays after you close this incognito tab." else "",
            listOf(AlertButton("Cancel", "cancel"), AlertButton("Download") { start() }),
            onCancel = {},
        )
    }

    // ---------------------------------------------------------------- print / previews
    fun capturePreview(id: String) {
        val wv = views[id]
        val tab = vm.tabs.firstOrNull { it.id == id }
        if (tab == null || tab.incognito) return // incognito tabs are never photographed: no snapshot in memory or on disk
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

    // last, so every property it touches is initialized: anything left from a previous run (e.g. the app was
    // killed with incognito tabs open) is deleted
    init { releaseProfiles() }
}

/**
 * mi's WebView. Incognito: page text fields ask the keyboard app not to learn from what's typed (like Chrome's).
 *
 * Pull to refresh, only from the very top of the page:
 *  - the touch must start with the page scrolled to the top ([getScrollY] == 0), so reaching the top mid-gesture
 *    never counts;
 *  - Chromium must report the pull as over-scroll at the top ([onOverScrolled] clampedY at 0), i.e. nothing on the
 *    page (including an inner scrolling panel) can scroll further up;
 *  - and it must do so within the first [ARM_WINDOW_DP] of downward travel: a panel scrolled partway down scrolls
 *    first, so its over-scroll arrives too late and the pull never arms.
 * Released past [PULL_DP] of travel after arming (half-speed, with resistance) reloads.
 */
@SuppressLint("ViewConstructor")
private class MiWebView(
    ctx: Context, private val incognito: Boolean, private val pullEnabled: () -> Boolean,
    private val onPull: (Float) -> Unit, private val onRefresh: () -> Unit,
) : WebView(ctx) {
    private val density = ctx.resources.displayMetrics.density
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var startedAtTop = false
    private var armedAt = Float.NaN
    private var progress = 0f

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? =
        super.onCreateInputConnection(outAttrs).also { if (incognito) outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; armedAt = Float.NaN
                startedAtTop = scrollY == 0 && pullEnabled()
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> disarm() // pinch zoom
            android.view.MotionEvent.ACTION_MOVE -> {
                lastX = e.x; lastY = e.y
                if (!armedAt.isNaN()) {
                    progress = ((e.y - armedAt) * 0.5f / (PULL_DP * density)).coerceIn(0f, 1.4f)
                    onPull(progress)
                }
            }
            android.view.MotionEvent.ACTION_UP -> { val go = !armedAt.isNaN() && progress >= 1f; disarm(); if (go) onRefresh() }
            android.view.MotionEvent.ACTION_CANCEL -> disarm()
        }
        return super.onTouchEvent(e)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (!clampedY || scrollY != 0 || !startedAtTop || !armedAt.isNaN()) return
        val dy = lastY - downY
        val dx = kotlin.math.abs(lastX - downX)
        // downward, mostly vertical, and right at the start of the pull
        if (dy > 0 && dy > dx && dy <= slop + ARM_WINDOW_DP * density) armedAt = lastY
        else startedAtTop = false // the page scrolled first: this gesture never refreshes
    }


    private fun disarm() {
        startedAtTop = false
        if (!armedAt.isNaN() || progress > 0f) { armedAt = Float.NaN; progress = 0f; onPull(0f) }
    }

    private companion object {
        const val ARM_WINDOW_DP = 24f
        const val PULL_DP = 72f
    }
}

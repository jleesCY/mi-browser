// A home-screen pin opened as an app (PinDialog's Open as App): its own window, with its own card in Recents (the pin's
// name, the site's icon and colour), running beside mi's browser window. Just the page: no Pill, no tabs. It shares mi's
// cookies (you're signed in as in mi), blocks ads as mi does and colours the status bar like the site. Links to other
// sites and downloads go to mi's browser window. Pages here aren't added to mi's history, as an installed app's
// wouldn't be. Tapping the same pin again comes back to this window (one window per address: documentLaunchMode).
package com.jlees.mibrowser.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.adblock.AdBlock
import com.jlees.mibrowser.adblock.ContentBlocker
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.data.darkFor
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.web.FileChooser
import com.jlees.mibrowser.web.PageScripts
import com.jlees.mibrowser.web.PageTimers
import com.jlees.mibrowser.web.UserAgents
import kotlinx.coroutines.launch
import java.io.File

class AppWindow : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var start: String
    private lateinit var title: String
    /** mi's settings as saved (this window doesn't change them; it reads them when it opens). */
    private val settings by lazy { JsonStore(File(filesDir, "store")).read("settings", Settings.serializer()) ?: Settings() }
    private val scripts by lazy { PageScripts(this) }
    private val blocker by lazy { ContentBlocker({ settings }) }
    private var video: View? = null
    private var videoDone: WebChromeClient.CustomViewCallback? = null

    // file uploads (FileChooser, shared with the browser window)
    private var resultCallback: ((ActivityResult) -> Unit)? = null
    private val results = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> resultCallback?.invoke(r); resultCallback = null }
    private val files by lazy { FileChooser(this) { intent, cb -> resultCallback = cb; results.launch(intent) } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        start = intent.dataString ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: displayHost(start)
        if (AdBlock.ads == null) lifecycleScope.launch { AdBlock.load(this@AppWindow) } // mi's browser window may not have run yet

        // pages see light or dark (prefers-color-scheme) from the theme they're made with, as in the browser window
        val themed = android.view.ContextThemeWrapper(this, if (settings.darkFor(resources.configuration)) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        web = WebView(themed)
        with(web.settings) {
            javaScriptEnabled = settings.jsEnabled
            domStorageEnabled = true
            @Suppress("DEPRECATION") databaseEnabled = true
            setSupportMultipleWindows(false) // a link opening a new window opens here (or, for another site, in mi)
            builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            textZoom = settings.textZoom
            mediaPlaybackRequiresUserGesture = settings.blockAutoplay
        }
        UserAgents(this).apply(web.settings, settings.desktopMode)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !settings.blockCookies && !settings.blockThirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings, settings.darkenPages)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            blocker.attach(web, KEY, scripts.cosmeticFilter) // ad and cookie-banner hiding, as in mi
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        // downloads: mi's browser window downloads them (its downloader, its Downloads page)
        web.setDownloadListener { url, _, _, _, _ -> toMi(url) }

        // the page between the status and navigation bars; the space behind them in the site's colour (set as it loads)
        root = FrameLayout(this).apply { addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        // turns with the phone, as mi does, unless Rotate Screen is off
        requestedOrientation = if (settings.rotateScreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        colour(if (settings.darkFor(resources.configuration)) Color.BLACK else Color.WHITE)

        // Back goes back through the page; at its first page the window goes to the background, kept in Recents (as apps do)
        onBackPressedDispatcher.addCallback(this) {
            when {
                video != null -> videoDone?.onCustomViewHidden()
                web.canGoBack() -> web.goBack()
                else -> moveTaskToBack(true)
            }
        }
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(start)
    }

    /** The site's [color]: behind the status and navigation bars (their icons dark or light to read on it) and on its Recents card. */
    private fun colour(color: Int) {
        val solid = color or (0xFF shl 24)
        root.setBackgroundColor(solid)
        val light = ColorUtils.calculateLuminance(solid) > 0.5
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        @Suppress("DEPRECATION") setTaskDescription(ActivityManager.TaskDescription(title, Favicons.bitmapFor(start), solid))
    }

    /** Opens [url] in mi's browser window (another site, a download). */
    private fun toMi(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this, MainActivity::class.java)) }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // a link you tapped to another site: mi's browser window (sign-ins stay here: leavesApp)
                if (request.isForMainFrame && leavesApp(start, url, request.hasGesture(), request.isRedirect)) { toMi(url); return true }
                return false
            }
            // other apps (tel:, mailto:, maps, intent:…), only from a tap, and only to apps that open web links' kind
            if (request.hasGesture()) runCatching {
                val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, request.url)
                startActivity(intent.addCategory(Intent.CATEGORY_BROWSABLE).apply { component = null; selector = null })
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            blocker.pageStarted(KEY, url?.let { Uri.parse(it).host })
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = blocker.intercept(KEY, request)

        override fun onPageFinished(view: WebView, url: String?) {
            if (!settings.jsEnabled) return
            view.evaluateJavascript(scripts.appColor) { result ->
                appColorOf(result)?.let(::colour)
            }
            if (settings.darkenPages) view.evaluateJavascript(scripts.darkFallback, null)
        }

        // the page's renderer went away: the window starts over with its page
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { recreate(); return true }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) = files.show(callback, params)

        // full-screen video: over the page, the bars hidden
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            video = view; videoDone = callback
            (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }

        override fun onHideCustomView() {
            video?.let { (window.decorView as ViewGroup).removeView(it) }
            video = null; videoDone = null
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
        // camera, microphone and location requests are declined (WebChromeClient's default) for now
    }

    override fun onStart() { super.onStart(); PageTimers.appWindows++; PageTimers.apply(web) }
    override fun onStop() { PageTimers.appWindows--; PageTimers.apply(web); super.onStop() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); CookieManager.getInstance().flush(); super.onPause() } // a sign-in here survives mi closing
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); web.saveState(outState) }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }

    companion object {
        /** The pin's name, for the window's Recents card. */
        const val EXTRA_TITLE = "title"
        /** The content blocker's name for this window's page (it keeps one per tab in the browser window). */
        private const val KEY = "app-window"
    }
}

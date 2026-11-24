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
    /** Files the page makes itself, saved into Downloads (mi's Downloads page lists them once mi next has them: not here). */
    private val blobs by lazy {
        com.jlees.mibrowser.downloads.BlobDownloads(this, { settings.downloadFolder },
            onSaved = { android.widget.Toast.makeText(this, "Saved ${it.fileName} to Downloads", android.widget.Toast.LENGTH_SHORT).show() },
            onFailed = { android.widget.Toast.makeText(this, "Couldn't save $it", android.widget.Toast.LENGTH_SHORT).show() })
    }
    private lateinit var pull: android.widget.ImageView
    private var video: View? = null
    private var videoDone: WebChromeClient.CustomViewCallback? = null

    // permissions a page asks for: what this window was allowed (until it closes), and Android's own prompt
    private val allowed = HashSet<String>()
    private var onAndroid: ((Boolean) -> Unit)? = null
    private val askAndroid = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> onAndroid?.invoke(r.values.any { it }); onAndroid = null }

    /** Asks whether the site may use [what] (once a window), then Android's [perms] if mi hasn't got them; [grant] or [deny]. */
    private fun ask(what: String, perms: Array<String>, grant: () -> Unit, deny: () -> Unit) {
        val go = {
            val missing = perms.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) grant() else { onAndroid = { ok -> if (ok) grant() else deny() }; askAndroid.launch(missing.toTypedArray()) }
        }
        if (what in allowed) { go(); return }
        android.app.AlertDialog.Builder(this).setTitle(title).setMessage("Allow this site to use your $what?")
            .setPositiveButton("Allow") { _, _ -> allowed += what; go() }
            .setNegativeButton("Don't Allow") { _, _ -> deny() }
            .setOnCancelListener { deny() }.show()
    }

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
        // mi's WebView: pull down from the top of the page to reload (a page that didn't load has no other way back)
        web = com.jlees.mibrowser.web.MiWebView(themed, incognito = false, pullEnabled = { settings.pullToRefresh },
            onPull = { p -> pull.alpha = p.coerceAtMost(1f); pull.translationY = p * 48 * resources.displayMetrics.density; pull.rotation = p * 270f },
            onRefresh = { web.reload() })
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
            { blocker.attach(web, KEY, scripts.cosmeticFilter, scripts.scriptlets); blobs.attach(web, scripts.blobDownload) } // ad hiding, and files the page makes
        web.setOnLongClickListener { linkMenu() }
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        // downloads: a file the page made itself is handed over and saved here; anything else, mi's browser window
        // downloads (its downloader and Downloads page)
        web.setDownloadListener { url, _, _, _, _ -> if (url.startsWith("blob:") || url.startsWith("data:")) blobs.fetch(web, url, false) else toMi(url) }

        // the page between the status and navigation bars; the space behind them in the site's colour (set as it loads)
        root = FrameLayout(this).apply { addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        pull = pullMark(settings.darkFor(resources.configuration))
        root.addView(pull, FrameLayout.LayoutParams(dp(40), dp(40), android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        turn(fullscreen = false)
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

    /** Turns with the phone, as mi does, unless Rotate Screen is off; full-screen video always turns, as in mi. */
    private fun turn(fullscreen: Boolean) {
        requestedOrientation = if (fullscreen || settings.rotateScreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** The refresh mark a pull shows: a round button-like mark, hidden until the page is pulled. */
    private fun pullMark(dark: Boolean) = android.widget.ImageView(this).apply {
        setImageResource(com.jlees.mibrowser.R.drawable.sym_refresh)
        setColorFilter(if (dark) 0xFFA8C7FA.toInt() else 0xFF0B57D0.toInt())
        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (dark) 0xFF2B2C33.toInt() else Color.WHITE) }
        setPadding(dp(8), dp(8), dp(8), dp(8)); elevation = dp(4).toFloat(); alpha = 0f
    }

    /** The site's [color]: behind the status and navigation bars (their icons dark or light to read on it) and on its Recents card. */
    private fun colour(color: Int) {
        val solid = color or (0xFF shl 24)
        root.setBackgroundColor(solid)
        val light = ColorUtils.calculateLuminance(solid) > 0.5
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        @Suppress("DEPRECATION") setTaskDescription(ActivityManager.TaskDescription(title, icon, solid))
    }

    /**
     * The site's icon for the Recents card, read from its stored file: mi's icon list (Favicons) is only loaded once
     * the browser window has run, which it may not have (the pin opened with mi closed). Else mi's own.
     */
    private val icon by lazy {
        com.jlees.mibrowser.logic.faviconHost(start)?.let { host -> runCatching { android.graphics.BitmapFactory.decodeFile(File(filesDir, "favicons/$host.png").path) }.getOrNull() }
    }

    /** A link held: open it in mi, copy it or share it. False (the page's own long-press) for anything else. */
    private fun linkMenu(): Boolean {
        val hit = web.hitTestResult
        val url = hit.extra?.takeIf { hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && (it.startsWith("http://") || it.startsWith("https://")) } ?: return false
        android.app.AlertDialog.Builder(this).setTitle(displayHost(url)).setItems(arrayOf("Open in mi", "Copy Link", "Share")) { _, i ->
            when (i) {
                0 -> toMi(url)
                1 -> getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Link", url))
                else -> runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)) }
            }
        }.show()
        return true
    }

    /** Opens [url] in mi's browser window (another site, a download). */
    private fun toMi(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this, MainActivity::class.java)) }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // a link you tapped to another site: mi's browser window (sign-ins stay here: leavesApp)
                if (request.isForMainFrame && leavesApp(start, view.url, url, request.hasGesture(), request.isRedirect)) { toMi(url); return true }
                return false
            }
            // other apps (tel:, mailto:, maps, intent:…), only from a tap, and only to apps that open web links' kind
            if (request.hasGesture()) {
                val intent = runCatching { if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, request.url) }.getOrNull()
                val opened = intent != null && runCatching { startActivity(Intent(intent).addCategory(Intent.CATEGORY_BROWSABLE).apply { component = null; selector = null }) }.isSuccess
                // the app isn't there: the web address the link gives for that case, if any
                if (!opened) intent?.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { view.loadUrl(it) }
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
            turn(fullscreen = true)
        }

        override fun onHideCustomView() {
            video?.let { (window.decorView as ViewGroup).removeView(it) }
            video = null; videoDone = null
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            turn(fullscreen = false)
        }

        // camera and microphone (a call, a scanner): asked once per window, then Android's own permission if mi lacks it
        override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
            val cam = android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources
            val mic = android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
            val grantable = request.resources.filter { it == android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE }.toTypedArray()
            if (grantable.isEmpty()) { request.deny(); return }
            val what = when { cam && mic -> "camera and microphone"; cam -> "camera"; else -> "microphone" }
            val perms = listOfNotNull(android.Manifest.permission.CAMERA.takeIf { cam }, android.Manifest.permission.RECORD_AUDIO.takeIf { mic }).toTypedArray()
            runOnUiThread { ask(what, perms, { request.grant(grantable) }, { request.deny() }) }
        }

        // location (a map, a store finder): the same
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
            ask("location", arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                { callback.invoke(origin, true, false) }, { callback.invoke(origin, false, false) })
        }
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

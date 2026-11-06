package com.jlees.mibrowser

import androidx.compose.runtime.CompositionLocalProvider
import com.jlees.mibrowser.ui.common.Haptics
import com.jlees.mibrowser.ui.common.LocalHaptics

import com.jlees.mibrowser.incognito.closeIncognitoTabs

import com.jlees.mibrowser.incognito.IncognitoNotice

import com.jlees.mibrowser.launcher.LauncherShortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.jlees.mibrowser.ui.App
import com.jlees.mibrowser.web.WebViewHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels()
    private lateinit var host: WebViewHost

    private var permCallback: ((Map<String, Boolean>) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permCallback?.invoke(r); permCallback = null
    }
    private var resultCallback: ((ActivityResult) -> Unit)? = null
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        resultCallback?.invoke(r); resultCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !vm.ready }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        com.jlees.mibrowser.data.Favicons.init(applicationContext)
        // disk cache for HttpURLConnection (favicons, suggestions): no re-downloading after every launch
        if (android.net.http.HttpResponseCache.getInstalled() == null)
            runCatching { android.net.http.HttpResponseCache.install(java.io.File(cacheDir, "http"), 16L * 1024 * 1024) }

        host = WebViewHost(
            activity = this, vm = vm,
            requestPermissions = { perms, cb -> permCallback = cb; permLauncher.launch(perms) },
            launchForResult = { intent, cb -> resultCallback = cb; resultLauncher.launch(intent) },
        )
        lifecycleScope.launch { vm.web.collect { host.handle(it) } }
        vm.capturePreview = host::capturePreview

        IncognitoNotice.update(this, 0) // left over if the process ended while incognito tabs were open
        vm.start(if (intent?.action == Intent.ACTION_VIEW) intent.dataString else null, shortcutOf(intent))
        LauncherShortcuts.publish(this)
        if (intent?.action == ACTION_SCAN_QR) vm.qrScannerVisible = true
        if (intent?.action == ACTION_OPEN_DOWNLOADS) vm.openView(Sheet.DOWNLOADS)

        // one Haptics for the whole UI, following the Haptic Feedback setting
        val haptics = Haptics(window.decorView) { vm.settings.hapticFeedback }
        setContent { if (vm.ready) CompositionLocalProvider(LocalHaptics provides haptics) { App(vm, host, this) } }
    }

    override fun onPause() {
        super.onPause()
        android.webkit.CookieManager.getInstance().flush() // keep logins if Android kills the app in the background
    }

    /**
     * The display mode with the highest refresh rate (e.g. 120 Hz) at the current resolution. Some phones (OnePlus/OPPO
     * among them) keep apps at 60 Hz unless asked, so mi asks while you interact: every touch switches to this mode
     * and it is released 2.5 s after the last touch (covering flings and the animations a touch starts). Pinning it all
     * the time would keep the screen at 120 Hz while you read a still page, costing battery for nothing.
     */
    private var fastModeId = 0
    private var fastActive = false
    private val releaseFast = Runnable { setFastRefresh(false) }

    private fun preferHighestRefreshRate() {
        val display = (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return
        val current = display.mode
        fastModeId = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }?.modeId ?: 0
    }

    private fun setFastRefresh(on: Boolean) {
        fastActive = on
        val id = if (on) fastModeId else 0 // 0: no preference, the system picks (and can drop when idle)
        if (window.attributes.preferredDisplayModeId != id) window.attributes = window.attributes.apply { preferredDisplayModeId = id }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            host.noteUserTouch()
            if (!fastActive && fastModeId != 0) setFastRefresh(true)
        }
        window.decorView.removeCallbacks(releaseFast)
        window.decorView.postDelayed(releaseFast, 2500)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() { super.onStart(); host.setAppVisible(true) }
    override fun onStop() { host.setAppVisible(false); super.onStop() }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        preferHighestRefreshRate() // the mode list can differ after a resolution or display change
    }

    /** Under memory pressure keep only the visible tab's WebView; the rest are rebuilt from saved state on demand. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) host.trimTo(1)
    }

    override fun onDestroy() {
        vm.capturePreview = null // the ViewModel outlives this Activity; don't keep its host alive
        // Clear on Exit, while the WebViews still exist (their cache is cleared through them)
        if (isFinishing && vm.settings.clearDataOnExit) { vm.clearOnExit { host.handle(WebCmd.ClearBrowsingData) }; vm.saveNow() }
        host.destroyAll(finishing = isFinishing)
        if (isFinishing) host.incognito.endProcessIfUsed { vm.saveNow() }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            Intent.ACTION_VIEW -> vm.handleIncomingUrl(intent.dataString)
            ACTION_SCAN_QR -> vm.qrScannerVisible = true
            ACTION_CLOSE_INCOGNITO -> vm.closeIncognitoTabs()
            ACTION_OPEN_DOWNLOADS -> vm.openView(Sheet.DOWNLOADS)
            ACTION_NEW_TAB, ACTION_NEW_INCOGNITO, ACTION_SEARCH -> shortcutOf(intent)?.let(vm::openFromShortcut)
        }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.jlees.mibrowser.SCAN_QR"
        const val ACTION_CLOSE_INCOGNITO = "com.jlees.mibrowser.CLOSE_INCOGNITO"
        const val ACTION_OPEN_DOWNLOADS = "com.jlees.mibrowser.OPEN_DOWNLOADS"
        const val ACTION_NEW_TAB = "com.jlees.mibrowser.NEW_TAB"
        const val ACTION_NEW_INCOGNITO = "com.jlees.mibrowser.NEW_INCOGNITO"
        const val ACTION_SEARCH = "com.jlees.mibrowser.SEARCH"
    }

    private fun shortcutOf(i: Intent?) = when (i?.action) {
        ACTION_NEW_TAB -> LaunchAction.NEW_TAB; ACTION_NEW_INCOGNITO -> LaunchAction.NEW_INCOGNITO_TAB; ACTION_SEARCH -> LaunchAction.SEARCH; else -> null
    }
}

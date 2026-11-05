package com.jlees.mibrowser

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

        updateIncognitoNotice(this, 0) // left over if the process ended while incognito tabs were open
        vm.start(if (intent?.action == Intent.ACTION_VIEW) intent.dataString else null, shortcutOf(intent))
        publishShortcuts()
        if (intent?.action == ACTION_SCAN_QR) vm.qrScannerVisible = true

        setContent { if (vm.ready) App(vm, host, this) }
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
        host.destroyAll(finishing = isFinishing)
        if (isFinishing) host.endProcessIfIncognitoUsed { vm.saveNow() }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            Intent.ACTION_VIEW -> vm.handleIncomingUrl(intent.dataString)
            ACTION_SCAN_QR -> vm.qrScannerVisible = true
            ACTION_CLOSE_INCOGNITO -> vm.closeIncognitoTabs()
            ACTION_NEW_TAB, ACTION_NEW_INCOGNITO -> shortcutOf(intent)?.let(vm::openFromShortcut)
        }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.jlees.mibrowser.SCAN_QR"
        const val ACTION_CLOSE_INCOGNITO = "com.jlees.mibrowser.CLOSE_INCOGNITO"
        const val ACTION_NEW_TAB = "com.jlees.mibrowser.NEW_TAB"
        const val ACTION_NEW_INCOGNITO = "com.jlees.mibrowser.NEW_INCOGNITO"
    }

    private fun shortcutOf(i: Intent?) = when (i?.action) { ACTION_NEW_TAB -> "newTab"; ACTION_NEW_INCOGNITO -> "incognito"; else -> null }

    /**
     * Long-press the launcher icon: New tab, New incognito tab (where WebView supports profiles) and Scan QR. Published
     * at runtime so they target this build's own package (the debug build has a different one).
     */
    private fun publishShortcuts() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        fun shortcut(id: String, label: String, icon: Int, action: String) =
            android.content.pm.ShortcutInfo.Builder(this, id).setShortLabel(label).setIcon(shortcutIcon(icon))
                .setIntent(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
        val list = listOfNotNull(
            shortcut("new_tab", "New tab", R.drawable.sym_add, ACTION_NEW_TAB),
            if (vm.incognitoSupported) shortcut("new_incognito", "New incognito tab", R.drawable.sym_incognito, ACTION_NEW_INCOGNITO) else null,
            shortcut("scan_qr", "Scan QR code", R.drawable.sym_qr_code_scanner, ACTION_SCAN_QR), // same id as the old static one: pinned copies keep working
        )
        runCatching { sm.dynamicShortcuts = list }
    }

    /** A shortcut icon: the symbol on a tinted adaptive background, like the system's own shortcuts. */
    private fun shortcutIcon(res: Int): android.graphics.drawable.Icon {
        val d = resources.displayMetrics.density
        val px = (108 * d).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(0xFFD3E3FD.toInt())
        val glyph = (44 * d).toInt(); val o = (px - glyph) / 2
        getDrawable(res)!!.mutate().apply { setTint(0xFF0B57D0.toInt()); setBounds(o, o, o + glyph, o + glyph); draw(canvas) }
        return android.graphics.drawable.Icon.createWithAdaptiveBitmap(bmp)
    }
}

/**
 * While incognito tabs are open: a silent notification that closes them all. Hidden on the lock screen, never
 * bridged to other devices. Cancelled at launch too, in case the process ended while it was showing.
 */
fun updateIncognitoNotice(ctx: android.content.Context, count: Int) {
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
    if (count == 0 || !nm.areNotificationsEnabled()) { nm.cancel(INCOGNITO_NOTICE_ID); return }
    nm.createNotificationChannel(android.app.NotificationChannel("incognito", "Incognito tabs", android.app.NotificationManager.IMPORTANCE_LOW).apply {
        description = "Shown while incognito tabs are open"; setShowBadge(false)
    })
    val close = android.app.PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_CLOSE_INCOGNITO),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
    nm.notify(INCOGNITO_NOTICE_ID, android.app.Notification.Builder(ctx, "incognito")
        .setSmallIcon(R.drawable.sym_incognito)
        .setContentTitle(if (count == 1) "1 incognito tab open" else "$count incognito tabs open")
        .setContentText("Tap to close incognito tabs")
        .setContentIntent(close)
        .setVisibility(android.app.Notification.VISIBILITY_SECRET)
        .setLocalOnly(true).setOngoing(true).setShowWhen(false).setOnlyAlertOnce(true)
        .build())
}

private const val INCOGNITO_NOTICE_ID = 0x1c0

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

        vm.start(if (intent?.action == Intent.ACTION_VIEW) intent.dataString else null)
        if (intent?.action == ACTION_SCAN_QR) vm.qrScannerVisible = true

        setContent { if (vm.ready) App(vm, host, this) }
    }

    override fun onPause() {
        super.onPause()
        android.webkit.CookieManager.getInstance().flush() // keep logins if Android kills the app in the background
    }

    /** Under memory pressure keep only the visible tab's WebView; the rest are rebuilt from saved state on demand. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) host.trimTo(1)
    }

    override fun onDestroy() {
        vm.capturePreview = null // the ViewModel outlives this Activity; don't keep its host alive
        host.destroyAll()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            Intent.ACTION_VIEW -> vm.handleIncomingUrl(intent.dataString)
            ACTION_SCAN_QR -> vm.qrScannerVisible = true
        }
    }

    companion object {
        const val ACTION_SCAN_QR = "com.jlees.mibrowser.SCAN_QR"
    }
}

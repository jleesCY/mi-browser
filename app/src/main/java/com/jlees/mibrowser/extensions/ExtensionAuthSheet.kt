// identity.launchWebAuthFlow: a site's sign-in page, shown in a sheet, until it sends the person back to the
// extension's return address (identityRedirect). That address is caught before it loads and handed to the extension;
// closing the sheet answers "not approved". A flow that asks not to be interactive loads out of sight and fails if the
// site wants the person to do anything. Sign-in pages use mi's normal cookies (never incognito's), as in Chrome.
package com.jlees.mibrowser.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*

/** A WebView for [flow]'s sign-in: [finish] hears the return address it reached (once), or nothing if it never did. */
@SuppressLint("SetJavaScriptEnabled")
internal fun authView(context: Context, flow: AuthFlow, onPage: (String) -> Unit = {}, finish: (String?) -> Unit): WebView {
    var over = false
    val end = { url: String? -> if (!over) { over = true; finish(url) } }
    return WebView(context).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.allowFileAccess = false; settings.allowContentAccess = false
        // as a tab: Chrome's identity (Google and others refuse to sign in inside embedded WebViews) and the
        // third-party cookies sign-in services use
        ExtensionFlow.engine?.userAgents?.apply(settings, desktop = false)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(flow.redirect)) { end(url); return true }
                // only web addresses: a sign-in page can't open other apps from here
                return !(url.startsWith("https://") || url.startsWith("http://"))
            }
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean { end(null); return true }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                url ?: return
                if (url.startsWith(flow.redirect)) { view.stopLoading(); end(url) } else onPage(url)
            }
        }
        loadUrl(flow.url)
    }
}

/** A flow with interactive: false: loaded out of sight; it fails if the site doesn't send the person back by itself. */
internal fun silentAuth(context: Context, flow: AuthFlow) {
    val main = Handler(Looper.getMainLooper())
    lateinit var wv: WebView
    val timeout = Runnable { wv.destroy(); flow.done(null) }
    wv = authView(context, flow) { url -> main.removeCallbacks(timeout); main.post { wv.destroy() }; flow.done(url) }
    main.postDelayed(timeout, 15_000)
}

@Composable
fun ExtensionAuthSheet() {
    val ui = LocalUi.current
    val flow = ExtensionFlow.auth
    var shown by remember { mutableStateOf(flow) }
    if (flow != null) shown = flow
    val f = shown ?: return
    val engine = ExtensionFlow.engine ?: return
    var host by remember(f) { mutableStateOf(displayHost(f.url)) }
    ModalFrame(flow != null, onDismiss = { f.done(null) }, dismissOnScrim = false) {
        val h = LocalConfiguration.current.screenHeightDp.dp * 0.85f
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth().padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(ui.cs.surfaceContainerHigh).blockTouches(),
        ) {
            // under the title, the site really showing, so the person knows who they're signing in to
            ExtensionSheetHeader(f.ext, "Sign In for ${engine.infos[f.ext]?.name ?: "Extension"}", host) { f.done(null) }
            key(f) {
                AndroidView(
                    factory = { ctx -> authView(ctx, f, onPage = { host = displayHost(it) }) { url -> f.done(url) } },
                    onRelease = { (it as? WebView)?.destroy() },
                    modifier = Modifier.fillMaxWidth().height(h).padding(bottom = 8.dp).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                )
            }
        }
    }
}

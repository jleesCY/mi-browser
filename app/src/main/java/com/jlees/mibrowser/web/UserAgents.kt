package com.jlees.mibrowser.web

import android.content.Context
import android.os.Build
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebSettings

/**
 * mi's identity: Chrome of the same version (what Chrome sends), not an embedded WebView. WebView's own UA ends in
 * "; wv) ... Version/4.0" and its client hints list an "Android WebView" brand; sites like PayPal and Google sign-in
 * refuse embedded WebViews, so neither is sent.
 */
class UserAgents(private val ctx: Context) {
    private val chromeVersion: String by lazy {
        runCatching { Regex("Chrome/([\\d.]+)").find(WebSettings.getDefaultUserAgent(ctx))?.groupValues?.get(1) }.getOrNull() ?: "140.0.0.0"
    }
    private val chromeMajor get() = chromeVersion.substringBefore('.')
    private val mobileUa by lazy { "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Mobile Safari/537.36" }
    private val desktopUa by lazy { "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Safari/537.36" }

    private fun metadata(desktop: Boolean): UserAgentMetadata {
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

    /** Mobile or [desktop] identity for [ws] (UA string and client hints); no-op if it already has it. */
    fun apply(ws: WebSettings, desktop: Boolean) {
        val ua = if (desktop) desktopUa else mobileUa
        if (ws.userAgentString == ua) return
        ws.userAgentString = ua
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) WebSettingsCompat.setUserAgentMetadata(ws, metadata(desktop))
    }
}

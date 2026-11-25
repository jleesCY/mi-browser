// Where ad blocking meets WebView: sub-resource requests (tabs and service workers) and element hiding on pages.
package com.jlees.mibrowser.adblock

import android.webkit.ServiceWorkerClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.data.Settings
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * @param settings the current settings (what to block, allowed sites); read on WebView threads
 * @param sessionAllowed more sites with blocking off for tab [tabId] only: an incognito tab's, kept in memory
 */
class ContentBlocker(private val settings: () -> Settings, private val sessionAllowed: (tabId: String) -> Collection<String> = { emptyList() }) {
    /** The settings for tab [tabId]: the saved allow-list plus the tab's session one. */
    private fun settingsFor(tabId: String): Settings {
        val s = settings(); val extra = sessionAllowed(tabId)
        return if (extra.isEmpty()) s else s.copy(adAllowlist = s.adAllowlist + extra)
    }

    /** Each tab's page host, for first/third-party and `$domain=` rules; written and read on WebView threads. */
    private val pageHosts = ConcurrentHashMap<String, String>()

    /** Tab [tabId] started loading a page on [host] (also pages restored from history or cache). */
    fun pageStarted(tabId: String, host: String?) { if (host != null) pageHosts[tabId] = host; BlockedCounts.pageStarted(tabId) }
    fun forget(tabId: String) { pageHosts.remove(tabId); BlockedCounts.forget(tabId) }

    /** WebViewClient.shouldInterceptRequest for tab [tabId]: an empty response for a blocked sub-resource. */
    fun intercept(tabId: String, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) { pageStarted(tabId, request.url.host); return null }
        val host = request.url.host ?: return null
        if (!AdBlock.shouldBlock(request.url.toString(), host, pageHosts[tabId], request.requestHeaders["Accept"], settingsFor(tabId))) return null
        BlockedCounts.blocked(tabId)
        return blocked()
    }

    /** Service workers fetch outside any tab: their requests are filtered without a page. */
    private fun interceptWorker(request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host ?: return null
        return if (!request.isForMainFrame && AdBlock.shouldBlock(request.url.toString(), host, null, request.requestHeaders["Accept"], settings())) blocked() else null
    }

    /** For incognito profiles, whose service-worker controller takes the platform client type. */
    val profileWorkers = object : ServiceWorkerClient() {
        override fun shouldInterceptRequest(request: WebResourceRequest) = interceptWorker(request)
    }

    /** Filters the normal profile's service workers. */
    fun filterServiceWorkers() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) return
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
            override fun shouldInterceptRequest(request: WebResourceRequest) = interceptWorker(request)
        })
    }

    /**
     * Element hiding on [wv]: [script] (assets/js/cosmetic-filter.js) asks, per frame, what to hide on its host;
     * a selector the page rejects is reported back ("bad:") and dropped. Needs the listener and start-script features.
     */
    fun attach(wv: WebView, tabId: String, script: String, scriptlets: String) {
        // scriptlets: each frame asks, as it starts, which run on its site (assets/js/scriptlets.js), and is answered at
        // once, before the page's own scripts; the answer is public filter-list data, so any page may ask
        val app = wv.context.applicationContext
        wv.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface fun get(host: String): String = AdBlock.scriptletsFor(host, settingsFor(tabId), app)
        }, "miScriptlets")
        WebViewCompat.addDocumentStartJavaScript(wv, scriptlets, setOf("*"))
        WebViewCompat.addWebMessageListener(wv, "miAdBlock", setOf("*")) { _, message, origin, isMainFrame, reply ->
            val host = origin.host ?: return@addWebMessageListener
            val data = message.data.orEmpty()
            if (data.startsWith("bad:")) { if (isMainFrame) AdBlock.dropInvalid(data.removePrefix("bad:")) }
            else reply.postMessage(AdBlock.cosmetics(host, isMainFrame, settingsFor(tabId)))
        }
        WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
    }

    companion object {
        /** An empty answer for a request that's dropped (price checks drop requests with it too). */
        fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}

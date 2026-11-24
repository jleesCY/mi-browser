// Sites whose security policy (Content-Security-Policy) blocks what extensions add to their pages: a browser exempts
// extensions from a site's policy, but in mi their content scripts run in the page's own world, so the policy applies to
// them. Once a content script reports a refusal (ext-content.js), that site's pages are fetched by mi with the policy
// allowing extensions (allowExtensionsIn, relaxMetaPolicies), for the rest of the session. A site whose bot check turns
// mi's own fetch away is left to WebView. Called from ExtensionRequests on WebView's network threads.
package com.jlees.mibrowser.extensions

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Whether [url] is on one of [strict] (an address with no site never is). */
internal fun isStrictSite(url: String, strict: Set<String>) = originOf(url)?.let { it in strict } == true

internal class ExtensionSitePolicies(private val e: ExtensionEngine, private val net: ExtensionRequests) {
    /** Sites whose policy blocked an extension's file: their pages are fetched by mi with it allowed. */
    private val strictSites: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Sites whose bot check turned mi's own fetch away: left to WebView (their policy isn't relaxed this session). */
    private val botChecked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Tab [tabId]'s page or frame [request], fetched with its policy relaxed if its site is strict; null: left to WebView. */
    fun intercept(tabId: String, request: WebResourceRequest): WebResourceResponse? =
        if (isDocument(request) && request.method.equals("GET", true) && isStrictSite(request.url.toString(), strictSites)) relaxedPage(tabId, request) else null

    /** A page or a frame's page (a frame has its own policy: a map, an embedded player). */
    private fun isDocument(r: WebResourceRequest) = r.isForMainFrame ||
        r.requestHeaders.orEmpty().entries.any { it.key.equals("Sec-Fetch-Dest", true) && (it.value == "iframe" || it.value == "frame") }

    /**
     * Tab [tabId]'s page at [url] blocked an extension's file by its policy (a content script saw it): from now on its
     * pages come through mi with extensions allowed in that policy, and the page is loaded again once so what's on it
     * now works too.
     */
    fun siteBlocksExtensions(tabId: String, url: String?) {
        val site = url?.let(::originOf) ?: return
        if (site in botChecked) return // its bot check turned mi's own fetch away: its policy stays as it is
        if (strictSites.add(site)) e.tabView(tabId)?.takeIf { it.url?.let(::originOf) == site }?.reload()
    }

    /** [headers] with any Content-Security-Policy allowing extensions' files, for a page of a strict site. */
    fun relaxed(url: String, headers: List<Pair<String, String>>) =
        if (!isStrictSite(url, strictSites)) headers
        else headers.map { (k, v) -> if (k.equals("Content-Security-Policy", true)) k to allowExtensionsIn(v) else k to v }

    /** Its bot check doesn't trust mi's own connection: WebView loads its pages itself from now on. */
    fun leaveToWebView(url: String) { originOf(url)?.let { botChecked += it; strictSites -= it } }

    /** Whether [url]'s site turned mi's own connection away (a bot check): its requests are left to WebView. */
    fun botChecked(url: String) = originOf(url)?.let { it in botChecked } == true

    /** A strict site's page, fetched by mi with its policy allowing extensions' files (null: WebView loads it after all). */
    private fun relaxedPage(tabId: String, request: WebResourceRequest): WebResourceResponse? = runCatching {
        val url = request.url.toString()
        val cookies = net.cookiesOf(tabId) ?: CookieManager.getInstance()
        val cookie = cookies.getCookie(url)?.takeIf { it.isNotBlank() && !e.vm.settings.blockCookies }
        // no Accept-Encoding of WebView's: the connection then unzips the page, so its HTML can be read
        val c = net.connect(url, request.requestHeaders.orEmpty().toList().filterNot { it.first.equals("Accept-Encoding", true) } + listOfNotNull(cookie?.let { "Cookie" to it }))
        val code = c.responseCode
        val headers = net.headersOf(c)
        if (!e.vm.settings.blockCookies) headers.filter { it.first.equals("Set-Cookie", true) }.forEach { cookies.setCookie(url, it.second) }
        val location = headers.firstOrNull { it.first.equals("Location", true) }?.second
        if (code in 300..399 && location != null) { c.disconnect(); return net.redirect(URL(URL(url), location).toString(), request, "main_frame") }
        val type = c.contentType
        // a site's bot check (Cloudflare, Fastly…) may not trust mi's own connection
        if (isBotCheck(code, headers)) { leaveToWebView(url); c.disconnect(); return null }
        // a policy in the page's own HTML (a meta tag) is relaxed too
        val body = if (code == 200 && type?.contains("html", true) != false) {
            val cs = net.charsetOf(type)?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
            val html = c.inputStream.use { it.readBytes() }.toString(cs)
            if (isBotCheckPage(html)) { leaveToWebView(url); return null }
            ByteArrayInputStream(relaxMetaPolicies(html).toByteArray(cs))
        } else null
        net.response(c, code, relaxed(url, headers), type, "text/html", body)
    }.getOrNull()
}

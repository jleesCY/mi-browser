// Web requests in tabs, for extensions: chrome.webRequest (watching, blocking, redirecting and changing requests and
// their headers) and declarativeNetRequest (the same by rules). Called from each tab's WebViewClient on WebView's own
// network thread, before mi's ad blocker:
// - Only extensions with a listener or rule for the request, and permission for its site, take part.
// - declarativeNetRequest decides first; then blocking listeners are asked, all at once, each with a short time limit
//   (a slow extension can't hang the page).
// - A block answers with nothing (a page explaining it, for a page); a redirect serves the new address.
// - When an extension wants to see or change headers, mi makes the request itself (GET and HEAD; WebView never shows
//   other requests' bodies), with the tab's cookies, honouring third-party cookie blocking; anything it changes
//   (Set-Cookie included) applies as the extension asked. Everything else stays on WebView's own path.
package com.jlees.mibrowser.extensions

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.jlees.mibrowser.data.json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * What a blocked part of a page gets: a body that can't be read, so WebView fails the request as a browser does a blocked
 * one (fetch rejects, onerror fires). A plain error response would count as loaded for fetch().
 */
internal fun blockedPart() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Access-Control-Allow-Origin" to "*"), object : InputStream() {
    override fun available(): Int = throw java.io.IOException("blocked by an extension") // checked before the headers go out
    override fun read(): Int = throw java.io.IOException("blocked by an extension")
})

class ExtensionRequests internal constructor(private val e: ExtensionEngine) {
    /** A webRequest listener an extension added: its event, which requests (urls, types, tab), and what it asked for. */
    internal class Listener(val ctx: ExtensionEngine.Ctx, val id: String, val event: String, val urls: List<String>, val types: Set<String>?, val tabId: Int?, val extra: Set<String>) {
        val blocking get() = "blocking" in extra
    }
    private val listeners = CopyOnWriteArrayList<Listener>()
    /** Each tab's page address (for initiators and third-party checks) and cookie store (incognito tabs have their own). */
    private val tabPages = ConcurrentHashMap<String, String>()
    private val tabCookies = ConcurrentHashMap<String, CookieManager>()
    internal fun cookiesOf(tabId: String): CookieManager? = tabCookies[tabId]
    private val seq = AtomicLong()
    /** A blocking listener's time to answer before the request goes ahead without it. */
    private val answerMs = 1500L

    // ---------------------------------------------------------------- declarativeNetRequest state
    /** The rules of extensions' enabled rulesets (tens of thousands; rebuilt only when those change), and their own rules. */
    @Volatile private var staticIndex = DnrIndex(emptyList())
    @Volatile private var liveIndex = DnrIndex(emptyList())
    /** Which rulesets [staticIndex] was built from (extension/ruleset ids). */
    private var staticKey: List<String>? = null
    /** Extensions whose allowAllRequests rule matched a tab's page: the page's other requests are theirs to allow. */
    private val allowAll = ConcurrentHashMap<String, Set<String>>()
    private val sessionRules = ConcurrentHashMap<String, List<DnrRule>>()
    /** Where rule changes are applied and the index rebuilt: one thread, so they're applied in the order they came. */
    private val indexer = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** The same thread for the engine's own rebuilds (start, an extension changing), so none overtakes a newer one. */
    internal val indexing = indexer.asCoroutineDispatcher()
    private val rulesSer = ListSerializer(DnrRule.serializer())

    /** Each tab's incognito profile ("" for a normal tab), set on the main thread when its WebView is made. */
    private val tabScopes = ConcurrentHashMap<String, String>()
    internal fun attachTab(tabId: String, cookies: CookieManager, scope: String?) { tabCookies[tabId] = cookies; tabScopes[tabId] = scope.orEmpty() }
    /** The cookie store tab [tabId] uses (its incognito profile's, for an incognito tab). */
    internal fun cookieStoreOf(tabId: String): CookieManager? = tabCookies[tabId]
    internal fun detachTab(tabId: String) { tabCookies.remove(tabId); tabPages.remove(tabId); allowAll.remove(tabId); tabScopes.remove(tabId) }

    /** Listeners added in [c] go with it (its page closed or the extension stopped). */
    internal fun forget(c: ExtensionEngine.Ctx) { listeners.removeAll { it.ctx === c } }
    internal fun forgetExtension(id: String) { listeners.removeAll { it.ctx.ext == id }; sessionRules.remove(id) }

    // ---------------------------------------------------------------- API calls (main thread)

    fun handle(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        when (name) {
            "webRequest.addListener" -> {
                val f = a.getOrNull(2) as? JsonObject
                listeners += Listener(c, a.getOrNull(1).str() ?: return done(false, null, "No id"), a.getOrNull(0).str() ?: return done(false, null, "No event"),
                    (f?.get("urls") as? JsonArray)?.mapNotNull { it.str() } ?: listOf("<all_urls>"),
                    (f?.get("types") as? JsonArray)?.mapNotNull { it.str() }?.toSet(),
                    f?.get("tabId").int(),
                    a.getOrNull(3).strings().toSet())
                done(true, null, null)
            }
            "webRequest.removeListener" -> { listeners.removeAll { it.ctx.ext == c.ext && it.id == a.getOrNull(0).str() }; done(true, null, null) }
            "dnr.updateDynamicRules", "dnr.updateSessionRules" -> indexer.execute {
                // off the main thread, in order: an extension's rules can be thousands, and the index is rebuilt over every
                // extension's (uBlock Origin Lite changes several sets each time a site's filtering mode changes)
                val o = a.getOrNull(0).obj()
                val remove = (o["removeRuleIds"] as? JsonArray)?.mapNotNull { it.int() }?.toSet().orEmpty()
                val add = runCatching { (o["addRules"] as? JsonArray)?.let { json.decodeFromJsonElement(rulesSer, it) }.orEmpty() }
                    .getOrElse { err -> e.main.post { done(false, null, "Invalid rule: ${err.message}") }; return@execute }
                val session = name == "dnr.updateSessionRules"
                val current = if (session) sessionRules[c.ext].orEmpty() else dynamicRules(c.ext)
                val next = current.filterNot { it.id in remove || add.any { r -> r.id == it.id } } + add
                if (session) sessionRules[c.ext] = next else e.store.setDnrDynamic(c.ext, json.encodeToJsonElement(rulesSer, next))
                reindex(); e.main.post { done(true, null, null) }
            }
            "dnr.getDynamicRules" -> done(true, json.encodeToJsonElement(rulesSer, dynamicRules(c.ext)), null)
            "dnr.getSessionRules" -> done(true, json.encodeToJsonElement(rulesSer, sessionRules[c.ext].orEmpty()), null)
            "dnr.updateEnabledRulesets" -> {
                val o = a.getOrNull(0).obj()
                val on = o["enableRulesetIds"].strings()
                val off = o["disableRulesetIds"].strings().toSet()
                e.store.update(c.ext) { copy(dnrRulesets = ((dnrRulesets ?: defaultRulesets(c.ext)) - off + on).distinct()) }
                indexer.execute { reindex(); e.main.post { done(true, null, null) } }
            }
            "dnr.getEnabledRulesets" -> done(true, JsonArray((e.store[c.ext]?.dnrRulesets ?: defaultRulesets(c.ext)).map(::JsonPrimitive)), null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    private fun dynamicRules(ext: String): List<DnrRule> =
        e.store.dnrDynamic(ext)?.let { runCatching { json.decodeFromJsonElement(rulesSer, it) }.getOrNull() }.orEmpty()

    /** The manifest's rulesets that start enabled. */
    private fun defaultRulesets(ext: String): List<String> = rulesetResources(ext).filter { it.second }.map { it.first }

    /** The manifest's declarative_net_request rule_resources: (id, enabled by default, path). */
    private fun rulesetResources(ext: String): List<Triple<String, Boolean, String>> {
        val raw = e.infos[ext]?.manifest?.raw ?: return emptyList()
        val res = ((raw["declarative_net_request"] as? JsonObject)?.get("rule_resources") as? JsonArray) ?: return emptyList()
        return res.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val path = o["path"].str() ?: return@mapNotNull null
            Triple(id, o["enabled"].str() != "false", path)
        }
    }

    /** Static ruleset files are read once (they can hold tens of thousands of rules). */
    private val rulesetCache = ConcurrentHashMap<String, List<DnrRule>>()

    /**
     * Rebuilds the index from every running extension's enabled static rulesets, dynamic and session rules. The files
     * are read on a background thread (call this off the main thread when they may not be cached yet).
     */
    internal fun reindex() {
        val enabled = e.store.list.filter { it.enabled && e.infos[it.id] != null }
        // the rulesets' index stays as it is unless which rulesets are on changed (a dynamic rule changing is common:
        // uBlock Origin Lite changes them whenever a site's filtering mode does)
        val sets = enabled.flatMap { ext ->
            val on = (ext.dnrRulesets ?: defaultRulesets(ext.id)).toSet()
            rulesetResources(ext.id).filter { it.first in on }.map { Triple(ext.id, it.first, it.third) }
        }
        val key = sets.map { "${it.first}/${it.second}" }
        if (key != staticKey || !key.all { rulesetCache.containsKey(it) }) {
            val all = ArrayList<Pair<String, DnrRule>>()
            for ((ext, id, path) in sets) {
                val rules = rulesetCache.getOrPut("$ext/$id") {
                    runCatching { json.decodeFromString(rulesSer, File(e.infos[ext]!!.dir, path.trimStart('/')).readText()) }.getOrDefault(emptyList())
                }
                rules.forEach { all += ext to it }
            }
            staticIndex = DnrIndex(all); staticKey = key
        }
        liveIndex = DnrIndex(enabled.flatMap { ext -> (dynamicRules(ext.id) + sessionRules[ext.id].orEmpty()).map { ext.id to it } })
    }

    internal fun dropRulesetCache(ext: String) { rulesetCache.keys.removeAll { it.startsWith("$ext/") }; staticKey = null }

    // ---------------------------------------------------------------- requests (WebView's network thread)

    /** Whether any extension is listening or has rules: when not, requests aren't touched at all. */
    private val active get() = listeners.isNotEmpty() || staticIndex.size > 0 || liveIndex.size > 0


    /** Pages of sites whose policy blocks extensions' files (ExtensionSitePolicies). */
    internal val sites = ExtensionSitePolicies(e, this)

    /**
     * Tab [tabId]'s request [request]: what extensions decide for it, or null to let it go as it is. [scope]: the tab's
     * incognito profile, if any (only extensions allowed there take part).
     */
    fun intercept(tabId: String, request: WebResourceRequest): WebResourceResponse? = decide(tabId, request) ?: sites.intercept(tabId, request)

    private fun decide(tabId: String, request: WebResourceRequest): WebResourceResponse? {
        val scope = tabScopes[tabId]?.ifEmpty { null }
        if (!active) { if (request.isForMainFrame) tabPages[tabId] = request.url.toString(); return null }
        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val headers = request.requestHeaders.orEmpty()
        val type = requestType(request.isForMainFrame, headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }?.value,
            headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value, url)
        // a page starting: its address is the initiator of everything it loads (the page before it started this one)
        val initiator = if (request.isForMainFrame) tabPages[tabId] else tabPages[tabId]
        if (request.isForMainFrame) { tabPages[tabId] = url; allowAll.remove(tabId) }
        val tabInt = e.tabInt(tabId)
        val net = NetRequest(url, request.method ?: "GET", type, initiator, tabInt)
        val running = { ext: String -> e.runsIn(ext, scope) }

        // ---- declarativeNetRequest
        var dnrHeaders: DnrOutcome.Headers? = null
        val allowedHere = allowAll[tabId].orEmpty()
        val matches = (staticIndex.matches(net) + liveIndex.matches(net)).filter { running(it.first) && it.first !in allowedHere }
        when (val o = dnrDecide(matches, net) { extOrigin(it) }) {
            is DnrOutcome.Block -> return blocked(request.isForMainFrame, o.ext)
            is DnrOutcome.Allow -> {
                if (request.isForMainFrame || type == "sub_frame") matches.firstOrNull { it.first == o.ext && it.second.action.type == "allowAllRequests" }?.let { allowAll[tabId] = allowedHere + o.ext }
                return null // allowed: no further rules or listeners of lower priority, and mi's blocker still applies
            }
            is DnrOutcome.Redirect -> if (o.url.startsWith(extOrigin(o.ext)) || e.mayReach(o.ext, url)) return redirect(o.url, request, type)
            is DnrOutcome.Headers -> if (e.mayReach(o.ext, url)) dnrHeaders = o
            DnrOutcome.None -> {}
        }

        // ---- webRequest listeners for this request
        val mine = listeners.filter { l ->
            running(l.ctx.ext) && e.mayReach(l.ctx.ext, url) && l.urls.any { matchesPattern(it, url) } &&
                (l.types == null || type in l.types) && (l.tabId == null || l.tabId == tabInt)
        }
        if (mine.isEmpty() && dnrHeaders == null) return null
        val requestId = seq.incrementAndGet().toString()
        val base = details(requestId, net, request.isForMainFrame, initiator, scope)

        // onBeforeRequest: cancel or redirect
        val before = ask(mine, "onBeforeRequest", base)
        before.firstOrNull { it.second["cancel"].bool() == true }?.let { (l, _) ->
            fire(mine, "onErrorOccurred", base + ("error" to JsonPrimitive("net::ERR_BLOCKED_BY_CLIENT")))
            return blocked(request.isForMainFrame, l.ctx.ext)
        }
        before.firstNotNullOfOrNull { it.second["redirectUrl"].str() }?.let { target ->
            fire(mine, "onBeforeRedirect", base + ("redirectUrl" to JsonPrimitive(target)) + ("statusCode" to JsonPrimitive(307)))
            return redirect(target, request, type)
        }

        // the request itself: mi makes it when headers are to be seen or changed (and it can: GET or HEAD, not media streams)
        // (not on a site whose bot check turned mi's own connection away: its pages wouldn't load at all; headers stay as they are)
        val wantsHeaders = (dnrHeaders != null || mine.any { it.event in HEADER_EVENTS }) && !sites.botChecked(url)
        val method = request.method?.uppercase() ?: "GET"
        if (!wantsHeaders || (method != "GET" && method != "HEAD") || type == "media" || headers.keys.any { it.equals("Range", true) }) {
            // on WebView's path: what can be told is told (the request was sent; it's done as far as mi can see)
            fire(mine, "onSendHeaders", base + ("requestHeaders" to headerJson(headers.toList())))
            fire(mine, "onCompleted", base + ("statusCode" to JsonPrimitive(200)) + ("fromCache" to JsonPrimitive(false)))
            return null
        }
        return fetchNative(tabId, request, base, mine, headers, dnrHeaders, type)
    }


    private fun details(id: String, r: NetRequest, mainFrame: Boolean, initiator: String?, scope: String?) = buildJsonObject {
        put("requestId", id); put("url", r.url); put("method", r.method); put("type", r.type)
        put("tabId", r.tabId); put("frameId", if (mainFrame) 0 else if (r.type == "sub_frame") 1 else 0); put("parentFrameId", if (mainFrame) -1 else 0)
        put("timeStamp", System.currentTimeMillis().toDouble())
        initiator?.let { i ->
            originOf(i)?.let { put("initiator", it) }
            put("originUrl", i); put("documentUrl", if (mainFrame) r.url else i)
        }
        put("thirdParty", isThirdParty(r.url, initiator)); put("incognito", scope != null)
        put("cookieStoreId", if (scope != null) "firefox-private" else "firefox-default")
    }.let { it.toMutableMap() }

    private operator fun MutableMap<String, JsonElement>.plus(p: Pair<String, JsonElement>): MutableMap<String, JsonElement> = HashMap(this).also { it[p.first] = p.second }

    private fun headerJson(h: List<Pair<String, String>>) = buildJsonArray { h.forEach { (k, v) -> add(buildJsonObject { put("name", k); put("value", v) }) } }
    private fun headersFrom(j: JsonElement?): List<Pair<String, String>>? = (j as? JsonArray)?.mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        val n = o["name"].str() ?: return@mapNotNull null
        n to (o["value"].str() ?: (o["binaryValue"] as? JsonArray)?.mapNotNull { it.int()?.toChar() }?.joinToString("") ?: "")
    }

    /**
     * Asks [mine]'s blocking listeners for [event] about a request, all at once (each extension's background answers on
     * the main thread); waits up to [answerMs]. The others are told without waiting. Returns the answers given.
     */
    private fun ask(mine: List<Listener>, event: String, details: Map<String, JsonElement>): List<Pair<Listener, JsonObject>> {
        val targets = mine.filter { it.event == event }
        if (targets.isEmpty()) return emptyList()
        val blocking = targets.filter { it.blocking }
        val answers = ConcurrentHashMap<Listener, JsonObject>()
        val latch = CountDownLatch(blocking.size)
        val data = JsonObject(details)
        e.main.post {
            for (l in targets) {
                val payload = buildJsonObject { put("id", l.id); put("details", data) }
                if (l.blocking) e.emit(listOf(l.ctx), "webRequest.event", payload) { v, ok -> if (ok == true && v is JsonObject) answers[l] = v; latch.countDown() }
                else e.emit(listOf(l.ctx), "webRequest.event", payload)
            }
        }
        if (blocking.isNotEmpty()) latch.await(answerMs, TimeUnit.MILLISECONDS)
        // in the order extensions were installed, as browsers apply conflicting answers
        return blocking.mapNotNull { l -> answers[l]?.let { l to it } }
    }

    private fun fire(mine: List<Listener>, event: String, details: Map<String, JsonElement>) {
        val targets = mine.filter { it.event == event }
        if (targets.isEmpty()) return
        val data = JsonObject(details)
        e.main.post { targets.forEach { l -> e.emit(listOf(l.ctx), "webRequest.event", buildJsonObject { put("id", l.id); put("details", data) }) } }
    }

    // ---------------------------------------------------------------- answers

    /** What a blocked request gets: nothing for a part of a page; for a page, a short page saying which extension blocked it. */
    private fun blocked(page: Boolean, ext: String): WebResourceResponse {
        if (!page) return blockedPart()
        val name = (e.infos[ext]?.name ?: "An extension").replace("<", "&lt;")
        val html = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Blocked</title>
            <body style="font:16px system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:90vh;margin:0;color:#888;text-align:center">
            <div><div style="font-size:40px">⦸</div><p><b>$name</b> blocked this page.</p></div></body>""".trimIndent()
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
    }

    /**
     * A request sent elsewhere: a page (or frame) moves to the new address itself, so it shows it; a part of a page is
     * answered from the new address (an extension's own file, data, or fetched by mi).
     */
    internal fun redirect(to: String, request: WebResourceRequest, type: String): WebResourceResponse? {
        // an extension names its own files by their native address (runtime.getURL: moz-extension://…), which WebView can't
        // load: mi's address for the same file
        val target = fromNative(to) ?: to
        if (request.isForMainFrame || type == "sub_frame") {
            val safe = JsonPrimitive(target).toString()
            val html = "<!doctype html><meta http-equiv=\"refresh\" content=\"0;url=${target.replace("\"", "%22")}\"><script>location.replace($safe)</script>"
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        }
        extIdOf(target)?.let { id -> return e.pages.serve(id, runCatching { java.net.URI(target).path }.getOrNull() ?: "/", all = false) }
        if (target.startsWith("data:")) {
            val meta = target.substringAfter("data:").substringBefore(',')
            val body = target.substringAfter(',')
            val bytes = if (meta.endsWith(";base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT) else java.net.URLDecoder.decode(body, "UTF-8").toByteArray()
            return WebResourceResponse(meta.removeSuffix(";base64").substringBefore(';').ifEmpty { "text/plain" }, null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
        }
        if (!target.startsWith("http")) return null
        return runCatching {
            val c = connect(target, request.requestHeaders.orEmpty().toList(), follow = true)
            val code = c.responseCode
            response(c, code.takeUnless { it in 300..399 } ?: 200, headersOf(c), c.contentType)
        }.getOrNull()
    }

    // ---- the requests mi makes itself

    /** A connection to [url] sending [headers] (as WebView would, but mi takes the compressed transfer itself). */
    internal fun connect(url: String, headers: List<Pair<String, String>>, method: String = "GET", follow: Boolean = false) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = follow; connectTimeout = 20_000; readTimeout = 30_000; requestMethod = method
            headers.forEach { (k, v) -> if (!k.equals("Accept-Encoding", true)) addRequestProperty(k, v) }
        }

    internal fun headersOf(c: HttpURLConnection) = c.headerFields.flatMap { (k, v) -> if (k == null) emptyList() else v.map { k to it } }

    /** [c]'s answer for WebView: its type, [headers] (as WebView takes them) and body. */
    internal fun response(c: HttpURLConnection, code: Int, headers: List<Pair<String, String>>, contentType: String?, fallback: String = "application/octet-stream", body: InputStream? = null) =
        WebResourceResponse(contentType?.substringBefore(';')?.trim()?.ifEmpty { null } ?: fallback, charsetOf(contentType), code,
            c.responseMessage?.ifEmpty { "OK" } ?: "OK", responseHeaderMap(headers),
            body ?: (if (code >= 400) c.errorStream else c.inputStream) ?: ByteArrayInputStream(ByteArray(0)))

    internal fun charsetOf(contentType: String?) = contentType?.let { Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.trim('"', ' ') }

    /** Response headers for WebView: one value per name (joined), without the ones that describe the raw transfer. */
    private fun responseHeaderMap(h: List<Pair<String, String>>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in h) {
            if (k.equals("Content-Encoding", true) || k.equals("Content-Length", true) || k.equals("Transfer-Encoding", true) || k.equals("Set-Cookie", true)) continue
            out[k] = out[k]?.let { "$it, $v" } ?: v
        }
        return out
    }

    // ---------------------------------------------------------------- mi making the request

    private fun fetchNative(
        tabId: String, request: WebResourceRequest, base: MutableMap<String, JsonElement>, mine: List<Listener>,
        webViewHeaders: Map<String, String>, dnr: DnrOutcome.Headers?, type: String,
    ): WebResourceResponse? {
        val url = request.url.toString()
        val cookies = tabCookies[tabId] ?: CookieManager.getInstance()
        val initiator = base["originUrl"].str()
        // cookies as WebView would send them: not for a third-party request while third-party cookies are blocked
        val thirdPartyBlocked = isThirdParty(url, initiator) && (e.vm.settings.blockThirdPartyCookies || e.vm.settings.blockCookies)
        val cookieAllowed = !e.vm.settings.blockCookies && !thirdPartyBlocked
        var reqHeaders = webViewHeaders.filterKeys { !it.equals("Accept-Encoding", true) }.toList()
        if (cookieAllowed) cookies.getCookie(url)?.takeIf { it.isNotBlank() }?.let { reqHeaders = reqHeaders + ("Cookie" to it) }
        if (dnr != null) reqHeaders = applyHeaderOps(reqHeaders, dnr.request)
        // onBeforeSendHeaders: an extension may change what's sent (each answer applies in turn)
        val sendDetails = base + ("requestHeaders" to headerJson(reqHeaders))
        for ((l, ans) in ask(mine, "onBeforeSendHeaders", sendDetails)) {
            if (ans["cancel"].bool() == true) return blocked(request.isForMainFrame, l.ctx.ext)
            headersFrom(ans["requestHeaders"])?.let { reqHeaders = it }
        }
        fire(mine, "onSendHeaders", base + ("requestHeaders" to headerJson(reqHeaders)))

        return try {
            val c = connect(url, reqHeaders, method = request.method?.uppercase() ?: "GET")
            val code = c.responseCode
            var resHeaders = headersOf(c)
            // a bot check (Cloudflare, Fastly…) won't pass on mi's own connection (its challenge is tied to the client it was
            // given to): WebView makes this request, and the site's from now on, itself
            if (isBotCheck(code, resHeaders)) { c.disconnect(); sites.leaveToWebView(url); return null }
            if (dnr != null) resHeaders = applyHeaderOps(resHeaders, dnr.response)
            val statusLine = "HTTP/1.1 $code ${c.responseMessage.orEmpty()}"
            // onHeadersReceived: an extension may change the response's headers, cancel it or send it elsewhere
            val recvDetails = base + ("statusCode" to JsonPrimitive(code)) + ("statusLine" to JsonPrimitive(statusLine)) + ("responseHeaders" to headerJson(resHeaders))
            for ((l, ans) in ask(mine, "onHeadersReceived", recvDetails)) {
                if (ans["cancel"].bool() == true) { c.disconnect(); return blocked(request.isForMainFrame, l.ctx.ext) }
                ans["redirectUrl"].str()?.let { c.disconnect(); return redirect(it, request, type) }
                headersFrom(ans["responseHeaders"])?.let { resHeaders = it }
            }
            // its cookies are kept as the (possibly changed) headers say, under the same rules as sending them
            if (cookieAllowed) resHeaders.filter { it.first.equals("Set-Cookie", true) }.forEach { cookies.setCookie(url, it.second) }
            val location = resHeaders.firstOrNull { it.first.equals("Location", true) }?.second
            if (code in 300..399 && location != null) {
                val next = URL(URL(url), location).toString()
                fire(mine, "onBeforeRedirect", recvDetails + ("redirectUrl" to JsonPrimitive(next)))
                c.disconnect()
                // WebView can't be handed a redirect: a page goes to the new address itself; a part of a page is fetched from it
                return redirect(next, request, type)
            }
            val doneDetails = recvDetails + ("fromCache" to JsonPrimitive(false))
            fire(mine, "onResponseStarted", doneDetails)
            val contentType = resHeaders.firstOrNull { it.first.equals("Content-Type", true) }?.second ?: c.contentType
            val raw: InputStream = (if (code >= 400) c.errorStream else c.inputStream) ?: ByteArrayInputStream(ByteArray(0))
            // a page that is a bot check's (its title says so, in its first bytes): the same
            val body = java.io.BufferedInputStream(raw, 8192)
            if (request.isForMainFrame || type == "sub_frame") {
                body.mark(4096); val head = ByteArray(4000); var n = 0
                while (n < head.size) { val r = body.read(head, n, head.size - n); if (r < 0) break; n += r }
                body.reset()
                if (isBotCheckPage(String(head, 0, n, Charsets.UTF_8))) { c.disconnect(); sites.leaveToWebView(url); return null }
            }
            // told it's complete once the page has read all of it
            val tracked = object : FilterInputStream(body) {
                private var told = false
                private fun tell() { if (!told) { told = true; fire(mine, "onCompleted", doneDetails) } }
                override fun read(): Int = super.read().also { if (it < 0) tell() }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it < 0) tell() }
                override fun close() { tell(); super.close() }
            }
            response(c, code, if (request.isForMainFrame) sites.relaxed(url, resHeaders) else resHeaders, contentType, body = tracked)
        } catch (t: Exception) {
            fire(mine, "onErrorOccurred", base + ("error" to JsonPrimitive("net::ERR_FAILED")))
            null // WebView tries it itself
        }
    }

    companion object {
        /** Events whose listeners need to see (or change) a request's headers: mi makes such requests itself. */
        private val HEADER_EVENTS = setOf("onBeforeSendHeaders", "onHeadersReceived", "onResponseStarted", "onBeforeRedirect", "onAuthRequired")
    }
}

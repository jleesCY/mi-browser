// Extensions in tabs: each tab's WebView gets one document-start script with every enabled extension's content scripts
// (assets/js/ext-content.js decides which run on each page, and when), and the channel they talk to mi on. When
// extensions change, the script is replaced in every tab; it applies from each tab's next page load. Pages can load an
// extension's files only where it allows (web_accessible_resources). Called from WebViewHost.
package com.jlees.mibrowser.extensions

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Content scripts bigger than this (characters) that can wait for the page are run on request (ExtensionTabs.runEntry). */
private const val LAZY_CHARS = 300_000

class ExtensionTabs internal constructor(private val e: ExtensionEngine) {
    private class Attached(val view: WebView, val scope: String?, var handler: ScriptHandler?, var pageHandler: ScriptHandler? = null)
    private val attached = HashMap<String, Attached>()
    /** The current script for normal tabs, and for incognito tabs (only extensions allowed there). */
    private var normal: String? = null
    private var incognito: String? = null
    /** The start script for extensions' own pages opened in tabs (normal tabs, incognito), and the origins it runs at. */
    private var pages: Pair<String, Set<String>>? = null
    private var incognitoPages: Pair<String, Set<String>>? = null
    /** Whether tabs have had their script yet (mi starting: pages may begin loading before extensions are read). */
    private var ready = false
    /** Tabs whose page started before that: loaded again once it's ready, so their extensions run in them. */
    private val missed = HashSet<String>()

    /** A page started in tab [tabId] (main thread). */
    internal fun pageStarted(tabId: String, url: String?) { if (!ready && url != null && url.startsWith("http")) missed += tabId }

    /** Per tab: the extension whose own page its main frame shows (that page's files are all served to it). */
    private val showing = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Per tab: the extensions whose content scripts run in its page (told by the page's frames as they start). */
    private val running = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    /** Tab [tabId]'s page (or one of its frames, not [top]) runs content scripts of [exts]. */
    internal fun noteRunning(tabId: String, exts: Set<String>, top: Boolean) {
        running[tabId] = if (top) exts else running[tabId].orEmpty() + exts
    }

    private val supported get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** Tab [tabId]'s new WebView: extensions' content scripts run in its pages. [scope]: its incognito profile, if any. */
    fun attach(wv: WebView, tabId: String, scope: String?, cookies: android.webkit.CookieManager) {
        e.requests.attachTab(tabId, cookies, scope)
        if (!supported) return
        WebViewCompat.addWebMessageListener(wv, "miExt", setOf("*")) { _, message, origin, _, proxy ->
            e.onMessage(ExtensionEngine.Source.Tab(tabId, scope, origin.toString().trimEnd('/')), message.data, proxy)
        }
        val script = if (scope == null) normal else incognito
        attached[tabId] = Attached(wv, scope, script?.let { WebViewCompat.addDocumentStartJavaScript(wv, it, setOf("*")) }, pageHandler(wv, scope))
    }

    fun detach(tabId: String) {
        attached.remove(tabId)
        showing.remove(tabId); running.remove(tabId)
        e.requests.detachTab(tabId)
        e.contexts.filter { it.tabId == tabId }.forEach { e.drop(it) }
    }

    /** Extensions changed: each tab's script is replaced (in effect from its next page). */
    internal suspend fun rebuild() {
        // the scripts hold every content script's code: built off the main thread, put in place on it
        val (n, i) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { script(null) to script("incognito") }
        normal = n; incognito = i
        pages = pageScript(null); incognitoPages = pageScript("incognito")
        for (a in attached.values) {
            a.handler?.remove(); a.pageHandler?.remove()
            val s = if (a.scope == null) normal else incognito
            a.handler = s?.let { WebViewCompat.addDocumentStartJavaScript(a.view, it, setOf("*")) }
            a.pageHandler = pageHandler(a.view, a.scope)
        }
        if (!ready) {
            ready = true
            // (a moment later: a script just added reaches the page's renderer only after it, and a reload at once can miss it)
            if (normal != null || incognito != null) missed.forEach { id -> attached[id]?.view?.let { v -> v.postDelayed({ if (v.url?.startsWith("http") == true) v.reload() }, 600) } }
            missed.clear()
        }
    }

    private fun pageHandler(wv: WebView, scope: String?) = (if (scope == null) pages else incognitoPages)?.let { (s, origins) -> WebViewCompat.addDocumentStartJavaScript(wv, s, origins) }

    /**
     * The start script for an extension's own page opened in a tab (Grammarly's welcome page, an options page in a tab):
     * the same API its popup gets, for the extension whose address the page is at. It only runs at extensions' origins.
     */
    private fun pageScript(scope: String?): Pair<String, Set<String>>? {
        val exts = e.infos.values.filter { e.runsIn(it.id, scope) }
        if (exts.isEmpty()) return null
        val map = kotlinx.serialization.json.buildJsonObject {
            exts.forEach { info -> put(info.id, JsonArray(listOf(e.pages.configOf(info, "tab", incognito = scope != null), JsonPrimitive(e.tokens[info.id] ?: "")))) }
        }
        val s = "(function(){var P=$map;var x=P[location.hostname.split('.')[0]];if(!x)return;(function(makeApi, CFG, TOKEN){\n(${e.scripts.extAddress})()([CFG.id]);\n${e.scripts.extPage}\n})(${e.scripts.extApi}(), x[0], x[1]);})();"
        return s to exts.map { extOrigin(it.id) }.toSet()
    }

    /** A page at [from] may send its tab to [to]: anything but an extension page, unless from that extension or one it lets pages load. */
    fun mayNavigate(from: String?, to: String): Boolean {
        val id = extIdOf(fromNative(to) ?: to) ?: return true
        if (extIdOf(from) == id) return true
        val info = e.infos[id] ?: return false
        return webAccessible(java.net.URI(to).path ?: "/", info.manifest.webAccessible)
    }

    /**
     * Tab [tabId] asked for an extension's file. Its main frame going to one of an extension's pages (opened by the
     * extension, typed, or restored: web pages can't navigate there, TabWebClient) gets the whole extension's files from
     * then on, as the page needs; anything else only the files the extension lets web pages load.
     */
    fun intercept(tabId: String, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val id = extIdOf(url)
        if (request.isForMainFrame) { if (id != null && request.method == "GET") showing[tabId] = id else showing.remove(tabId) }
        id ?: return null
        val path = request.url.path ?: "/"
        // a page the extension's content scripts run in may load its files (style sheets, scripts, fonts, pictures its
        // content scripts add), as in Firefox; its pages (HTML) stay only for those it lets pages load
        val page = path.endsWith("/") || path.endsWith(".html", true) || path.endsWith(".htm", true)
        return e.pages.serve(id, path, all = showing[tabId] == id || (!page && id in running[tabId].orEmpty()))
    }

    /** The document-start script for tabs of [scope] (null: normal tabs), or null when no extension has content scripts. */
    private fun script(scope: String?): String? {
        val all = e.infos.values.associateWith { it.manifest.contentScripts + e.scripting.contentScriptsOf(it.id) }
        val exts = all.keys.filter { e.runsIn(it.id, scope) && all[it].orEmpty().isNotEmpty() }
        if (exts.isEmpty()) return null
        val list = JsonArray(exts.map { info ->
            buildJsonObject {
                put("id", info.id); put("token", e.tokens[info.id] ?: "")
                put("offSites", JsonArray(e.store[info.id]?.offSites.orEmpty().map(::JsonPrimitive)))
                put("cfg", e.pages.configOf(info, "content", incognito = scope != null))
                put("scripts", JsonArray(all[info].orEmpty().map { cs ->
                    buildJsonObject {
                        put("matches", JsonArray(cs.matches.map(::JsonPrimitive))); put("excludeMatches", JsonArray(cs.excludeMatches.map(::JsonPrimitive)))
                        put("includeGlobs", JsonArray(cs.includeGlobs.map(::JsonPrimitive))); put("excludeGlobs", JsonArray(cs.excludeGlobs.map(::JsonPrimitive)))
                        put("css", JsonArray(cs.css.mapNotNull { read(info, it)?.let { css -> extensionCss(css, info.id) } }.map(::JsonPrimitive)))
                        put("runAt", cs.runAt); put("allFrames", cs.allFrames); put("mainWorld", cs.mainWorld)
                    }
                }))
            }
        })
        // each entry's files run together in one function (so one file's top-level names reach the next, as in a browser),
        // with chrome, browser and globalThis as its own variables. Entries with the same files share one function (FNS),
        // so a big file isn't in the script twice. A big one that waits for the page anyway (top frame, document end or
        // idle) isn't in it at all: it's 0, and mi runs it in the page when asked (runEntry), so the megabytes aren't
        // read again in every frame and page it doesn't run in.
        val fns = HashMap<String, Int>() // an entry's files (and world) → its function's index in FNS
        val body = StringBuilder()
        val code = exts.joinToString(",", "{", "}") { info ->
            JsonPrimitive(info.id).toString() + ":[" + all[info].orEmpty().joinToString(",") { cs ->
                if (lazy(info, cs)) "0"
                else {
                    val key = info.id + "\u0000" + cs.mainWorld + "\u0000" + cs.js.joinToString("\u0000")
                    val i = fns[key] ?: run {
                        // its files, written straight into the script (no copies of megabytes of code)
                        val files = cs.js.mapNotNull { read(info, it) }
                        if (files.isEmpty()) return@run -1
                        if (fns.isNotEmpty()) body.append(",\n")
                        // a "MAIN" world script is the page's own: no extension variables at all
                        body.append("function(").append(if (cs.mainWorld) "" else "chrome,browser,globalThis,self,WebSocket").append("){\n")
                        files.forEachIndexed { n, f -> if (n > 0) body.append("\n;\n"); body.append(if (cs.mainWorld) f else isolatedWorld(f)) }
                        body.append("\n}")
                        fns.size.also { fns[key] = it }
                    }
                    if (i < 0) "null" else "FNS[$i]"
                }
            } + "]"
        }
        return "(function(makeApi, EXTS, CODE, installAddresses){\n${e.scripts.extContent}\n})(${e.scripts.extApi}(), $list, (function(){var FNS=[$body];return $code;})(), ${e.scripts.extAddress}());"
    }

    /** Run on request (runEntry) rather than in the tab script: told by its files' sizes, without reading them. */
    private fun lazy(info: ExtensionInfo, cs: ContentScript) =
        !cs.allFrames && !cs.mainWorld && cs.runAt != "document_start" && cs.js.none { it.startsWith(INLINE) } &&
            cs.js.sumOf { File(info.dir, it.trimStart('/')).length() } > LAZY_CHARS

    /**
     * A content script left out of the tab script (0 in CODE): run in tab [c]'s page now. Its page says which entry
     * ([index]) and a [nonce] only it knows, so a page that replaced it meanwhile doesn't run it.
     */
    internal fun runEntry(c: ExtensionEngine.Ctx, index: Int, nonce: String, done: Done) {
        val tabId = c.tabId ?: return done(false, null, "No tab")
        if (!c.top) return done(false, null, "Top frame only")
        val info = e.infos[c.ext] ?: return done(false, null, "No extension")
        e.vm.viewModelScope.launch {
            val code = withContext(Dispatchers.IO) {
                val cs = (info.manifest.contentScripts + e.scripting.contentScriptsOf(info.id)).getOrNull(index)
                cs?.takeIf { lazy(info, it) }?.js?.mapNotNull { read(info, it) }?.joinToString("\n;\n")?.let(::isolatedWorld)
            } ?: return@launch done(false, null, "No such script")
            val wv = e.tabView(tabId) ?: return@launch done(false, null, "No tab")
            val args = listOf(JsonPrimitive(info.id), JsonPrimitive(e.tokens[info.id] ?: ""), JsonPrimitive(nonce)).joinToString(",")
            wv.evaluateJavascript("window.__miExtRunEntry&&window.__miExtRunEntry($args,function(chrome,browser,globalThis,self,WebSocket){\n$code\n});void 0", null)
            done(true, null, null)
        }
    }

    private fun read(info: ExtensionInfo, path: String): String? {
        if (path.startsWith(INLINE)) return path.removePrefix(INLINE) // code given while running (Firefox's contentScripts.register)
        val dir = info.dir.canonicalFile
        val f = File(dir, path.trimStart('/')).canonicalFile
        return if (f.path.startsWith(dir.path) && f.isFile) String(f.readBytes(), Charsets.UTF_8) else null
    }
}

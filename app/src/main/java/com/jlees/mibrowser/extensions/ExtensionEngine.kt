// The extension engine: runs installed extensions on WebView. It knows where each extension's code is running (its
// content scripts in each page and frame, its background, its popup or options page), passes messages between them as
// chrome.runtime and chrome.tabs do, sends them events (tabs changing, alarms, storage changes), and answers their API
// calls (ExtensionApi). Content scripts are set up by ExtensionTabs, an extension's own pages by ExtensionPages.
// Main thread only, as WebView's message callbacks are.
package com.jlees.mibrowser.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.webkit.JavaScriptReplyProxy
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.uniqueId
import com.jlees.mibrowser.web.PageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.security.SecureRandom

/** Extension pages are served at https://<id>.ext.mi.invalid/ (".invalid" is never a real site). */
const val EXT_HOST = ".ext.mi.invalid"
fun extOrigin(id: String) = "https://$id$EXT_HOST"

/** Extension [id]'s address as its browser gives it: chrome-extension:// for a Chrome Web Store id, else moz-extension://. */
fun nativeBase(id: String) = if (id.matches(Regex("[a-p]{32}"))) "chrome-extension://$id/" else "moz-extension://$id/"

/** mi's address for an extension's page ([extOrigin]) as the extension knows it ([nativeBase]); anything else unchanged. */
fun toNative(url: String?): String? = url?.let { u -> extIdOf(u)?.let { id -> nativeBase(id) + u.removePrefix(extOrigin(id)).trimStart('/') } ?: u }

/** An extension's native address (moz-extension://<id>/…) as the address mi serves it at, or null if it isn't one. */
fun fromNative(url: String?): String? = url?.let { Regex("""^(?:moz|chrome)-extension://([a-z0-9-]+)/(.*)$""", RegexOption.IGNORE_CASE).find(it) }
    ?.let { m -> "${extOrigin(m.groupValues[1].lowercase())}/${m.groupValues[2]}" }

/** The extension behind an address at its origin, or null. */
fun extIdOf(url: String?): String? = url?.let { Regex("""^https://([a-z0-9-]+)\Q$EXT_HOST\E(/|$)""").find(it)?.groupValues?.get(1) }

/** A toolbar button's state (chrome.action), shown in the tab menu. */
data class ActionState(
    val badge: String = "",
    val badgeColor: Long? = null,
    val title: String? = null,
    /** The icon's path inside the extension. */
    val icon: String? = null,
    val popup: String? = null,
    val enabled: Boolean = true,
    /** Per tab (mi's tab id): the badge set for that tab only. */
    val tabBadges: Map<String, String> = emptyMap(),
    /** The side panel's page set by the extension (sidePanel.setOptions / sidebarAction.setPanel), over the manifest's. */
    val panel: String? = null,
    val panelEnabled: Boolean = true,
    /** Tapping the button opens the side panel (sidePanel.setPanelBehavior). */
    val panelOnClick: Boolean = false,
)

class ExtensionEngine(internal val app: Context, internal val vm: BrowserViewModel) {
    internal val store get() = vm.extensions
    internal val scripts = PageScripts(app)
    internal val main = Handler(Looper.getMainLooper())
    /** mi's identity (Chrome's, not an embedded WebView's): extension pages, sign-in sheets and extensions' requests send it, as tabs do. */
    internal val userAgents = com.jlees.mibrowser.web.UserAgents(app)
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Installed extensions, read from their folders (enabled or not). */
    internal val infos = java.util.concurrent.ConcurrentHashMap<String, ExtensionInfo>() // read on network threads too
    /** A secret per extension for this run: its calls must carry it, so pages can't make calls of their own. */
    internal val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** The live WebView of mi's tab [id], for running scripts in it (set by WebViewHost). */
    var tabView: (String) -> WebView? = { null }

    internal val pages = ExtensionPages(this)
    val tabs = ExtensionTabs(this)
    val requests = ExtensionRequests(this)
    internal val scripting = ExtensionScripting(this)
    val navigation = ExtensionNavigation(this)
    internal val platform = ExtensionPlatform(this)
    internal val sockets = ExtensionSockets(this)
    val menus = ExtensionMenus(this)

    /** Registered content scripts changed: every tab's script is rebuilt (in order with other changes). */
    internal fun rebuildTabScripts() { vm.viewModelScope.launch { lock.withLock { tabs.rebuild() } } }
    internal val api = ExtensionApi(this)

    /** Toolbar buttons, by extension id. */
    var actions by mutableStateOf(mapOf<String, ActionState>())
        internal set

    fun dirOf(id: String) = File(app.filesDir, "extensions/$id")

    // ---------------------------------------------------------------- where extension code runs

    /** TAB: one of the extension's own pages opened in a tab (tabs.create), with the full API like its popup. */
    enum class Kind { CONTENT, BACKGROUND, POPUP, OPTIONS, OFFSCREEN, TAB }

    /** Somewhere an extension's code runs, and the way to reach it. */
    inner class Ctx(val ext: String, val kind: Kind, val tabId: String?, val top: Boolean, val url: String, val scope: String?) {
        var proxy: JavaScriptReplyProxy? = null
        /** Pages: their scripts have run (a background says so), so events can be delivered. */
        var ready = kind == Kind.CONTENT
        internal val queue = ArrayList<String>()
        fun send(msg: JsonObject) {
            val text = msg.toString()
            val p = proxy
            if (p == null || !ready) { queue += text; return }
            try { p.postMessage(text) } catch (_: Exception) { contexts.remove(this) } // the frame is gone
        }
        fun flush() { val p = proxy ?: return; queue.forEach { runCatching { p.postMessage(it) } }; queue.clear() }
        val info get() = infos[ext]
    }

    internal val contexts = ArrayList<Ctx>()
    private fun pageContexts(ext: String, except: Ctx? = null) = contexts.filter { it.ext == ext && it.kind != Kind.CONTENT && it !== except }
    internal fun contentContexts(ext: String, tabId: String, topOnly: Boolean) = contexts.filter { it.ext == ext && it.kind == Kind.CONTENT && it.tabId == tabId && (!topOnly || it.top) }

    // ---------------------------------------------------------------- starting and stopping

    private var started = false

    /** Reads the installed extensions and starts the enabled ones (after the stores have loaded). */
    fun start() {
        if (started) return
        started = true
        vm.viewModelScope.launch {
            lock.withLock {
                // reading and scanning extensions' files (megabytes of code) happens off the main thread
                val read = withContext(Dispatchers.IO) {
                    ExtensionInstaller.staging(app).deleteRecursively() // left from a fetch never added
                    File(app.cacheDir, "ext-staging").deleteRecursively() // where they waited before 2.0
                    store.list.map { it.id to inspect(it.id) }
                }
                read.forEach { (id, info) -> info?.let { apply(id, it) } }
                tabs.rebuild()
                withContext(Dispatchers.IO) { requests.reindex() }
                store.list.filter { it.enabled }.forEach { pages.startBackground(it.id, reason = "startup") }
            }
            observeTabs()
            platform.observeDownloads()
            platform.watchIdle()
        }
    }

    /** Changes to extensions (start, install, switches) one at a time, in order. */
    private val lock = Mutex()

    /** Extension [id] read from its folder (off the main thread), or null. */
    private fun inspect(id: String): ExtensionInfo? {
        val dir = dirOf(id)
        if (!File(dir, "manifest.json").exists()) return null
        return runCatching { ExtensionPackage.inspect(dir, id = id) }.getOrNull()
    }

    /** [info] in use (main thread): its rating brought up to date, its token and its button. */
    private fun apply(id: String, info: ExtensionInfo) {
        info.let {
            infos[id] = it
            // ratings follow mi: as more of the API works, an installed extension's rating is brought up to date
            val c = it.compatibility
            if (store[id]?.let { e -> e.fit != c.fit || e.reasons != c.reasons || e.serious != c.serious } == true) store.update(id) { copy(fit = c.fit, reasons = c.reasons, serious = c.serious) }
            tokens.getOrPut(id) { ByteArray(16).also { b -> SecureRandom().nextBytes(b) }.joinToString("") { b -> "%02x".format(b) } }
            if (actions[id] == null) actions = actions + (id to ActionState(title = it.manifest.actionTitle?.let { t -> localize(t, it.messages) } ?: it.name,
                icon = it.manifest.icon(32), popup = it.manifest.popup))
        }
    }

    /** Extension [id] was installed, updated, turned on or off, or removed: brought in line everywhere. */
    fun refresh(id: String, reason: String? = null) {
        pages.stopBackground(id)
        removeContexts { it.ext == id }
        alarms.keys.filter { it.startsWith("$id/") }.forEach { cancelAlarm(it) }
        requests.forgetExtension(id)
        if (store[id] == null) scripting.forget(id) else scripting.keepPersistent(id)
        menus.forget(id)
        vm.viewModelScope.launch {
            lock.withLock {
                val e = store[id]
                if (e == null) { infos.remove(id); tokens.remove(id); actions = actions - id }
                else {
                    val info = withContext(Dispatchers.IO) { inspect(id) }
                    actions = actions - id
                    info?.let { apply(id, it) }
                }
                tabs.rebuild()
                withContext(Dispatchers.IO) { requests.dropRulesetCache(id); requests.reindex() }
                if (e != null && e.enabled) pages.startBackground(id, reason = reason ?: "startup")
            }
        }
    }

    /** Whether extension [id] runs in a tab of [scope] (an incognito profile, or null for normal tabs). */
    internal fun runsIn(id: String, scope: String?): Boolean {
        val e = store[id] ?: return false
        return e.enabled && (scope == null || e.incognito)
    }

    // ---------------------------------------------------------------- messages from extension code

    /** Who sent a message: a tab's page or frame, or one of an extension's own pages. */
    sealed interface Source {
        /** [origin]: the sending frame's, as WebView reports it (a page can't fake it). */
        data class Tab(val tabId: String, val scope: String?, val origin: String? = null) : Source
        class Page(val ctx: Ctx) : Source
    }

    private val pendingExec = HashMap<Int, (Boolean, JsonElement?, String?) -> Unit>()
    private var execSeq = 0

    fun onMessage(source: Source, data: String?, proxy: JavaScriptReplyProxy) {
        val m = runCatching { json.parseToJsonElement(data ?: return).let { it as JsonObject } }.getOrNull() ?: return
        val t = (m["t"] as? JsonPrimitive)?.contentOrNull
        if (t == "hello" && source is Source.Tab) { if (m["x"] is JsonPrimitive) pageInTab(source, m, proxy) else helloFromTab(source, m, proxy); return }
        // a page's DOM is ready (told by the content-script loader; harmless if a page fakes it)
        // the page's policy blocked an extension's file (told by the content-script loader; a page faking it only gets its
        // own policy loosened for extensions' files)
        if (t == "csp" && source is Source.Tab) { requests.siteBlocksExtensions(source.tabId, (m["u"] as? JsonPrimitive)?.contentOrNull); return }
        if (t == "nav" && source is Source.Tab) { if ((m["top"] as? JsonPrimitive)?.contentOrNull == "true") navigation.domReady(source.tabId, (m["u"] as? JsonPrimitive)?.contentOrNull); return }
        val ext = (m["x"] as? JsonPrimitive)?.contentOrNull ?: return
        if (tokens[ext] == null || (m["k"] as? JsonPrimitive)?.contentOrNull != tokens[ext]) return // not from the extension
        val ctx = when (source) {
            is Source.Page -> source.ctx.also { it.proxy = proxy }
            is Source.Tab -> contexts.firstOrNull { it.ext == ext && it.proxy == proxy } ?: return
        }
        when (t) {
            "hello" -> { ctx.proxy = proxy; if (ctx.kind != Kind.BACKGROUND) { ctx.ready = true; ctx.flush() } }
            "ready" -> pages.backgroundReady(ctx)
            "call" -> {
                val callId = (m["c"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return
                val name = (m["n"] as? JsonPrimitive)?.contentOrNull ?: return
                api.handle(ctx, name, (m["a"] as? JsonArray) ?: JsonArray(emptyList())) { ok, value, error ->
                    respond(proxy, callId, ok, value, error)
                }
            }
            "reply" -> answer((m["r"] as? JsonPrimitive)?.contentOrNull ?: return, m["v"], (m["ok"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull())
            "exec" -> pendingExec.remove((m["c"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return)
                ?.invoke((m["ok"] as? JsonPrimitive)?.contentOrNull == "true", m["v"], (m["e"] as? JsonPrimitive)?.contentOrNull)
        }
    }

    /** One of an extension's own pages started in a tab: a page context for it, if it really is that extension's (origin and token). */
    private fun pageInTab(source: Source.Tab, m: JsonObject, proxy: JavaScriptReplyProxy) {
        val id = (m["x"] as? JsonPrimitive)?.contentOrNull ?: return
        if (tokens[id] == null || (m["k"] as? JsonPrimitive)?.contentOrNull != tokens[id]) return
        if (source.origin != extOrigin(id) || !runsIn(id, source.scope)) return
        removeContexts { (it.kind == Kind.CONTENT || it.kind == Kind.TAB) && it.tabId == source.tabId }
        contexts += Ctx(id, Kind.TAB, source.tabId, true, (m["u"] as? JsonPrimitive)?.contentOrNull.orEmpty(), source.scope).also { it.proxy = proxy; it.ready = true }
    }

    private fun helloFromTab(source: Source.Tab, m: JsonObject, proxy: JavaScriptReplyProxy) {
        val top = (m["top"] as? JsonPrimitive)?.contentOrNull == "true"
        val url = (m["u"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        // a new page in the tab: the contexts of the page it replaced are gone
        if (top) removeContexts { (it.kind == Kind.CONTENT || it.kind == Kind.TAB) && it.tabId == source.tabId }
        val accepted = HashSet<String>()
        (m["x"] as? JsonArray)?.forEach { pair ->
            val a = pair as? JsonArray ?: return@forEach
            val id = (a.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val token = (a.getOrNull(1) as? JsonPrimitive)?.contentOrNull
            if (tokens[id] == null || tokens[id] != token || !runsIn(id, source.scope)) return@forEach
            contexts += Ctx(id, Kind.CONTENT, source.tabId, top, url, source.scope).also { it.proxy = proxy }
            accepted += id
        }
        tabs.noteRunning(source.tabId, accepted, top)
    }

    private fun respond(proxy: JavaScriptReplyProxy, callId: Int, ok: Boolean, value: JsonElement?, error: String?) {
        val msg = buildJsonObject { put("t", "res"); put("c", callId); put("ok", ok); put("v", value ?: JsonNull); if (error != null) put("e", error) }
        runCatching { proxy.postMessage(msg.toString()) }
    }

    // ---------------------------------------------------------------- events and answers

    /** Waiting for an answer from any of several contexts: the first real answer wins. */
    private class Route(var waiting: Int, val done: (JsonElement?, Boolean?) -> Unit)
    private val routes = HashMap<String, Route>()

    /**
     * Sends event [name] with [data] to [targets]. With [onAnswer], each can answer (runtime.onMessage): the first answer
     * (ok true, or false for an error) is passed on; if none of them answers, it's called with (null, null).
     */
    fun emit(targets: List<Ctx>, name: String, data: JsonElement, onAnswer: ((JsonElement?, Boolean?) -> Unit)? = null) {
        val r = if (onAnswer != null) uniqueId().also { routes[it] = Route(targets.size, onAnswer) } else null
        if (onAnswer != null && targets.isEmpty()) { routes.remove(r); onAnswer(null, null); return }
        for (c in targets) c.send(buildJsonObject { put("t", "ev"); put("x", c.ext); put("n", name); put("d", data); if (r != null) put("r", r) })
        // a context that never answers (closed, or busy forever) doesn't keep the caller waiting past a minute
        if (r != null) main.postDelayed({ routes.remove(r)?.done?.invoke(null, null) }, 60_000)
    }

    private fun answer(routeId: String, value: JsonElement?, ok: Boolean?) {
        val route = routes[routeId] ?: return
        if (ok != null) { routes.remove(routeId); route.done(value, ok); return }
        if (--route.waiting <= 0) { routes.remove(routeId); route.done(null, null) }
    }

    /** Sends event [name] to every one of extension [ext]'s own pages (its background first of all). */
    fun emitToPages(ext: String, name: String, data: JsonElement) = emit(pageContexts(ext), name, data)

    /** runtime.sendMessage from [from]: to the extension's own pages (not to [from] itself). */
    internal fun sendRuntimeMessage(from: Ctx, message: JsonElement, done: (JsonElement?, Boolean?) -> Unit) {
        val targets = pageContexts(from.ext, except = from)
        if (targets.isEmpty()) { done(JsonPrimitive("Could not establish connection. Receiving end does not exist."), false); return }
        emit(targets, "runtime.onMessage", buildJsonObject { put("message", message); put("sender", senderOf(from)) }, done)
    }

    /** tabs.sendMessage: to extension [ext]'s content scripts in tab [tabId] (the top frame only with frameId 0). */
    internal fun sendTabMessage(from: Ctx, tabId: String, topOnly: Boolean, message: JsonElement, done: (JsonElement?, Boolean?) -> Unit) {
        val targets = contentContexts(from.ext, tabId, topOnly)
        if (targets.isEmpty()) { done(JsonPrimitive("Could not establish connection. Receiving end does not exist."), false); return }
        emit(targets, "runtime.onMessage", buildJsonObject { put("message", message); put("sender", senderOf(from)) }, done)
    }

    /** chrome.runtime.MessageSender for a message from [c]. */
    internal fun senderOf(c: Ctx): JsonObject = buildJsonObject {
        put("id", c.ext)
        // its own pages by their native address, as its browser would say (a page in a tab: the page's own)
        val url = toNative(c.url)!!
        put("url", url)
        put("origin", if (extIdOf(c.url) != null) nativeBase(c.ext).trimEnd('/') else runCatching { java.net.URI(url).let { u -> "${u.scheme}://${u.authority}" } }.getOrNull().orEmpty())
        // from a page in a tab (a content script, or one of the extension's own pages opened in a tab): that tab, as in Chrome
        if ((c.kind == Kind.CONTENT || c.kind == Kind.TAB) && c.tabId != null) {
            vm.tabs.list.firstOrNull { it.id == c.tabId }?.let { put("tab", tabJson(it)) }
            put("frameId", if (c.top) 0 else 1)
        }
    }

    // ---------------------------------------------------------------- ports (runtime.connect)

    private class Port(val ext: String, val a: Ctx, val b: MutableList<Ctx>)
    private val ports = HashMap<String, Port>()

    internal fun connect(from: Ctx, portId: String, name: String, tabId: String?) {
        val targets = if (tabId != null) contentContexts(from.ext, tabId, topOnly = false) else pageContexts(from.ext, except = from)
        if (targets.isEmpty()) { from.send(buildJsonObject { put("t", "ev"); put("x", from.ext); put("n", "port.disconnect"); put("d", buildJsonObject { put("portId", portId) }) }); return }
        ports[portId] = Port(from.ext, from, targets.toMutableList())
        emit(targets, "runtime.onConnect", buildJsonObject { put("portId", portId); put("name", name); put("sender", senderOf(from)) })
    }

    internal fun portPost(from: Ctx, portId: String, message: JsonElement) {
        val p = ports[portId] ?: return
        val to = if (from === p.a) p.b else listOf(p.a)
        emit(to, "port.message", buildJsonObject { put("portId", portId); put("message", message) })
    }

    internal fun portDisconnect(from: Ctx, portId: String) {
        val p = ports.remove(portId) ?: return
        val to = if (from === p.a) p.b else listOf(p.a)
        emit(to, "port.disconnect", buildJsonObject { put("portId", portId) })
    }

    /** Context [c] went away (a page closed, a tab navigated): its ports close. */
    /** The contexts matching [gone] are over (a page replaced, an extension stopped): their sockets close with them. */
    private fun removeContexts(gone: (Ctx) -> Boolean) {
        contexts.filter(gone).forEach { sockets.dropped(it) }
        contexts.removeAll(gone)
    }

    internal fun drop(c: Ctx) {
        sockets.dropped(c)
        contexts.remove(c)
        requests.forget(c)
        ports.entries.filter { it.value.a === c || c in it.value.b }.forEach { (id, p) ->
            ports.remove(id)
            val others = (p.b + p.a).filter { it !== c }
            emit(others, "port.disconnect", buildJsonObject { put("portId", id) })
        }
    }

    // ---------------------------------------------------------------- running code in tabs

    /** Runs [body] (a JS function taking chrome, browser, globalThis) as extension [ext] in tab [tabId]'s page. */
    internal fun execInTab(ext: String, tabId: String, body: String, done: (Boolean, JsonElement?, String?) -> Unit) {
        val wv = tabView(tabId) ?: return done(false, null, "No tab with id")
        val callId = ++execSeq
        pendingExec[callId] = done
        wv.evaluateJavascript("window.__miExtExec&&window.__miExtExec(${JsonPrimitive(ext)},${JsonPrimitive(tokens[ext] ?: "")},$callId,$body)") { r ->
            // the page has no extension layer (it started before the extension did, or it's not a web page)
            // (the page says it took the code: its answer comes when it's done, however long it runs)
            if (r != "true") pendingExec.remove(callId)?.invoke(false, null, "Cannot access contents of the page")
        }
    }

    internal fun cssInTab(ext: String, tabId: String, key: String, css: String, remove: Boolean) {
        tabView(tabId)?.evaluateJavascript("window.__miExtCss&&window.__miExtCss(${JsonPrimitive(ext)},${JsonPrimitive(tokens[ext] ?: "")},${JsonPrimitive(key)},${JsonPrimitive(css)},$remove)", null)
    }

    // ---------------------------------------------------------------- tabs as extensions see them

    // read from WebView's network threads too (ExtensionRequests)
    private val tabIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val nextTab = java.util.concurrent.atomic.AtomicInteger(1)
    fun tabInt(id: String) = tabIds.getOrPut(id) { nextTab.getAndIncrement() }
    internal fun tabIntSafe(id: String) = tabInt(id)
    fun tabOf(n: Int): String? = tabIds.entries.firstOrNull { it.value == n }?.key

    fun tabJson(t: TabItem): JsonObject = buildJsonObject {
        put("id", tabInt(t.id)); put("index", vm.tabs.list.indexOf(t).coerceAtLeast(0)); put("windowId", 1)
        put("active", t.id == vm.tabs.activeId); put("highlighted", t.id == vm.tabs.activeId); put("selected", t.id == vm.tabs.activeId)
        put("pinned", t.pinned); put("incognito", t.incognito); put("audible", false); put("discarded", false); put("autoDiscardable", true)
        put("status", if (t.loading) "loading" else "complete")
        put("url", toNative(t.url) ?: ""); put("pendingUrl", toNative(t.requestedUrl ?: t.url) ?: ""); put("title", t.title)
        put("mutedInfo", buildJsonObject { put("muted", false) }); put("groupId", -1)
        put("width", app.resources.displayMetrics.widthPixels); put("height", app.resources.displayMetrics.heightPixels)
    }

    /** Tabs opening, closing, loading and switching: told to every running background as chrome.tabs events. */
    private fun observeTabs() = vm.viewModelScope.launch {
        var before = emptyMap<String, Triple<String?, String, Boolean>>()
        var activeBefore: String? = null
        snapshotFlow { vm.tabs.list.associate { it.id to Triple(it.url, it.title, it.loading) } to vm.tabs.activeId }.collect { (now, active) ->
            val running = store.list.filter { it.enabled }.map { it.id }
            fun all(name: String, data: JsonElement) = running.forEach { emitToPages(it, name, data) }
            for (id in now.keys - before.keys) vm.tabs.list.firstOrNull { it.id == id }?.let { all("tabs.onCreated", JsonArray(listOf(tabJson(it)))) }
            for (id in before.keys - now.keys) {
                all("tabs.onRemoved", JsonArray(listOf(JsonPrimitive(tabInt(id)), buildJsonObject { put("windowId", 1); put("isWindowClosing", false) })))
                contexts.filter { it.tabId == id }.forEach { drop(it) }
                navigation.forget(id)
            }
            for ((id, v) in now) {
                val old = before[id] ?: continue
                if (old == v) continue
                val change = buildJsonObject {
                    if (old.third != v.third) put("status", if (v.third) "loading" else "complete")
                    if (old.first != v.first) put("url", toNative(v.first) ?: "")
                    if (old.second != v.second) put("title", v.second)
                }
                vm.tabs.list.firstOrNull { it.id == id }?.let { all("tabs.onUpdated", JsonArray(listOf(JsonPrimitive(tabInt(id)), change, tabJson(it)))) }
            }
            if (active != activeBefore && active != null) all("tabs.onActivated", JsonArray(listOf(buildJsonObject { put("tabId", tabInt(active)); put("windowId", 1) })))
            before = now; activeBefore = active
        }
    }

    // ---------------------------------------------------------------- alarms

    internal class Alarm(val ext: String, val name: String, var at: Long, val period: Double?, val run: Runnable)
    internal val alarms = HashMap<String, Alarm>()

    internal fun setAlarm(ext: String, name: String, at: Long, periodMinutes: Double?) {
        cancelAlarm("$ext/$name")
        lateinit var alarm: Alarm
        val run = Runnable {
            emitToPages(ext, "alarms.onAlarm", alarmJson(alarm))
            if (alarm.period != null) { alarm.at = System.currentTimeMillis() + (alarm.period!! * 60_000).toLong(); main.postDelayed(alarm.run, (alarm.period!! * 60_000).toLong()) }
            else alarms.remove("$ext/$name")
        }
        alarm = Alarm(ext, name, at, periodMinutes, run)
        alarms["$ext/$name"] = alarm
        main.postDelayed(run, (at - System.currentTimeMillis()).coerceAtLeast(0))
    }

    internal fun cancelAlarm(key: String): Boolean = alarms.remove(key)?.also { main.removeCallbacks(it.run) } != null

    internal fun alarmJson(a: Alarm) = buildJsonObject { put("name", a.name); put("scheduledTime", a.at); a.period?.let { put("periodInMinutes", it) } }

    // ---------------------------------------------------------------- the toolbar button

    /** The extension's button was tapped in the tab menu: its popup opens, or it's told (action.onClicked). */
    fun pressAction(id: String) {
        val a = actions[id] ?: return
        val panel = panelOf(id)
        if (panel != null && (a.panelOnClick || a.popup == null)) { ExtensionFlow.page = OpenPage(id, panel, Kind.POPUP); return }
        if (a.popup != null) { ExtensionFlow.page = OpenPage(id, a.popup, Kind.POPUP); return }
        vm.tabs.current?.let { emitToPages(id, "action.onClicked", JsonArray(listOf(tabJson(it)))) }
    }

    /** The badge for extension [id] on the current tab. */
    fun badgeOf(id: String): String = actions[id]?.let { a -> vm.tabs.activeId?.let { a.tabBadges[it] } ?: a.badge }.orEmpty()

    /** Optional permissions the person allowed (permissions.request), kept with the extension's data. */
    internal fun grantedOf(id: String): Set<String> = (store.savedValue(id, "PERM", "granted") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().toSet()
    internal fun granted(id: String, perm: String) = perm in grantedOf(id)
    internal fun setGranted(id: String, perms: Set<String>) = store.setSavedValue(id, "PERM", "granted", JsonArray(perms.sorted().map(::JsonPrimitive)))

    /** The extension's side panel page (Chrome's side_panel, Firefox's sidebar_action), shown as a sheet; null if none or turned off. */
    fun panelOf(id: String): String? {
        val a = actions[id]
        if (a?.panelEnabled == false) return null
        val raw = infos[id]?.manifest?.raw ?: return a?.panel
        return a?.panel ?: (raw["side_panel"] as? JsonObject)?.get("default_path")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: (raw["sidebar_action"] as? JsonObject)?.get("default_panel")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }

    internal fun updateAction(id: String, change: ActionState.() -> ActionState) {
        actions = actions + (id to (actions[id] ?: ActionState()).change())
    }
}

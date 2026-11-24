// chrome.webNavigation: a tab's page loads, as WebView reports them (TabWebClient calls in), for extensions with the
// webNavigation permission. The main frame's whole life is told: before navigating, committed, DOM ready, completed,
// errors, and in-page changes (history.pushState, #fragment). Frames inside a page are only known where an
// extension's content script runs in them (getAllFrames), which is why webNavigation is rated "might have problems".
package com.jlees.mibrowser.extensions

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

class ExtensionNavigation internal constructor(private val e: ExtensionEngine) {
    /** Per tab: the page committed now, and whether its DOM-ready was told (by its content scripts, or at finish). */
    private val committed = HashMap<String, String>()
    private val domTold = HashSet<String>()

    private fun listeners() = e.store.list.filter { x -> x.enabled && e.infos[x.id]?.manifest?.permissions?.contains("webNavigation") == true }

    private fun details(tabId: String, url: String, extra: JsonObject.() -> Map<String, JsonElement> = { emptyMap() }) = buildJsonObject {
        put("tabId", e.tabInt(tabId)); put("url", toNative(url)!!); put("frameId", 0); put("parentFrameId", -1); put("processId", 1)
        put("timeStamp", System.currentTimeMillis().toDouble()); put("frameType", "outermost_frame"); put("documentLifecycle", "active")
    }.let { JsonObject(it + it.extra()) }

    private fun tell(tabId: String, event: String, d: JsonObject) {
        val scope = e.vm.tabs.list.firstOrNull { it.id == tabId }?.profile
        listeners().filter { e.runsIn(it.id, scope) }.forEach { e.emitToPages(it.id, "webNavigation.$event", JsonArray(listOf(d))) }
    }

    /** A page started loading in [tabId] (WebView's onPageStarted): before it, and committed (WebView reports them together). */
    fun started(tabId: String, url: String?) {
        e.tabs.pageStarted(tabId, url)
        url ?: return
        e.platform.pageCookies(tabId, url, scopeOf(tabId))
        domTold.remove(tabId)
        committed[tabId] = url
        tell(tabId, "onBeforeNavigate", details(tabId, url))
        tell(tabId, "onCommitted", details(tabId, url) { mapOf("transitionType" to JsonPrimitive("link"), "transitionQualifiers" to JsonArray(emptyList())) })
    }

    /** The page's DOM is ready (its content scripts said so; or, at the latest, when it finished). */
    fun domReady(tabId: String, url: String?) {
        url ?: return
        if (!domTold.add(tabId)) return
        tell(tabId, "onDOMContentLoaded", details(tabId, url))
    }

    fun finished(tabId: String, url: String?) {
        url ?: return
        e.platform.pageCookies(tabId, url, scopeOf(tabId))
        domReady(tabId, url)
        tell(tabId, "onCompleted", details(tabId, url))
    }

    fun failed(tabId: String, url: String, error: String) = tell(tabId, "onErrorOccurred", details(tabId, url) { mapOf("error" to JsonPrimitive(error)) })

    /** The address changed without a new page: a #fragment, or the page's own history (pushState). */
    fun addressChanged(tabId: String, url: String?) {
        url ?: return
        e.platform.pageCookies(tabId, url, scopeOf(tabId))
        val before = committed[tabId] ?: return
        if (before == url) return
        committed[tabId] = url
        val event = if (before.substringBefore('#') == url.substringBefore('#')) "onReferenceFragmentUpdated" else "onHistoryStateUpdated"
        tell(tabId, event, details(tabId, url) { mapOf("transitionType" to JsonPrimitive("link"), "transitionQualifiers" to JsonArray(emptyList())) })
    }

    /** A page opened a new window (a popup tab). */
    fun createdTarget(sourceTabId: String, newTabId: String, url: String) = tell(sourceTabId, "onCreatedNavigationTarget", buildJsonObject {
        put("sourceTabId", e.tabInt(sourceTabId)); put("sourceProcessId", 1); put("sourceFrameId", 0)
        put("tabId", e.tabInt(newTabId)); put("url", url); put("timeStamp", System.currentTimeMillis().toDouble())
    })

    fun forget(tabId: String) { committed.remove(tabId); domTold.remove(tabId); e.platform.forgetTab(tabId) }

    private fun scopeOf(tabId: String) = e.vm.tabs.list.firstOrNull { it.id == tabId }?.profile

    // ---- API calls

    fun handle(name: String, a: JsonArray, done: Done) {
        val o = a.getOrNull(0) as? JsonObject ?: JsonObject(emptyMap())
        val tabId = (o["tabId"] as? JsonPrimitive)?.intOrNull?.let(e::tabOf) ?: return done(true, if (name == "webNavigation.getAllFrames") JsonArray(emptyList()) else null, null)
        val url = committed[tabId] ?: e.vm.tabs.list.firstOrNull { it.id == tabId }?.url ?: return done(true, null, null)
        val main = buildJsonObject { put("frameId", 0); put("parentFrameId", -1); put("processId", 1); put("url", url); put("errorOccurred", false); put("frameType", "outermost_frame") }
        when (name) {
            "webNavigation.getFrame" -> {
                val frameId = (o["frameId"] as? JsonPrimitive)?.intOrNull ?: 0
                if (frameId == 0) return done(true, main, null)
                val f = framesOf(tabId).getOrNull(frameId - 1) ?: return done(true, null, null)
                done(true, buildJsonObject { put("frameId", frameId); put("parentFrameId", 0); put("processId", 1); put("url", f); put("errorOccurred", false); put("frameType", "sub_frame") }, null)
            }
            "webNavigation.getAllFrames" -> done(true, buildJsonArray {
                add(main)
                framesOf(tabId).forEachIndexed { i, f -> add(buildJsonObject { put("frameId", i + 1); put("parentFrameId", 0); put("processId", 1); put("url", f); put("errorOccurred", false); put("frameType", "sub_frame") }) }
            }, null)
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    /** Frames inside [tabId]'s page that mi knows of: those where an extension's content script runs. */
    private fun framesOf(tabId: String) = e.contexts.filter { it.tabId == tabId && it.kind == ExtensionEngine.Kind.CONTENT && !it.top }.map { it.url }.distinct()
}

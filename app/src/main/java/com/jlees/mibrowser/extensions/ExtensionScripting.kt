// Content scripts an extension adds while running (chrome.scripting.registerContentScripts and Firefox's
// contentScripts.register), and chrome.privacy's settings. Registered scripts join the extension's manifest ones in
// every tab (ExtensionTabs), from each tab's next page; Chrome's are kept across restarts unless the extension says not
// to. Privacy settings mi has itself (third-party cookies, HTTPS Only) are applied; the rest are remembered and reported.
package com.jlees.mibrowser.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** A content script registered while running. js/css are the extension's file paths, or inline code ([INLINE] + code). */
@Serializable
data class DynamicScript(
    val id: String,
    val matches: List<String>,
    val excludeMatches: List<String> = emptyList(),
    val includeGlobs: List<String> = emptyList(),
    val excludeGlobs: List<String> = emptyList(),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    val runAt: String = "document_idle",
    val allFrames: Boolean = false,
    val mainWorld: Boolean = false,
    val persist: Boolean = true,
) {
    fun asContentScript() = ContentScript(matches, excludeMatches, includeGlobs, excludeGlobs, js, css, runAt, allFrames, mainWorld)
}

/** Marks inline code (Firefox's contentScripts.register can pass code, not only files). */
const val INLINE = "\u0000code:"

internal class ExtensionScripting(private val e: ExtensionEngine) {
    private val scripts = ConcurrentHashMap<String, List<DynamicScript>>()
    private val ser = ListSerializer(DynamicScript.serializer())
    private var loaded = false

    /** The extension's registered scripts, as content scripts (read on a background thread when tab scripts are built). */
    fun contentScriptsOf(ext: String): List<ContentScript> { load(); return scripts[ext].orEmpty().map { it.asContentScript() } }

    private fun load() {
        if (loaded) return
        loaded = true
        for (x in e.store.list) e.store.savedValue(x.id, "DYN", "scripts")?.let { j -> runCatching { e.json.decodeFromJsonElement(ser, j) }.getOrNull()?.let { scripts[x.id] = it } }
    }

    private fun save(ext: String) {
        load()
        val keep = scripts[ext].orEmpty().filter { it.persist }
        e.main.post { e.store.setSavedValue(ext, "DYN", "scripts", e.json.encodeToJsonElement(ser, keep)) }
    }

    fun forget(ext: String) { scripts.remove(ext) }
    /** A new run of the extension (mi started, or it was turned on again): only scripts kept across sessions remain. */
    fun keepPersistent(ext: String) { load(); scripts[ext] = scripts[ext].orEmpty().filter { it.persist } }


    private fun fromChrome(o: JsonObject, old: DynamicScript? = null) = DynamicScript(
        id = o["id"].str() ?: old?.id ?: "",
        matches = (o["matches"] as? JsonArray)?.let { it.strings() } ?: old?.matches.orEmpty(),
        excludeMatches = (o["excludeMatches"] as? JsonArray)?.let { it.strings() } ?: old?.excludeMatches.orEmpty(),
        includeGlobs = (o["includeGlobs"] as? JsonArray)?.let { it.strings() } ?: old?.includeGlobs.orEmpty(),
        excludeGlobs = (o["excludeGlobs"] as? JsonArray)?.let { it.strings() } ?: old?.excludeGlobs.orEmpty(),
        js = (o["js"] as? JsonArray)?.let { it.strings() } ?: old?.js.orEmpty(),
        css = (o["css"] as? JsonArray)?.let { it.strings() } ?: old?.css.orEmpty(),
        runAt = o["runAt"].str() ?: old?.runAt ?: "document_idle",
        allFrames = o["allFrames"].bool() ?: old?.allFrames ?: false,
        mainWorld = o["world"].str()?.let { it == "MAIN" } ?: old?.mainWorld ?: false,
        persist = o["persistAcrossSessions"].bool() ?: old?.persist ?: true,
    )

    private fun toChrome(d: DynamicScript) = buildJsonObject {
        put("id", d.id); put("matches", JsonArray(d.matches.map(::JsonPrimitive))); put("excludeMatches", JsonArray(d.excludeMatches.map(::JsonPrimitive)))
        put("js", JsonArray(d.js.filterNot { it.startsWith(INLINE) }.map(::JsonPrimitive))); put("css", JsonArray(d.css.filterNot { it.startsWith(INLINE) }.map(::JsonPrimitive)))
        put("runAt", d.runAt); put("allFrames", d.allFrames); put("world", if (d.mainWorld) "MAIN" else "ISOLATED"); put("persistAcrossSessions", d.persist)
    }

    /** Firefox: js and css as [{file} or {code}]. */
    private fun items(j: JsonElement?) = (j as? JsonArray)?.mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        o["file"].str() ?: o["code"].str()?.let { INLINE + it }
    }.orEmpty()

    fun handle(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        load()
        val ext = c.ext
        val mine = scripts[ext].orEmpty()
        when (name) {
            "scripting.registerContentScripts" -> {
                val add = (a.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::fromChrome) }
                if (add.any { it.id.isEmpty() || it.id.startsWith("_") }) return done(false, null, "A content script needs an id that doesn't start with _")
                add.firstOrNull { n -> mine.any { it.id == n.id } }?.let { return done(false, null, "Duplicate script ID '${it.id}'") }
                scripts[ext] = mine + add; changed(ext); done(true, null, null)
            }
            "scripting.updateContentScripts" -> {
                val upd = (a.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                scripts[ext] = mine.map { old -> upd.firstOrNull { it["id"].str() == old.id }?.let { fromChrome(it, old) } ?: old }
                changed(ext); done(true, null, null)
            }
            "scripting.getRegisteredContentScripts" -> {
                val ids = (a.getOrNull(0) as? JsonObject)?.get("ids").strings().toSet()
                done(true, JsonArray(mine.filter { !it.id.startsWith("ff:") && (ids.isEmpty() || it.id in ids) }.map(::toChrome)), null)
            }
            "scripting.unregisterContentScripts" -> {
                val ids = (a.getOrNull(0) as? JsonObject)?.get("ids").strings().toSet()
                scripts[ext] = mine.filterNot { !it.id.startsWith("ff:") && (ids.isEmpty() || it.id in ids) }
                changed(ext); done(true, null, null)
            }
            "contentScripts.register" -> {
                val o = a.getOrNull(0) as? JsonObject ?: return done(false, null, "No script")
                val d = DynamicScript(
                    id = "ff:" + com.jlees.mibrowser.logic.uniqueId(), matches = o["matches"].strings(), excludeMatches = o["excludeMatches"].strings(),
                    includeGlobs = o["includeGlobs"].strings(), excludeGlobs = o["excludeGlobs"].strings(), js = items(o["js"]), css = items(o["css"]),
                    runAt = o["runAt"].str() ?: "document_idle", allFrames = o["allFrames"].bool() ?: false, persist = false,
                )
                scripts[ext] = mine + d; changed(ext); done(true, JsonPrimitive(d.id), null)
            }
            "contentScripts.unregister" -> { scripts[ext] = mine.filterNot { it.id == a.getOrNull(0).str() }; changed(ext); done(true, null, null) }
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    private fun changed(ext: String) { save(ext); e.rebuildTabScripts() }

    // ---------------------------------------------------------------- chrome.privacy

    /** Settings set by extensions (key → value), for the ones mi doesn't apply itself. */
    private val privacy = ConcurrentHashMap<String, JsonElement>()

    /** A privacy setting's current value: mi's own where it has one, else what an extension set, else the default. */
    private fun privacyValue(key: String): JsonElement = when (key) {
        "websites.thirdPartyCookiesAllowed" -> JsonPrimitive(!e.vm.settings.blockThirdPartyCookies && !e.vm.settings.blockCookies)
        "network.httpsOnlyMode" -> JsonPrimitive(if (e.vm.settings.httpsOnly) "always" else "never")
        "services.passwordSavingEnabled", "services.autofillEnabled", "services.autofillAddressEnabled", "services.autofillCreditCardEnabled" -> JsonPrimitive(false)
        "network.webRTCIPHandlingPolicy" -> privacy[key] ?: JsonPrimitive("default")
        "websites.trackingProtectionMode" -> privacy[key] ?: JsonPrimitive(if (e.vm.settings.blockTrackers) "always" else "never")
        "websites.cookieConfig" -> privacy[key] ?: buildJsonObject { put("behavior", if (e.vm.settings.blockThirdPartyCookies) "reject_third_party" else "allow_all"); put("nonPersistentCookies", false) }
        else -> privacy[key] ?: JsonPrimitive(key !in setOf("websites.doNotTrackEnabled", "websites.resistFingerprinting", "websites.firstPartyIsolate"))
    }

    fun handlePrivacy(name: String, a: JsonArray, done: Done) {
        val key = a.getOrNull(0).str() ?: return done(false, null, "No setting")
        when (name) {
            "privacy.get" -> done(true, buildJsonObject {
                put("value", privacyValue(key))
                put("levelOfControl", if (privacy.containsKey(key)) "controlled_by_this_extension" else "controllable_by_this_extension")
            }, null)
            "privacy.set" -> {
                val v = (a.getOrNull(1) as? JsonObject)?.get("value") ?: return done(false, null, "No value")
                // the ones mi has: changed for real (an extension asking for it is the person's choice, made by installing it)
                when (key) {
                    "websites.thirdPartyCookiesAllowed" -> v.bool()?.let { allowed -> e.vm.updateSettings { copy(blockThirdPartyCookies = !allowed) } }
                    "network.httpsOnlyMode" -> v.str()?.let { mode -> e.vm.updateSettings { copy(httpsOnly = mode != "never") } }
                    else -> privacy[key] = v
                }
                if (key == "websites.thirdPartyCookiesAllowed" || key == "network.httpsOnlyMode") privacy[key] = v
                e.emit(e.contexts.filter { it.kind != ExtensionEngine.Kind.CONTENT }, "privacy.changed", buildJsonObject { put("key", key); put("value", privacyValue(key)) })
                done(true, JsonPrimitive(true), null)
            }
            "privacy.clear" -> { privacy.remove(key); done(true, JsonPrimitive(true), null) }
            else -> done(false, null, "$name is not supported in mi.")
        }
    }
}

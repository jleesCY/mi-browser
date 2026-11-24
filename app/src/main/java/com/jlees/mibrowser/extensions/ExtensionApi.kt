// Answers the API calls extensions make (assets/js/ext-api.js sends them here): storage, messaging and ports, tabs,
// scripting, alarms, the toolbar button, notifications, permissions, and requests to other sites made through mi. Each
// call ends in done(ok, value, error), possibly later (a message answered, a request finished).
package com.jlees.mibrowser.extensions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Base64
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

typealias Done = (ok: Boolean, value: JsonElement?, error: String?) -> Unit

private val DYNAMIC_SCRIPTS = setOf("scripting.registerContentScripts", "scripting.updateContentScripts", "scripting.getRegisteredContentScripts",
    "scripting.unregisterContentScripts", "contentScripts.register", "contentScripts.unregister")

/** ExtensionPlatform's namespaces, and the permission each needs (null: none). Never callable from content scripts. */
private val PLATFORM = mapOf("cookies" to "cookies", "identity" to "identity", "sidePanel" to null, "idle" to "idle", "dns" to "dns",
    "management" to null, "tabsx" to null, "browsingData" to "browsingData", "downloads" to "downloads", "offscreen" to "offscreen")

internal class ExtensionApi(private val e: ExtensionEngine) {
    private val vm get() = e.vm

    private fun areaOf(name: String?) = when (name) { "sync" -> StorageArea.SYNC; "session" -> StorageArea.SESSION; else -> StorageArea.LOCAL }

    /** Its native addresses (moz-extension://<id>/…) in what it passes in, as mi's (messages and stored data stay as they are). */
    private fun fromNativeIn(j: JsonElement): JsonElement = when (j) {
        is JsonPrimitive -> if (j.isString) fromNative(j.content)?.let(::JsonPrimitive) ?: j else j
        is JsonArray -> JsonArray(j.map(::fromNativeIn))
        is JsonObject -> JsonObject(j.mapValues { fromNativeIn(it.value) })
        else -> j
    }
    private val VERBATIM = setOf("runtime.sendMessage", "tabs.sendMessage", "port.post", "storage.set", "storage.remove", "permissions.request", "permissions.contains", "ws.send")

    fun handle(c: ExtensionEngine.Ctx, name: String, args: JsonArray, done: Done) {
        val ext = c.ext
        val a = if (name in VERBATIM) args else fromNativeIn(args) as JsonArray
        try {
            if (name.startsWith("webRequest.") || name.startsWith("dnr.")) return e.requests.handle(c, name, a, done)
            if (name in DYNAMIC_SCRIPTS) return e.scripting.handle(c, name, a, done)
            if (name.startsWith("privacy.")) return e.scripting.handlePrivacy(c, name, a, done)
            if (name.startsWith("webNavigation.")) return e.navigation.handle(name, a, done)
            if (name.startsWith("ws.")) return e.sockets.handle(c, name, a, done)
            if (name == "content.run") return if (c.kind == ExtensionEngine.Kind.CONTENT) e.tabs.runEntry(c, a.getOrNull(0).int() ?: -1, a.getOrNull(1).str().orEmpty(), done) else done(false, null, "Content scripts only")
            val ns = name.substringBefore('.')
            if (ns == "menus") {
                val m = e.infos[ext]?.manifest
                if (c.kind == ExtensionEngine.Kind.CONTENT) return done(false, null, "$name isn't available to content scripts.")
                if (listOf("contextMenus", "menus").none { m?.permissions?.contains(it) == true || e.granted(ext, it) }) return done(false, null, "$name needs the \"contextMenus\" permission.")
                return e.menus.handle(c, name, a, done)
            }
            if (ns in PLATFORM) {
                if (c.kind == ExtensionEngine.Kind.CONTENT) return done(false, null, "$name isn't available to content scripts.")
                val perm = PLATFORM[ns]
                val m = e.infos[ext]?.manifest
                if (perm != null && m?.permissions?.contains(perm) != true && !e.granted(ext, perm)) return done(false, null, "$name needs the \"$perm\" permission.")
                return e.platform.handle(c, name, a, done)
            }
            when (name) {
                // ---- storage
                "storage.get" -> done(true, JsonObject(e.store.read(ext, areaOf(a.getOrNull(0).str()), c.scope)), null)
                "storage.set" -> storageWrite(c, a.getOrNull(0).str(), a.getOrNull(1).obj(), emptyList(), false, done)
                "storage.remove" -> storageWrite(c, a.getOrNull(0).str(), emptyMap(), (a.getOrNull(1) as? JsonArray)?.mapNotNull { it.str() }.orEmpty(), false, done)
                "storage.clear" -> storageWrite(c, a.getOrNull(0).str(), emptyMap(), emptyList(), true, done)
                "storage.bytes" -> done(true, JsonPrimitive(e.store.bytesInUse(ext, areaOf(a.getOrNull(0).str()), c.scope)), null)

                // ---- messaging
                "runtime.sendMessage" -> {
                    val target = a.getOrNull(0).str()
                    if (target != null && target != ext) return done(false, null, "Messages to other extensions aren't supported in mi.")
                    e.sendRuntimeMessage(c, a.getOrNull(1) ?: JsonNull) { v, ok -> if (ok == false) done(false, null, v.str() ?: "Error") else done(true, v, null) }
                }
                "port.connect" -> { e.connect(c, a.getOrNull(0).str() ?: return, a.getOrNull(1).str().orEmpty(), a.getOrNull(2).int()?.let { e.tabOf(it) }); done(true, null, null) }
                "port.post" -> { e.portPost(c, a.getOrNull(0).str() ?: return, a.getOrNull(1) ?: JsonNull); done(true, null, null) }
                "port.disconnect" -> { e.portDisconnect(c, a.getOrNull(0).str() ?: return); done(true, null, null) }
                "runtime.openOptionsPage" -> {
                    val page = e.infos[ext]?.manifest?.optionsPage ?: return done(false, null, "This extension has no options page")
                    ExtensionFlow.page = OpenPage(ext, page, ExtensionEngine.Kind.OPTIONS); done(true, null, null)
                }
                "runtime.reload" -> { e.main.post { e.refresh(ext) }; done(true, null, null) }

                // ---- tabs
                "tabs.query" -> done(true, JsonArray(queryTabs(a.getOrNull(0).obj(), c).map { e.tabJson(it) }), null)
                "tabs.getCurrent" -> done(true, c.tabId?.let { id -> vm.tabs.list.firstOrNull { it.id == id } }?.let { e.tabJson(it) }, null)
                "tabs.get" -> tab(a.getOrNull(0).int())?.let { done(true, e.tabJson(it), null) } ?: done(false, null, "No tab with id: ${a.getOrNull(0)}")
                "tabs.create" -> {
                    val o = a.getOrNull(0).obj()
                    val url = o["url"].str()?.let { resolve(ext, it) }
                    if (o["active"].bool() == false && url != null) vm.addBackgroundTab(url)
                    // a tab it opens is shown, over the Library, the switcher or its popup (a browser's popup closes as its tab opens)
                    else { vm.addNewTab(url); vm.activeView = com.jlees.mibrowser.Sheet.NONE; vm.switcherVisible = false; ExtensionFlow.page = null; ExtensionFlow.opened = null }
                    val t = if (o["active"].bool() == false) vm.tabs.list.lastOrNull() else vm.tabs.current
                    done(true, t?.let { e.tabJson(it) }, null)
                }
                "tabs.update" -> {
                    val t = tab(a.getOrNull(0).int()) ?: vm.tabs.current ?: return done(false, null, "No tab")
                    val o = a.getOrNull(1).obj()
                    o["url"].str()?.let { vm.loadInTab(t.id, resolve(ext, it)) }
                    if (o["active"].bool() == true) { vm.setActiveTab(t.id); vm.switcherVisible = false }
                    o["pinned"].bool()?.let { p -> if (p != t.pinned) vm.tabs.update(t.id) { copy(pinned = p) } }
                    done(true, vm.tabs.list.firstOrNull { it.id == t.id }?.let { e.tabJson(it) }, null)
                }
                "tabs.remove" -> { vm.closeTabs((a.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { it.int()?.let(e::tabOf) }.toSet()); done(true, null, null) }
                "tabs.reload" -> { (tab(a.getOrNull(0).int()) ?: vm.tabs.current)?.let { e.tabView(it.id)?.reload() }; done(true, null, null) }
                "tabs.sendMessage" -> {
                    val t = tab(a.getOrNull(0).int()) ?: return done(false, null, "No tab with id: ${a.getOrNull(0)}")
                    val frame = a.getOrNull(2).obj()["frameId"].int()
                    e.sendTabMessage(c, t.id, frame == 0, a.getOrNull(1) ?: JsonNull) { v, ok -> if (ok == false) done(false, null, v.str() ?: "Error") else done(true, v, null) }
                }
                "tabs.executeScript" -> {
                    val t = tab(a.getOrNull(0).int()) ?: vm.tabs.current ?: return done(false, null, "No tab")
                    val d = a.getOrNull(1).obj()
                    val code = d["code"].str() ?: d["file"].str()?.let { readFile(ext, it) } ?: return done(false, null, "Nothing to run")
                    // tabs.executeScript answers with an array of results, one per frame
                    e.execInTab(ext, t.id, valueOf(code)) { ok, v, err -> if (ok) done(true, JsonArray(listOf(v ?: JsonNull)), null) else done(false, null, err) }
                }
                "tabs.insertCSS", "tabs.removeCSS" -> {
                    val t = tab(a.getOrNull(0).int()) ?: vm.tabs.current ?: return done(false, null, "No tab")
                    val d = a.getOrNull(1).obj()
                    val css = d["code"].str() ?: d["file"].str()?.let { readFile(ext, it)?.let { c -> extensionCss(c, ext) } } ?: ""
                    e.cssInTab(ext, t.id, css.hashCode().toString(), css, name == "tabs.removeCSS"); done(true, null, null)
                }

                // ---- scripting (Manifest V3)
                "scripting.executeScript" -> {
                    val d = a.getOrNull(0).obj()
                    val t = tab(d["target"].obj()["tabId"].int()) ?: return done(false, null, "No tab with that id")
                    // a function's result (code: "return f(…)"), or its files' last value
                    val body = d["code"].str()?.let { code ->
                        if (d["world"].str() == "MAIN") "function(){return (function(){return $code\n}).call(window)}"
                        else "function(chrome,browser,globalThis,self,WebSocket){return (function(){return ${isolatedWorld(code)}\n}).call(window)}"
                    } ?: (d["files"] as? JsonArray)?.mapNotNull { f -> f.str()?.let { readFile(ext, it) } }?.joinToString("\n;\n")?.let { valueOf(it, main = d["world"].str() == "MAIN") }
                        ?: return done(false, null, "Nothing to run")
                    e.execInTab(ext, t.id, body) { ok, v, err ->
                        if (ok) done(true, buildJsonArray { add(buildJsonObject { put("frameId", 0); put("result", v ?: JsonNull); put("documentId", t.id) }) }, null) else done(false, null, err)
                    }
                }
                "scripting.insertCSS", "scripting.removeCSS" -> {
                    val d = a.getOrNull(0).obj()
                    val t = tab(d["target"].obj()["tabId"].int()) ?: return done(false, null, "No tab with that id")
                    val css = d["css"].str() ?: (d["files"] as? JsonArray)?.mapNotNull { f -> f.str()?.let { readFile(ext, it)?.let { c -> extensionCss(c, ext) } } }?.joinToString("\n") ?: ""
                    e.cssInTab(ext, t.id, css.hashCode().toString(), css, name == "scripting.removeCSS"); done(true, null, null)
                }

                // ---- alarms
                "alarms.create" -> {
                    val alarmName = a.getOrNull(0).str().orEmpty(); val info = a.getOrNull(1).obj()
                    val period = (info["periodInMinutes"] as? JsonPrimitive)?.doubleOrNull
                    val delay = (info["delayInMinutes"] as? JsonPrimitive)?.doubleOrNull
                    val now = System.currentTimeMillis()
                    val at = (info["when"] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: (now + ((delay ?: period ?: 0.0) * 60_000).toLong())
                    e.setAlarm(ext, alarmName, at, period); done(true, null, null)
                }
                "alarms.get" -> done(true, e.alarms["$ext/${a.getOrNull(0).str().orEmpty()}"]?.let { e.alarmJson(it) }, null)
                "alarms.getAll" -> done(true, JsonArray(e.alarms.values.filter { it.ext == ext }.map { e.alarmJson(it) }), null)
                "alarms.clear" -> done(true, JsonPrimitive(e.cancelAlarm("$ext/${a.getOrNull(0).str().orEmpty()}")), null)
                "alarms.clearAll" -> { val keys = e.alarms.keys.filter { it.startsWith("$ext/") }; keys.forEach { e.cancelAlarm(it) }; done(true, JsonPrimitive(keys.isNotEmpty()), null) }

                // ---- the toolbar button
                "action.setBadgeText" -> {
                    val o = a.getOrNull(0).obj(); val text = o["text"].str().orEmpty()
                    val tabId = o["tabId"].int()?.let(e::tabOf)
                    e.updateAction(ext) { if (tabId != null) copy(tabBadges = tabBadges + (tabId to text)) else copy(badge = text, tabBadges = emptyMap()) }; done(true, null, null)
                }
                "action.getBadgeText" -> done(true, JsonPrimitive(e.actions[ext]?.let { s -> a.getOrNull(0).obj()["tabId"].int()?.let(e::tabOf)?.let { s.tabBadges[it] } ?: s.badge }.orEmpty()), null)
                "action.setBadgeBackgroundColor" -> { e.updateAction(ext) { copy(badgeColor = colorOf(a.getOrNull(0).obj()["color"])) }; done(true, null, null) }
                "action.setTitle" -> { e.updateAction(ext) { copy(title = a.getOrNull(0).obj()["title"].str()) }; done(true, null, null) }
                "action.getTitle" -> done(true, JsonPrimitive(e.actions[ext]?.title.orEmpty()), null)
                "action.setIcon" -> {
                    val path = a.getOrNull(0).obj()["path"].let { p -> p.str() ?: (p as? JsonObject)?.entries?.minByOrNull { (k, _) -> kotlin.math.abs((k.toIntOrNull() ?: 0) - 32) }?.value.str() }
                    if (path != null) e.updateAction(ext) { copy(icon = relativeTo(c, path)) }; done(true, null, null)
                }
                "action.setPopup" -> { e.updateAction(ext) { copy(popup = a.getOrNull(0).obj()["popup"].str()?.takeIf { it.isNotBlank() }?.let { relativeTo(c, it) }) }; done(true, null, null) }
                "action.getPopup" -> done(true, JsonPrimitive(e.actions[ext]?.popup?.let { toNative(resolve(ext, it)) }.orEmpty()), null)
                "action.enable", "action.disable" -> { e.updateAction(ext) { copy(enabled = name == "action.enable") }; done(true, null, null) }
                "action.openPopup" -> { e.actions[ext]?.popup?.let { ExtensionFlow.page = OpenPage(ext, it, ExtensionEngine.Kind.POPUP) }; done(true, null, null) }

                // ---- notifications, permissions
                "notifications.create" -> done(true, JsonPrimitive(notify(ext, a.getOrNull(0).str().orEmpty(), a.getOrNull(1).obj())), null)
                "notifications.clear" -> { notifications().cancel(notificationTag(ext, a.getOrNull(0).str().orEmpty()), 0); done(true, JsonPrimitive(true), null) }
                "notifications.getAll" -> done(true, JsonObject(emptyMap()), null)
                "permissions.contains" -> {
                    val m = e.infos[ext]?.manifest ?: return done(true, JsonPrimitive(false), null)
                    val o = a.getOrNull(0).obj()
                    val perms = (o["permissions"] as? JsonArray).orEmpty().mapNotNull { it.str() }
                    val origins = (o["origins"] as? JsonArray).orEmpty().mapNotNull { it.str() }
                    val have = m.permissions + e.grantedOf(ext)
                    done(true, JsonPrimitive(perms.all { it in have } && origins.all { or -> m.hostPermissions.any { it == or || it == "<all_urls>" || matchesPattern(it, or.replace("*", "x")) } }), null)
                }
                "permissions.getAll" -> {
                    val m = e.infos[ext]?.manifest
                    done(true, buildJsonObject { put("permissions", JsonArray((m?.permissions.orEmpty() + e.grantedOf(ext)).distinct().map { JsonPrimitive(it) })); put("origins", JsonArray(m?.hostPermissions.orEmpty().map { JsonPrimitive(it) })) }, null)
                }
                // an optional permission: the person is asked (only those the manifest lists as optional can be asked for)
                "permissions.request" -> {
                    val m = e.infos[ext]?.manifest ?: return done(false, null, "No extension")
                    val perms = (a.getOrNull(0).obj()["permissions"] as? JsonArray).orEmpty().mapNotNull { it.str() }
                    val missing = perms.filter { it !in m.permissions && !e.granted(ext, it) }
                    if (missing.any { it !in m.optionalPermissions }) return done(false, null, "Only permissions listed in optional_permissions can be requested.")
                    if (missing.isEmpty()) return done(true, JsonPrimitive(true), null)
                    val name0 = e.infos[ext]?.name ?: "This extension"
                    vm.showAlert("Allow $name0 More?", "It would like to:\n" + missing.map(::permissionWords).distinct().joinToString("\n") { "• $it" }, listOf(
                        com.jlees.mibrowser.AlertButton("Don't Allow", com.jlees.mibrowser.ButtonStyle.CANCEL) { done(true, JsonPrimitive(false), null) },
                        com.jlees.mibrowser.AlertButton("Allow") {
                            e.setGranted(ext, e.grantedOf(ext) + missing)
                            e.emitToPages(ext, "permissions.onAdded", buildJsonObject { put("permissions", JsonArray(missing.map(::JsonPrimitive))); put("origins", JsonArray(emptyList())) })
                            done(true, JsonPrimitive(true), null)
                        },
                    ), onCancel = { done(true, JsonPrimitive(false), null) })
                }
                "permissions.remove" -> {
                    val perms = (a.getOrNull(0).obj()["permissions"] as? JsonArray).orEmpty().mapNotNull { it.str() }.toSet()
                    val gone = e.grantedOf(ext).intersect(perms)
                    if (gone.isNotEmpty()) {
                        e.setGranted(ext, e.grantedOf(ext) - gone)
                        e.emitToPages(ext, "permissions.onRemoved", buildJsonObject { put("permissions", JsonArray(gone.map(::JsonPrimitive))); put("origins", JsonArray(emptyList())) })
                    }
                    done(true, JsonPrimitive(gone.isNotEmpty()), null)
                }

                // ---- a content script adding one of its own files to the page as a script: run in the page by mi, as a site's
                // security rules would refuse to load it (ext-content.js)
                "page.injectScript" -> {
                    val url = a.getOrNull(0).str() ?: return done(false, null, "No script")
                    val tabId = c.tabId ?: return done(false, null, "Not in a tab")
                    if (extIdOf(url) != ext) return done(false, null, "Not this extension's file")
                    val code = readFile(ext, java.net.URI(url).path ?: "") ?: return done(false, null, "No such file")
                    val wv = e.tabView(tabId) ?: return done(false, null, "No tab")
                    // document.currentScript is the script element while it runs, as in a browser; the file runs as the page's own script
                    wv.evaluateJavascript("Object.defineProperty(document,'currentScript',{configurable:true,get:function(){return window.__miExtCurrentScript||null}})", null)
                    wv.evaluateJavascript("$code\n//# sourceURL=$url", null)
                    wv.evaluateJavascript("delete document.currentScript;delete window.__miExtCurrentScript") { done(true, null, null) }
                }

                // ---- requests to other sites (an extension's own pages)
                "net.fetch" -> fetch(c, a.getOrNull(0).obj(), done)

                else -> done(false, null, "$name is not supported in mi.")
            }
        } catch (t: Throwable) { done(false, null, t.message ?: t.javaClass.simpleName) }
    }

    private fun storageWrite(c: ExtensionEngine.Ctx, areaName: String?, values: Map<String, JsonElement>, removed: List<String>, clear: Boolean, done: Done) {
        val area = areaOf(areaName)
        val changes = e.store.write(c.ext, area, c.scope, values, removed, clear)
        done(true, null, null)
        if (changes.isEmpty()) return
        // every context of the extension hears of it (chrome.storage.onChanged), the one that changed it too
        val data = buildJsonObject {
            put("area", area.name.lowercase())
            put("changes", buildJsonObject { changes.forEach { (k, v) -> put(k, buildJsonObject { v.first?.let { put("oldValue", it) }; v.second?.let { put("newValue", it) } }) } })
        }
        e.emit(e.contexts.filter { it.ext == c.ext && it.scope == c.scope }, "storage.changed", data)
    }

    /** tabs.query: mi's tabs filtered as asked ("currentWindow" and the like are all of them: mi has one window). */
    private fun queryTabs(q: JsonObject, c: ExtensionEngine.Ctx) = vm.tabs.list.filter { t ->
        (q["active"].bool()?.let { it == (t.id == vm.tabs.activeId) } ?: true) &&
            (q["highlighted"].bool()?.let { it == (t.id == vm.tabs.activeId) } ?: true) &&
            (q["pinned"].bool()?.let { it == t.pinned } ?: true) &&
            (q["status"].str()?.let { it == (if (t.loading) "loading" else "complete") } ?: true) &&
            (q["title"].str()?.let { globMatches(it, t.title) } ?: true) &&
            (q["url"].let { u -> val pats = (u as? JsonArray)?.mapNotNull { it.str() } ?: listOfNotNull(u.str()); pats.isEmpty() || pats.any { p -> t.url != null && matchesPattern(p, t.url) } }) &&
            (q["incognito"].bool()?.let { it == t.incognito } ?: (c.scope != null || !t.incognito || e.store[c.ext]?.incognito == true))
    }

    private fun tab(n: Int?) = n?.let(e::tabOf)?.let { id -> vm.tabs.list.firstOrNull { it.id == id } }

    /** A path an extension's page gives ("../icons/a.png"), as a path inside the extension: relative to that page, as in a browser. */
    private fun relativeTo(c: ExtensionEngine.Ctx, path: String): String =
        runCatching { java.net.URI(if (extIdOf(c.url) == c.ext) c.url else "${extOrigin(c.ext)}/").resolve(path.replace(" ", "%20")).path.trimStart('/') }.getOrDefault(path)

    /** An address an extension gives: its own files are relative to its origin. */
    /**
     * [code] as the function execInTab runs, answering with its last value (as Chrome's executeScript does): run by eval,
     * which keeps that value with chrome and browser in scope; where the page's policy forbids eval, run as it is (it
     * runs, but the value is lost). [main]: as the page's own script, without extension variables.
     */
    private fun valueOf(source: String, main: Boolean = false): String {
        val code = if (main) source else isolatedWorld(source)
        val src = JsonPrimitive(code).toString()
        val params = if (main) "" else "chrome,browser,globalThis,self,WebSocket"
        return "function($params){try{return eval($src)}catch(e){if(!(e instanceof EvalError))throw e;return (function(){$code\n}).call(window)}}"
    }

    private fun resolve(ext: String, url: String) = if (Regex("^[a-z][a-z0-9+.-]*:").containsMatchIn(url)) url else "${extOrigin(ext)}/${url.trimStart('/')}"

    private fun readFile(ext: String, path: String): String? {
        val dir = e.dirOf(ext).canonicalFile
        val f = File(dir, path.substringAfter(extOrigin(ext)).trimStart('/')).canonicalFile
        return if (f.path.startsWith(dir.path) && f.isFile) f.readText() else null
    }

    private fun colorOf(c: JsonElement?): Long? = when (c) {
        is JsonArray -> c.mapNotNull { it.int() }.takeIf { it.size >= 3 }?.let { (r, g, b) -> 0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong() }
        is JsonPrimitive -> c.contentOrNull?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }
        else -> null
    }

    // ---- notifications

    private fun notifications() = e.app.getSystemService(NotificationManager::class.java)
    private fun notificationTag(ext: String, id: String) = "ext:$ext:$id"

    private fun notify(ext: String, id: String, o: JsonObject): String {
        val nid = id.ifEmpty { "n" + System.currentTimeMillis() }
        val nm = notifications()
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("extensions", "Extensions", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Notifications from your extensions" })
        val name = e.infos[ext]?.name ?: "Extension"
        val n = NotificationCompat.Builder(e.app, "extensions")
            .setSmallIcon(R.drawable.sym_extension)
            .setContentTitle(o["title"].str() ?: name)
            .setContentText(o["message"].str().orEmpty())
            .setSubText(name)
            .setAutoCancel(true)
            .setContentIntent(com.jlees.mibrowser.MainActivity.extensionNotificationIntent(e.app, ext, nid))
            .build()
        runCatching { nm.notify(notificationTag(ext, nid), 0, n) }
        return nid
    }

    // ---- requests to other sites

    private fun fetch(c: ExtensionEngine.Ctx, o: JsonObject, done: Done) {
        val url = o["url"].str() ?: return done(false, null, "No address")
        val m = e.infos[c.ext]?.manifest ?: return done(false, null, "Unknown extension")
        // only to sites the extension may reach (its host permissions)
        if (m.hostPermissions.none { matchesPattern(it, url) }) return done(false, null, "Failed to fetch")
        vm.viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { request(url, o) } }
            r.onSuccess { done(true, it, null) }.onFailure { done(false, null, "Failed to fetch") }
        }
    }

    private fun request(start: String, o: JsonObject): JsonObject {
        var url = start
        var method = o["method"].str() ?: "GET"
        val headers = o["headers"].obj()
        val body = o["body"].obj()
        val cookies = CookieManager.getInstance()
        val withCookies = o["credentials"].str() != "omit"
        repeat(10) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method; instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 30_000
                setRequestProperty("User-Agent", e.userAgents.mobile) // a browser's, as the request would carry from a page
                headers.forEach { (k, v) -> v.str()?.let { setRequestProperty(k, it) } }
                body["type"].str()?.let { if (headers.keys.none { it.equals("content-type", true) }) setRequestProperty("Content-Type", it) }
                if (withCookies) cookies.getCookie(url)?.let { setRequestProperty("Cookie", it) }
            }
            val bytes = body["b64"].str()?.let { Base64.decode(it, Base64.DEFAULT) } ?: body["text"].str()?.toByteArray()
            if (bytes != null && method != "GET" && method != "HEAD") { conn.doOutput = true; conn.outputStream.use { it.write(bytes) } }
            val code = conn.responseCode
            if (withCookies) conn.headerFields["Set-Cookie"]?.forEach { cookies.setCookie(url, it) }
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && location != null && o["redirect"].str() != "manual") {
                url = URL(URL(url), location).toString()
                if (code == 303 || ((code == 301 || code == 302) && method == "POST")) method = "GET"
                conn.disconnect(); return@repeat
            }
            val data = (if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            return buildJsonObject {
                put("status", code); put("statusText", conn.responseMessage.orEmpty()); put("url", url)
                put("headers", buildJsonObject { conn.headerFields.forEach { (k, v) -> if (k != null) put(k.lowercase(), v.joinToString(", ")) } })
                put("body", Base64.encodeToString(data, Base64.NO_WRAP))
            }
        }
        error("too many redirects")
    }
}

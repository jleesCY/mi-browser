// chrome.contextMenus / browser.menus: items extensions add to mi's long-press menu (links and images) and to the menu
// over selected text. Tapping one tells the extension (menus.onClicked) with what was held or selected and the tab.
// Manifest V3 extensions' items are kept across restarts, as Chrome keeps them (such extensions usually add them only
// when installed); Manifest V2 ones add theirs each time they start, so theirs last for the run.
package com.jlees.mibrowser.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MenuItem(
    val id: String,
    val title: String = "",
    val contexts: List<String> = listOf("page"),
    val parentId: String? = null,
    val type: String = "normal",
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val documentUrlPatterns: List<String> = emptyList(),
    val targetUrlPatterns: List<String> = emptyList(),
)

/** Where the menu is: a held link and/or image, or selected text. */
enum class MenuPlace { LINK, IMAGE, SELECTION }

/** Which of an extension's items show for [places] (pure): visible, enabled, a matching context and addresses, under a shown parent. */
fun menuItemsFor(items: List<MenuItem>, places: Set<MenuPlace>, pageUrl: String?, targetUrl: String?): List<MenuItem> {
    val wanted = places.map { it.name.lowercase() }.toSet()
    fun fits(i: MenuItem): Boolean {
        if (!i.visible || !i.enabled || i.type == "separator") return false
        if (i.documentUrlPatterns.isNotEmpty() && (pageUrl == null || i.documentUrlPatterns.none { matchesPattern(it, pageUrl) })) return false
        if (i.targetUrlPatterns.isNotEmpty() && (targetUrl == null || i.targetUrlPatterns.none { matchesPattern(it, targetUrl) })) return false
        return "all" in i.contexts || i.contexts.any { it in wanted }
    }
    val byId = items.associateBy { it.id }
    // a parent hidden or disabled hides its children; a parent whose children show is only a heading (not listed)
    fun shown(i: MenuItem): Boolean = i.parentId?.let { p -> byId[p]?.let { it.visible && it.enabled } ?: false } ?: true
    val parents = items.mapNotNull { it.parentId }.toSet()
    return items.filter { it.id !in parents && shown(it) && fits(it) }
}

class ExtensionMenus internal constructor(private val e: ExtensionEngine) {
    private val items = ConcurrentHashMap<String, List<MenuItem>>()
    private val ser = ListSerializer(MenuItem.serializer())

    /** Manifest V3 (a service worker): items are kept across restarts. */
    private fun keeps(ext: String) = e.infos[ext]?.manifest?.serviceWorker != null

    /** The extension starts: its kept items come back (none after an install or update: it adds them again then). */
    internal fun started(ext: String, reason: String) {
        items.remove(ext)
        if (!keeps(ext)) return
        if (reason == "install" || reason == "update") { save(ext); return }
        e.store.savedValue(ext, "MENU", "items")?.let { j -> runCatching { e.json.decodeFromJsonElement(ser, j) }.getOrNull()?.let { items[ext] = it } }
    }

    internal fun forget(ext: String) { items.remove(ext) }

    private fun save(ext: String) {
        if (!keeps(ext)) return
        e.store.setSavedValue(ext, "MENU", "items", e.json.encodeToJsonElement(ser, items[ext].orEmpty()))
    }


    private fun read(o: JsonObject, old: MenuItem) = old.copy(
        title = o["title"].str() ?: old.title,
        contexts = if (o.containsKey("contexts")) o["contexts"].strings() else old.contexts,
        parentId = if (o.containsKey("parentId")) o["parentId"].str() else old.parentId,
        type = o["type"].str() ?: old.type,
        checked = o["checked"].bool() ?: old.checked,
        enabled = o["enabled"].bool() ?: old.enabled,
        visible = o["visible"].bool() ?: old.visible,
        documentUrlPatterns = if (o.containsKey("documentUrlPatterns")) o["documentUrlPatterns"].strings() else old.documentUrlPatterns,
        targetUrlPatterns = if (o.containsKey("targetUrlPatterns")) o["targetUrlPatterns"].strings() else old.targetUrlPatterns,
    )

    internal fun handle(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val ext = c.ext
        val mine = items[ext].orEmpty()
        when (name) {
            "menus.create" -> {
                val o = a.getOrNull(0) as? JsonObject ?: return done(false, null, "No properties")
                val id = o["id"].str() ?: return done(false, null, "No id")
                if (mine.any { it.id == id }) return done(false, null, "Cannot create item with duplicate id $id")
                val item = read(o, MenuItem(id))
                if (item.parentId != null && mine.none { it.id == item.parentId }) return done(false, null, "Cannot find menu item with id ${item.parentId}")
                items[ext] = mine + item
            }
            "menus.update" -> {
                val id = a.getOrNull(0).str()
                val o = a.getOrNull(1) as? JsonObject ?: JsonObject(emptyMap())
                if (mine.none { it.id == id }) return done(false, null, "Cannot find menu item with id $id")
                items[ext] = mine.map { if (it.id == id) read(o, it) else it }
            }
            "menus.remove" -> {
                val id = a.getOrNull(0).str()
                if (mine.none { it.id == id }) return done(false, null, "Cannot find menu item with id $id")
                // with its children, and theirs
                val gone = HashSet<String>().apply { add(id!!) }
                var grew = true
                while (grew) { grew = mine.filter { it.parentId in gone && it.id !in gone }.map { gone.add(it.id) }.isNotEmpty() }
                items[ext] = mine.filter { it.id !in gone }
            }
            "menus.removeAll" -> items[ext] = emptyList()
            else -> return done(false, null, "$name is not supported in mi.")
        }
        save(ext)
        done(true, null, null)
    }

    /** The extensions' items (extension, title, what tapping it does) for a held link/image ([places]) or selected text in tab [tabId]; [selection] is read when one is tapped. */
    fun entries(tabId: String, places: Set<MenuPlace>, linkUrl: String?, srcUrl: String?, selection: ((String) -> Unit) -> Unit = { it("") }): List<Triple<String, String, () -> Unit>> {
        val tab = e.vm.tabs.list.firstOrNull { it.id == tabId } ?: return emptyList()
        val pageUrl = tab.url
        return e.store.list.filter { x -> x.enabled && e.runsIn(x.id, tab.profile) }.flatMap { x ->
            menuItemsFor(items[x.id].orEmpty(), places, pageUrl, linkUrl ?: srcUrl).map { item ->
                Triple(x.id, item.title.replace("%s", if (MenuPlace.SELECTION in places) "Selection" else "").trim(), {
                    selection { text ->
                        if (item.type == "checkbox") items[x.id] = items[x.id].orEmpty().map { if (it.id == item.id) it.copy(checked = !it.checked) else it }
                        val info = buildJsonObject {
                            put("menuItemId", item.id); item.parentId?.let { put("parentMenuItemId", it) }
                            pageUrl?.let { put("pageUrl", it) }; put("frameId", 0); put("editable", false)
                            linkUrl?.let { put("linkUrl", it) }
                            srcUrl?.let { put("srcUrl", it); put("mediaType", "image") }
                            if (text.isNotEmpty()) put("selectionText", text)
                            if (item.type == "checkbox" || item.type == "radio") { put("wasChecked", item.checked); put("checked", item.type == "radio" || !item.checked) }
                        }
                        e.emitToPages(x.id, "menus.onClicked", JsonArray(listOf(info, e.tabJson(tab))))
                    }
                })
            }
        }
    }

    /** Items for the selected-text menu in tab [tabId]: the selected text is read from [wv] when one is tapped. */
    fun selectionItems(tabId: String, wv: android.webkit.WebView): List<Pair<String, () -> Unit>> =
        entries(tabId, setOf(MenuPlace.SELECTION), null, null) { got ->
            wv.evaluateJavascript("String(window.getSelection())") { r -> got(runCatching { org.json.JSONTokener(r).nextValue() as? String }.getOrNull().orEmpty()) }
        }.map { (_, title, run) -> title to run }
}

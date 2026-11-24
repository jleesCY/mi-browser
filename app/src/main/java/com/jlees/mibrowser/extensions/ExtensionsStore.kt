// Installed extensions and their saved data (chrome.storage). The list and each extension's switches are Compose state
// for Settings and the tab menu; storage is kept per extension and written debounced. In incognito, an extension's
// storage changes stay in memory (nothing from incognito is written to disk).
package com.jlees.mibrowser.extensions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/** An installed extension, as Settings shows it. */
@Serializable
data class InstalledExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val enabled: Boolean = true,
    /** Runs in incognito tabs too (off by default). */
    val incognito: Boolean = false,
    val fit: Fit = Fit.MAYBE,
    val reasons: List<String> = emptyList(),
    /** The reasons that make it Not recommended (the rest of [reasons] are lesser problems). */
    val serious: List<String> = emptyList(),
    /** Its button shows in the tab menu's Extensions row. */
    val showButton: Boolean = true,
    /** Sites (hosts) where its content scripts don't run; their subdomains too. */
    val offSites: List<String> = emptyList(),
    /** declarativeNetRequest: its static rulesets turned on (null: as its manifest says). */
    val dnrRulesets: List<String>? = null,
    /** Where it came from: its Firefox Add-ons page (for updates), or null (a file). */
    val source: String? = null,
)

private const val DATA = "extension_storage"

/** chrome.storage's areas; `sync` is kept on the phone like `local` (mi has no accounts to sync through). */
enum class StorageArea { LOCAL, SYNC, SESSION }

class ExtensionsStore(private val store: JsonStore) {
    var list by mutableStateOf(listOf<InstalledExtension>()); private set
    /** chrome.storage and the engine's own data per extension, one value per file (ExtensionStorage). */
    private val storage = ExtensionStorage(store.folder(DATA))
    /** Removed extensions' data, kept until mi next starts (Undo puts it back). */
    private val trash = store.folder("extension_storage_trash")
    private var loaded = false

    private val listSer = ListSerializer(InstalledExtension.serializer())

    @Synchronized fun load() {
        if (loaded) return
        list = store.read("extensions", listSer) ?: emptyList()
        trash.deleteRecursively() // removals from before can't be undone any more
        loaded = true
    }

    val savers = listOf(Saver(800, { list }) { store.write("extensions", listSer, it) })

    operator fun get(id: String) = list.firstOrNull { it.id == id }

    fun add(e: InstalledExtension) { list = list.filterNot { it.id == e.id } + e }
    fun update(id: String, change: InstalledExtension.() -> InstalledExtension) { list = list.map { if (it.id == id) it.change() else it } }
    /** The tab menu's extension buttons dragged: the extension at [from] moves to [to] (among [shown], the ones with buttons). */
    fun reorder(shown: List<String>, from: Int, to: Int) {
        val moved = shown.toMutableList().apply { add(to, removeAt(from)) }
        // the ones with buttons take the dragged order, in the places they had; the rest stay where they are
        val slots = list.indices.filter { list[it].id in shown }
        val out = list.toMutableList()
        slots.forEachIndexed { i, at -> out[at] = list.first { it.id == moved[i] } }
        list = out
    }
    /** Removes extension [id]; its data waits aside until mi next starts ([restore] puts both back: Undo). */
    fun remove(id: String) { list = list.filterNot { it.id == id }; storage.moveTo(id, java.io.File(trash, id)) }
    fun restore(e: InstalledExtension) { storage.restoreFrom(e.id, java.io.File(trash, e.id)); add(e) }

    // ---------------------------------------------------------------- chrome.storage (values as JSON text)

    /** [keys] of [area] (null: all) for extension [id]: key → JSON text. [scope]: an incognito profile (its changes stay in memory). */
    fun get(id: String, area: StorageArea, scope: String? = null, keys: Collection<String>? = null) = storage.get(id, area.name, scope, keys)

    /** Sets [values] (key → JSON text), removes [removed] (or everything, [clear]); returns key → (old, new) for onChanged. */
    fun set(id: String, area: StorageArea, scope: String?, values: Map<String, String>, removed: Collection<String> = emptyList(), clear: Boolean = false) =
        storage.set(id, area.name, scope, values, removed, clear)

    /** The keys extension [id] has set in [area] (storage.getKeys). */
    fun keys(id: String, area: StorageArea, scope: String?) = storage.keys(id, area.name, scope)

    /** Bytes [id] stores in [area] (chrome.storage getBytesInUse). */
    fun bytesInUse(id: String, area: StorageArea, scope: String?) = storage.bytes(id, area.name, scope)

    /** A value kept for the engine in extension [id]'s data ([area] "DNR", "DYN"…: never seen by the extension's storage API). */
    fun savedValue(id: String, area: String, key: String): JsonElement? =
        storage.get(id, area, null, listOf(key))[key]?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() }
    fun setSavedValue(id: String, area: String, key: String, v: JsonElement) { storage.set(id, area, null, mapOf(key to v.toString())) }

    /** declarativeNetRequest's dynamic rules for extension [id] (kept with its data, so they survive updates). */
    fun dnrDynamic(id: String): JsonElement? = savedValue(id, "DNR", "dynamic")
    fun setDnrDynamic(id: String, rules: JsonElement) = setSavedValue(id, "DNR", "dynamic", rules)

    /** Clear Data: everything extension [id] stored, in every area. */
    fun clearAll(id: String) = storage.clearAll(id)

    /** An incognito profile ended: what its extensions stored there goes. */
    fun forgetScope(scope: String) = storage.forgetScope(scope)
}

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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    /** Where it came from: its Firefox Add-ons or Chrome Web Store page (for updates), or null (a file). */
    val source: String? = null,
    val installedAt: Long = 0,
)

private const val DATA = "extension_storage"

/** chrome.storage's areas; `sync` is kept on the phone like `local` (mi has no accounts to sync through). */
enum class StorageArea { LOCAL, SYNC, SESSION }

class ExtensionsStore(private val store: JsonStore) {
    var list by mutableStateOf(listOf<InstalledExtension>()); private set
    /** Saved storage: extension id → area → key → value. */
    private var saved by mutableStateOf(mapOf<String, Map<String, Map<String, JsonElement>>>())
    /** `storage.session` and incognito's copy: never written. */
    private val memory = HashMap<String, MutableMap<String, JsonElement>>()
    private var loaded = false

    private val listSer = ListSerializer(InstalledExtension.serializer())
    private val dataSer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), JsonElement.serializer()))
    private val storageSer = MapSerializer(String.serializer(), dataSer)
    /** Each extension's data is its own file (extension_storage/<id>), written only when that extension's data changed. */
    private val written = HashMap<String, Map<String, Map<String, JsonElement>>>()

    @Synchronized fun load() {
        if (loaded) return
        list = store.read("extensions", listSer) ?: emptyList()
        // from before each extension had its own file: split up on the first start
        val old = store.read("extension_storage", storageSer)
        saved = old ?: store.keysIn(DATA).associateWith { store.read("$DATA/$it", dataSer) ?: emptyMap() }
        if (old != null) { old.forEach { (id, d) -> store.write("$DATA/$id", dataSer, d) }; store.delete("extension_storage") }
        written.putAll(saved)
        loaded = true
    }

    val savers = listOf(
        Saver(800, { list }) { store.write("extensions", listSer, it) },
        Saver(800, { saved }) { now -> writeData(now) },
    )

    /** Writes the extensions whose data changed since the last write (by identity: maps are replaced, never changed). */
    @Synchronized private fun writeData(now: Map<String, Map<String, Map<String, JsonElement>>>) {
        for ((id, d) in now) if (written[id] !== d) { store.write("$DATA/$id", dataSer, d); written[id] = d }
        for (id in written.keys - now.keys) { store.delete("$DATA/$id"); written.remove(id) }
    }

    operator fun get(id: String) = list.firstOrNull { it.id == id }

    fun add(e: InstalledExtension) { list = list.filterNot { it.id == e.id } + e }
    fun update(id: String, change: InstalledExtension.() -> InstalledExtension) { list = list.map { if (it.id == id) it.change() else it } }
    /** Extension [id]'s saved storage, to put back with [restore] (Undo after removing it). */
    fun savedData(id: String) = saved[id].orEmpty()
    fun restore(e: InstalledExtension, data: Map<String, Map<String, JsonElement>>) { add(e); if (data.isNotEmpty()) saved = saved + (e.id to data) }
    /** The tab menu's extension buttons dragged: the extension at [from] moves to [to] (among [shown], the ones with buttons). */
    fun reorder(shown: List<String>, from: Int, to: Int) {
        val moved = shown.toMutableList().apply { add(to, removeAt(from)) }
        // the ones with buttons take the dragged order, in the places they had; the rest stay where they are
        val slots = list.indices.filter { list[it].id in shown }
        val out = list.toMutableList()
        slots.forEachIndexed { i, at -> out[at] = list.first { it.id == moved[i] } }
        list = out
    }
    fun remove(id: String) { list = list.filterNot { it.id == id }; saved = saved - id; memory.keys.removeAll { it.startsWith("$id/") } }

    // ---------------------------------------------------------------- chrome.storage

    private fun memKey(id: String, area: StorageArea, scope: String?) = "$id/${area.name}/${scope.orEmpty()}"

    /**
     * The whole of [area] for extension [id]. [scope]: an incognito profile, whose changes stay in memory on top of the
     * saved data; session storage is in memory always.
     */
    fun read(id: String, area: StorageArea, scope: String? = null): Map<String, JsonElement> {
        val base = if (area == StorageArea.SESSION) emptyMap() else saved[id]?.get(area.name).orEmpty()
        val mem = memory[memKey(id, area, scope)]
        return if (mem == null) base else mem
    }

    /** Sets [values] and removes [removed] in [area]; returns what changed, as chrome.storage.onChanged reports it (key → old, new). */
    fun write(id: String, area: StorageArea, scope: String?, values: Map<String, JsonElement>, removed: Collection<String> = emptyList(), clear: Boolean = false): Map<String, Pair<JsonElement?, JsonElement?>> {
        val before = read(id, area, scope)
        val after = (if (clear) emptyMap() else before - removed.toSet()) + values
        val changes = (before.keys + after.keys).mapNotNull { k -> (before[k] to after[k]).takeIf { it.first != it.second }?.let { k to it } }.toMap()
        if (changes.isEmpty()) return changes
        if (area == StorageArea.SESSION || scope != null) memory[memKey(id, area, scope)] = after.toMutableMap()
        else saved = saved + (id to (saved[id].orEmpty() + (area.name to after)))
        return changes
    }

    /** A value kept for the engine in extension [id]'s data ([area] "DNR", "DYN"…: never seen by the extension's storage API). */
    fun savedValue(id: String, area: String, key: String): JsonElement? = saved[id]?.get(area)?.get(key)
    fun setSavedValue(id: String, area: String, key: String, v: JsonElement) { saved = saved + (id to (saved[id].orEmpty() + (area to (saved[id]?.get(area).orEmpty() + (key to v))))) }

    /** declarativeNetRequest's dynamic rules for extension [id] (kept with its data, so they survive updates). */
    fun dnrDynamic(id: String): JsonElement? = saved[id]?.get("DNR")?.get("dynamic")
    fun setDnrDynamic(id: String, rules: JsonElement) { saved = saved + (id to (saved[id].orEmpty() + ("DNR" to mapOf("dynamic" to rules)))) }

    /** Clear Data: everything extension [id] stored, in every area. */
    fun clearAll(id: String) { saved = saved - id; memory.keys.removeAll { it.startsWith("$id/") } }

    /** An incognito profile ended: what its extensions stored there goes. */
    fun forgetScope(scope: String) { memory.keys.removeAll { it.endsWith("/$scope") } }

    /** Bytes [id] stores in [area] (chrome.storage getBytesInUse), as JSON text. */
    fun bytesInUse(id: String, area: StorageArea, scope: String?) = JsonObject(read(id, area, scope)).toString().length
}

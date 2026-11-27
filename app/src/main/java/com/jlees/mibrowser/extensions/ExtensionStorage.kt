// chrome.storage for extensions, kept on the phone one value per file: extension_storage/<id>/<AREA>/<sha256 of key>,
// each holding the key (first line) and the value's JSON text. Values are read only when asked for and written only when
// they change, never all at once: uBlock Origin alone keeps tens of megabytes there (its compiled filter lists), which as
// one file had to be read whole at every start (and ran out of memory) and written whole at every change. The JSON text
// is passed through as it is (never parsed just to be stored or sent). Incognito tabs' changes and storage.session stay
// in memory. Engine data kept per extension (DNR rules, menus, registered scripts) are areas of their own ("DNR"…).
// Calls run on the caller's thread: the API runs them on one background thread, in order (ExtensionApi).
package com.jlees.mibrowser.extensions

import com.jlees.mibrowser.data.writeSynced
import java.io.File
import java.security.MessageDigest

/** Stored JSON [text] as a JSON element, put in as it is (not parsed again); JSON null as JsonNull (the one value it can't be). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
fun rawJson(text: String): kotlinx.serialization.json.JsonElement =
    if (text.trim() == "null") kotlinx.serialization.json.JsonNull else kotlinx.serialization.json.JsonUnquotedLiteral(text)

class ExtensionStorage(private val root: File) {
    /** An incognito profile's or storage.session's changes on top of the saved values (null: removed). */
    private class Overlay(var cleared: Boolean) { val values = HashMap<String, String?>() }
    private val overlays = HashMap<String, Overlay>()

    private fun dir(id: String, area: String) = File(root, "${safe(id)}/$area")
    private fun fileOf(id: String, area: String, key: String) = File(dir(id, area), sha(key))
    private fun overlayKey(id: String, area: String, scope: String?) = "$id/$area/${scope.orEmpty()}"

    /** The keys saved in [area] (each file's first line). */
    private fun savedKeys(id: String, area: String): List<String> =
        dir(id, area).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.mapNotNull { f -> f.bufferedReader().use { it.readLine() }?.let(::unquote) }.orEmpty()

    private fun savedValue(id: String, area: String, key: String): String? =
        fileOf(id, area, key).takeIf { it.isFile }?.bufferedReader()?.use { r -> r.readLine(); r.readText() }

    private fun overlay(id: String, area: String, scope: String?): Overlay? =
        overlays[overlayKey(id, area, scope)] ?: if (area == StorageArea.SESSION.name) Overlay(cleared = true).also { overlays[overlayKey(id, area, scope)] = it } else null

    /** [keys] of [area] (null: all) as key → JSON text; keys not set are left out. [scope]: an incognito profile. */
    @Synchronized fun get(id: String, area: String, scope: String?, keys: Collection<String>?): Map<String, String> {
        val o = overlay(id, area, scope)
        val wanted = keys ?: ((if (o?.cleared == true) emptyList() else savedKeys(id, area)) + o?.values?.keys.orEmpty()).distinct()
        val out = LinkedHashMap<String, String>()
        for (k in wanted) {
            val v = if (o != null && k in o.values) o.values[k] else if (o?.cleared == true) null else savedValue(id, area, k)
            if (v != null) out[k] = v
        }
        return out
    }

    /**
     * Sets [values] (key → JSON text) and removes [removed] (or everything first, [clear]) in [area]; returns what changed
     * as chrome.storage.onChanged reports it: key → (old JSON text, new JSON text).
     */
    @Synchronized fun set(id: String, area: String, scope: String?, values: Map<String, String>, removed: Collection<String> = emptyList(), clear: Boolean = false): Map<String, Pair<String?, String?>> {
        val gone = if (clear) get(id, area, scope, null).keys - values.keys else removed.toSet() - values.keys
        val touched = gone + values.keys
        val before = get(id, area, scope, touched)
        val changes = touched.mapNotNull { k -> (before[k] to values[k]).takeIf { it.first != it.second }?.let { k to it } }.toMap()
        if (changes.isEmpty() && !clear) return changes
        val inMemory = scope != null || area == StorageArea.SESSION.name
        if (inMemory) {
            val o = overlays.getOrPut(overlayKey(id, area, scope)) { Overlay(cleared = false) }
            if (clear) { o.cleared = true; o.values.clear() }
            gone.forEach { o.values[it] = null }
            values.forEach { (k, v) -> o.values[k] = v }
        } else {
            if (clear) dir(id, area).deleteRecursively()
            gone.forEach { fileOf(id, area, it).delete() }
            for ((k, v) in values) if (before[k] != v) write(fileOf(id, area, k), k, v)
        }
        return changes
    }

    private fun write(f: File, key: String, value: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        runCatching {
            // on disk before the rename: a phone switched off right after keeps the old value, never an empty file
            tmp.writeSynced { out -> out.bufferedWriter().run { write(quote(key)); write("\n"); write(value); flush() } }
            if (!tmp.renameTo(f)) tmp.delete()
        }.onFailure { tmp.delete() } // storage full: the last good value stays
    }

    /** The keys set in [area] (without reading their values). */
    @Synchronized fun keys(id: String, area: String, scope: String?): List<String> {
        val o = overlay(id, area, scope)
        val saved = if (o?.cleared == true) emptyList() else savedKeys(id, area)
        return (saved + o?.values?.keys.orEmpty()).distinct().filter { k -> o?.values?.containsKey(k) != true || o.values[k] != null }
    }

    /** Bytes [area] holds (chrome.storage getBytesInUse): about the size of its files, without reading them. */
    @Synchronized fun bytes(id: String, area: String, scope: String?): Int {
        val o = overlay(id, area, scope)
        if (o == null) return dir(id, area).listFiles()?.filter { !it.name.endsWith(".tmp") }?.sumOf { it.length() }?.toInt() ?: 0
        return get(id, area, scope, null).entries.sumOf { (k, v) -> k.length + v.length }
    }

    /** Everything extension [id] stored, every area. */
    @Synchronized fun clearAll(id: String) {
        File(root, safe(id)).deleteRecursively()
        overlays.keys.removeAll { it.startsWith("$id/") }
    }

    /** Extension [id]'s data moves to [trash] (removed, with Undo), or comes back from it. */
    @Synchronized fun moveTo(id: String, trash: File) { trash.deleteRecursively(); trash.parentFile?.mkdirs(); File(root, safe(id)).renameTo(trash); overlays.keys.removeAll { it.startsWith("$id/") } }
    @Synchronized fun restoreFrom(id: String, trash: File) { if (trash.exists()) { File(root, safe(id)).deleteRecursively(); trash.renameTo(File(root, safe(id))) } }

    /** An incognito profile ended: what extensions stored in it goes. */
    @Synchronized fun forgetScope(scope: String) { overlays.keys.removeAll { it.endsWith("/$scope") } }

    companion object {
        private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        private fun quote(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
        private fun unquote(s: String) = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(s).let { (it as kotlinx.serialization.json.JsonPrimitive).content } }.getOrNull()
    }
}

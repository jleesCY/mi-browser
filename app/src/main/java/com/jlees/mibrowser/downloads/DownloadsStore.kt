// The download list: what's been downloaded and what's in progress, saved between launches.
package com.jlees.mibrowser.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import kotlinx.serialization.builtins.ListSerializer

class DownloadsStore(private val store: JsonStore) {
    /** Newest first. */
    var items by mutableStateOf(listOf<DownloadItem>()); private set

    private val serializer = ListSerializer(DownloadItem.serializer())

    fun load() { items = afterRestart(store.read("downloads", serializer) ?: emptyList()) }

    /** Incognito downloads are kept for the session only (keepsTraces). */
    val savers = listOf(Saver(1000, { items.filterNot { it.incognito } }) { store.write("downloads", serializer, it) })

    operator fun get(id: String) = items.firstOrNull { it.id == id }
    fun add(item: DownloadItem) { items = listOf(item) + items }
    fun update(id: String, f: DownloadItem.() -> DownloadItem) { items = items.map { if (it.id == id) it.f() else it } }
    /** Removes [id] from the list (its file stays); returns it and where it was, for Undo ([restore]). */
    fun remove(id: String): Pair<DownloadItem, Int>? {
        val i = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val item = items[i]; items = items.filterNot { it.id == id }
        return item to i
    }
    /** Puts back what [remove] returned, where it was. */
    fun restore(removed: Pair<DownloadItem, Int>) { items = items.toMutableList().apply { add(removed.second.coerceIn(0, size), removed.first) } }
    /** Wipe All Data: the list is emptied (files in Downloads stay; they belong to the user). */
    fun clear() { items = items.filter { it.active } }
    /** Incognito ended: its downloads leave the list (their files stay in Downloads, as the prompt said). */
    fun forgetIncognito() { items = items.filterNot { it.incognito && !it.active } }
}

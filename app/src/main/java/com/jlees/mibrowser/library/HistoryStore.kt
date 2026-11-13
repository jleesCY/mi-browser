// Browsing history and recent searches: what's kept, how it's saved, and the edits the Library makes.
package com.jlees.mibrowser.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import com.jlees.mibrowser.library.addHistory
import com.jlees.mibrowser.library.pruneHistory
import com.jlees.mibrowser.logic.uniqueId
import kotlinx.serialization.builtins.ListSerializer

class HistoryStore(private val store: JsonStore) {
    /** Visited pages, newest first (at most 1000). */
    var items by mutableStateOf(listOf<HistoryItem>()); private set
    /** Searches made, newest first (at most 20), shown above the Pill while typing. */
    var recents by mutableStateOf(listOf<HistoryItem>()); private set

    private val serializer = ListSerializer(HistoryItem.serializer())

    /** Reads the saved lists (off the main thread). Ids are session-only, so they're issued fresh. */
    fun load() {
        items = (store.read("history", serializer) ?: emptyList()).map { it.copy(id = uniqueId()) }
        recents = (store.read("recent_searches", serializer) ?: emptyList()).map { it.copy(id = uniqueId()) }
    }

    val savers = listOf(
        Saver(1000, { items }) { store.write("history", serializer, it) },
        Saver(1000, { recents }) { store.write("recent_searches", serializer, it) },
    )

    /** A page finished loading (in a tab that keeps traces); a search results page also becomes a recent search. */
    fun add(url: String, title: String?) {
        val (h, r) = addHistory(items, recents, url, title)
        if (h !== items) items = h
        if (r !== recents) recents = r
    }

    /** Deletes what was visited in the last [ms] (-1: everything). */
    fun deleteRecent(ms: Long) {
        items = pruneHistory(items, ms, { it.timestamp })
        recents = pruneHistory(recents, ms, { it.timestamp })
    }

    /** Removes [id]; returns it and where it was, for Undo ([restore]). */
    fun remove(id: String): Pair<HistoryItem, Int>? {
        val i = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val item = items[i]; items = items.filterNot { it.id == id }
        return item to i
    }
    /** Puts back what [remove] returned, where it was. */
    fun restore(removed: Pair<HistoryItem, Int>) { items = items.toMutableList().apply { add(removed.second.coerceIn(0, size), removed.first) } }
    fun removeSearch(id: String) { recents = recents.filter { it.id != id } }
    fun clear() { items = emptyList(); recents = emptyList() }
}

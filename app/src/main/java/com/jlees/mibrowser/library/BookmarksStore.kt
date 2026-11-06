// Bookmarks (with folders) and the favorites row: what's kept, how it's saved, and the edits the Library makes.
package com.jlees.mibrowser.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.data.Favorite
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import com.jlees.mibrowser.library.addBookmark
import com.jlees.mibrowser.library.addFolder
import com.jlees.mibrowser.library.deleteBookmark
import com.jlees.mibrowser.library.moveBookmark
import com.jlees.mibrowser.library.reorderBookmarks
import com.jlees.mibrowser.logic.uniqueId
import com.jlees.mibrowser.library.updateBookmark
import kotlinx.serialization.builtins.ListSerializer

class BookmarksStore(private val store: JsonStore) {
    /** Bookmarks and folders, flat; a node's parentId is its folder (null: the top level). */
    var nodes by mutableStateOf(listOf<BookmarkNode>()); private set
    /** The favorites row in the Pill's drawer (at most [MAX_FAVORITES]). */
    var favorites by mutableStateOf(listOf<Favorite>()); private set

    private val nodeSerializer = ListSerializer(BookmarkNode.serializer())
    private val favoriteSerializer = ListSerializer(Favorite.serializer())

    fun load() {
        nodes = store.read("bookmarks", nodeSerializer) ?: emptyList()
        favorites = store.read("favorites", favoriteSerializer) ?: emptyList()
    }

    val savers = listOf(
        Saver(500, { nodes }) { store.write("bookmarks", nodeSerializer, it) },
        Saver(500, { favorites }) { store.write("favorites", favoriteSerializer, it) },
    )

    // ---- bookmarks
    fun add(title: String, url: String, folder: String?) { nodes = addBookmark(nodes, title, url, folder) }
    /** Returns the new folder's id. */
    fun addFolder(title: String, folder: String?): String { nodes = addFolder(nodes, title, folder); return nodes.last().id }
    /** Renames [id] (and changes its address unless it's a folder, [url] null), moving it into [folder]. */
    fun edit(id: String, title: String, url: String?, folder: String?) {
        var list = nodes
        if (list.firstOrNull { it.id == id }?.parentId != folder) list = moveBookmark(list, id, folder)
        nodes = updateBookmark(list, id, title, url)
    }
    fun reorder(folder: String?, from: Int, to: Int) { nodes = reorderBookmarks(nodes, folder, from, to) }
    /** Deletes [id] (a folder with everything in it). */
    fun delete(id: String) { nodes = deleteBookmark(nodes, id) }

    // ---- favorites
    val canAddFavorite get() = favorites.size < MAX_FAVORITES
    fun isFavorite(url: String) = favorites.any { it.url == url }
    fun addFavorite(title: String, url: String) {
        if (!canAddFavorite || isFavorite(url)) return
        favorites = favorites + Favorite(uniqueId(), url, title.ifEmpty { "Favorite" })
    }
    fun removeFavorite(id: String) { favorites = favorites.filter { it.id != id } }
    /** Renames a favorite or changes its address (the favorites bar's edit dialog). */
    fun updateFavorite(id: String, title: String, url: String) {
        favorites = favorites.map { if (it.id == id) it.copy(title = title.ifBlank { it.title }, url = url) else it }
    }
    fun toggleFavorite(title: String, url: String) {
        favorites.firstOrNull { it.url == url }?.let { removeFavorite(it.id) } ?: addFavorite(title, url)
    }
    fun reorderFavorites(from: Int, to: Int) { favorites = favorites.toMutableList().apply { add(to, removeAt(from)) } }

    fun clear() { nodes = emptyList(); favorites = emptyList() }

    // ---- files (see BookmarkFile.kt)
    /**
     * Adds bookmarks read from a file. A mi export goes back where it was; another browser's goes into a folder
     * named [folder]. Pages already bookmarked are skipped. Returns how many were added and skipped.
     */
    fun import(entries: List<BookmarkEntry>, fromMi: Boolean, folder: String): Pair<Int, Int> {
        val (list, added, skipped) = importBookmarks(nodes, if (fromMi) entries else listOf(BookmarkEntry.Folder(folder, entries)), null)
        nodes = list
        return added to skipped
    }

    fun export(): String = writeBookmarkFile(nodes)

    companion object { const val MAX_FAVORITES = 5 }
}

/** The tab menu's star: adds the current page to favorites, or removes it. */
fun BrowserViewModel.toggleFavorite() {
    val url = activeUrl ?: return
    bookmarks.toggleFavorite(tabs.current?.title.orEmpty(), url)
}

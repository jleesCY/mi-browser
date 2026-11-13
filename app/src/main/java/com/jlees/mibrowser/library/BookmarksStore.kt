// Bookmarks (with folders, the Favorites folder among them): what's kept, how it's saved, and the edits the Library makes.
package com.jlees.mibrowser.library

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.data.Favorite
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import kotlinx.serialization.builtins.ListSerializer

class BookmarksStore(private val store: JsonStore) {
    /** Bookmarks and folders, flat; a node's parentId is its folder (null: the top level). */
    var nodes by mutableStateOf(listOf<BookmarkNode>()); private set

    private val nodeSerializer = ListSerializer(BookmarkNode.serializer())
    private val favoriteSerializer = ListSerializer(Favorite.serializer())

    fun load() {
        nodes = store.read("bookmarks", nodeSerializer) ?: emptyList()
        // favorites used to be their own list: moved into the Favorites folder once, and the old list emptied
        val old = store.read("favorites", favoriteSerializer).orEmpty()
        if (old.isNotEmpty()) {
            old.forEach { nodes = addFavorite(nodes, it.title, it.url) }
            store.write("bookmarks", nodeSerializer, nodes)
            store.write("favorites", favoriteSerializer, emptyList())
        }
    }

    val savers = listOf(
        Saver(500, { nodes }) { store.write("bookmarks", nodeSerializer, it) },
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

    // ---- favorites: the bookmarks in the Favorites folder (FAVORITES_FOLDER), shown on the home page
    // worked out once each time the bookmarks change, not on every read (the home page, the tab menu, switcher cards)
    val favorites by derivedStateOf { favoritesOf(nodes) }
    fun isFavorite(url: String) = favorites.any { it.url == url }
    fun addFavorite(title: String, url: String) { nodes = addFavorite(nodes, title, url) }
    fun reorderFavorites(from: Int, to: Int) { nodes = reorderFavorites(nodes, from, to) }
    /** Renames a favorite or changes its address (its edit dialog); a blank name keeps the old one. */
    fun updateFavorite(id: String, title: String, url: String) {
        val old = nodes.firstOrNull { it.id == id } ?: return
        nodes = updateBookmark(nodes, id, title.ifBlank { old.title }, url)
    }
    fun toggleFavorite(title: String, url: String) {
        favorites.firstOrNull { it.url == url }?.let { delete(it.id) } ?: addFavorite(title, url)
    }

    fun clear() { nodes = emptyList() }

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

}

/** The tab menu's star: adds the current page to favorites (the Favorites folder), or removes it. */
fun BrowserViewModel.toggleFavorite() {
    val url = activeUrl ?: return
    bookmarks.toggleFavorite(tabs.current?.title.orEmpty(), url)
}

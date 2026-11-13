// Bookmark rules: adding, editing, moving, ordering and deleting nodes in the flat folder tree.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.data.BookmarkType

import com.jlees.mibrowser.logic.*

private fun nextOrder(all: List<BookmarkNode>, parentId: String?) = (all.filter { it.parentId == parentId }.maxOfOrNull { it.order } ?: -1) + 1

fun addBookmark(all: List<BookmarkNode>, title: String, url: String, parentId: String?) =
    all + BookmarkNode(uniqueId(), BookmarkType.BOOKMARK, title.ifEmpty { url }, url, parentId, nextOrder(all, parentId))

fun addFolder(all: List<BookmarkNode>, title: String, parentId: String?) =
    all + BookmarkNode(uniqueId(), BookmarkType.FOLDER, title, "", parentId, nextOrder(all, parentId))

/** The Favorites folder's id: favorites are the bookmarks in it, so deleting the folder deletes them all. */
const val FAVORITES_FOLDER = "favorites"

/** The favorites, in their order: the bookmarks in the Favorites folder (none once it's deleted). */
fun favoritesOf(all: List<BookmarkNode>): List<BookmarkNode> =
    all.filter { it.parentId == FAVORITES_FOLDER && !it.isFolder }.sortedBy { it.order }

/** [url] added to favorites (the end of the Favorites folder, made first in Bookmarks if it's gone); once only. */
fun addFavorite(all: List<BookmarkNode>, title: String, url: String): List<BookmarkNode> {
    if (favoritesOf(all).any { it.url == url }) return all
    val first = (all.filter { it.parentId == null }.minOfOrNull { it.order } ?: 0) - 1
    val withFolder = if (all.any { it.id == FAVORITES_FOLDER }) all
        else all + BookmarkNode(FAVORITES_FOLDER, BookmarkType.FOLDER, "Favorites", "", null, first)
    return addBookmark(withFolder, title.ifEmpty { "Favorite" }, url, FAVORITES_FOLDER)
}

/**
 * The favorite at [from] moved to [to] (places among the favorites, as the home page shows them). Folders kept in the
 * Favorites folder aren't favorites, so the move is made among all its contents, landing where the favorite at [to] is.
 */
fun reorderFavorites(all: List<BookmarkNode>, from: Int, to: Int): List<BookmarkNode> {
    val favs = favoritesOf(all)
    if (from !in favs.indices || to !in favs.indices) return all
    val siblings = all.filter { it.parentId == FAVORITES_FOLDER }.sortedBy { it.order }
    return reorderBookmarks(all, FAVORITES_FOLDER, siblings.indexOf(favs[from]), siblings.indexOf(favs[to]))
}

fun descendantsAndSelf(all: List<BookmarkNode>, id: String): Set<String> {
    val out = mutableSetOf<String>()
    fun walk(x: String) { if (out.add(x)) all.filter { it.parentId == x }.forEach { walk(it.id) } }
    walk(id)
    return out
}

fun deleteBookmark(all: List<BookmarkNode>, id: String): List<BookmarkNode> {
    val ids = descendantsAndSelf(all, id)
    return all.filter { it.id !in ids }
}

fun updateBookmark(all: List<BookmarkNode>, id: String, title: String, url: String?): List<BookmarkNode> = all.map { b ->
    if (b.id != id) b
    else if (b.isFolder || url == null) b.copy(title = title)
    else b.copy(title = title, url = url)
}

fun moveBookmark(all: List<BookmarkNode>, id: String, newParent: String?): List<BookmarkNode> {
    val order = nextOrder(all, newParent)
    return all.map { if (it.id == id) it.copy(parentId = newParent, order = order) else it }
}

fun reorderBookmarks(all: List<BookmarkNode>, parentId: String?, from: Int, to: Int): List<BookmarkNode> {
    val siblings = all.filter { it.parentId == parentId }.sortedBy { it.order }.toMutableList()
    if (from !in siblings.indices || to !in siblings.indices) return all
    siblings.add(to, siblings.removeAt(from))
    return all.filter { it.parentId != parentId } + siblings.mapIndexed { i, b -> b.copy(order = i) }
}

/** The folders directly inside [parent] (null: the top level), in their order, leaving out [excluded]. */
fun childFolders(all: List<BookmarkNode>, parent: String?, excluded: Set<String> = emptySet()): List<BookmarkNode> =
    all.filter { it.isFolder && it.parentId == parent && it.id !in excluded }.sortedBy { it.order }

/** The folders from the top level down to [folderId] (empty for the top level itself). */
fun folderTrail(all: List<BookmarkNode>, folderId: String?): List<BookmarkNode> {
    val trail = mutableListOf<BookmarkNode>()
    var cur = folderId
    while (cur != null && trail.size < 50) { // 50: a guard against a broken (cyclic) tree
        val f = all.firstOrNull { it.id == cur } ?: break
        trail.add(0, f); cur = f.parentId
    }
    return trail
}

/** How many folders and bookmarks are directly inside [folderId]. */
fun folderContents(all: List<BookmarkNode>, folderId: String): Pair<Int, Int> =
    all.filter { it.parentId == folderId }.partition { it.isFolder }.let { (f, b) -> f.size to b.size }

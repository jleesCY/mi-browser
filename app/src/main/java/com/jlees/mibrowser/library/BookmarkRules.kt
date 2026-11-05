// Bookmark rules: adding, editing, moving, ordering and deleting nodes in the flat folder tree.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.data.BookmarkNode

import com.jlees.mibrowser.logic.*

private fun nextOrder(all: List<BookmarkNode>, parentId: String?) = (all.filter { it.parentId == parentId }.maxOfOrNull { it.order } ?: -1) + 1

fun addBookmark(all: List<BookmarkNode>, title: String, url: String, parentId: String?) =
    all + BookmarkNode(uniqueId(), "bookmark", title.ifEmpty { url }, url, parentId, nextOrder(all, parentId))

fun addFolder(all: List<BookmarkNode>, title: String, parentId: String?) =
    all + BookmarkNode(uniqueId(), "folder", title, "", parentId, nextOrder(all, parentId))

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

fun folderPath(all: List<BookmarkNode>, folderId: String?): String {
    if (folderId == null) return "Bookmarks (Root)"
    val path = mutableListOf<String>()
    var cur: String? = folderId
    var depth = 0
    while (cur != null && depth < 10) {
        val f = all.firstOrNull { it.id == cur } ?: break
        path.add(0, f.title); cur = f.parentId; depth++
    }
    return path.joinToString(" / ")
}

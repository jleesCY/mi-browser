// Bookmark files: the "Netscape bookmark file" HTML that Chrome, Firefox, Edge, Safari and Brave export and import.
// mi reads it to import from other browsers and writes it to export, so a mi export opens anywhere.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.decodeEntities
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.jlees.mibrowser.data.BookmarkNode

/** A bookmark or a folder (with what's inside it), as read from a file. */
sealed interface BookmarkEntry {
    data class Link(val title: String, val url: String) : BookmarkEntry
    data class Folder(val title: String, val children: List<BookmarkEntry>) : BookmarkEntry
}

/** Written into mi's own exports, so importing one restores it as it was instead of into an "Imported" folder. */
const val MI_EXPORT_MARK = "<!-- mi browser bookmarks -->"

fun isMiExport(html: String) = html.contains(MI_EXPORT_MARK)

/**
 * Reads a bookmark file. Folders are `<H3>` headings followed by a `<DL>` list; bookmarks are `<A HREF>` links. Only
 * web addresses are kept (not `javascript:` bookmarklets or Firefox's `place:` queries), and folders left empty go too.
 */
fun parseBookmarkFile(html: String): List<BookmarkEntry> {
    val tokens = Regex("""<(H3|A)\b([^>]*)>(.*?)</\1>|<(/?)DL\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    // a stack of open folders; the bottom one holds the top level
    val stack = ArrayDeque<Pair<String, MutableList<BookmarkEntry>>>().apply { addLast("" to mutableListOf()) }
    var pendingFolder: String? = null // an <H3> waiting for its <DL>
    var started = false               // the file's own outer <DL> opens the top level
    for (m in tokens.findAll(html)) {
        val tag = m.groupValues[1].uppercase()
        when {
            tag == "H3" -> pendingFolder = decodeEntities(stripTags(m.groupValues[3])).trim()
            tag == "A" -> {
                val url = HREF.find(m.groupValues[2])?.groupValues?.get(1)?.let(::decodeEntities)?.trim()
                if (url != null && (url.startsWith("http://", true) || url.startsWith("https://", true))) {
                    val title = decodeEntities(stripTags(m.groupValues[3])).trim().ifEmpty { url }
                    stack.last().second += BookmarkEntry.Link(title, url)
                }
            }
            m.groupValues[4] == "/" -> if (stack.size > 1) {
                val (title, children) = stack.removeLast()
                if (children.isNotEmpty()) stack.last().second += BookmarkEntry.Folder(title, children)
            }
            else -> {
                if (!started && pendingFolder == null) { started = true; continue }
                stack.addLast((pendingFolder ?: "Folder") to mutableListOf())
                pendingFolder = null
            }
        }
    }
    while (stack.size > 1) { // a file cut short: keep what was read
        val (title, children) = stack.removeLast()
        if (children.isNotEmpty()) stack.last().second += BookmarkEntry.Folder(title, children)
    }
    return stack.first().second
}

/** Writes [nodes] (mi's flat folder tree) as a bookmark file, in mi's order. */
fun writeBookmarkFile(nodes: List<BookmarkNode>): String = buildString {
    appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
    appendLine(MI_EXPORT_MARK)
    appendLine("""<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">""")
    appendLine("<TITLE>Bookmarks</TITLE>")
    appendLine("<H1>Bookmarks</H1>")
    fun level(parent: String?, indent: String) {
        appendLine("$indent<DL><p>")
        nodes.filter { it.parentId == parent }.sortedBy { it.order }.forEach { n ->
            if (n.isFolder) {
                appendLine("$indent    <DT><H3>${escape(n.title)}</H3>")
                level(n.id, "$indent    ")
            } else appendLine("""$indent    <DT><A HREF="${escape(n.url)}">${escape(n.title)}</A>""")
        }
        appendLine("$indent</DL><p>")
    }
    level(null, "")
}

/**
 * Adds [entries] to [existing] inside [parent] (null: the top level). Pages already bookmarked anywhere are skipped,
 * and a folder is merged into one of the same name already at that place. Returns the new list and how many
 * bookmarks were added and skipped.
 */
fun importBookmarks(existing: List<BookmarkNode>, entries: List<BookmarkEntry>, parent: String?): Triple<List<BookmarkNode>, Int, Int> {
    var all = existing
    val known = existing.filter { !it.isFolder }.map { it.url.trimEnd('/') }.toMutableSet()
    var added = 0; var skipped = 0
    fun add(items: List<BookmarkEntry>, into: String?) {
        for (e in items) when (e) {
            is BookmarkEntry.Link -> {
                if (!known.add(e.url.trimEnd('/'))) { skipped++; continue }
                all = addBookmark(all, e.title, e.url, into); added++
            }
            is BookmarkEntry.Folder -> {
                // a mi file's top-level Favorites is the Favorites folder (its bookmarks are the home page's favorites),
                // made if this phone has none yet, not a plain folder that happens to share the name
                val id = if (into == null && e.title == "Favorites") FAVORITES_FOLDER.also { all = withFavoritesFolder(all) }
                    else (all.firstOrNull { it.isFolder && it.parentId == into && it.title == e.title }
                        ?: addFolder(all, e.title, into).also { all = it }.last()).id
                add(e.children, id)
            }
        }
    }
    add(entries, parent)
    // folders that ended up empty (everything in them was already bookmarked) aren't kept
    val before = existing.map { it.id }.toSet()
    while (true) {
        val empty = all.filter { it.isFolder && it.id !in before && all.none { c -> c.parentId == it.id } }
        if (empty.isEmpty()) break
        all = all - empty.toSet()
    }
    return Triple(all, added, skipped)
}

private fun stripTags(s: String) = s.replace(TAG, "")
private val TAG = Regex("<[^>]*>")
private val HREF = Regex("""HREF\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** For a backup (Back Up): your bookmarks, as a mi bookmark file (what Export Bookmarks saves). */
fun BrowserViewModel.bookmarksBackup(): JsonElement = JsonPrimitive(bookmarks.export())

/** The bookmarks in a backup's [data] (none in one from before they were saved). */
fun backedUpBookmarks(data: JsonObject): List<BookmarkEntry> =
    (data["bookmarks"] as? JsonPrimitive)?.contentOrNull?.let(::parseBookmarkFile).orEmpty()

/** A backup's bookmarks join yours, in their folders; pages already bookmarked are skipped. How many were added. */
fun BrowserViewModel.restoreBookmarks(entries: List<BookmarkEntry>): Int = bookmarks.import(entries, fromMi = true, folder = "").first

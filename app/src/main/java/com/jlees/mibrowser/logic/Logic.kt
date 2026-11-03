// Pure logic ports:
//  - per-tab history stack: legacy/app/index.tsx handleTabUpdate (244-331) & navigateTab (401-456)
//  - history rules: legacy/src/hooks/useHistory.ts
//  - grouping/dates: legacy/src/utils.ts
//  - bookmark ops: legacy/src/hooks/useBookmarks.ts
package com.jlees.mibrowser.logic

import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.data.TabItem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A navigation-state update reported by the WebView for [t] (handleTabUpdate). */
fun applyNavUpdate(t: TabItem, url: String?, canGoForward: Boolean): Pair<List<String>, Int> {
    var stack = if (t.historyStack.isNotEmpty()) t.historyStack.toMutableList() else (t.url?.let { mutableListOf(it) } ?: mutableListOf())
    var idx = if (t.historyStack.isNotEmpty() || t.currentIndex != -1) t.currentIndex else if (t.url != null) 0 else -1
    if (url.isNullOrEmpty()) return stack to idx
    val n = normalizeUrl(url)
    when {
        idx >= 0 && idx < stack.size && normalizeUrl(stack[idx]) == n -> stack[idx] = url
        idx > 0 && idx - 1 < stack.size && normalizeUrl(stack[idx - 1]) == n -> idx--
        idx < stack.size - 1 && idx + 1 >= 0 && normalizeUrl(stack[idx + 1]) == n -> idx++
        normalizeUrl(t.url ?: "") != n -> {
            if (canGoForward && idx >= 0 && idx < stack.size) {
                stack[idx] = url
            } else {
                if (idx < stack.size - 1) stack = stack.subList(0, idx + 1).toMutableList()
                if (stack.isEmpty() || normalizeUrl(stack.last()) != n) {
                    stack.add(url)
                    idx = stack.size - 1
                }
            }
        }
    }
    return capped(stack, idx)
}

/** Per-tab history keeps the newest [MAX_TAB_HISTORY] entries (tabs.json is rewritten on every navigation). */
const val MAX_TAB_HISTORY = 100
fun capped(stack: List<String>, idx: Int): Pair<List<String>, Int> {
    val drop = stack.size - MAX_TAB_HISTORY
    return if (drop > 0) stack.drop(drop) to (idx - drop).coerceAtLeast(0) else stack to idx
}

/** User-initiated navigation (navigateTab): truncate forward history and push. */
fun pushNavigation(t: TabItem, url: String): Pair<List<String>, Int> {
    var stack = t.historyStack.toMutableList()
    var idx = t.currentIndex
    if (stack.isEmpty() && t.url != null) { stack.add(t.url); idx = 0 }
    if (idx < stack.size - 1) stack = stack.subList(0, idx + 1).toMutableList()
    if (stack.isEmpty() || normalizeUrl(stack.last()) != normalizeUrl(url)) {
        stack.add(url); idx = stack.size - 1
    } else if (idx >= 0) {
        stack[idx] = url
    }
    return capped(stack, idx)
}

/** addToHistory: returns new (history, recentSearches). */
fun addHistory(history: List<HistoryItem>, recents: List<HistoryItem>, url: String, title: String?, now: Long = System.currentTimeMillis()): Pair<List<HistoryItem>, List<HistoryItem>> {
    if (url.isEmpty() || url == "about:blank") return history to recents
    val newHistory = if (history.isNotEmpty() && history[0].url == url) history else {
        val trimmed = url.removeSuffix("/")
        (listOf(HistoryItem(url, historyTitle(url, title), now, uniqueId())) + history.filter { it.url.removeSuffix("/") != trimmed }).take(1000)
    }
    val query = searchQueryFromUrl(url) ?: return newHistory to recents
    val newRecents = (listOf(HistoryItem(url, query, now, uniqueId())) + recents.filter { searchQueryFromUrl(it.url) != query }).take(20)
    return newHistory to newRecents
}

/** deleteHistory(ms): -1 clears everything, else removes items newer than now-ms. */
fun <T> pruneHistory(items: List<T>, ms: Long, ts: (T) -> Long, now: Long = System.currentTimeMillis()): List<T> =
    if (ms == -1L) emptyList() else items.filter { ts(it) < now - ms }

data class HistorySection(val title: String, val items: List<HistoryItem>)

private fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

fun groupHistoryByDate(items: List<HistoryItem>, now: Long = System.currentTimeMillis()): List<HistorySection> {
    val today = cal(now)
    val yesterday = cal(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val lastWeek = now - 7L * 86_400_000
    val groups = linkedMapOf<String, MutableList<HistoryItem>>("Today" to mutableListOf(), "Yesterday" to mutableListOf(), "Last 7 Days" to mutableListOf(), "Older" to mutableListOf())
    for (item in items) {
        val c = cal(item.timestamp)
        val key = when {
            sameDay(c, today) -> "Today"
            sameDay(c, yesterday) -> "Yesterday"
            item.timestamp > lastWeek -> "Last 7 Days"
            else -> "Older"
        }
        groups.getValue(key).add(item)
    }
    return groups.filter { it.value.isNotEmpty() }.map { HistorySection(it.key, it.value) }
}

fun groupHistoryBySite(items: List<HistoryItem>): List<HistorySection> =
    items.groupBy { displayHost(it.url).ifEmpty { "Unknown" } }.toSortedMap().map { HistorySection(it.key, it.value) }

fun smartDate(ts: Long, now: Long = System.currentTimeMillis()): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))
    val c = cal(ts)
    return when {
        sameDay(c, cal(now)) -> time
        sameDay(c, cal(now).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday, $time"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts)) + ", " + time
    }
}

// ---- Bookmarks ----

private fun nextOrder(all: List<BookmarkNode>, parentId: String?) = (all.filter { it.parentId == parentId }.maxOfOrNull { it.order } ?: -1) + 1

fun addBookmark(all: List<BookmarkNode>, title: String, url: String, parentId: String?) =
    all + BookmarkNode(uniqueId(), "bookmark", title.ifEmpty { url }, url, parentId, nextOrder(all, parentId), faviconUrl(url))

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
    else b.copy(title = title, url = url, icon = if (url != b.url) faviconUrl(url) else b.icon)
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

fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

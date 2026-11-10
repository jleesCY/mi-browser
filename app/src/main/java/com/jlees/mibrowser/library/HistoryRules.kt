// History rules: what a visit records, deleting by time, and grouping for the History screen.
package com.jlees.mibrowser.library


import com.jlees.mibrowser.search.searchQueryFromUrl

import com.jlees.mibrowser.data.HistoryItem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.jlees.mibrowser.logic.*

/** A tab's [com.jlees.mibrowser.data.TabItem.lastVisit] after mi restarts: its first load is the restore, not a visit. */
const val RESTORED_VISIT = "\u0000restored"

/**
 * Whether a finished load of [url], in a tab whose last recorded page is [lastVisit], is a new visit for history.
 * Reloading the same page isn't, and neither is the first load of a tab restored at startup (it was recorded when
 * first visited; its address may also come back slightly different, e.g. a search with fresh parameters).
 */
fun isNewVisit(lastVisit: String?, url: String) = lastVisit != RESTORED_VISIT && lastVisit?.removeSuffix("/") != url.removeSuffix("/")

/** A visit to [url]: the new (history, recent searches); a search results page is also a recent search. */
fun addHistory(history: List<HistoryItem>, recents: List<HistoryItem>, url: String, title: String?, now: Long = System.currentTimeMillis()): Pair<List<HistoryItem>, List<HistoryItem>> {
    if (url.isEmpty() || url == "about:blank") return history to recents
    val newHistory = if (history.isNotEmpty() && history[0].url == url) {
        // the page was recorded before its title arrived (host placeholder): take the real title now
        val better = historyTitle(url, title)
        if (better != history[0].title && better != displayHost(url)) listOf(history[0].copy(title = better)) + history.drop(1) else history
    } else {
        val trimmed = url.removeSuffix("/")
        (listOf(HistoryItem(url, historyTitle(url, title), now, uniqueId())) + history.filter { it.url.removeSuffix("/") != trimmed }).take(1000)
    }
    val query = searchQueryFromUrl(url) ?: return newHistory to recents
    val newRecents = (listOf(HistoryItem(url, query, now, uniqueId())) + recents.filter { searchQueryFromUrl(it.url) != query }).take(20)
    return newHistory to newRecents
}

/** Removes items from the last [ms] (-1: everything). */
fun <T> pruneHistory(items: List<T>, ms: Long, ts: (T) -> Long, now: Long = System.currentTimeMillis()): List<T> =
    if (ms == -1L) emptyList() else items.filter { ts(it) < now - ms }

data class HistorySection(val title: String, val items: List<HistoryItem>)

private fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

/** Groups [items] (newest first) by when they happened: Today, Yesterday, Last 7 Days, Older; empty groups left out. */
fun <T> groupByDate(items: List<T>, now: Long = System.currentTimeMillis(), time: (T) -> Long): List<Pair<String, List<T>>> {
    val today = cal(now)
    val yesterday = cal(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val lastWeek = now - 7L * 86_400_000
    val groups = linkedMapOf<String, MutableList<T>>("Today" to mutableListOf(), "Yesterday" to mutableListOf(), "Last 7 Days" to mutableListOf(), "Older" to mutableListOf())
    for (item in items) {
        val t = time(item)
        val c = cal(t)
        val key = when {
            sameDay(c, today) -> "Today"
            sameDay(c, yesterday) -> "Yesterday"
            t > lastWeek -> "Last 7 Days"
            else -> "Older"
        }
        groups.getValue(key).add(item)
    }
    return groups.filter { it.value.isNotEmpty() }.map { it.key to it.value }
}

fun groupHistoryByDate(items: List<HistoryItem>, now: Long = System.currentTimeMillis()): List<HistorySection> =
    groupByDate(items, now) { it.timestamp }.map { (title, list) -> HistorySection(title, list) }

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

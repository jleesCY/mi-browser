// The rules for the tab list, as plain functions over it (no Android, no state), so they're unit-tested on their own.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.data.newTab

/** The tab list and which tab is showing. */
data class TabsState(val tabs: List<TabItem>, val activeId: String)

/**
 * The tab to show after [closed] is gone from [remaining] (not empty): its opener for a pop-up (window.close, Back out
 * of a pop-up), otherwise the most recently used, like an app switcher.
 */
fun nextTabAfterClosing(closed: TabItem, remaining: List<TabItem>): String =
    closed.openerId?.takeIf { o -> remaining.any { it.id == o } } ?: remaining.maxBy { it.lastUsed }.id

/**
 * Closes [ids] from [state]. The active tab moves on by [nextTabAfterClosing] if it was closed; with nothing left,
 * one new tab starts over.
 */
fun closeTabs(state: TabsState, ids: Set<String>): TabsState {
    val remaining = state.tabs.filter { it.id !in ids }
    if (remaining.isEmpty()) return newTab().let { TabsState(listOf(it), it.id) }
    if (state.activeId !in ids) return state.copy(tabs = remaining)
    val closed = state.tabs.first { it.id == state.activeId }
    return TabsState(remaining, nextTabAfterClosing(closed, remaining))
}

/** "Clear all tabs": everything but pinned tabs ([keepPinned]). */
fun tabsToClear(tabs: List<TabItem>, keepPinned: Boolean): Set<String> =
    tabs.filterNot { keepPinned && it.pinned }.map { it.id }.toSet()

/** Only a fresh tab (nothing loaded, no history, not a pop-up) can switch between normal and incognito. */
fun canToggleIncognito(tab: TabItem, supported: Boolean) = supported && tab.isFresh

/** [tab] put back at [index] (clamped), where it was closed from. */
fun reinsert(tabs: List<TabItem>, tab: TabItem, index: Int) = tabs.toMutableList().apply { add(index.coerceIn(0, size), tab) }

/** A tab opened in the background goes right after the active one, so it sits next to it in the switcher. */
fun insertAfterActive(state: TabsState, tab: TabItem) = reinsert(state.tabs, tab, state.tabs.indexOfFirst { it.id == state.activeId } + 1)

/**
 * The tabs at launch, from the saved ones ([saved], already repaired) and [savedActive]:
 *  - a link that opened mi ([linkTab]) comes first and shows;
 *  - [StartupTabs.LAST]: the last active tab (or one with a page) shows;
 *  - otherwise a blank tab shows, reusing a saved one if there is one.
 */
fun restoreSession(saved: List<TabItem>, savedActive: String?, mode: StartupTabs, linkTab: TabItem?): TabsState = when {
    linkTab != null -> TabsState(listOf(linkTab) + saved, linkTab.id)
    mode == StartupTabs.LAST && saved.isNotEmpty() ->
        TabsState(saved, (saved.firstOrNull { it.id == savedActive } ?: saved.firstOrNull { it.url != null } ?: saved[0]).id)
    else -> saved.firstOrNull { it.url == null }?.let { TabsState(saved, it.id) }
        ?: newTab().let { TabsState(listOf(it) + saved, it.id) }
}

/** A saved tab made consistent: its index inside its history stack, its page the one at that index. */
fun repairSaved(t: TabItem): TabItem {
    val stack = t.historyStack
    val idx = if (t.currentIndex in stack.indices) t.currentIndex else stack.size - 1
    val url = stack.getOrNull(idx)
    return t.copy(currentIndex = if (stack.isEmpty()) -1 else idx, url = url, requestedUrl = url, hasLoadedOnce = false, // loads when opened
        lastVisit = com.jlees.mibrowser.library.RESTORED_VISIT, title = t.title.ifEmpty { "New Tab" })
}

/**
 * The recently closed tabs after [closing] closes: newest first, at most [max], stamped with when they closed, and only
 * tabs with a page that keep traces (a closed incognito tab is gone for good).
 */
fun rememberClosed(recent: List<TabItem>, closing: List<TabItem>, max: Int = 20, now: Long = System.currentTimeMillis()): List<TabItem> =
    (closing.filter { it.keepsTraces && it.historyStack.isNotEmpty() }.map { it.copy(closedAt = now) }.reversed() + recent).distinctBy { it.id }.take(max)

/** The recently closed tabs still within [keep] at [now] (all of them for SESSION: those only live until mi closes). */
fun keptClosed(closed: List<TabItem>, keep: com.jlees.mibrowser.data.KeepClosedTabs, now: Long = System.currentTimeMillis()): List<TabItem> =
    if (keep == com.jlees.mibrowser.data.KeepClosedTabs.SESSION) closed else closed.filter { now - it.closedAt < keep.ms }

/** Tab search in the switcher: [query] (trimmed, any case) appears in the tab's title or its page's address. */
fun matchesTab(tab: TabItem, query: String): Boolean {
    val q = query.trim()
    val address = tab.url ?: tab.historyStack.getOrNull(tab.currentIndex).orEmpty()
    return tab.title.contains(q, ignoreCase = true) || address.contains(q, ignoreCase = true)
}

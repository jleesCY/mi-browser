// How many ads and trackers mi blocked on each tab's page, shown in the tab menu's "Block ads here" tile and in Site
// settings. Counted per page: a new page starts again from zero. Kept in memory only (never written), so incognito
// tabs leave nothing behind; written on WebView's network threads, read when a menu opens.
package com.jlees.mibrowser.adblock

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object BlockedCounts {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** Tab [tabId] started a new page: its count starts again. */
    fun pageStarted(tabId: String) { counts[tabId] = AtomicInteger() }
    /** mi blocked one of the page's requests. */
    fun blocked(tabId: String) { counts.getOrPut(tabId) { AtomicInteger() }.incrementAndGet() }
    fun forget(tabId: String) { counts.remove(tabId) }
    /** Requests blocked on tab [tabId]'s current page. */
    fun on(tabId: String?): Int = tabId?.let { counts[it]?.get() } ?: 0
}

/** "On" or "On · 12 blocked", for a tab whose page mi is blocking ads on. */
fun blockingLabel(blocked: Int): String = if (blocked > 0) "On · $blocked blocked" else "On"

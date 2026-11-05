// Each tab's own back/forward stack, kept beside WebView's so a restored tab can still go back.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.data.TabItem

import com.jlees.mibrowser.logic.*

/** A navigation-state update reported by the WebView for [t] */
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

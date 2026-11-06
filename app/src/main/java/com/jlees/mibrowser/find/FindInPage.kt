// Find in page: what's being searched for and where in the matches the user is. The WebView does the searching
// (WebCmd.Find / FindNext) and reports back through [onResult].
package com.jlees.mibrowser.find

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.WebCmd

/** @param send passes a search to the visible tab's WebView */
class FindInPage(private val send: (WebCmd) -> Unit) {
    var visible by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    /** The highlighted match, from 1 (0: none). */
    var current by mutableIntStateOf(0); private set
    var total by mutableIntStateOf(0); private set

    fun open() { visible = true }

    fun close() {
        visible = false
        search("")
    }

    /** New search text: highlights every match and jumps to the first (empty clears them). */
    fun search(q: String) {
        query = q
        if (q.isEmpty()) { current = 0; total = 0 }
        send(WebCmd.Find(q))
    }

    /**
     * The next (or previous) match. With none found yet it searches again instead: content may have arrived since
     * (a page still loading, or one that builds itself with script).
     */
    fun next(forward: Boolean) {
        if (query.isEmpty()) return
        send(if (total == 0) WebCmd.Find(query) else WebCmd.FindNext(forward))
    }

    /** Another page now shows (a tab switch, or the tab loaded a page): search it for the same text. */
    fun refresh() { if (visible && query.isNotEmpty()) send(WebCmd.Find(query)) }

    /** From WebView's find listener: [ordinal] is 0-based. */
    fun onResult(ordinal: Int, matches: Int) {
        total = matches
        current = if (matches == 0) 0 else ordinal + 1
    }
}

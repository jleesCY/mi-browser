// The open tabs: the list, which one shows, the last closed one (for Undo), and saving them. Which tab comes next
// and similar decisions are the rules in TabRules.kt; showing a tab on screen is the ViewModel's job.
package com.jlees.mibrowser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.logic.displayHost
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** @param destroy releases closed tabs' WebViews (one call per close, so a big close can't overflow anything) */
class TabsStore(private val store: JsonStore, private val destroy: (ids: Set<String>) -> Unit) {
    var list by mutableStateOf(listOf<TabItem>()); private set
    var activeId by mutableStateOf(""); private set
    /** The last tab closed (and where it was), for Undo. Only one can be restored. */
    var closed by mutableStateOf<Pair<TabItem, Int>?>(null); private set
    /** Tabs closed this session, newest first (History → Recently closed). In memory only, never saved. */
    var recentlyClosed by mutableStateOf(listOf<TabItem>()); private set

    val current get() = get(activeId)
    operator fun get(id: String) = list.firstOrNull { it.id == id }

    /** Restores the saved session at launch (see [restoreSession]); [link] is a page that opened mi. */
    fun load(link: String?, mode: StartupTabs, backgroundRefresh: Boolean) {
        val saved = (store.read("tabs", ListSerializer(TabItem.serializer())) ?: emptyList()).map { repairSaved(it, backgroundRefresh) }
        val restored = restoreSession(saved, store.read("activeTabId", String.serializer()), mode, link?.let { newTab(it, "External Link") })
        list = restored.tabs
        select(restored.activeId)
    }

    /** Tabs that keep traces are saved (incognito tabs never are), with the active one, or the latest used of them. */
    val saver = Saver(500, {
        list.filter { it.keepsTraces }.let { saved -> saved to (activeId.takeIf { id -> saved.any { it.id == id } } ?: saved.maxByOrNull { it.lastUsed }?.id ?: "") }
    }) {
        store.write("tabs", ListSerializer(TabItem.serializer()), it.first)
        store.write("activeTabId", String.serializer(), it.second)
    }

    fun update(id: String, f: TabItem.() -> TabItem) { list = list.map { if (it.id == id) it.f() else it } }

    /** Makes [id] the tab that shows (and the most recently used). */
    fun select(id: String) {
        activeId = id
        update(id) { copy(hasLoadedOnce = true, lastUsed = System.currentTimeMillis()) }
    }

    /** Opens [tab] in front and shows it. */
    fun open(tab: TabItem) { list = listOf(tab) + list; select(tab.id) }

    /** Opens [tab] right after the active one without showing it. */
    fun openInBackground(tab: TabItem) { list = insertAfterActive(TabsState(list, activeId), tab) }

    /**
     * Closes [ids] (see [closeTabs]) and returns the tab that shows now. [undoable]: a single closed tab that keeps
     * traces can be restored by [undoClose] (a closed incognito tab is gone for good). Previews of tabs that can't
     * come back are deleted.
     */
    fun close(ids: Set<String>, undoable: Boolean = false): String {
        val closing = list.filter { it.id in ids }
        if (closing.isEmpty()) return activeId
        val keep = closing.singleOrNull()?.takeIf { undoable && it.keepsTraces }
        if (keep != null) { dismissUndo(); closed = keep to list.indexOf(keep) }
        closing.filter { it !== keep }.forEach { deletePreview(it) }
        destroy(closing.map { it.id }.toSet())
        recentlyClosed = rememberClosed(recentlyClosed, closing.map { it.copy(previewImage = null) })
        val next = closeTabs(TabsState(list, activeId), ids)
        list = next.tabs
        if (next.activeId != activeId) select(next.activeId)
        return next.activeId
    }

    /** Puts the last closed tab back where it was, reloading its page (it doesn't show). */
    fun undoClose() {
        val (t, idx) = closed ?: return
        closed = null
        recentlyClosed = recentlyClosed.filterNot { it.id == t.id }
        val url = t.historyStack.getOrNull(t.currentIndex)
        list = reinsert(list, t.copy(url = url, requestedUrl = url, loadNonce = t.loadNonce + 1, hasLoadedOnce = true, loading = false), idx)
    }

    /** Opens a recently closed tab again, in front, on the page it was on. */
    fun reopen(id: String) {
        val t = recentlyClosed.firstOrNull { it.id == id } ?: return
        recentlyClosed = recentlyClosed - t
        if (closed?.first?.id == id) dismissUndo() // the Undo bar for this same tab would open a second copy
        val url = t.historyStack.getOrNull(t.currentIndex)
        open(t.copy(url = url, requestedUrl = url, loadNonce = t.loadNonce + 1, hasLoadedOnce = true, loading = false, previewImage = null))
    }

    /** Drops [id] from the recently closed list, or (null) the whole list: clearing history clears it too. */
    fun forgetClosed(id: String? = null) { recentlyClosed = if (id == null) emptyList() else recentlyClosed.filterNot { it.id == id } }

    fun dismissUndo() { closed?.first?.let(::deletePreview); closed = null }

    fun togglePinned(id: String) = update(id) { copy(pinned = !pinned) }

    /** A blank [text] gives the tab its page's name back. */
    fun rename(id: String, text: String) {
        val t = text.trim()
        update(id) { if (t.isNotEmpty()) copy(title = t, isCustomTitle = true) else copy(title = url?.let { displayHost(it) } ?: "New Tab", isCustomTitle = false) }
    }

    /** Forgets every tab's preview (Clear Browsing Data); open tabs take a new one when next left. */
    fun forgetPreviews() {
        list = list.map { it.copy(previewImage = null) }
        closed = closed?.let { (t, i) -> t.copy(previewImage = null) to i }
    }

    private fun deletePreview(t: TabItem) { t.previewImage?.let { File(it).delete() } }
}

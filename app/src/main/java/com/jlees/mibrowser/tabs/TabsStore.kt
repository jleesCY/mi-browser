// The open tabs: the list, which one shows, the last closed one (for Undo), and saving them. Which tab comes next
// and similar decisions are the rules in TabRules.kt; showing a tab on screen is the ViewModel's job.
package com.jlees.mibrowser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.KeepClosedTabs
import com.jlees.mibrowser.data.Saver
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.logic.displayHost
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * @param keepClosed how long Recently Closed keeps tabs (Settings); past this session they're saved
 * @param destroy releases closed tabs' WebViews (one call per close, so a big close can't overflow anything)
 */
class TabsStore(
    private val store: JsonStore, private val keepClosed: () -> KeepClosedTabs,
    private val now: () -> Long = System::currentTimeMillis, // the time, for when tabs were closed (tests move it)
    private val destroy: (ids: Set<String>) -> Unit,
) {
    var list by mutableStateOf(listOf<TabItem>()); private set
    var activeId by mutableStateOf(""); private set
    /** The last tab closed (and where it was), for Undo. Only one can be restored. */
    var closed by mutableStateOf<Pair<TabItem, Int>?>(null); private set
    /** Closed tabs, newest first (History's Recently Closed). Saved only when [keepClosed] reaches past this session. */
    var recentlyClosed by mutableStateOf(listOf<TabItem>()); private set

    val current get() = get(activeId)
    operator fun get(id: String) = list.firstOrNull { it.id == id }

    /** Restores the saved session at launch (see [restoreSession]); [link] is a page that opened mi. */
    fun load(link: String?, mode: StartupTabs) {
        val saved = (store.read("tabs", ListSerializer(TabItem.serializer())) ?: emptyList()).map(::repairSaved)
        val restored = restoreSession(saved, store.read("activeTabId", String.serializer()), mode, link?.let { newTab(it, "External Link") })
        list = restored.tabs
        select(restored.activeId)
        val keep = keepClosed()
        if (keep != KeepClosedTabs.SESSION) recentlyClosed = keptClosed(store.read("recently_closed", ListSerializer(TabItem.serializer())) ?: emptyList(), keep, now())
    }

    /** Recently Closed, saved when they're kept past this session; otherwise the file is emptied (nothing lingers on disk). */
    val closedSaver = Saver(1000, { if (keepClosed() == KeepClosedTabs.SESSION) emptyList() else recentlyClosed }) {
        store.write("recently_closed", ListSerializer(TabItem.serializer()), it)
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
     * traces can be restored by [undoClose] (a closed incognito tab is gone for good). A tab that goes to Recently
     * Closed keeps its preview (its card there shows it); the others' previews are deleted.
     */
    fun close(ids: Set<String>, undoable: Boolean = false): String {
        val closing = list.filter { it.id in ids }
        if (closing.isEmpty()) return activeId
        val keep = closing.singleOrNull()?.takeIf { undoable && it.keepsTraces }
        if (keep != null) { dismissUndo(); closed = keep to list.indexOf(keep) }
        destroy(closing.map { it.id }.toSet())
        val before = recentlyClosed
        recentlyClosed = keptClosed(rememberClosed(recentlyClosed, closing, now = now()), keepClosed(), now()) // the expired ones go too
        val next = closeTabs(TabsState(list, activeId), ids)
        list = next.tabs
        if (next.activeId != activeId) select(next.activeId)
        (closing + before).forEach { releasePreview(it.previewImage) } // kept only where still shown (or for Undo)
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
        open(t.copy(url = url, requestedUrl = url, loadNonce = t.loadNonce + 1, hasLoadedOnce = true, loading = false, closedAt = 0))
    }

    /** Drops [id] from the recently closed list, or (null) the whole list: clearing history clears it too. */
    fun forgetClosed(id: String? = null) {
        val dropped = if (id == null) recentlyClosed else recentlyClosed.filter { it.id == id }
        recentlyClosed = recentlyClosed - dropped.toSet()
        dropped.forEach { releasePreview(it.previewImage) }
    }

    /** Drops the closed tabs past their time (Keep Recently Closed), and their previews: not just hidden, but gone. */
    fun pruneExpired() {
        val expired = recentlyClosed - keptClosed(recentlyClosed, keepClosed(), now()).toSet()
        if (expired.isEmpty()) return
        recentlyClosed = recentlyClosed - expired.toSet()
        expired.forEach { releasePreview(it.previewImage) }
    }

    fun dismissUndo() { val t = closed?.first; closed = null; releasePreview(t?.previewImage) }

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
        recentlyClosed = recentlyClosed.map { it.copy(previewImage = null) }
    }

    /** Deletes a preview file once nothing shows it any more: no open tab, recently closed tab or Undo. */
    private fun releasePreview(path: String?) {
        if (path == null) return
        if (list.any { it.previewImage == path } || recentlyClosed.any { it.previewImage == path } || closed?.first?.previewImage == path) return
        File(path).delete()
    }
}

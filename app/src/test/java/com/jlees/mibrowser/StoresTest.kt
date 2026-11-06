package com.jlees.mibrowser

import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.data.BookmarkType
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.json
import kotlinx.serialization.builtins.ListSerializer
import com.jlees.mibrowser.find.FindInPage
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.newIncognitoProfile
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.library.BookmarksStore
import com.jlees.mibrowser.library.HistoryStore
import com.jlees.mibrowser.tabs.TabsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** The stores: what closing, Undo and saving do, and that what's saved loads back (without incognito). */
class StoresTest {
    private val store = JsonStore(Files.createTempDirectory("store").toFile())
    private val destroyed = mutableListOf<Set<String>>()
    private val tabs = TabsStore(store) { destroyed += it }

    @Test fun closeAndUndo() {
        val a = newTab("https://a.com"); val b = newTab("https://b.com")
        tabs.open(a); tabs.open(b) // b in front, showing
        tabs.close(setOf(b.id), undoable = true)
        assertEquals(listOf(setOf(b.id)), destroyed)
        assertEquals(a.id, tabs.activeId)
        assertEquals(b.id, tabs.closed?.first?.id)
        tabs.undoClose()
        assertEquals(listOf(b.id, a.id), tabs.list.map { it.id }) // back where it was
        assertNull(tabs.closed)
    }

    @Test fun closingManyTabsReleasesThemInOneCall() {
        val many = List(100) { newTab("https://t$it.com") }
        many.forEach { tabs.open(it) }
        tabs.close(many.map { it.id }.toSet())
        assertEquals(1, destroyed.size)
        assertEquals(100, destroyed.single().size)
    }

    @Test fun aClosedIncognitoTabCantBeRestored() {
        tabs.open(newTab("https://a.com"))
        val inc = newTab("https://secret.com").copy(profile = newIncognitoProfile())
        tabs.open(inc)
        tabs.close(setOf(inc.id), undoable = true)
        assertNull(tabs.closed)
    }

    @Test fun savedTabsLoadBackWithoutIncognito() {
        val a = newTab("https://a.com")
        tabs.open(a)
        tabs.open(newTab("https://secret.com").copy(profile = newIncognitoProfile()))
        tabs.saver.writeNow()
        val loaded = TabsStore(store) {}
        loaded.load(null, StartupTabs.LAST, backgroundRefresh = false)
        assertEquals(listOf(a.id), loaded.list.map { it.id })
        assertEquals(a.id, loaded.activeId) // the incognito tab was active: the latest saved one shows instead
    }

    @Test fun historyAndBookmarksSaveAndLoad() {
        val h = HistoryStore(store)
        h.add("https://www.google.com/search?q=cats", "cats - Google Search")
        h.savers.forEach { it.writeNow() }
        val h2 = HistoryStore(store).apply { load() }
        assertEquals(1, h2.items.size); assertEquals("cats", h2.recents.single().title)

        val b = BookmarksStore(store)
        repeat(7) { b.addFavorite("f$it", "https://f$it.com") }
        assertEquals(BookmarksStore.MAX_FAVORITES, b.favorites.size)
        b.toggleFavorite("f0", "https://f0.com")
        assertTrue(b.canAddFavorite)
        b.add("Site", "https://s.com", null)
        b.savers.forEach { it.writeNow() }
        val b2 = BookmarksStore(store).apply { load() }
        assertEquals(listOf("Site"), b2.nodes.map { it.title })
        assertEquals(4, b2.favorites.size)
    }

    @Test fun findInPageCountsAndSearchesAgain() {
        val sent = mutableListOf<WebCmd>()
        val find = FindInPage { sent += it }
        find.open(); find.search("mars")
        find.next(true) // nothing found yet: search again rather than step
        assertEquals(listOf(WebCmd.Find("mars"), WebCmd.Find("mars")), sent)
        find.onResult(ordinal = 1, matches = 7)
        assertEquals(2, find.current); assertEquals(7, find.total)
        find.next(false)
        assertEquals(WebCmd.FindNext(false), sent.last())
        find.refresh() // another tab now shows
        assertEquals(WebCmd.Find("mars"), sent.last())
        find.close()
        assertEquals(WebCmd.Find(""), sent.last()); assertEquals(0, find.total)
        find.refresh(); assertEquals(5, sent.size) // closed: nothing more is searched
    }

    @Test fun bookmarksSavedAsTextStillLoad() {
        // bookmarks.json from before BookmarkType: "type" is plain text
        val old = """[{"id":"1","type":"folder","title":"Work"},{"id":"2","type":"bookmark","title":"A","url":"https://a.com","parentId":"1"},{"id":"3","type":"mystery","title":"B","url":"https://b.com"}]"""
        val nodes = json.decodeFromString(ListSerializer(BookmarkNode.serializer()), old)
        assertEquals(listOf(BookmarkType.FOLDER, BookmarkType.BOOKMARK, BookmarkType.BOOKMARK), nodes.map { it.type })
        assertTrue(json.encodeToString(ListSerializer(BookmarkNode.serializer()), nodes).contains("\"type\":\"folder\""))
    }

    @Test fun undoPutsDeletedEntriesBackWhereTheyWere() {
        val h = HistoryStore(store)
        listOf("a", "b", "c").forEach { h.add("https://$it.com", it) } // newest first: c, b, a
        val removed = h.remove(h.items[1].id)!!
        assertEquals(listOf("c", "a"), h.items.map { it.title })
        h.restore(removed)
        assertEquals(listOf("c", "b", "a"), h.items.map { it.title })

        val d = com.jlees.mibrowser.downloads.DownloadsStore(store)
        listOf("x", "y").forEach { d.add(com.jlees.mibrowser.downloads.DownloadItem(it, "https://$it.com", "$it.zip")) } // y, x
        val r = d.remove("x")!!
        d.restore(r)
        assertEquals(listOf("y", "x"), d.items.map { it.id })
        assertNull(d.remove("missing"))
    }

    @Test fun recentlyClosedTabsReopen() {
        val a = newTab("https://a.com"); val b = newTab("https://b.com"); val blank = newTab()
        val inc = newTab("https://secret.com").copy(profile = newIncognitoProfile())
        listOf(a, b, blank, inc).forEach { tabs.open(it) }
        tabs.close(setOf(a.id)); tabs.close(setOf(b.id, blank.id, inc.id))
        // newest first; a blank tab or an incognito one is never kept
        assertEquals(setOf(a.id, b.id), tabs.recentlyClosed.map { it.id }.toSet())
        assertEquals(a.id, tabs.recentlyClosed.last().id)
        tabs.reopen(a.id)
        assertEquals(a.id, tabs.activeId)
        assertEquals("https://a.com", tabs.current?.url)
        assertEquals(listOf(b.id), tabs.recentlyClosed.map { it.id })
        tabs.forgetClosed()
        assertTrue(tabs.recentlyClosed.isEmpty())
    }

    @Test fun editingAFavorite() {
        val b = BookmarksStore(store)
        b.addFavorite("Old", "https://old.com")
        val id = b.favorites.single().id
        b.updateFavorite(id, "New", "https://new.com")
        assertEquals("New" to "https://new.com", b.favorites.single().let { it.title to it.url })
        b.updateFavorite(id, "  ", "https://new.com") // a blank name keeps the old one
        assertEquals("New", b.favorites.single().title)
    }

    @Test fun reopeningATabCancelsItsUndo() {
        val a = newTab("https://a.com"); val b = newTab("https://b.com")
        tabs.open(a); tabs.open(b)
        tabs.close(setOf(b.id), undoable = true) // Undo bar for b, and b in recently closed
        tabs.reopen(b.id)
        assertNull(tabs.closed)
        tabs.undoClose() // the bar can't bring back a second copy
        assertEquals(1, tabs.list.count { it.id == b.id })
    }
}

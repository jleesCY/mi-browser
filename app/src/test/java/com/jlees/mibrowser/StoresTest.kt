package com.jlees.mibrowser

import com.jlees.mibrowser.data.JsonStore
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
}

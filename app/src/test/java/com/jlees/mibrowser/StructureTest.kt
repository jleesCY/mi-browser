package com.jlees.mibrowser

import com.jlees.mibrowser.data.LoadingBar
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.ThemeMode
import com.jlees.mibrowser.data.json
import com.jlees.mibrowser.data.migrated
import com.jlees.mibrowser.data.newIncognitoProfile
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.tabs.TabsState
import com.jlees.mibrowser.tabs.canToggleIncognito
import com.jlees.mibrowser.tabs.closeTabs
import com.jlees.mibrowser.tabs.insertAfterActive
import com.jlees.mibrowser.tabs.reinsert
import com.jlees.mibrowser.tabs.repairSaved
import com.jlees.mibrowser.tabs.restoreSession
import com.jlees.mibrowser.tabs.tabsToClear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tab rules, settings migration and the incognito recording rule. */
class StructureTest {
    private fun tab(id: String, used: Long = 0, url: String? = "https://$id.com", pinned: Boolean = false, opener: String? = null) =
        newTab(url).copy(id = id, lastUsed = used, pinned = pinned, openerId = opener, historyStack = listOfNotNull(url), currentIndex = if (url != null) 0 else -1)

    @Test fun closingTheActiveTabGoesToTheLastUsed() {
        val s = TabsState(listOf(tab("a", 3), tab("b", 9), tab("c", 5)), "a")
        assertEquals(TabsState(listOf(tab("b", 9), tab("c", 5)), "b"), closeTabs(s, setOf("a")))
    }

    @Test fun closingAPopupGoesBackToItsOpener() {
        val s = TabsState(listOf(tab("p", 10, opener = "o"), tab("o", 1), tab("x", 5)), "p")
        assertEquals("o", closeTabs(s, setOf("p")).activeId)
    }

    @Test fun closingAnotherTabKeepsTheActiveOne() {
        val s = TabsState(listOf(tab("a"), tab("b")), "a")
        assertEquals("a", closeTabs(s, setOf("b")).activeId)
    }

    @Test fun closingEverythingStartsOneNewTab() {
        val s = closeTabs(TabsState(listOf(tab("a"), tab("b")), "a"), setOf("a", "b"))
        assertEquals(1, s.tabs.size)
        assertEquals(s.tabs[0].id, s.activeId)
        assertTrue(s.tabs[0].isFresh)
    }

    @Test fun clearAllKeepsPinnedTabsUnlessWiping() {
        val tabs = listOf(tab("a", pinned = true), tab("b"), tab("c"))
        assertEquals(setOf("b", "c"), tabsToClear(tabs, keepPinned = true))
        assertEquals(setOf("a", "b", "c"), tabsToClear(tabs, keepPinned = false))
        assertEquals(listOf("a"), closeTabs(TabsState(tabs, "b"), tabsToClear(tabs, true)).tabs.map { it.id })
    }

    @Test fun insertingAndRestoringTabs() {
        val s = TabsState(listOf(tab("a"), tab("b"), tab("c")), "b")
        assertEquals(listOf("a", "b", "n", "c"), insertAfterActive(s, tab("n")).map { it.id })
        assertEquals(listOf("a", "b", "c", "z"), reinsert(s.tabs, tab("z"), 99).map { it.id })
    }

    @Test fun onlyAFreshTabSwitchesIncognito() {
        assertTrue(canToggleIncognito(newTab(), supported = true))
        assertFalse(canToggleIncognito(newTab(), supported = false))
        assertFalse(canToggleIncognito(tab("a"), supported = true)) // has a page
        assertFalse(canToggleIncognito(newTab().copy(openerId = "o"), supported = true)) // a pop-up
    }

    @Test fun incognitoTabsKeepNoTraces() {
        assertTrue(newTab().keepsTraces)
        assertFalse(newTab().copy(profile = newIncognitoProfile()).keepsTraces)
    }

    @Test fun sessionRestore() {
        val saved = listOf(tab("a", 1), tab("blank", 2, url = null), tab("c", 3))
        assertEquals("c", restoreSession(saved, "c", StartupTabs.LAST, null).activeId)
        assertEquals("a", restoreSession(saved, "gone", StartupTabs.LAST, null).activeId) // first with a page
        assertEquals("blank", restoreSession(saved, "c", StartupTabs.NEW, null).activeId) // reuses the blank tab
        val fresh = restoreSession(saved.filter { it.url != null }, null, StartupTabs.NEW, null)
        assertEquals(3, fresh.tabs.size); assertEquals(fresh.tabs[0].id, fresh.activeId)
        val link = tab("link")
        assertEquals(TabsState(listOf(link) + saved, "link"), restoreSession(saved, "c", StartupTabs.LAST, link))
    }

    @Test fun repairingASavedTab() {
        val t = newTab().copy(historyStack = listOf("https://a.com", "https://b.com"), currentIndex = 7, title = "")
        val r = repairSaved(t, loadedOnce = false)
        assertEquals(1, r.currentIndex); assertEquals("https://b.com", r.url); assertEquals("New Tab", r.title)
        assertEquals(-1, repairSaved(newTab().copy(currentIndex = 3), true).currentIndex)
    }

    @Test fun settingsMigration() {
        // v0: third-party cookies were blocked by default; now allowed
        assertFalse(Settings(settingsVersion = 0, blockThirdPartyCookies = true).migrated().blockThirdPartyCookies)
        // v1 → v2: with "Block Ads & Trackers" off, ads and cookie banners stay off too
        val off = Settings(settingsVersion = 1, blockTrackers = false, blockAds = true, hideCookieBanners = true).migrated()
        assertFalse(off.blockAds); assertFalse(off.hideCookieBanners); assertEquals(2, off.settingsVersion)
        val on = Settings(settingsVersion = 1).migrated()
        assertTrue(on.blockAds); assertTrue(on.hideCookieBanners)
        // current settings are left alone
        val current = Settings(settingsVersion = 2, blockAds = false)
        assertEquals(current, current.migrated())
    }

    @Test fun resetSettingsAreNotMigratedAgain() {
        // Reset stores Settings().migrated(); a later "trackers off" must survive the next launch's migration
        val reset = Settings().migrated().copy(blockTrackers = false)
        assertTrue(reset.migrated().blockAds)
        assertTrue(reset.migrated().hideCookieBanners)
    }

    @Test fun trackingParamsOnlyInTheQuery() {
        assertEquals("https://a.com/?id=5#/r?utm_source=x", com.jlees.mibrowser.adblock.stripTracking("https://a.com/?id=5&utm_source=y#/r?utm_source=x"))
        assertEquals("https://app.example/#/route?utm_source=x", com.jlees.mibrowser.adblock.stripTracking("https://app.example/#/route?utm_source=x"))
        assertEquals("https://a.com/?q=what?", com.jlees.mibrowser.adblock.stripTracking("https://a.com/?q=what?&fbclid=1"))
    }

    @Test fun oldSettingsJsonLoads() {
        val s = json.decodeFromString(Settings.serializer(), """{"themeMode":"dark","progressBarMode":"ltr","startupTabMode":"last","uiPadding":"airy"}""")
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(LoadingBar.STANDARD, s.progressBarMode)
        assertEquals(StartupTabs.LAST, s.startupTabMode)
        // an unknown value falls back to the default instead of failing the whole file
        assertEquals(Settings().themeMode, json.decodeFromString(Settings.serializer(), """{"themeMode":"neon"}""").themeMode)
        assertNotEquals(Settings().startupTabMode, s.startupTabMode)
    }
}

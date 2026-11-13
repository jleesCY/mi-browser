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
import com.jlees.mibrowser.tabs.rememberClosed
import com.jlees.mibrowser.tabs.keptClosed
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

    @Test fun hidingTheSwitcherEndsItsSettledState() {
        // every way of hiding it (a bookmark opened over it, the walkthrough…) must leave the Pill's swipes working
        val sw = com.jlees.mibrowser.tabs.SwitcherTransition()
        sw.visible = true; sw.settled = true; sw.flyingTabId = "t"
        sw.visible = false
        assertFalse(sw.settled); assertEquals(null, sw.flyingTabId)
    }

    @Test fun updateCheckWaitsOnlyAfterNothingNew() {
        val hour = 3_600_000L
        assertTrue(com.jlees.mibrowser.update.Updates.dueForCheck(now = 13 * hour, lastNothingNew = 0)) // never checked
        assertFalse(com.jlees.mibrowser.update.Updates.dueForCheck(now = 11 * hour, lastNothingNew = 0)) // checked recently
        assertTrue(com.jlees.mibrowser.update.Updates.dueForCheck(now = 12 * hour, lastNothingNew = 0))
    }

    @Test fun dataSavedByOlderVersionsStillLoads() {
        // an update must never lose data: fields a newer version dropped ("skippedUpdate") or doesn't know are ignored,
        // and everything else reads back as it was
        val old = """{"themeMode":"dark","accentColor":"#3366FF","skippedUpdate":"1.4.0","someFutureKey":true,"settingsVersion":3}"""
        val s = com.jlees.mibrowser.data.json.decodeFromString(Settings.serializer(), old)
        assertEquals(com.jlees.mibrowser.data.ThemeMode.DARK, s.themeMode)
        assertEquals("#3366FF", s.accentColor)
        val tabs = """[{"id":"t1","title":"Mars","historyStack":["https://en.wikipedia.org/wiki/Mars"],"currentIndex":0,"pinned":true,"retiredField":1}]"""
        val t = com.jlees.mibrowser.data.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.jlees.mibrowser.data.TabItem.serializer()), tabs).single()
        assertEquals(listOf("https://en.wikipedia.org/wiki/Mars"), t.historyStack); assertTrue(t.pinned)
    }

    @Test fun recentlyClosedNeverKeepsIncognitoOrBlankTabs() {
        val normal = tab("a")
        val incognito = tab("b").copy(profile = com.jlees.mibrowser.data.newIncognitoProfile())
        val blank = tab("c", url = null)
        assertEquals(listOf("a"), rememberClosed(emptyList(), listOf(normal, incognito, blank)).map { it.id })
        // newest first, at most 10
        val many = (1..12).map { tab("t$it") }
        assertEquals("t12", rememberClosed(emptyList(), many).first().id)
        assertEquals(10, rememberClosed(emptyList(), many, max = 10).size)
    }

    @Test fun recentlyClosedDropOffAfterTheirTime() {
        val closed = rememberClosed(emptyList(), listOf(tab("old")), now = 0) + rememberClosed(emptyList(), listOf(tab("new")), now = 3_000_000)
        assertEquals(1000L * 0, closed.first { it.id == "old" }.closedAt) // stamped when closed
        val dayLater = 86_400_000L + 1
        assertEquals(listOf("new"), keptClosed(closed, com.jlees.mibrowser.data.KeepClosedTabs.DAY, dayLater).map { it.id })
        assertEquals(2, keptClosed(closed, com.jlees.mibrowser.data.KeepClosedTabs.WEEK, dayLater).size)
        // a 1 Hour choice saved by 1.6.0 reads as 1 Day
        assertEquals(com.jlees.mibrowser.data.KeepClosedTabs.DAY, com.jlees.mibrowser.data.json.decodeFromString(Settings.serializer(), """{"keepClosedTabs":"hour"}""").keepClosedTabs)
        assertEquals(listOf("new"), keptClosed(closed, com.jlees.mibrowser.data.KeepClosedTabs.WEEK, 604_800_000L + 1).map { it.id }) // a week on, only the newer one
        assertEquals(2, keptClosed(closed, com.jlees.mibrowser.data.KeepClosedTabs.SESSION, Long.MAX_VALUE).size) // until mi closes
    }

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
        val r = repairSaved(t)
        assertEquals(1, r.currentIndex); assertEquals("https://b.com", r.url); assertEquals("New Tab", r.title)
        assertEquals(-1, repairSaved(newTab().copy(currentIndex = 3)).currentIndex)
    }

    @Test fun settingsMigration() {
        // v0: third-party cookies were blocked by default; now allowed
        assertFalse(Settings(settingsVersion = 0, blockThirdPartyCookies = true).migrated().blockThirdPartyCookies)
        // v1 → v2: with "Block Ads & Trackers" off, ads and cookie banners stay off too
        val off = Settings(settingsVersion = 1, blockTrackers = false, blockAds = true, hideCookieBanners = true).migrated()
        assertFalse(off.blockAds); assertFalse(off.hideCookieBanners); assertEquals(4, off.settingsVersion) // then on to the latest
        val on = Settings(settingsVersion = 1).migrated()
        assertTrue(on.blockAds); assertTrue(on.hideCookieBanners)
        // v2 → v3: HTTPS Only (and Warn Every Time) switch on once, even where it was off
        val v2 = Settings(settingsVersion = 2, httpsOnly = false, httpsWarnEveryTime = false).migrated()
        assertTrue(v2.httpsOnly && v2.httpsWarnEveryTime); assertEquals(4, v2.settingsVersion)
        // after that it's an ordinary setting: turned off, it stays off
        val turnedOff = v2.copy(httpsOnly = false)
        assertEquals(turnedOff, turnedOff.migrated())
        // current settings are left alone
        val current = Settings(settingsVersion = 4, blockAds = false)
        assertEquals(current, current.migrated())
    }

    @Test fun fontMigrationAndWalkthrough() {
        // v3 → v4: the Rounded Font switch becomes the Font choice
        val rounded = Settings(settingsVersion = 3, roundedFont = true).migrated()
        assertEquals(com.jlees.mibrowser.data.AppFont.ROUNDED, rounded.font)
        assertEquals(com.jlees.mibrowser.data.AppFont.STANDARD, Settings(settingsVersion = 3).migrated().font)
        // the Quick Guide shows to existing users after updating, as well as to new installs
        assertFalse(rounded.walkthroughSeen); assertFalse(Settings().migrated().walkthroughSeen)
        // saved under its name from before the rename, so installs that saw it don't get it again
        assertTrue(com.jlees.mibrowser.data.json.encodeToString(Settings.serializer(), Settings(walkthroughSeen = true)).contains("\"quickGuideSeen\":true"))
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

    @Test fun downloadSettingsSaveAndLoad() {
        val s = json.decodeFromString(Settings.serializer(), """{"downloadHandler":"android","askWhereToSave":true,"downloadFolder":"content://x/tree/primary%3ADocuments"}""")
        assertEquals(com.jlees.mibrowser.data.DownloadHandler.ANDROID, s.downloadHandler)
        assertTrue(s.askWhereToSave)
        assertEquals(s, json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), s)))
        // defaults: mi's downloader, into Downloads, with the confirmation
        assertEquals(com.jlees.mibrowser.data.DownloadHandler.MI, Settings().downloadHandler)
        assertTrue(Settings().confirmDownloads && Settings().downloadFolder == null && !Settings().askWhereToSave)
    }

    @Test fun warnEveryTimeIsOnByDefault() {
        assertTrue(Settings().httpsWarnEveryTime) // HTTPS Only asks before every insecure visit unless turned off
        assertTrue(!json.decodeFromString(Settings.serializer(), """{"httpsWarnEveryTime":false}""").httpsWarnEveryTime)
    }

    @Test fun clearOnExitSavesAndLoads() {
        assertTrue(json.decodeFromString(Settings.serializer(), """{"clearDataOnExit":true}""").clearDataOnExit)
        assertTrue(!Settings().clearDataOnExit) // off unless chosen
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

    @Test fun dateGroupsForAnyList() {
        val now = java.util.Calendar.getInstance().apply { set(2026, 10, 6, 15, 0) }.timeInMillis
        val day = 86_400_000L
        val times = listOf(now - 3_600_000, now - day, now - 3 * day, now - 30 * day)
        assertEquals(listOf("Today", "Yesterday", "Last 7 Days", "Older"), com.jlees.mibrowser.library.groupByDate(times, now) { it }.map { it.first })
    }
}

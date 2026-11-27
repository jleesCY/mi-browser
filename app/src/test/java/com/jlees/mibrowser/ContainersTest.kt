package com.jlees.mibrowser

import com.jlees.mibrowser.containers.Container
import com.jlees.mibrowser.containers.containerOf
import com.jlees.mibrowser.containers.containerProfile
import com.jlees.mibrowser.containers.inheritedContainer
import com.jlees.mibrowser.containers.isolatedProfile
import com.jlees.mibrowser.containers.orphanedProfiles
import com.jlees.mibrowser.containers.ProfileColor
import com.jlees.mibrowser.containers.ProfileIcon
import com.jlees.mibrowser.containers.newProfileLook
import com.jlees.mibrowser.containers.profileName
import com.jlees.mibrowser.containers.profileTabs
import com.jlees.mibrowser.containers.tabToShow
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.KeepClosedTabs
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.importedOver
import com.jlees.mibrowser.data.newIncognitoProfile
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.library.addHistory
import com.jlees.mibrowser.library.inProfiles
import com.jlees.mibrowser.tabs.TabsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ContainersTest {
    private val work = Container("w", ProfileIcon.WORK, ProfileColor.BLUE, isolated = true)
    private val games = Container("g", ProfileIcon.GAMES, ProfileColor.GREEN) // its tabs and history apart, sign-ins shared with Default

    @Test fun aNewTabOpensInTheProfileYoureIn() {
        val opener = newTab("https://mail.example").copy(container = "w")
        assertEquals("w", inheritedContainer(opener, listOf(work)))
        // an incognito tab is listed in its profile too (its storage stays its own); a deleted profile is Default
        assertEquals("w", inheritedContainer(opener.copy(profile = newIncognitoProfile()), listOf(work)))
        assertNull(inheritedContainer(opener, listOf(games)))
        assertNull(inheritedContainer(null, listOf(work)))
    }

    @Test fun switchingProfileShowsOnlyItsTabs() {
        val a = newTab("https://a.example").copy(lastUsed = 1)
        val w1 = newTab("https://w1.example").copy(container = "w", lastUsed = 2)
        val w2 = newTab("https://w2.example").copy(container = "w", lastUsed = 5)
        assertEquals(listOf(a), profileTabs(listOf(a, w1, w2), null))
        assertEquals(listOf(a, w1, w2), profileTabs(listOf(a, w1, w2), null, enabled = false)) // profiles off: all together
        assertEquals(w2, tabToShow(listOf(a, w1, w2), "w")) // its most recent
        assertNull(tabToShow(listOf(a), "w")) // none yet: a new tab starts it
    }

    @Test fun closingAProfilesLastTabStaysInThatProfile() {
        val a = newTab("https://a.example").copy(lastUsed = 9)
        val w = newTab("https://w.example").copy(container = "w", lastUsed = 1)
        val after = com.jlees.mibrowser.tabs.closeTabs(com.jlees.mibrowser.tabs.TabsState(listOf(a, w), w.id), setOf(w.id))
        // not the more recent Default tab: a fresh tab in Work
        assertEquals("w", after.tabs.first { it.id == after.activeId }.container)
        assertEquals(listOf(a.id), after.tabs.filter { it.id != after.activeId }.map { it.id })
    }

    @Test fun onlyAProfileKeepingItsSignInsSeparateHasItsOwnStorage() {
        assertEquals("w", isolatedProfile("w", listOf(work, games)))
        assertNull(isolatedProfile("g", listOf(work, games))) // browses with Default's sign-ins
        assertNull(isolatedProfile(null, listOf(work)))
        // the storage of a deleted profile, or of one that stopped keeping its own, goes; never Default's or incognito's
        val names = listOf("Default", "incognito-1", containerProfile("w"), containerProfile("g"), containerProfile("old"))
        assertEquals(listOf(containerProfile("g"), containerProfile("old")), orphanedProfiles(names, listOf(work, games)))
    }

    @Test fun aNewProfileLooksUnlikeTheOthers() {
        // the first icon and colour not taken; Default's Web and Accent stay its own
        assertEquals(ProfileIcon.WORK to ProfileColor.BLUE, newProfileLook(emptyList()))
        assertEquals(ProfileIcon.HOME to ProfileColor.RED, newProfileLook(listOf(work, games)))
        assertEquals("Games", profileName(games))
        assertEquals("Default", profileName(null))
    }

    @Test fun eachProfileHasItsOwnHistory() {
        var (h, _) = addHistory(emptyList(), emptyList(), "https://news.example", "News", now = 1)
        h = addHistory(h, emptyList(), "https://news.example", "News", now = 2, profile = "w").first
        // the same page in two profiles is listed in each
        assertEquals(listOf("w", null), h.map { it.profile })
        assertEquals(listOf<String?>(null), inProfiles(h, setOf(null)).map { it.profile })
        assertEquals(2, inProfiles(h, null).size)
        assertEquals(work, containerOf(newTab().copy(container = "w"), listOf(work)))
    }

    @Test fun restoringABackupKeepsThisPhonesProfiles() {
        // one left out would have its sign-ins deleted at the next launch
        val restored = Settings(containers = listOf(games)).importedOver(Settings(containers = listOf(work, games)))
        assertEquals(listOf(games, work), restored.containers)
    }

    @Test fun aTabsProfileIsSaved() {
        val dir = Files.createTempDirectory("containers").toFile()
        val s = TabsStore(JsonStore(dir), { KeepClosedTabs.SESSION }) {}
        val t = newTab("https://a.example")
        s.open(t)
        s.setContainer(t.id, "w")
        s.saver.writeNow()
        assertEquals("w", TabsStore(JsonStore(dir), { KeepClosedTabs.SESSION }) {}.apply { load(null, StartupTabs.LAST) }[t.id]?.container)
    }

    @Test fun historyEntriesKeepTheirProfile() {
        val item = HistoryItem("https://a.example", "A", 1, profile = "w")
        val json = com.jlees.mibrowser.data.json.encodeToString(HistoryItem.serializer(), item)
        assertEquals("w", com.jlees.mibrowser.data.json.decodeFromString(HistoryItem.serializer(), json).profile)
    }
}

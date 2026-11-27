package com.jlees.mibrowser

import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.KeepClosedTabs
import com.jlees.mibrowser.data.StartupTabs
import com.jlees.mibrowser.data.newIncognitoProfile
import com.jlees.mibrowser.data.newTab
import com.jlees.mibrowser.tabs.TabGroup
import com.jlees.mibrowser.tabs.TabsStore
import com.jlees.mibrowser.tabs.groupFromLink
import com.jlees.mibrowser.tabs.groupName
import com.jlees.mibrowser.tabs.liveGroups
import com.jlees.mibrowser.tabs.tabsInGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class TabGroupsTest {
    private val dir = Files.createTempDirectory("groups").toFile()
    private fun store() = TabsStore(JsonStore(dir), { KeepClosedTabs.SESSION }) {}

    @Test fun aLinkStartsAGroupNamedForItsSiteAndJoinsIt() {
        val opener = newTab("https://news.example.com/today")
        val (groups, id) = groupFromLink(opener, emptyList()) { "g1" }!!
        assertEquals(listOf(TabGroup("g1", "news.example.com")), groups)
        assertEquals("g1", id)
        // a page already in a group: the new tab joins it, no new group
        val (same, joined) = groupFromLink(opener.copy(groupId = "g1"), groups) { "g2" }!!
        assertEquals(groups, same)
        assertEquals("g1", joined)
    }

    @Test fun incognitoAndBlankTabsAreNeverGrouped() {
        // a group named for an incognito tab's site would stay on the phone after it
        assertNull(groupFromLink(newTab("https://secret.example").copy(profile = newIncognitoProfile()), emptyList()) { "g" })
        assertNull(groupFromLink(newTab(null), emptyList()) { "g" })
    }

    @Test fun aGroupGoesWithItsLastTabUnlessUndoCanBringItBack() {
        val groups = listOf(TabGroup("a", "A"), TabGroup("b", "B"))
        val tabs = listOf(newTab("https://x.example").copy(groupId = "a"))
        assertEquals(listOf("a"), liveGroups(groups, tabs).map { it.id })
        assertEquals(listOf("a", "b"), liveGroups(groups, tabs, kept = newTab().copy(groupId = "b")).map { it.id })
    }

    @Test fun aChipShowsItsGroupsTabs() {
        val a = newTab("https://a.example").copy(groupId = "g")
        val b = newTab("https://b.example")
        assertEquals(listOf(a, b), tabsInGroup(listOf(a, b), null))
        assertEquals(listOf(a), tabsInGroup(listOf(a, b), "g"))
        assertEquals("Trip", groupName("  Trip ", "Group"))
        assertEquals("Group", groupName("   ", "Group"))
    }

    @Test fun groupsAreKeptForTheSavedTabsAndDropWhenEmptied() {
        val s = store()
        s.load(null, StartupTabs.LAST)
        val opener = newTab("https://recipes.example/soup")
        s.open(opener)
        val child = newTab("https://recipes.example/bread")
        s.openInBackground(child)
        s.joinGroupFromLink(opener.id, child.id)
        assertEquals(listOf("recipes.example"), s.groups.map { it.name })
        assertEquals(setOf(s.groups.single().id), setOf(s[opener.id]?.groupId, s[child.id]?.groupId))
        s.saver.writeNow(); s.groupSaver.writeNow()

        val again = store().apply { load(null, StartupTabs.LAST) }
        assertEquals(listOf("recipes.example"), again.groups.map { it.name })
        assertEquals(again.groups.single().id, again[child.id]?.groupId)

        // both taken out: the group has no tab left and goes
        again.setGroup(opener.id, null)
        again.setGroup(child.id, null)
        assertEquals(emptyList<TabGroup>(), again.groups)
    }

    @Test fun ungroupingKeepsTheTabsOpen() {
        val s = store()
        val t = newTab("https://a.example")
        s.open(t)
        val id = s.newGroup("Work", t.id)!!
        s.ungroup(id)
        assertEquals(emptyList<TabGroup>(), s.groups)
        assertNull(s[t.id]?.groupId)
        assertEquals(1, s.list.count { it.id == t.id })
    }
}

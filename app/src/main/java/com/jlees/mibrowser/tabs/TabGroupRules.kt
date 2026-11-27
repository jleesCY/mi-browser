// Tab groups: tabs gathered under a name, shown together from a chip in the tab switcher. A tab opened from a page's
// link joins that page's group, or the two start one named for its site (Settings › Tabs › Group Tabs from Links);
// any tab can be put in a group, or taken out, from the tab menu. The decisions are here; TabsStore keeps the groups.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import kotlinx.serialization.Serializable

/** A named group; its tabs are the ones whose groupId is its [id]. */
@Serializable
data class TabGroup(val id: String, val name: String)

/**
 * The group a tab opened from [opener]'s page goes in: the opener's, else a new one named for the opener's site, which
 * the opener joins too. Null when nothing is grouped: an incognito tab (a group named for a site would stay on the
 * phone after it), or an opener showing no page.
 * @return the groups after it, and the group both tabs are in
 */
fun groupFromLink(opener: TabItem, groups: List<TabGroup>, newId: () -> String): Pair<List<TabGroup>, String>? {
    if (!opener.keepsTraces) return null
    opener.groupId?.takeIf { id -> groups.any { it.id == id } }?.let { return groups to it }
    val site = opener.url?.let(::displayHost)?.takeIf { it.isNotBlank() && it != "about:blank" } ?: return null
    val group = TabGroup(newId(), site)
    return groups + group to group.id
}

/**
 * The groups that still hold a tab, among [tabs] and [kept] (a closed tab Undo can bring back keeps its group): a group
 * goes once its last tab does.
 */
fun liveGroups(groups: List<TabGroup>, tabs: List<TabItem>, kept: TabItem? = null): List<TabGroup> =
    groups.filter { g -> tabs.any { it.groupId == g.id } || kept?.groupId == g.id }

/** The tabs a switcher chip shows: every tab ([groupId] null), or one group's. */
fun tabsInGroup(tabs: List<TabItem>, groupId: String?): List<TabItem> =
    if (groupId == null) tabs else tabs.filter { it.groupId == groupId }

/** A group's name as typed: trimmed, and never blank (an empty name keeps [fallback]). */
fun groupName(typed: String, fallback: String): String = typed.trim().ifEmpty { fallback }

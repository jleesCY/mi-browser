// Tab groups on screen (the rules are in TabGroupRules.kt, the groups in TabsStore): the row of chips in the tab switcher
// (All, then each group with its count; + makes a group of the centred tab; holding a chip renames or ungroups it), and
// the dialogs: picking a tab's group from the tab menu, and naming a group.
package com.jlees.mibrowser.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.delay

/** What a group dialog is for: a new group holding [tabId], or renaming group [groupId]. */
sealed interface GroupNaming {
    data class New(val tabId: String) : GroupNaming
    data class Rename(val groupId: String) : GroupNaming
}

/** Which group dialog is open, if any: shown by [TabGroupDialogs], from the tab menu and the switcher's chips. */
object TabGroupFlow {
    /** The tab whose group is being picked (the tab menu's Group tile). */
    var picking by mutableStateOf<String?>(null)
    /** A group being named. */
    var naming by mutableStateOf<GroupNaming?>(null)
    /** The group whose chip was held: its Rename and Ungroup. */
    var managing by mutableStateOf<String?>(null)
}

/**
 * The switcher's chips over the cards, shown once a group exists: All, then each group with how many tabs it has. The
 * chosen one narrows the cards to its tabs ([selected] null: All). [+] makes a new group of [centred] (not an incognito
 * tab: those aren't grouped). Holding a group's chip offers to rename or ungroup it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChips(groups: List<TabGroup>, tabs: List<TabItem>, selected: String?, centred: TabItem?, onSelect: (String?) -> Unit) {
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    @Composable
    fun Chip(label: String, count: Int?, on: Boolean, onClick: () -> Unit, onHold: (() -> Unit)? = null) {
        val press = remember { MutableInteractionSource() }
        val bg by animateColorAsState(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHigh, label = "chip")
        val fg = if (on) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant
        Row(
            Modifier.height(40.dp).pressScale(press).clip(CircleShape).background(bg)
                .combinedClickable(press, androidx.compose.material3.ripple(), onLongClick = onHold?.let { h -> { haptics?.longPress(); h() } }, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(label, 14f, fg, ui.semibold, maxLines = 1, pan = false)
            if (count != null) Txt("$count", 12f, fg.copy(alpha = 0.7f), ui.semibold, Modifier.padding(start = 6.dp), maxLines = 1)
        }
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { Chip("All", null, selected == null, { onSelect(null) }) }
        items(groups, key = { it.id }) { g ->
            Chip(g.name, tabs.count { it.groupId == g.id }, selected == g.id, { onSelect(g.id) }) { TabGroupFlow.managing = g.id }
        }
        if (centred?.keepsTraces == true) item(key = "new") {
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier.size(40.dp).pressScale(press).clip(CircleShape).background(ui.cs.surfaceContainerHigh)
                    .touchable(source = press) { TabGroupFlow.naming = GroupNaming.New(centred.id) },
                contentAlignment = Alignment.Center,
            ) { Sym("add", 22f, ui.cs.onSurfaceVariant, label = "New group") }
        }
    }
}

/** The group dialogs (see [TabGroupFlow]): one at a time, each keeping what it showed while it fades out. */
@Composable
fun TabGroupDialogs(vm: BrowserViewModel) {
    GroupPicker(vm)
    GroupNamer(vm)
    GroupManager(vm)
}

/** The tab menu's Group tile: the tab's group chosen from the list, a new one made, or the tab taken out. */
@Composable
private fun GroupPicker(vm: BrowserViewModel) {
    val picking = TabGroupFlow.picking
    var last by remember { mutableStateOf(picking) }
    if (picking != null) last = picking
    val tab = last?.let { vm.tabs[it] } ?: return
    val close = { TabGroupFlow.picking = null }
    ModalFrame(picking != null, close, dismissOnScrim = true) {
        DialogCard("Tab Group", actions = { DialogAction("Cancel", onClick = close) }) {
            // the groups in its profile (containers/): another profile's aren't seen from here
            vm.tabs.groups.filter { g -> vm.tabs.list.any { it.groupId == g.id && it.container == tab.container } }.forEach { g ->
                DialogRow(g.name, on = tab.groupId == g.id, mark = { Sym("tab_group", 20f, LocalUi.current.cs.onSurfaceVariant) }) { vm.tabs.setGroup(tab.id, g.id); close() }
            }
            DialogRow("New Group", mark = { Sym("add", 20f, LocalUi.current.cs.onSurfaceVariant) }) { close(); TabGroupFlow.naming = GroupNaming.New(tab.id) }
            if (tab.groupId != null) DialogRow("Remove from Group", mark = { Sym("close", 20f, LocalUi.current.cs.onSurfaceVariant) }) { vm.tabs.setGroup(tab.id, null); close() }
        }
    }
}

/** Naming a new group (it holds the tab it was made for) or an existing one. */
@Composable
private fun GroupNamer(vm: BrowserViewModel) {
    val naming = TabGroupFlow.naming
    var last by remember { mutableStateOf(naming) }
    if (naming != null) last = naming
    val n = last ?: return
    val current = (n as? GroupNaming.Rename)?.let { r -> vm.tabs.groups.firstOrNull { it.id == r.groupId }?.name }.orEmpty()
    var text by remember(n) { mutableStateOf(current) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(naming) { if (naming != null) { delay(300); runCatching { fr.requestFocus() } } } // after the dialog's animation
    val close = { TabGroupFlow.naming = null }
    ModalFrame(naming != null, close) {
        DialogCard(if (n is GroupNaming.New) "New Group" else "Rename Group", actions = {
            DialogAction("Cancel", onClick = close)
            DialogAction(if (n is GroupNaming.New) "Create" else "Save") {
                when (n) {
                    is GroupNaming.New -> vm.tabs.newGroup(text, n.tabId)
                    is GroupNaming.Rename -> vm.tabs.renameGroup(n.groupId, text)
                }
                close()
            }
        }) { ClearableField(text, { text = it }, fr, placeholder = "Group name") }
    }
}

/** A held chip: rename the group, or ungroup it (its tabs stay open). */
@Composable
private fun GroupManager(vm: BrowserViewModel) {
    val managing = TabGroupFlow.managing
    var last by remember { mutableStateOf(managing) }
    if (managing != null) last = managing
    val group = last?.let { id -> vm.tabs.groups.firstOrNull { it.id == id } } ?: return
    val close = { TabGroupFlow.managing = null }
    ModalFrame(managing != null, close, dismissOnScrim = true) {
        DialogCard(null, actions = { DialogAction("Cancel", onClick = close) }) {
            val ui = LocalUi.current
            Txt(group.name, 18f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 12.dp), maxLines = 2)
            DialogRow("Rename Group", mark = { Sym("edit", 20f, LocalUi.current.cs.onSurfaceVariant) }) { close(); TabGroupFlow.naming = GroupNaming.Rename(group.id) }
            DialogRow("Ungroup", mark = { Sym("tab_group", 20f, LocalUi.current.cs.onSurfaceVariant) }) { vm.tabs.ungroup(group.id); close() }
        }
    }
}

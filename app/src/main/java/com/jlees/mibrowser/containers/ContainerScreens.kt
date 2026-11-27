// Profiles on screen (named "containers" in the code): each drawn as its icon in its colour (ProfileBadge). The tab
// switcher shows the one you're in beside its tab count; tapped, every profile to switch to or edit. Also the profile's
// actions (change its icon and colour, keep its sign-ins separate, delete it), moving a tab to another profile, or a new
// one, from the tab menu, and History's profile chips.
package com.jlees.mibrowser.containers

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*

/** Default's key in [ContainerFlow] (it has no id: its tabs belong to no container). */
private const val DEFAULT = ""
private fun key(id: String?) = id ?: DEFAULT
private fun idOf(key: String) = key.takeIf { it != DEFAULT }

/** Which profile dialog is open, if any: shown by [ContainerDialogs]. */
object ContainerFlow {
    /** The tab being moved to another profile (the tab menu's Profile tile). */
    var picking by mutableStateOf<String?>(null)
    /** The profile being edited: its actions ("" for Default). */
    var managing by mutableStateOf<String?>(null)
    /** The profile whose icon and colour are being changed ("" for Default). */
    var lookFor by mutableStateOf<String?>(null)
    /** The tab switcher's list of profiles ([CurrentProfile] tapped). */
    var choosing by mutableStateOf(false)
    /** Making a new profile ([newProfile]). */
    var creating by mutableStateOf(false)
    /** The tab that moves into it once made (from Move to Profile), if any. */
    var creatingFor: String? = null
    /** Whether you go to it once made (made from the tab switcher's list). */
    var creatingOpens = false
}

/** Opens profile [id]'s actions (null: Default's): from the tab switcher's list of profiles, or Settings › Tabs. */
fun editProfile(id: String?) { ContainerFlow.managing = key(id) }

/** Opens New Profile; once made, tab [moving] (if any) moves into it, or ([open]) you go to it. */
fun newProfile(moving: String? = null, open: Boolean = false) {
    ContainerFlow.creatingFor = moving; ContainerFlow.creatingOpens = open; ContainerFlow.creating = true
}

/** A profile colour as drawn: its swatch, or the accent exactly as picked (as the main action wears it). */
@Composable
fun ProfileColor.shade(): Color = argb?.let { Color(it) } ?: LocalUi.current.mainAction

/**
 * A profile as a circle: its icon in its colour on a light wash of it, or, when [on] (the one you're in, one picked),
 * filled with its colour, the icon white (black on a light colour such as yellow).
 */
@Composable
fun ProfileBadge(icon: ProfileIcon, color: ProfileColor, on: Boolean, modifier: Modifier = Modifier, size: Float = 40f) {
    val ui = LocalUi.current
    val c = color.shade()
    // light mode: the icon a little darker, so light colours (yellow, mint) still read on their wash
    val ink = if (ui.isDark) c else lerp(c, Color.Black, 0.25f)
    val bg by animateColorAsState(if (on) c else c.copy(alpha = 0.2f), label = "profile")
    Box(modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Sym(icon.symbol, size * 0.5f, if (!on) ink else if (c.luminance() > 0.6f) Color.Black else Color.White)
    }
}

/**
 * The top of the tab switcher while profiles are on: the profile you're in, as its badge, then [count] (its tabs). Tap
 * it for every profile ([ProfileChooser]).
 */
@Composable
fun CurrentProfile(vm: BrowserViewModel, count: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val id = vm.currentProfile
    val (icon, color) = vm.lookOf(id)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = 48.dp).pressScale(press).clip(CircleShape).touchable(source = press) { ContainerFlow.choosing = true }
            .padding(start = 6.dp, end = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${vm.nameOf(id)} profile, $count. Choose a profile" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileBadge(icon, color, on = true, size = 28f)
        Txt(count, 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(start = 8.dp))
        Sym("keyboard_arrow_down", 18f, ui.cs.onSurfaceVariant, Modifier.padding(start = 2.dp))
    }
}

/** Every profile to switch to (the one you're in filled), each with its edit button, then New Profile. */
@Composable
private fun ProfileChooser(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val close = { ContainerFlow.choosing = false }
    ModalFrame(ContainerFlow.choosing, close, dismissOnScrim = true) {
        DialogCard("Profiles", actions = { DialogAction("Done", onClick = close) }) {
            val current = vm.currentProfile
            (listOf<String?>(null) + vm.settings.containers.map { it.id }).forEach { id ->
                val (icon, color) = vm.lookOf(id)
                val n = vm.tabs.list.count { it.container == id }
                DialogRow("${vm.nameOf(id)} · ${if (n == 1) "1 tab" else "$n tabs"}", mark = { ProfileBadge(icon, color, on = id == current, size = 28f) },
                    trailing = {
                        androidx.compose.material3.IconButton(onClick = { close(); editProfile(id) }) { Sym("edit", 20f, ui.cs.onSurfaceVariant, label = "Edit ${vm.nameOf(id)} profile") }
                    }) { close(); vm.switchProfile(id) }
            }
            DialogRow("New Profile", mark = { Sym("add", 20f, ui.cs.primary) }) { close(); newProfile(open = true) }
        }
    }
}

@Composable
fun ContainerDialogs(vm: BrowserViewModel) {
    ProfileChooser(vm)
    ProfileActions(vm)
    LookChanger(vm)
    ProfileMaker(vm)
    MovePicker(vm)
}

/** A row of a profile dialog: a mark, a label, and a check on the one that's [on] (or a [trailing] control). */
@Composable
private fun DialogRow(label: String, on: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, mark: @Composable () -> Unit, trailing: (@Composable () -> Unit)? = null, onPress: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).touchable(onClick = onPress).heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { mark() }
        Txt(label, 15f, color ?: ui.cs.onSurface, ui.semibold, Modifier.padding(start = 16.dp).weight(1f), maxLines = 1)
        if (trailing != null) trailing() else if (on) Sym("check", 20f, ui.cs.primary)
    }
}

/** Choosing a profile's look: its icon (shown in the chosen colour), then its colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPicker(icon: ProfileIcon, color: ProfileColor, onPick: (ProfileIcon, ProfileColor) -> Unit) {
    val ui = LocalUi.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileIcon.entries.forEach { i ->
            ProfileBadge(i, color, i == icon, Modifier.clip(CircleShape).touchable { onPick(i, color) }
                .semantics { contentDescription = i.label + if (i == icon) ", chosen" else "" }, size = 44f)
        }
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileColor.entries.forEach { c ->
            val on = c == color
            val shade = c.shade()
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(shade).touchable { onPick(icon, c) }
                    .semantics { contentDescription = c.label + if (on) ", chosen" else "" },
                contentAlignment = Alignment.Center,
            ) { if (on) Sym("check", 20f, if (shade.luminance() > 0.6f) Color.Black else Color.White) }
        }
    }
}

/** A held profile: change its icon and colour, keep its sign-ins separate (not Default: it is the shared one), or delete it. */
@Composable
private fun ProfileActions(vm: BrowserViewModel) {
    val managing = ContainerFlow.managing
    var last by remember { mutableStateOf(managing) }
    if (managing != null) last = managing
    val k = last ?: return
    val id = idOf(k)
    val profile = id?.let { c -> vm.settings.containers.firstOrNull { it.id == c } }
    if (id != null && profile == null) return
    val ui = LocalUi.current
    val close = { ContainerFlow.managing = null }
    ModalFrame(managing != null, close, dismissOnScrim = true) {
        DialogCard(null, actions = { DialogAction("Done", onClick = close) }) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val (icon, color) = vm.lookOf(id)
                ProfileBadge(icon, color, on = true, size = 48f)
                Column(Modifier.padding(start = 14.dp)) {
                    Txt(vm.nameOf(id), 18f, ui.cs.onSurface, ui.semibold)
                    val n = vm.tabs.list.count { it.container == id }
                    Txt(if (n == 1) "1 tab" else "$n tabs", 13f, ui.cs.onSurfaceVariant, ui.regular)
                }
            }
            DialogRow("Icon and Color", mark = { Sym("palette", 20f, ui.cs.onSurfaceVariant) }) { close(); ContainerFlow.lookFor = k }
            if (profile != null) {
                if (containersSupported) DialogRow("Keep Sign-ins Separate", mark = { Sym("lock", 20f, ui.cs.onSurfaceVariant) },
                    trailing = { MiSwitch(profile.isolated) { confirmIsolation(vm, profile, it) } }) { confirmIsolation(vm, profile, !profile.isolated) }
                DialogRow("Delete Profile", color = ui.cs.error, mark = { Sym("delete", 20f, ui.cs.error) }) { close(); confirmDelete(vm, profile) }
            }
        }
    }
}

/** Switching a profile's storage reloads its pages, and turning it off deletes its own sign-ins: asked first. */
private fun confirmIsolation(vm: BrowserViewModel, c: Container, on: Boolean) {
    vm.showAlert(
        if (on) "Keep Sign-ins Separate?" else "Share Default's Sign-ins?",
        if (on) "This profile's tabs reload signed out, with their own cookies and site data from now on."
        else "This profile's own cookies, sign-ins and site data are deleted, and its tabs reload with Default's. This can't be undone.",
        listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton(if (on) "Keep Separate" else "Share", if (on) ButtonStyle.DEFAULT else ButtonStyle.DESTRUCTIVE) { vm.setIsolated(c.id, on) }),
    )
}

private fun confirmDelete(vm: BrowserViewModel, c: Container) {
    val n = vm.tabs.list.count { it.container == c.id }
    val tabsLine = if (n == 0) "Its history moves to Default." else "Its ${if (n == 1) "tab" else "$n tabs"} and history move to Default."
    val dataLine = if (c.isolated) " Its own sign-ins and site data are deleted. This can't be undone." else ""
    vm.showAlert("Delete ${profileName(c)} Profile?", tabsLine + dataLine, listOf(
        AlertButton("Cancel", ButtonStyle.CANCEL),
        AlertButton("Delete", ButtonStyle.DESTRUCTIVE) { vm.deleteContainer(c.id) },
    ))
}

/** Changing a profile's icon and colour (Default's too). */
@Composable
private fun LookChanger(vm: BrowserViewModel) {
    val target = ContainerFlow.lookFor
    var last by remember { mutableStateOf(target) }
    if (target != null) last = target
    val k = last ?: return
    var look by remember(k) { mutableStateOf(vm.lookOf(idOf(k))) }
    val close = { ContainerFlow.lookFor = null }
    ModalFrame(target != null, close) {
        DialogCard("Icon and Color", actions = {
            DialogAction("Cancel", onClick = close)
            DialogAction("Save") { vm.setLook(idOf(k), look.first, look.second); close() }
        }) { LookPicker(look.first, look.second) { i, c -> look = i to c } }
    }
}

/** A new profile: its icon and colour, and whether it keeps its own sign-ins; made, the tab it was made for (if any) moves there. */
@Composable
private fun ProfileMaker(vm: BrowserViewModel) {
    val open = ContainerFlow.creating
    var look by remember(open) { mutableStateOf(newProfileLook(vm.settings.containers)) }
    var isolated by remember(open) { mutableStateOf(false) }
    val ui = LocalUi.current
    val close = { ContainerFlow.creating = false }
    ModalFrame(open, close) {
        DialogCard("New Profile", actions = {
            DialogAction("Cancel", onClick = close)
            DialogAction("Create") { val c = vm.newContainer(look.first, look.second, isolated); ContainerFlow.creatingFor?.let { vm.moveToContainer(it, c.id) }; if (ContainerFlow.creatingOpens) vm.switchProfile(c.id); close() }
        }) {
            Txt("Its own tabs, tab groups and history.", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 14.dp), lineHeight = 20f, scaled = false)
            LookPicker(look.first, look.second) { i, c -> look = i to c }
            if (containersSupported) Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).touchable { isolated = !isolated }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Txt("Keep Sign-ins Separate", 15f, ui.cs.onSurface, ui.semibold)
                    Txt("Its own cookies, sign-ins and site data", 13f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 18f)
                }
                MiSwitch(isolated) { isolated = it }
            }
        }
    }
}

/** The tab menu's Profile tile: the tab moves to another profile; its page reloads there. */
@Composable
private fun MovePicker(vm: BrowserViewModel) {
    val picking = ContainerFlow.picking
    var last by remember { mutableStateOf(picking) }
    if (picking != null) last = picking
    val tab = last?.let { vm.tabs[it] } ?: return
    val ui = LocalUi.current
    val close = { ContainerFlow.picking = null }
    ModalFrame(picking != null, close, dismissOnScrim = true) {
        DialogCard("Move to Profile", actions = { DialogAction("Cancel", onClick = close) }) {
            Txt("The tab goes there; its page reloads in that profile.", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp), lineHeight = 20f, scaled = false)
            (listOf<String?>(null) + vm.settings.containers.map { it.id }).forEach { id ->
                DialogRow(vm.nameOf(id), tab.container == id, mark = { val (i, c) = vm.lookOf(id); ProfileBadge(i, c, on = false, size = 28f) }) { vm.moveToContainer(tab.id, id); close() }
            }
            DialogRow("New Profile", mark = { Sym("add", 20f, ui.cs.primary) }) { close(); newProfile(moving = tab.id) }
        }
    }
}

/**
 * History's profile chips: each profile's badge, the ones whose history shows ([shown]) filled; tap to add or take one
 * away (one always stays).
 */
@Composable
fun ProfileChips(vm: BrowserViewModel, shown: Set<String?>, modifier: Modifier = Modifier, onChange: (Set<String?>) -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<String?>(null) + vm.settings.containers.map { it.id }).forEach { id ->
            val on = id in shown
            val (icon, color) = vm.lookOf(id)
            ProfileBadge(icon, color, on, Modifier.clip(CircleShape)
                .touchable { onChange(if (!on) shown + id else (shown - id).ifEmpty { shown }) }
                .semantics { contentDescription = "${vm.nameOf(id)} profile" + if (on) ", shown" else "" })
        }
    }
}

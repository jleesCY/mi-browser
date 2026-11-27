// Settings › Tabs: what opens at startup, resting unused tabs, grouping tabs opened from links, profiles (on or off, and
// each one to edit), and site icons in the tab switcher.
package com.jlees.mibrowser.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.containers.ProfileBadge
import com.jlees.mibrowser.containers.editProfile
import com.jlees.mibrowser.containers.lookOf
import com.jlees.mibrowser.containers.nameOf
import com.jlees.mibrowser.containers.newProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The Tabs section of Settings. */
fun tabsSection(vm: BrowserViewModel) = SettingsSection("tabs", "Tabs", "tab", "How tabs behave", listOf(
    SettingsRow("On Startup", "session restore continue") {
    ChoiceItem("power_settings_new", "On Startup", null, vm.settings.startupTabMode) { vm.updateSettings { copy(startupTabMode = it) } }
    },
    SettingsRow("Rest Unused Tabs", "memory battery sleep discard unload background tabs performance") {
        SwitchItem("hourglass_empty", "Rest Unused Tabs", "Tabs you haven't opened in 10 minutes free their memory and reload when you open them", vm.settings.restUnusedTabs) { vm.updateSettings { copy(restUnusedTabs = it) } }
    },
    SettingsRow("Group Tabs from Links", "tab groups group links new tab opened together organize") {
        SwitchItem("tab_group", "Group Tabs from Links", "Tabs you open from a page's links join its group", vm.settings.groupLinkTabs) { vm.updateSettings { copy(groupLinkTabs = it) } }
    },
    SettingsRow("Profiles", "profiles containers separate tabs history cookies sign in accounts work personal isolate switch edit new icon color delete") {
        val on = vm.settings.profilesEnabled
        // on: every profile under it, tapped to edit (its icon and colour, separate sign-ins, delete), and + for a new one
        Item("workspaces", "Profiles", "Separate tabs and history", onClick = { vm.updateSettings { copy(profilesEnabled = !on) } },
            trailing = { MiSwitch(on, null) }, below = if (!on) null else { { ProfileList(vm) } })
    },
    SettingsRow("Site Icons", "logo favicon switcher") { SwitchItem("tab", "Site Icons", "Show each tab's site icon in the tab switcher", vm.settings.showTabLogo) { vm.updateSettings { copy(showTabLogo = it) } } },
))

/** Under Profiles: Default and every other profile (tap one for its actions), then + for a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileList(vm: BrowserViewModel) {
    val ui = LocalUi.current
    FlowRow(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<String?>(null) + vm.settings.containers.map { it.id }).forEach { id ->
            val (icon, color) = vm.lookOf(id)
            ProfileBadge(icon, color, on = false, modifier = Modifier.clip(CircleShape).touchable { editProfile(id) }
                .semantics { contentDescription = "Edit ${vm.nameOf(id)} profile" })
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(ui.cs.surfaceContainerHigh).touchable { newProfile() }, contentAlignment = Alignment.Center) {
            Sym("add", 22f, ui.cs.onSurfaceVariant, label = "New profile")
        }
    }
}

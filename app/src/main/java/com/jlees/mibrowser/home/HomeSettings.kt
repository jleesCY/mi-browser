// Settings → Home Page: what the new-tab page shows.
package com.jlees.mibrowser.home

import com.jlees.mibrowser.ui.settings.ChoiceItem
import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.OptionRow
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import com.jlees.mibrowser.data.HomeShortcut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Home Page section of Settings. */
fun homeSection(vm: BrowserViewModel) = SettingsSection("home", "Home Page", "home", "Logo, clock, date, shortcut button", listOf(
        SettingsRow("Logo", "home logo fidget center") {
            ChoiceItem("home", "Logo", null, vm.settings.homeLogoType) { vm.updateSettings { copy(homeLogoType = it) } }
        },
        SettingsRow("Clock", "home clock time") { ChoiceItem("history", "Clock", null, vm.settings.homeClockType) { vm.updateSettings { copy(homeClockType = it) } } },
        SettingsRow("Date", "home date calendar") { ChoiceItem("calendar_today", "Date", null, vm.settings.homeDateType) { vm.updateSettings { copy(homeDateType = it) } } },
        SettingsRow("Shortcut Button", "home shortcut button action qr scan") { ShortcutItem(vm) },
))

@Composable
private fun ShortcutItem(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val s = vm.settings
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SwitchItem("apps", "Shortcut Button", "A button on the home page", s.showHomeShortcuts) { vm.updateSettings { copy(showHomeShortcuts = it) } }
        if (s.showHomeShortcuts) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant.copy(alpha = 0.5f)))
            ExpandItem("bolt", "Shortcut Action", null, s.homeShortcutAction.label) { close ->
                HomeShortcut.entries.forEach { v ->
                    OptionRow(v.label, s.homeShortcutAction == v, leading = { Sym(v.icon, 20f, ui.cs.onSurfaceVariant) }) { vm.updateSettings { copy(homeShortcutAction = v) }; close() }
                }
            }
        }
    }
}


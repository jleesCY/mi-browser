// Settings → Tabs.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.ui.settings.ChoiceItem

import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Tabs section of Settings. */
fun tabsSection(vm: BrowserViewModel) = SettingsSection("tabs", "Tabs", "tab", "Startup, recently closed, tab icons, background refresh", listOf(
        SettingsRow("On Startup", "session restore continue") {
                ChoiceItem("power_settings_new", "On Startup", null, vm.settings.startupTabMode) { vm.updateSettings { copy(startupTabMode = it) } }
            },
        SettingsRow("Keep Recently Closed", "closed tabs history reopen time") {
            ChoiceItem("history", "Keep Recently Closed", "How long closed tabs stay in History", vm.settings.keepClosedTabs) { vm.updateSettings { copy(keepClosedTabs = it) } }
        },
        SettingsRow("Site Icons on Tabs", "logo favicon switcher") { SwitchItem("tab", "Site Icons on Tabs", "Show each site's icon in the tab switcher", vm.settings.showTabLogo) { vm.updateSettings { copy(showTabLogo = it) } } },
        SettingsRow("Background Refresh", "preload tabs startup") {
            SwitchItem("bolt", "Background Refresh", "Load open tabs when mi starts for instant switching (uses more battery)", vm.settings.backgroundRefresh) { vm.updateSettings { copy(backgroundRefresh = it) } }
        },
))

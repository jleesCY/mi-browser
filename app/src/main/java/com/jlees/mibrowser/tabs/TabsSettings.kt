// Settings → Tabs.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.ui.settings.ChoiceItem

import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Tabs section of Settings. */
fun tabsSection(vm: BrowserViewModel) = SettingsSection("tabs", "Tabs", "tab", "Startup, tab icons", listOf(
        SettingsRow("On Startup", "session restore continue") {
                ChoiceItem("power_settings_new", "On Startup", null, vm.settings.startupTabMode) { vm.updateSettings { copy(startupTabMode = it) } }
            },
        SettingsRow("Site Icons on Tabs", "logo favicon switcher") { SwitchItem("tab", "Site Icons on Tabs", "Show each site's icon in the tab switcher", vm.settings.showTabLogo) { vm.updateSettings { copy(showTabLogo = it) } } },
))

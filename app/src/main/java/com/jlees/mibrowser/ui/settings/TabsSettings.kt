// Settings › Tabs: what opens at startup, and site icons in the tab switcher.
package com.jlees.mibrowser.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Tabs section of Settings. */
fun tabsSection(vm: BrowserViewModel) = SettingsSection("tabs", "Tabs", "tab", "How tabs behave", listOf(
    SettingsRow("On Startup", "session restore continue") {
    ChoiceItem("power_settings_new", "On Startup", null, vm.settings.startupTabMode) { vm.updateSettings { copy(startupTabMode = it) } }
    },
    SettingsRow("Site Icons", "logo favicon switcher") { SwitchItem("tab", "Site Icons", "Show each tab's site icon in the tab switcher", vm.settings.showTabLogo) { vm.updateSettings { copy(showTabLogo = it) } } },
))

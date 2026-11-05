// Settings → Tabs.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import com.jlees.mibrowser.ConfirmAction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Tabs section of Settings. */
fun tabsSection(vm: BrowserViewModel) = SettingsSection("tabs", "Tabs", "tab", "Tab icons, background refresh", listOf(
        SettingsRow("Site Icons on Tabs", "logo favicon switcher") { SwitchItem("tab", "Site Icons on Tabs", "Show each site's icon in the tab switcher", vm.settings.showTabLogo) { vm.updateSettings { copy(showTabLogo = it) } } },
        SettingsRow("Background Refresh", "preload tabs startup") {
            SwitchItem("bolt", "Background Refresh", "Load open tabs when mi starts for instant switching (uses more battery)", vm.settings.backgroundRefresh) { on ->
                if (on) vm.confirm = ConfirmData(ConfirmAction.BACKGROUND_REFRESH) else vm.updateSettings { copy(backgroundRefresh = false) }
            }
        },
))

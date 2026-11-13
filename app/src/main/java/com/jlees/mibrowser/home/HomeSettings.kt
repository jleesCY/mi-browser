// Settings → Home Page: what the new-tab page shows.
package com.jlees.mibrowser.home

import com.jlees.mibrowser.ui.settings.ChoiceItem
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Home Page section of Settings. */
fun homeSection(vm: BrowserViewModel) = SettingsSection("home", "Home Page", "home", "Logo, clock, date, favorites", listOf(
        SettingsRow("Logo", "home logo fidget center") {
            ChoiceItem("home", "Logo", null, vm.settings.homeLogoType) { vm.updateSettings { copy(homeLogoType = it) } }
        },
        SettingsRow("Clock", "home clock time") { ChoiceItem("history", "Clock", null, vm.settings.homeClockType) { vm.updateSettings { copy(homeClockType = it) } } },
        SettingsRow("Date", "home date calendar") { ChoiceItem("calendar_today", "Date", null, vm.settings.homeDateType) { vm.updateSettings { copy(homeDateType = it) } } },
        SettingsRow("Favorites", "home favorites speed dial sites grid") {
            SwitchItem("star", "Favorites", "Your favorites at the bottom of the home page", vm.settings.homeFavorites) { vm.updateSettings { copy(homeFavorites = it) } }
        },
))

// Settings › Home Page: what a blank tab shows (logo, clock, date, favorites).
package com.jlees.mibrowser.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/** The Home Page section of Settings. */
fun homePageSection(vm: BrowserViewModel) = SettingsSection("home", "Home Page", "home", "How a blank tab appears", listOf(
    SettingsRow("Logo", "home logo fidget center") {
        ChoiceItem("home", "Logo", null, vm.settings.homeLogoType) { vm.updateSettings { copy(homeLogoType = it) } }
    },
    SettingsRow("Clock", "home clock time") { ChoiceItem("history", "Clock", null, vm.settings.homeClockType) { vm.updateSettings { copy(homeClockType = it) } } },
    SettingsRow("Date", "home date calendar") { ChoiceItem("calendar_today", "Date", null, vm.settings.homeDateType) { vm.updateSettings { copy(homeDateType = it) } } },
    SettingsRow("Favorites", "home favorites speed dial sites grid") {
        SwitchItem("star", "Favorites", "Show your favorites right above the Pill", vm.settings.homeFavorites) { vm.updateSettings { copy(homeFavorites = it) } }
    },
))

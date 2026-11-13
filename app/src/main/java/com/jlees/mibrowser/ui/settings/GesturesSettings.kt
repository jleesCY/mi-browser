// Settings › Gestures, divided by where each gesture is made: the tab switcher's Pill (its swipes pick which page each
// opens, and so which button shows it: a swipe left the right button, a swipe right the left button, a swipe up the
// button left of +; each page has one place, so picking one swaps it with the page that swipe opened; and its long
// press), and web pages (pull to refresh). Gestures elsewhere (the browsing Pill…) get their own heading here.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.tabs.PillSwipe
import com.jlees.mibrowser.tabs.switcherLayout
import com.jlees.mibrowser.tabs.withSwitcherLayout

fun gesturesSection(vm: BrowserViewModel) = SettingsSection("gestures", "Gestures", "vibration", "Tab switcher Pill, pull to refresh", listOf(
    SettingsHeading("Tab Switcher Pill"),
) + listOf(
    Triple(PillSwipe.LEFT, "Swipe Left", "Also the right button"),
    Triple(PillSwipe.RIGHT, "Swipe Right", "Also the left button"),
    Triple(PillSwipe.UP, "Swipe Up", "Also the button left of +"),
).map { (swipe, title, sub) ->
    SettingsRow(title, "tab switcher pill swipe gesture library settings history buttons") {
        val icon = when (swipe) { PillSwipe.LEFT -> "arrow_back"; PillSwipe.RIGHT -> "arrow_forward"; PillSwipe.UP -> "arrow_upward" }
        ChoiceItem(icon, title, sub, vm.settings.switcherLayout.at(swipe)) { p -> vm.updateSettings { withSwitcherLayout(switcherLayout.with(swipe, p)) } }
    }
} + listOf(
    SettingsRow("Long Press", "tab switcher pill hold search tabs find") {
        SwitchItem("search", "Long Press to Search", "Hold the Pill to search your open tabs", vm.settings.longPressTabSearch) { vm.updateSettings { copy(longPressTabSearch = it) } }
    },
    SettingsHeading("Web Pages"),
    SettingsRow("Pull to Refresh", "reload swipe down gesture") {
        SwitchItem("refresh", "Pull to Refresh", "Pull down at the very top of a page to reload it", vm.settings.pullToRefresh) { vm.updateSettings { copy(pullToRefresh = it) } }
    },
))

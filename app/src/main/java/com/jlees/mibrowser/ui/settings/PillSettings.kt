// Settings › Pill: how it hides and shows loading, then what appears above it while you type (quick actions, inline
// answers, shortcuts, recent searches, your search engine's suggestions).
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings

/** Settings › Pill: hiding and the loading bar, then what it shows while you type (actions, answers, searches). */
fun pillSection(vm: BrowserViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("pill", "Pill", "toolbar", "How the browsing Pill behaves", listOf(
        SettingsHeading("Display"),
        SettingsRow("Auto Hide", "hide on scroll pill toolbar scrolling recall minimize") {
            SwitchItem("keyboard_arrow_down", "Auto Hide", "Automatically minimize the Pill while scrolling", s.hidePillOnScroll) { set { copy(hidePillOnScroll = it) } }
        },
        SettingsRow("Loading Bar", "progress") {
            ChoiceItem("hourglass_empty", "Loading Bar", "The style of the page loading progress bar", s.progressBarMode) { set { copy(progressBarMode = it) } }
        },
        SettingsHeading("While Typing"),
        SettingsRow("Quick Actions", "paste and go clipboard copied link") {
            SwitchItem("content_paste", "Quick Actions", "Actions like Paste and Go appear above the Pill", s.quickActions) { set { copy(quickActions = it) } }
        },
        SettingsRow("Inline Answers", "calculator math unit conversion convert time date coin dice answer") {
            SwitchItem("calculate", "Inline Answers", "Answers to math, unit conversions and more appear above the Pill", s.inlineAnswers) { set { copy(inlineAnswers = it) } }
        },
        SettingsRow("Shortcuts", "bang shortcut autocomplete !") {
            SwitchItem("bolt", "Shortcuts", "Available shortcuts appear above the Pill as you type them", s.shortcutSuggestions) { set { copy(shortcutSuggestions = it) } }
        },
        SettingsRow("Recent Searches", "history past searches suggestions") {
            SwitchItem("history", "Recent Searches", "Your past searches appear above the Pill", s.recentSearches) { set { copy(recentSearches = it) } }
        },
        SettingsRow("Search Suggestions", "autocomplete") {
            SwitchItem("search", "Search Suggestions", "Suggestions from your search engine appear above the Pill", s.searchSuggestions) { set { copy(searchSuggestions = it) } }
        },
        SettingsRow("Suggestions in Incognito", "autocomplete private incognito") {
            SwitchItem("incognito", "Suggestions in Incognito", "Suggestions from your search engine appear above the Pill in Incognito mode", s.incognitoSuggestions) { set { copy(incognitoSuggestions = it) } }
        },
    ))
}


// Settings → Library: bookmarks and history.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.ui.settings.ChoiceItem
import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.OptionRow
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import com.jlees.mibrowser.ConfirmAction
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.library.HISTORY_RANGES

/** The Library section of Settings. */
fun librarySection(vm: BrowserViewModel) = SettingsSection("library", "Library", "library_books", "Bookmark icons, history grouping, clearing history", listOf(
        SettingsRow("Site Icons on Bookmarks", "logo favicon") { SwitchItem("bookmarks", "Site Icons on Bookmarks", "Show each site's icon in your bookmarks", vm.settings.showBookmarkIcons) { vm.updateSettings { copy(showBookmarkIcons = it) } } },
        SettingsRow("Group History By", "history time site") {
            ChoiceItem("layers", "Group History By", null, vm.settings.historyGrouping) { vm.updateSettings { copy(historyGrouping = it) } }
        },
        SettingsRow("History Page Size", "history load count") {
            ChoiceItem("list", "History Page Size", "Entries loaded at a time", listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, vm.settings.historyLoadCount.toString()) { vm.updateSettings { copy(historyLoadCount = it.toInt()) } }
        },
        SettingsRow("Clear History", "delete history") { ClearHistoryItem(vm) },
))

@Composable
private fun ClearHistoryItem(vm: BrowserViewModel) {
    ExpandItem("delete", "Clear History", "Choose how far back to delete", null, danger = true) { close ->
        HISTORY_RANGES.forEach { (label, ms) -> OptionRow(label, false) { vm.confirm = ConfirmData(ConfirmAction.CLEAR_HISTORY, ms, label); close() } }
    }
}


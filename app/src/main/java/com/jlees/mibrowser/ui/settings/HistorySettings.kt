// Settings › History: everything on the History page. How history is grouped and cleared, and how long recently
// closed tabs are kept.
package com.jlees.mibrowser.ui.settings

import androidx.compose.runtime.Composable
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.library.HISTORY_RANGES
import com.jlees.mibrowser.library.confirmClearRecentlyClosed

fun historySection(vm: BrowserViewModel) = SettingsSection("history", "History", "history", "How your history is displayed", listOf(
    SettingsHeading("History"),
    SettingsRow("Group History By", "history time site") {
        ChoiceItem("layers", "Group History By", null, vm.settings.historyGrouping) { vm.updateSettings { copy(historyGrouping = it) } }
    },
    SettingsRow("Clear History", "delete history") { ClearHistoryItem(vm) },
    SettingsHeading("Recently Closed"),
    SettingsRow("Keep Recently Closed", "closed tabs history reopen time") {
        ChoiceItem("history", "Keep Recently Closed", "How long closed tabs stay in History", vm.settings.keepClosedTabs) { vm.updateSettings { copy(keepClosedTabs = it) } }
    },
    SettingsRow("Clear Recently Closed Tabs", "delete closed tabs recent reopen") {
        Item("delete", "Clear Recently Closed Tabs", null, danger = true, onClick = { vm.confirmClearRecentlyClosed() })
    },
))

@Composable
private fun ClearHistoryItem(vm: BrowserViewModel) {
    ExpandItem("delete", "Clear History", null, null, danger = true) { close ->
        HISTORY_RANGES.forEach { (label, ms) -> OptionRow(label, false) { vm.confirm = ConfirmData(ConfirmAction.CLEAR_HISTORY, ms, label); close() } }
    }
}


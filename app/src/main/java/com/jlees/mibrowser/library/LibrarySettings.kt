// Settings → Library: bookmarks and history.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.ui.settings.LocalSettingsNav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.jlees.mibrowser.ui.settings.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
fun librarySection(vm: BrowserViewModel) = SettingsSection("library", "Library", "library_books", "Bookmarks, history, import and export", listOf(
        SettingsRow("Site Icons on Bookmarks", "logo favicon") { SwitchItem("bookmarks", "Site Icons on Bookmarks", "Show each site's icon in your bookmarks", vm.settings.showBookmarkIcons) { vm.updateSettings { copy(showBookmarkIcons = it) } } },
        SettingsRow("Import Bookmarks", "import chrome firefox edge safari html bookmarks file") { ImportBookmarksItem(vm) },
        SettingsRow("Export Bookmarks", "export save bookmarks html file backup") { ExportBookmarksItem(vm) },
        SettingsRow("Group History By", "history time site") {
            ChoiceItem("layers", "Group History By", null, vm.settings.historyGrouping) { vm.updateSettings { copy(historyGrouping = it) } }
        },
        SettingsRow("History Page Size", "history load count") {
            ChoiceItem("list", "History Page Size", "Entries loaded at a time", listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, vm.settings.historyLoadCount.toString()) { vm.updateSettings { copy(historyLoadCount = it.toInt()) } }
        },
        SettingsRow("Clear History", "delete history") { ClearHistoryItem(vm) },
))

/** Opens the Import Bookmarks page. */
@Composable
private fun ImportBookmarksItem(vm: BrowserViewModel) {
    val nav = LocalSettingsNav.current
    Item("download", "Import Bookmarks", onClick = { nav.open(bookmarkImportPage(vm)) }, trailing = { Sym("chevron_right", 22f, LocalUi.current.cs.onSurfaceVariant) })
}

/** Saves all bookmarks to a bookmarks file (HTML) that mi and other browsers can import. */
@Composable
private fun ExportBookmarksItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val html = vm.bookmarks.export()
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(html.toByteArray()) } }.exceptionOrNull() }
            if (err == null) vm.showAlert("Bookmarks Exported", "Import the file in mi or any other browser to bring your bookmarks there.")
            else vm.showAlert("Couldn't Export", err.message ?: "The file couldn't be saved.")
        }
    }
    val count = vm.bookmarks.nodes.count { !it.isFolder }
    Item("upload", "Export Bookmarks", if (count == 0) "No bookmarks yet" else if (count == 1) "1 bookmark" else "$count bookmarks", onClick = if (count == 0) null else {
        { saver.launch("mi-bookmarks.html") }
    })
}

@Composable
private fun ClearHistoryItem(vm: BrowserViewModel) {
    ExpandItem("delete", "Clear History", "Choose how far back to delete", null, danger = true) { close ->
        HISTORY_RANGES.forEach { (label, ms) -> OptionRow(label, false) { vm.confirm = ConfirmData(ConfirmAction.CLEAR_HISTORY, ms, label); close() } }
    }
}


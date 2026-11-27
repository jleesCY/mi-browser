// Settings › Backup & Reset: backing up your settings, bookmarks and followed prices to a file and restoring them, resetting
// settings, and wiping everything.
package com.jlees.mibrowser.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.DataTransfer
import com.jlees.mibrowser.data.ImportError
import com.jlees.mibrowser.data.applyImport
import com.jlees.mibrowser.follow.backedUpFollows
import com.jlees.mibrowser.follow.followsBackup
import com.jlees.mibrowser.ui.common.rememberNotifyAsk
import com.jlees.mibrowser.follow.restoreFollows
import com.jlees.mibrowser.library.backedUpBookmarks
import com.jlees.mibrowser.library.bookmarksBackup
import com.jlees.mibrowser.library.restoreBookmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun backupSection(vm: BrowserViewModel) = SettingsSection("data", "Backup & Reset", "save", "Back up, restore, reset", listOf(
    SettingsHeading("Backup"),
    SettingsRow("Back Up", "backup export save settings bookmarks followed prices following") { BackUpItem(vm) },
    SettingsRow("Restore", "import load backup") { RestoreItem(vm) },
    SettingsHeading("Reset"),
    SettingsRow("Reset All Settings", "defaults") {
        Item("restart_alt", "Reset All Settings", "Back to defaults; bookmarks, history and tabs are kept", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.RESET_SETTINGS) })
    },
    SettingsRow("Wipe All Data", "erase delete everything") {
        Item("delete", "Wipe All Data", "Delete bookmarks, history, tabs, followed prices, downloads and settings", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.WIPE_DATA) })
    },
))

/** Saves your settings, bookmarks and followed prices to a file you choose. */
@Composable
private fun BackUpItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, vm.settings, mapOf("follows" to vm.followsBackup(), "bookmarks" to vm.bookmarksBackup())) }.exceptionOrNull() }
            if (err == null) com.jlees.mibrowser.ui.common.Notices.show("Saved backup", done = true)
            else com.jlees.mibrowser.ui.common.Notices.show("Couldn't save the backup")
        }
    }
    Item("upload", "Back Up", "Save your settings, bookmarks and followed prices to a file", onClick = { exporter.launch("mi-browser-${java.time.LocalDate.now()}.mibrowserdata") })
}

/** Brings back settings, bookmarks and followed prices from a backup file, after asking. */
@Composable
private fun RestoreItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val askNotify = rememberNotifyAsk()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DataTransfer.import(ctx, uri).let { it to backedUpBookmarks(it.data) } } }
            r.onSuccess { (backup, marks) ->
                // bookmarks and followed prices you don't already have are added; yours stay as they are
                val follows = backedUpFollows(backup.data)
                val what = listOfNotNull("Bookmarks".takeIf { marks.isNotEmpty() }, "followed prices".takeIf { follows.isNotEmpty() })
                val also = if (what.isEmpty()) "" else " ${what.joinToString(" and ").replaceFirstChar { it.uppercase() }} you don't already have will be added."
                vm.showAlert("Restore Backup?", "Your current settings will be replaced by the backup's.$also", listOf(
                    AlertButton("Cancel", ButtonStyle.CANCEL),
                    AlertButton("Restore") {
                        vm.applyImport(backup.settings)
                        val added = vm.restoreFollows(follows)
                        val marked = vm.restoreBookmarks(marks)
                        // prices followed, notifications on, but not allowed on this phone yet: Android's prompt
                        if (vm.settings.followNotifications && vm.follows.follows.isNotEmpty()) askNotify(false)
                        val more = listOfNotNull(
                            "$marked ${if (marked == 1) "bookmark" else "bookmarks"}".takeIf { marked > 0 },
                            "$added followed ${if (added == 1) "price" else "prices"}".takeIf { added > 0 },
                        ).joinToString(" and ").let { if (it.isEmpty()) "" else ", $it added" }
                        com.jlees.mibrowser.ui.common.Notices.show("Restored backup$more", done = true)
                    },
                ))
            }.onFailure { e ->
                if (e is ImportError) vm.showAlert(e.title, e.message ?: "") else vm.showAlert("Couldn't Restore", "That file isn't a mi. Browser backup.")
            }
        }
    }
    Item("download", "Restore", "Bring back everything from a backup file", onClick = { importer.launch(arrayOf("*/*")) })
}

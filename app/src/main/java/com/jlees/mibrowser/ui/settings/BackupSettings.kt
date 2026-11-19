// Settings › Backup & Reset: saving your settings to a file and restoring them, resetting them, and wiping everything.
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
import com.jlees.mibrowser.follow.rememberNotifyAsk
import com.jlees.mibrowser.follow.restoreFollows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun backupSection(vm: BrowserViewModel) = SettingsSection("data", "Backup & Reset", "save", "Export, import, reset", listOf(
    SettingsRow("Export Settings", "backup save followed prices following") {
        ExportSettingsItem(vm)
    },
    SettingsRow("Import Settings", "restore load") { ImportSettingsItem(vm) },
    SettingsRow("Reset All Settings", "defaults") {
        Item("restart_alt", "Reset All Settings", "Back to defaults; bookmarks, history and tabs are kept", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.RESET_SETTINGS) })
    },
    SettingsRow("Wipe All Data", "erase delete everything") {
        Item("delete", "Wipe All Data", "Delete bookmarks, history, tabs, followed prices, downloads and settings", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.WIPE_DATA) })
    },
))

/** Saves your settings to a file you choose. */
@Composable
private fun ExportSettingsItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, vm.settings, mapOf("follows" to vm.followsBackup())) }.exceptionOrNull() }
            if (err == null) vm.showAlert("Success", "Data exported successfully!") else vm.showAlert("Export Failed", err.message ?: "Unknown error occurred")
        }
    }
    Item("upload", "Export Settings", "Save your settings and followed prices to a file", onClick = { exporter.launch("mi-backup-${java.time.LocalDate.now()}.midata") })
}

/** Restores settings from an exported file, after asking. */
@Composable
private fun ImportSettingsItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val askNotify = rememberNotifyAsk()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DataTransfer.import(ctx, uri) } }
            r.onSuccess { backup ->
                // followed prices you don't already follow are added; yours stay as they are
                val follows = backedUpFollows(backup.data)
                val also = if (follows.isEmpty()) "" else " Followed prices you don't already have will be added."
                vm.showAlert("Restore Settings?", "This will replace your current settings.$also Continue?", listOf(
                    AlertButton("Cancel", ButtonStyle.CANCEL),
                    AlertButton("Restore") {
                        vm.applyImport(backup.settings)
                        val added = vm.restoreFollows(follows)
                        // prices followed, notifications on, but not allowed on this phone yet: Android's prompt
                        if (vm.settings.followNotifications && vm.follows.follows.isNotEmpty()) askNotify(false)
                        val more = if (added > 0) " $added followed ${if (added == 1) "price" else "prices"} added." else ""
                        scope.launch { delay(300); vm.showAlert("Success", "Settings restored successfully.$more") }
                    },
                ))
            }.onFailure { e ->
                if (e is ImportError) vm.showAlert(e.title, e.message ?: "") else vm.showAlert("Import Failed", "Could not parse file. ${e.message ?: ""}")
            }
        }
    }
    Item("download", "Import Settings", "Restore from an exported file", onClick = { importer.launch(arrayOf("*/*")) })
}

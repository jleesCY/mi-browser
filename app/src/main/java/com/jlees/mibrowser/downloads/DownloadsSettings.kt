// Settings → Downloads: who downloads (mi or Android's download manager), where mi saves, and what's asked.
package com.jlees.mibrowser.downloads

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.DownloadHandler
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.settings.ChoiceItem
import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.OptionRow
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

/** The Downloads section of Settings; the location rows show only for mi's downloader (the others don't use them). */
fun downloadsSection(vm: BrowserViewModel) = SettingsSection("downloads", "Downloads", "download", "Downloader, location, confirmations", listOfNotNull(
    SettingsRow("Download With", "downloader download manager android") {
        ChoiceItem("download", "Download With", when (vm.settings.downloadHandler) {
            DownloadHandler.MI -> "mi: pause, resume, and your Downloads list"
            DownloadHandler.ANDROID -> "Android's download manager: saves to Downloads; not listed in mi"
        }, vm.settings.downloadHandler) { vm.updateSettings { copy(downloadHandler = it) } }
    },
    if (vm.settings.downloadHandler == DownloadHandler.MI) SettingsRow("Download Location", "folder save location storage") { LocationItem(vm) } else null,
    if (vm.settings.downloadHandler == DownloadHandler.MI) SettingsRow("Ask Where to Save", "choose folder name each time save as") {
        SwitchItem("save", "Ask Where to Save", "Choose the folder and name for each download", vm.settings.askWhereToSave) { vm.updateSettings { copy(askWhereToSave = it) } }
    } else null,
    SettingsRow("Confirm Downloads", "ask before download prompt") {
        SwitchItem("download", "Confirm Downloads", "Ask before a download starts", vm.settings.confirmDownloads) { vm.updateSettings { copy(confirmDownloads = it) } }
    },
))

/** Where mi saves: the Downloads folder, or one you pick (mi keeps permission to it until you switch back). */
@Composable
private fun LocationItem(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun release(folder: String?) { folder?.let { runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(it), flags) } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }.onFailure { return@rememberLauncherForActivityResult }
        release(vm.settings.downloadFolder?.takeIf { it != uri.toString() })
        vm.updateSettings { copy(downloadFolder = uri.toString()) }
    }
    val folder = vm.settings.downloadFolder
    ExpandItem("folder", "Download Location", "Where mi saves downloads", folderLabel(folder)) { close ->
        OptionRow("Downloads", folder == null) { release(folder); vm.updateSettings { copy(downloadFolder = null) }; close() }
        OptionRow(if (folder != null) "Choose Another Folder…" else "Choose a Folder…", false, leading = { Sym("folder", 20f, ui.cs.onSurfaceVariant) }) {
            close(); runCatching { picker.launch(folder?.let(Uri::parse)) }
        }
    }
}

/** "Downloads", or the picked folder's path ("Documents/Work"). */
private fun folderLabel(folder: String?): String {
    if (folder == null) return "Downloads"
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(folder)) }.getOrNull() ?: return "Chosen folder"
    return id.substringAfter(':').ifEmpty { "Phone storage" }
}

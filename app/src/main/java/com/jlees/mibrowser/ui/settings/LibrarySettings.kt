// Settings › Library: everything on the Library page. Bookmarks (icons, import, export), Downloads (who downloads, where
// mi saves, what's asked) and Following (background checks, notifications on or off).
package com.jlees.mibrowser.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.DownloadHandler
import com.jlees.mibrowser.ui.common.rememberNotifyAsk
import com.jlees.mibrowser.follow.scheduleChecks
import com.jlees.mibrowser.library.bookmarkImportPage
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun librarySection(vm: BrowserViewModel) = SettingsSection("library", "Library", "library_books", "Bookmarks, downloads and followed sites", listOfNotNull(
    SettingsHeading("Bookmarks"),
    SettingsRow("Site Icons", "logo favicon") { SwitchItem("bookmarks", "Site Icons", "Show the site's icon for each bookmark", vm.settings.showBookmarkIcons) { vm.updateSettings { copy(showBookmarkIcons = it) } } },
    SettingsRow("Import Bookmarks", "import chrome firefox edge safari html bookmarks file") { ImportBookmarksItem(vm) },
    SettingsRow("Export Bookmarks", "export save bookmarks html file backup") { ExportBookmarksItem(vm) },
    SettingsHeading("Downloads"),
    // where mi saves, and asking each time, are mi's own downloader's (Android's download manager doesn't use them)
    SettingsRow("Download Manager", "downloader download manager android") {
        ChoiceItem("download", "Download Manager", when (vm.settings.downloadHandler) {
            DownloadHandler.MI -> "Using mi's built-in download manager"
            DownloadHandler.ANDROID -> "Using Android's native download manager"
        }, vm.settings.downloadHandler) { vm.updateSettings { copy(downloadHandler = it) } }
    },
    if (vm.settings.downloadHandler == DownloadHandler.MI) SettingsRow("Location", "download location folder save storage") { LocationItem(vm) } else null,
    if (vm.settings.downloadHandler == DownloadHandler.MI) SettingsRow("Ask Every Time", "ask where to save choose folder name each time save as") {
        SwitchItem("save", "Ask Every Time", "Choose the download location for every download", vm.settings.askWhereToSave) { vm.updateSettings { copy(askWhereToSave = it) } }
    } else null,
    SettingsRow("Confirm Downloads", "ask before download prompt") {
        SwitchItem("download", "Confirm Downloads", "Confirm before a download starts", vm.settings.confirmDownloads) { vm.updateSettings { copy(confirmDownloads = it) } }
    },
    SettingsHeading("Following"),
    SettingsRow("Background Checks", "follow price track check background battery notification") {
        SwitchItem("sell", "Background Checks", "Check followed prices while mi is closed", vm.settings.followChecks) {
            vm.updateSettings { copy(followChecks = it) }
            vm.scheduleChecks()
        }
    },
    if (vm.settings.followChecks) SettingsRow("Wi-Fi Only", "follow price check background wifi mobile data metered") {
        SwitchItem("wifi", "Wi-Fi Only", "Check prices in the background only on Wi-Fi", vm.settings.followWifiOnly) {
            vm.updateSettings { copy(followWifiOnly = it) }
            vm.scheduleChecks()
        }
    } else null,
    SettingsRow("Notifications", "follow price notification alert summary digest") {
        val askNotify = rememberNotifyAsk()
        // turned on: Android's permission too, if mi hasn't got it (else the switch would be on and nothing would come)
        SwitchItem("notifications", "Notifications", "Get notified when a followed price changes", vm.settings.followNotifications) { vm.updateSettings { copy(followNotifications = it) }; if (it) askNotify(true) }
    },
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
    ExpandItem("folder", "Location", "The system folder where downloads are saved", folderLabel(folder)) { close ->
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


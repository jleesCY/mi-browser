// The Downloads page, beside Bookmarks and History: what's downloading (pause, resume, cancel) and what's done
// (open, share, delete). Newest first; search by file name or site.
package com.jlees.mibrowser.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.library.smartDate
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*

@Composable
fun DownloadsView(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()
    // a tab that keeps no traces doesn't see (or list) incognito downloads once incognito has ended (DownloadsStore)
    val items = vm.downloads.items.filter { q.isEmpty() || it.fileName.lowercase().contains(q) || displayHost(it.url).contains(q) }

    fun open(d: DownloadItem) {
        val uri = vm.downloader.shareableUri(d) ?: return
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: ActivityNotFoundException) { vm.showAlert("Can't open", "No app on this phone can open ${d.fileName}.") }
    }
    fun share(d: DownloadItem) {
        val uri = vm.downloader.shareableUri(d) ?: return
        runCatching {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(d.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
        }
    }
    fun delete(d: DownloadItem) = vm.showAlert(
        "Delete ${d.fileName}?", "Delete the file from your phone, or only remove it from this list?",
        listOf(
            AlertButton("Cancel", ButtonStyle.CANCEL),
            AlertButton("Remove from List") { vm.downloads.remove(d.id) },
            AlertButton("Delete", ButtonStyle.DESTRUCTIVE) { vm.downloader.cancel(d.id) },
        ),
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(rowMargin(ui.padding).dp)) {
            if (items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Sym("download", 50f, ui.cs.onSurfaceVariant)
                    Txt(if (q.isEmpty()) "No downloads yet." else "No downloads match.", 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
                }
            }
            items(items, key = { it.id }) { d ->
                // swipe left: cancel a download in progress, or remove a finished one from the list (the file stays)
                SwipeActionRow(null, onDelete = { if (d.active || d.state == DownloadState.PAUSED) vm.downloader.cancel(d.id) else vm.downloads.remove(d.id) },
                    Modifier.animateItem()) {
                DownloadRow(
                    d,
                    onPress = { if (d.state == DownloadState.DONE) open(d) },
                    actions = {
                        when (d.state) {
                            DownloadState.QUEUED, DownloadState.RUNNING -> {
                                RowAction("pause") { vm.downloader.pause(d.id) }
                                RowAction("close") { vm.downloader.cancel(d.id) }
                            }
                            DownloadState.PAUSED -> {
                                RowAction("play_arrow") { vm.downloader.resume(d.id) }
                                RowAction("close") { vm.downloader.cancel(d.id) }
                            }
                            DownloadState.FAILED -> {
                                RowAction("refresh") { vm.downloader.resume(d.id) }
                                RowAction("close") { vm.downloader.cancel(d.id) }
                            }
                            DownloadState.DONE -> {
                                RowAction("share") { share(d) }
                                RowAction("delete") { delete(d) }
                            }
                        }
                    },
                )
                }
            }
        }
        SearchHeaderWithMenu(search, { search = it }, "Search Downloads...") { close ->
            SheetMenuItem("delete", "Clear List") { vm.downloads.clearFinished(); close() }
            SheetMenuItem("folder", "Open Downloads Folder") {
                close()
                runCatching { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, onPress: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = rowHeight(ui.padding, ui.fontScale).dp)
            .clip(libraryRowShape()).background(ui.cs.surfaceContainerHighest)
            .touchable(onClick = onPress).padding(start = 15.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIconTile { Sym(if (d.mime.startsWith("image/")) "image" else "download", 22f, ui.cs.onSurfaceVariant) }
        Column(Modifier.weight(1f)) {
            Txt(d.fileName, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
            Row {
                if (d.state == DownloadState.DONE) Txt(smartDate(d.startedAt), 12f, ui.cs.primary, ui.semibold, Modifier.padding(end = 6.dp), maxLines = 1)
                Txt(downloadStatus(d), 12f, if (d.state == DownloadState.FAILED) ui.cs.error else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(end = 6.dp), maxLines = 1)
                if (d.state == DownloadState.DONE) Txt(displayHost(d.url), 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
            }
            if (d.state == DownloadState.RUNNING || d.state == DownloadState.PAUSED || d.state == DownloadState.QUEUED) {
                val f = d.fraction
                val m = Modifier.padding(top = 6.dp, end = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                if (f == null && d.state != DownloadState.PAUSED) LinearProgressIndicator(m, color = ui.cs.primary, trackColor = ui.cs.surfaceVariant)
                else LinearProgressIndicator({ f ?: 0f }, m, color = if (d.state == DownloadState.PAUSED) ui.cs.outline else ui.cs.primary, trackColor = ui.cs.surfaceVariant)
            }
        }
        actions()
    }
}

@Composable
private fun RowAction(icon: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Sym(icon, 20f, LocalUi.current.cs.onSurfaceVariant) }
}

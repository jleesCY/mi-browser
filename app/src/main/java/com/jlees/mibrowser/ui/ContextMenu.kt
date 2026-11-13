// The long-press menu for links and images on a page.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.adblock.cleanLink
import com.jlees.mibrowser.incognito.incognitoSupported

import com.jlees.mibrowser.Sheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.UiEvent
import com.jlees.mibrowser.ui.common.*

// ---------------------------------------------------------------- context menu
@Composable
fun ContextMenu(vm: BrowserViewModel, onSaveImage: (String) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var last by remember { mutableStateOf(vm.contextMenu) }
    vm.contextMenu?.let { last = it }
    val d = last ?: return
    val target = d.url ?: d.imgUrl
    val close = { vm.contextMenu = null }
    ModalFrame(vm.contextMenu != null, close, dismissOnScrim = true) {
        DialogCard(null, actions = { DialogAction("Cancel", onClick = close) }) {
            Txt(d.text.ifEmpty { if (d.imgUrl != null) "Image" else "Link" }, 18f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 4.dp), maxLines = 2)
            Txt(target ?: "", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp), maxLines = 1)
            @Composable
            fun Action(icon: String, label: String, onPress: () -> Unit) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).touchable(onClick = onPress).heightIn(min = 48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym(icon, 22f, ui.cs.onSurfaceVariant, Modifier.padding(end = 16.dp))
                    Txt(label, 15f, ui.cs.onSurface, ui.semibold)
                }
            }
            val web = target != null && (target.startsWith("http://") || target.startsWith("https://"))
            if (web) Action("open_in_new", "Open in New Tab") { vm.addNewTab(target, incognito = vm.tabs.current?.incognito == true); close() }
            if (web && vm.tabs.current?.incognito != true && incognitoSupported) Action("incognito", "Open in Incognito Tab") { vm.addNewTab(target, incognito = true); close() }
            if (web && d.url != null) Action("tab", "Open in Background Tab") {
                vm.addBackgroundTab(d.url)
                android.widget.Toast.makeText(ctx, "Opened in background", android.widget.Toast.LENGTH_SHORT).show()
                close()
            }
            // a link straight to the home page's favorites (the Favorites folder), named by its text or else its site
            if (web && d.url != null && !vm.bookmarks.isFavorite(d.url)) Action("star", "Add to Favorites") {
                vm.bookmarks.addFavorite(d.text.trim().ifEmpty { displayHost(d.url).removePrefix("www.") }, d.url)
                android.widget.Toast.makeText(ctx, "Added to favorites", android.widget.Toast.LENGTH_SHORT).show()
                close()
            }
            if (target != null) Action("content_copy", if (d.url != null) "Copy Link" else "Copy Image Link") {
                copyText(ctx, vm.cleanLink(target))
                close()
            }
            Action("edit", "Edit in Address Bar") {
                if (target != null) { vm.activeView = Sheet.NONE; vm.ui.tryEmit(UiEvent.FocusInput); vm.inputUrl = target }
                close()
            }
            Action("share", "Share") { target?.let { shareText(ctx, vm.cleanLink(it)) }; close() }
            if (d.imgUrl != null) Action("download", "Save Image") { onSaveImage(d.imgUrl) }
        }
    }
}

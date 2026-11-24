// Library › Extensions: getting extensions and the installed ones, each opening its own panel (ExtensionPanel). Get
// Extensions is a page one level deep, with a back arrow, Back and the search Pill's swipe going up a level. The sheet's search Pill filters the installed extensions, and on Get Extensions searches Firefox Add-ons.
// The installed ones are Library rows (Cards or Table, as the List Style says); swiping one left removes it.
package com.jlees.mibrowser.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.LocalSettingsNav
import com.jlees.mibrowser.ui.settings.PageSearch
import com.jlees.mibrowser.ui.settings.SettingsNav

@Composable
fun ExtensionsView(vm: BrowserViewModel) {
    val ui = LocalUi.current
    // null: the list; "get": Get Extensions (an extension opens in its own panel, ExtensionPanel)
    var page by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ExtensionFlow.libraryTarget) { ExtensionFlow.libraryTarget?.let { if (it == "get") page = it else ExtensionFlow.opened = it; ExtensionFlow.libraryTarget = null } }
    val getSearch = remember { PageSearch("Search Firefox Add-ons") }
    var filter by remember { mutableStateOf("") }
    val nav = remember { SettingsNav(open = {}, close = { page = null }) }
    BackHandler(page != null) { page = null }
    // swiping the search Pill goes up a level first, as in Settings
    val sheetBack = LocalSheetBack.current
    DisposableEffect(page) {
        sheetBack?.inner = if (page == null) null else BackSwipe(drag = {}, release = { up, _ -> if (up) page = null })
        onDispose { sheetBack?.inner = null }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (page != null) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = null }) { Sym("arrow_back", 24f, ui.cs.onSurface, label = "Back") }
                Txt("Get Extensions", 20f, ui.cs.onSurface, ui.bold, Modifier.padding(start = 4.dp), maxLines = 1)
            }
            // the installed list sets its own sides (the List Style: cards in from the edges, or a table edge to edge)
            Box(Modifier.weight(1f).then(if (page == null) Modifier else Modifier.padding(start = 20.dp, end = 20.dp))) {
                CompositionLocalProvider(LocalSettingsNav provides nav) {
                    when (val p = page) {
                        null -> InstalledList(vm, filter) { if (it == "get") page = it else ExtensionFlow.opened = it }
                        else -> GetExtensionsPage(vm, getSearch)
                    }
                }
            }
        }
        if (page == "get") SheetSearchPill(getSearch.query, { getSearch.query = it }, placeholder = getSearch.placeholder)
        else SheetSearchPill(filter, { filter = it }, placeholder = "Search extensions")
    }
}

/**
 * Get Extensions, then the installed extensions (filtered by [filter]), as Library rows that follow the List Style
 * (cards or a table). Swiping one left removes it, after asking (with Undo after).
 */
@Composable
private fun InstalledList(vm: BrowserViewModel, filter: String, open: (String) -> Unit) {
    val ui = LocalUi.current
    val q = filter.trim().lowercase()
    val list = vm.extensions.list.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }.sortedBy { it.name.lowercase() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = listSides(), end = listSides(), top = 16.dp, bottom = SheetPillSpace), verticalArrangement = Arrangement.spacedBy(listGap().dp)) {
        // always a card (also in Table style), in from the edges, so it stands apart from the installed rows
        if (q.isEmpty()) item(key = "get") {
            Row(Modifier.listInset().padding(bottom = if (LocalUi.current.tableLists) 12.dp else 0.dp).fillMaxWidth()
                .heightIn(min = rowHeight(ui.padding, ui.fontScale).dp).clip(libraryRowShape()).background(ui.cs.surfaceContainerHighest)
                .touchable { open("get") }.padding(start = 15.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIconTile { Sym("add", 22f, ui.cs.onSurfaceVariant) }
                Column(Modifier.weight(1f)) {
                    Txt("Get Extensions", 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                    Txt("Firefox Add-ons and the Chrome Web Store", 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                }
                Sym("chevron_right", 22f, ui.cs.onSurfaceVariant)
            }
        }
        when {
            vm.extensions.list.isEmpty() -> item { EmptyState("extension", "No Extensions Yet", top = 40.dp) }
            list.isEmpty() -> item { EmptyState("extension", "No matches", top = 40.dp) }
            else -> items(list, key = { it.id }) { e ->
                SwipeActionRow(null, onDelete = { vm.removeExtension(e.id) }, Modifier.animateItem(), askDelete = { answer ->
                    vm.showAlert("Remove ${e.name}?", "It stops running, and its settings and saved data go with it.", listOf(
                        com.jlees.mibrowser.AlertButton("Cancel", com.jlees.mibrowser.ButtonStyle.CANCEL) { answer(false) },
                        com.jlees.mibrowser.AlertButton("Remove", com.jlees.mibrowser.ButtonStyle.DESTRUCTIVE) { answer(true); vm.removeExtension(e.id) },
                    ), onCancel = { answer(false) })
                }) {
                    ListRowPan {
                        Row(ListRowModifier().touchable { open(e.id) }.padding(start = 15.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RowIconTile { ExtensionIcon(e.id, 26f) }
                            Column(Modifier.weight(1f)) {
                                Txt(e.name, 16f, if (e.enabled) ui.cs.onSurface else ui.cs.onSurfaceVariant, ui.bold, maxLines = 1)
                                Txt(
                                    if (!e.enabled) "Off" else when (e.fit) { Fit.WORKS -> "Works"; Fit.MAYBE -> "Might have problems"; Fit.NO -> "Not recommended" },
                                    12f, if (!e.enabled) ui.cs.onSurfaceVariant else when (e.fit) { Fit.WORKS -> ui.cs.primary; Fit.MAYBE -> fitAmber(); Fit.NO -> ui.cs.error },
                                    ui.semibold, maxLines = 1,
                                )
                            }
                            Sym("chevron_right", 22f, ui.cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A Library row's box: full width, the row height, cards or table as the List Style says. */
@Composable
private fun ListRowModifier() = Modifier.fillMaxWidth().heightIn(min = rowHeight(LocalUi.current.padding, LocalUi.current.fontScale).dp).listRow()

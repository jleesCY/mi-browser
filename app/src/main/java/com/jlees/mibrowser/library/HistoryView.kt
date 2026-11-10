// The History screen: search, grouping by time or site, swipe to delete.
package com.jlees.mibrowser.library

import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.data.HistoryGrouping

import com.jlees.mibrowser.data.Favicons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.logic.*
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.common.SheetSearchPill
import com.jlees.mibrowser.ui.common.SheetPillSpace
import kotlinx.coroutines.launch


val HISTORY_RANGES = listOf("Last Hour" to 3_600_000L, "Last 24 Hours" to 86_400_000L, "Last 7 Days" to 604_800_000L, "Last 4 Weeks" to 2_419_200_000L, "All Time" to -1L)

@Composable
fun HistoryView(vm: BrowserViewModel, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val search = vm.historySearch
    val chunk = vm.settings.historyLoadCount.takeIf { it > 0 } ?: 25
    var visibleCount by remember(search, chunk) { mutableIntStateOf(chunk) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val searching = search.trim().isNotEmpty()
    LaunchedEffect(vm.history.items.size) { focusManager.clearFocus() }

    val filtered = vm.history.items.filter { it.title.lowercase().contains(search.lowercase()) || it.url.lowercase().contains(search.lowercase()) }
    val visible = filtered.take(visibleCount)
    val sections = if (vm.settings.historyGrouping == HistoryGrouping.SITE) groupHistoryBySite(visible) else groupHistoryByDate(visible)

    // all visible sections collapsed but more exist → load another chunk
    LaunchedEffect(collapsed, visibleCount, filtered.size) {
        if (visibleCount < filtered.size && visible.isNotEmpty() && sections.none { (searching || it.title !in collapsed) && it.items.isNotEmpty() }) visibleCount += chunk
    }
    // onEndReached
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: false } }
    LaunchedEffect(nearEnd, filtered.size) { if (nearEnd && visibleCount < filtered.size) visibleCount += chunk }

    var undo by remember { mutableStateOf<Undo?>(null) } // the last swipe-deleted entry, for a few seconds
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = SheetPillSpace)) {
            if (sections.isEmpty()) item { EmptyState("history", if (searching) "No matches" else "No History Yet") }
            sections.forEach { sec ->
                item(key = "h_" + sec.title) {
                    Row(
                        Modifier.fillMaxWidth().touchable { collapsed = if (sec.title in collapsed) collapsed - sec.title else collapsed + sec.title }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.settings.historyGrouping == HistoryGrouping.SITE) NetImage(Favicons.src(sec.title), Modifier.padding(end = 8.dp).size(16.dp).clip(RoundedCornerShape(2.dp)))
                        Txt(sec.title, 14f, ui.cs.onSurfaceVariant, ui.bold, Modifier.weight(1f))
                        Sym(if (sec.title in collapsed && !searching) "keyboard_arrow_down" else "keyboard_arrow_up", 16f, ui.cs.onSurfaceVariant)
                    }
                }
                if (searching || sec.title !in collapsed) {
                    items(sec.items, key = { it.id }) { item ->
                        HistoryRow(item, Modifier.animateItem(), onPress = { vm.navigateTab(item.url, item.title.ifEmpty { displayHost(item.url) }); closeOverlay() }) { vm.history.remove(item.id)?.let { r -> undo = Undo("Removed from history") { vm.history.restore(r) } } }
                    }
                }
            }
        }

        UndoBar(undo, onTimeout = { undo = null }, Modifier.align(Alignment.BottomCenter).padding(bottom = SheetPillSpace))
        SheetSearchPill(search, { vm.historySearch = it }) { close ->
            var open by remember { mutableStateOf(false) }
            SheetMenuItem("delete", "Clear History", trailing = { Sym(if (open) "keyboard_arrow_up" else "keyboard_arrow_down", 16f, ui.cs.onSurface) }) { open = !open }
            // the time ranges unfold under it (and the Pill grows with them)
            androidx.compose.animation.AnimatedVisibility(
                open,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Column(Modifier.padding(top = 5.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(ui.cs.surface))
                    HISTORY_RANGES.forEach { (label, ms) ->
                        Box(Modifier.fillMaxWidth().touchable { close(); vm.confirm = ConfirmData(ConfirmAction.CLEAR_HISTORY, ms, label) }.padding(start = 38.dp, top = 8.dp, bottom = 8.dp)) {
                            Txt(label, 12f * 1.08f, ui.cs.onSurface, ui.semibold)
                        }
                    }
                }
            }
        }

        val density = LocalDensity.current.density
        // derived: recomposes only when crossing the threshold, not on every scroll frame
        val showTop by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset / density > 100 } }
        if (showTop) Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = SheetPillSpace).size(40.dp)
                .background(ui.cs.surfaceContainerHighest, CircleShape).touchable { scope.launch { list.animateScrollToItem(0) } },
            contentAlignment = Alignment.Center,
        ) { Sym("arrow_upward", 24f, ui.cs.onSurface) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, modifier: Modifier, icon: String = "history", onPress: () -> Unit, onDelete: () -> Unit) {
    val ui = LocalUi.current
    val height = rowHeight(ui.padding, ui.fontScale)
    val margin = rowMargin(ui.padding)
    // swipe left to delete (see SwipeActionRow)
    SwipeActionRow(height.dp, onDelete, modifier, slotHeight = (height + margin).dp) {
        Row(
            Modifier.fillMaxWidth().height(height.dp)
                .clip(libraryRowShape()).background(ui.cs.surfaceContainerHighest)
                .touchable(onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIconTile { Sym(icon, 22f, ui.cs.onSurfaceVariant) }
            Column(Modifier.weight(1f)) {
                Txt(item.title.ifEmpty { "Untitled" }, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                Row {
                    Txt(smartDate(item.timestamp), 12f, ui.cs.primary, ui.semibold, Modifier.padding(end = 6.dp), maxLines = 1)
                    Txt(displayHost(item.url), 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                }
            }
            Sym("chevron_right", 20f, ui.cs.onSurfaceVariant)
        }
    }
}

/**
 * History's other section: closed tabs (never incognito ones), newest first, as cards like the tab switcher's: the
 * site and title above, the page as it was below. Tap one to open it again where it was; ✕ forgets it. Searches by
 * title and address from its Pill.
 */
@Composable
fun RecentlyClosedView(vm: BrowserViewModel, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.tabs.pruneExpired() } // the expired ones (and their previews) are deleted, not just hidden
    // past their time (Keep Recently Closed) they drop off, even while mi is open
    val closed = com.jlees.mibrowser.tabs.keptClosed(vm.tabs.recentlyClosed, vm.settings.keepClosedTabs).mapNotNull { t ->
        val url = t.historyStack.getOrNull(t.currentIndex) ?: return@mapNotNull null
        (t to url).takeIf { search.isBlank() || t.title.contains(search, true) || url.contains(search, true) }
    }
    // the cards have the page's shape, as in the tab switcher
    val aspect = com.jlees.mibrowser.tabs.cardAspect(vm)
    Box(Modifier.fillMaxSize()) {
        if (closed.isEmpty()) Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            EmptyState("tab", if (search.isNotBlank()) "No matches" else "No Recently Closed Tabs")
        }
        else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = SheetPillSpace),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(closed.size, key = { closed[it].first.id }) { i ->
                val (t, url) = closed[i]
                Column(Modifier.animateItem().clip(RoundedCornerShape(20.dp)).touchable { vm.reopenClosedTab(t.id); closeOverlay() }) {
                    // the site and title, as above a switcher card; ✕ forgets it
                    Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            val globe = @Composable { Sym("public", 14f, ui.cs.onSurfaceVariant) }
                            if (vm.settings.showTabLogo) NetImage(Favicons.src(url), Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)), fallback = globe) else globe()
                        }
                        Txt(t.title.ifEmpty { displayHost(url) }, 13f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                        Box(Modifier.size(28.dp).clip(CircleShape).touchable { vm.tabs.forgetClosed(t.id) }, contentAlignment = Alignment.Center) {
                            Sym("close", 16f, ui.cs.onSurfaceVariant, label = "Remove")
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(20.dp)).background(ui.cs.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        val host = @Composable { Txt(displayHost(url), 13f, ui.cs.onSurfaceVariant, ui.extrabold, Modifier.padding(8.dp), maxLines = 2) }
                        if (t.previewImage != null) NetImage(t.previewImage, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.FillWidth, alignmentTop = true, fallback = host)
                        else host()
                    }
                }
            }
        }
        // ⋮: clear them all (asks first, as History's Clear does)
        SheetSearchPill(search, { search = it }) { close ->
            SheetMenuItem("delete", "Clear Recently Closed") {
                close()
                vm.showAlert(
                    "Clear recently closed?", "The tabs you closed are removed from this list. Your history stays.",
                    listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Clear", ButtonStyle.DESTRUCTIVE) { vm.tabs.forgetClosed() }),
                )
            }
        }
    }
}

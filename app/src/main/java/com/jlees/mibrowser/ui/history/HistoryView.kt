// Port of legacy/src/components/History/{HistoryView,SwipeableHistoryRow}.tsx
package com.jlees.mibrowser.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.HistoryItem
import com.jlees.mibrowser.logic.*
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.interp
import com.jlees.mibrowser.ui.springDefault
import com.jlees.mibrowser.ui.tabs.SearchHeaderWithMenu
import com.jlees.mibrowser.ui.timing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val HISTORY_RANGES = listOf("Last Hour" to 3_600_000L, "Last 24 Hours" to 86_400_000L, "Last 7 Days" to 604_800_000L, "Last 4 Weeks" to 2_419_200_000L, "All Time" to -1L)

@Composable
fun HistoryView(vm: BrowserViewModel, onFocusSearch: () -> Unit, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val search = vm.historySearch
    val chunk = vm.settings.historyLoadCount.takeIf { it > 0 } ?: 25
    var visibleCount by remember(search, chunk) { mutableIntStateOf(chunk) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val searching = search.trim().isNotEmpty()
    LaunchedEffect(vm.history.size) { focusManager.clearFocus() }

    val filtered = vm.history.filter { it.title.lowercase().contains(search.lowercase()) || it.url.lowercase().contains(search.lowercase()) }
    val visible = filtered.take(visibleCount)
    val sections = if (vm.settings.historyGrouping == "Site") groupHistoryBySite(visible) else groupHistoryByDate(visible)

    // all visible sections collapsed but more exist → load another chunk
    LaunchedEffect(collapsed, visibleCount, filtered.size) {
        if (visibleCount < filtered.size && visible.isNotEmpty() && sections.none { (searching || it.title !in collapsed) && it.items.isNotEmpty() }) visibleCount += chunk
    }
    // onEndReached
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: false } }
    LaunchedEffect(nearEnd, filtered.size) { if (nearEnd && visibleCount < filtered.size) visibleCount += chunk }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 100.dp)) {
            if (sections.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ion("time-outline", 50f, ui.textSec)
                    Txt("No history found.", 16f, ui.textSec, ui.semibold, Modifier.padding(top = 10.dp))
                }
            }
            sections.forEach { sec ->
                item(key = "h_" + sec.title) {
                    Row(
                        Modifier.fillMaxWidth().touchable { collapsed = if (sec.title in collapsed) collapsed - sec.title else collapsed + sec.title }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.settings.historyGrouping == "Site") NetImage(faviconUrl(sec.title), Modifier.padding(end = 8.dp).size(16.dp).clip(RoundedCornerShape(2.dp)))
                        Txt(sec.title, 14f, ui.textSec, ui.bold, Modifier.weight(1f))
                        Ion(if (sec.title in collapsed && !searching) "chevron-down" else "chevron-up", 16f, ui.textSec)
                    }
                }
                if (searching || sec.title !in collapsed) {
                    items(sec.items, key = { it.id }) { item ->
                        HistoryRow(item, Modifier.animateItem(), onPress = { vm.navigateTab(item.url, item.title.ifEmpty { displayHost(item.url) }); closeOverlay() }) { vm.deleteHistoryItem(item.id) }
                    }
                }
            }
        }

        SearchHeaderWithMenu(search, { vm.historySearch = it }, "Search History...", onFocusSearch) { close ->
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().touchable { open = !open }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Ion("trash-outline", 18f, ui.text, Modifier.padding(end = 10.dp))
                Txt("Clear History", 14f, ui.text, ui.semibold, Modifier.weight(1f))
                Ion(if (open) "chevron-up" else "chevron-down", 16f, ui.text)
            }
            if (open) Column(Modifier.animateContentSize().padding(top = 5.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
                HISTORY_RANGES.forEach { (label, ms) ->
                    Box(Modifier.fillMaxWidth().touchable { close(); vm.confirm = ConfirmData("history", ms, label) }.padding(start = 38.dp, top = 8.dp, bottom = 8.dp)) {
                        Txt(label, 12f * 1.08f, ui.text, ui.semibold)
                    }
                }
            }
        }

        val density = LocalDensity.current.density
        val showTop = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset / density > 100
        if (showTop) Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 40.dp).size(40.dp)
                .background(ui.card, CircleShape).touchable { scope.launch { list.animateScrollToItem(0) } },
            contentAlignment = Alignment.Center,
        ) { Ion("arrow-up", 24f, ui.text) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, modifier: Modifier, onPress: () -> Unit, onDelete: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val height = rowHeight("history", ui.padding, ui.fontScale)
    val margin = rowMargin(ui.padding)
    val tx = remember { Animatable(0f) }
    val collapse = remember { Animatable(1f) }
    var deleting by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth().height(((height + margin) * collapse.value).dp).alpha(collapse.value)) {
        val wPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current.density
        val iconScale = interp(tx.value / density, listOf(-100f, -50f, 0f), listOf(1.2f, 0.5f, 0f))
        Box(Modifier.align(Alignment.TopEnd).height(height.dp).padding(end = 20.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, contentAlignment = Alignment.Center) {
            Ion("trash", 20f, Red)
        }
        Row(
            Modifier.fillMaxWidth().height(height.dp)
                .offset { IntOffset(tx.value.roundToInt(), 0) }
                .draggable(
                    rememberDraggableState { d -> if (!deleting) scope.launch { tx.snapTo((tx.value + d).coerceAtMost(0f)) } },
                    Orientation.Horizontal, enabled = !deleting,
                    onDragStopped = {
                        if (tx.value < -wPx * 0.3f) {
                            deleting = true
                            launch { tx.animateTo(-wPx, timing(200)) }
                            collapse.animateTo(0f, timing(200))
                            onDelete()
                        } else tx.animateTo(0f, springDefault())
                    },
                )
                .clip(RoundedCornerShape((ui.radius / 2f).dp)).background(ui.card)
                .touchable(activeOpacity = 0.9f, onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(end = 15.dp).size(32.dp).background(ui.text.copy(alpha = 0.05f), RoundedCornerShape((ui.radius / 3f).dp)), contentAlignment = Alignment.Center) {
                Ion("time-outline", 16f, ui.textSec)
            }
            Column(Modifier.weight(1f)) {
                Txt(item.title.ifEmpty { "Untitled" }, 15f, ui.text, ui.bold, maxLines = 1)
                Row {
                    Txt(smartDate(item.timestamp), 12f, ui.accent, ui.semibold, Modifier.padding(end = 6.dp), maxLines = 1)
                    Txt(displayHost(item.url), 12f, ui.textSec, ui.regular, maxLines = 1)
                }
            }
            Ion("chevron-forward", 20f, ui.textSec)
        }
    }
}

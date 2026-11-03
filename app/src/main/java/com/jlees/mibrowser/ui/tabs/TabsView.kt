// Port of legacy/src/components/Tabs/{TabsView,TabCard,SwipeableTabRow}.tsx
package com.jlees.mibrowser.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconUrl
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.blockTouches
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.interp
import com.jlees.mibrowser.ui.timing
import kotlinx.coroutines.launch

/** Search header with ⋮ overflow menu (SearchHeader in TabsView/HistoryView). */
@Composable
fun SearchHeaderWithMenu(
    value: String, onChange: (String) -> Unit, placeholder: String, onFocus: () -> Unit,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().zIndex(1000f).padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
        SheetSearchField(value, onChange, placeholder, onFocus, trailing = {
            Box(Modifier.touchable { open = !open }.padding(5.dp)) { Ion("ellipsis-vertical", 20f, ui.textSec) }
        })
        if (open) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 50.dp).width(IntrinsicSize.Max).widthIn(min = 150.dp)
                    .shadow(4.dp, RoundedCornerShape(10.dp)).background(ui.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, ui.bg, RoundedCornerShape(10.dp)).padding(5.dp),
            ) { menu { open = false } }
        }
    }
}

@Composable
fun ScrollTopAndFab(sheetH: Float, snapDefault: Float, kbVisible: Boolean, showScrollTop: Boolean, onScrollTop: () -> Unit, fabs: @Composable () -> Unit) {
    val ui = LocalUi.current
    val bottom = interp(sheetH, listOf(0f, snapDefault), listOf(-100f, 20f))
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).offset(y = (-bottom).dp).alpha(if (kbVisible) 0f else 1f),
            horizontalAlignment = Alignment.End,
        ) {
            if (showScrollTop && !kbVisible) {
                Box(
                    Modifier.padding(bottom = 15.dp).size(40.dp).shadow(4.dp, CircleShape).background(ui.card, CircleShape).border(1.dp, ui.bg, CircleShape)
                        .touchable(onClick = onScrollTop),
                    contentAlignment = Alignment.Center,
                ) { Ion("arrow-up", 24f, ui.text) }
            }
            if (!kbVisible) fabs()
        }
    }
}

@Composable
fun TabsView(vm: BrowserViewModel, sheetH: Float, snapDefault: Float, kbVisible: Boolean, onFocusSearch: () -> Unit, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    val search = vm.tabsSearch
    val filtered = vm.tabs.filter { it.title.lowercase().contains(search.lowercase()) || (it.url ?: "").lowercase().contains(search.lowercase()) }
    LaunchedEffect(vm.tabs.size) { focusManager.clearFocus() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val cards = vm.settings.tabViewMode == "cards"
        val cardW = (w - 60) / 2
        val slotW = if (cards) (w - 40) / 2 else w - 40
        val rowH = rowHeight("tab", ui.padding, ui.fontScale)
        val margin = rowMargin(ui.padding)
        val slotH = if (cards) cardW / 0.85f + 16 else rowH + margin

        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 140.dp)) {
            key(vm.settings.tabViewMode) {
                SortableSlots(
                    items = filtered, key = { it.id }, slotW = slotW.dp, slotH = slotH.dp, columns = if (cards) 2 else 1,
                    enabled = true,
                    onReorder = { from, to -> if (search.isEmpty()) vm.reorderTabs(from, to) },
                ) { tab ->
                    val active = tab.id == vm.activeTabId
                    val onPress = { vm.setActiveTab(tab.id) }
                    val onDelete = { focusManager.clearFocus(); vm.deleteTab(tab.id) }
                    val onRename = { vm.renameTabId = tab.id }
                    val logo = vm.settings.showTabLogo
                    if (cards) TabCard(tab, active, cardW.dp, logo, vm.settings.showTabPreview, onPress, onDelete, onRename)
                    else TabRow(tab, active, rowH.dp, logo, onPress, onDelete, onRename)
                }
            }
        }

        SearchHeaderWithMenu(search, { vm.tabsSearch = it }, "Search Tabs...", onFocusSearch) { close ->
            Row(Modifier.fillMaxWidth().touchable { close(); vm.clearAllTabs() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Ion("trash-outline", 18f, ui.text, Modifier.padding(end = 10.dp))
                Txt("Clear all tabs", 14f, ui.text, ui.semibold)
            }
        }

        ScrollTopAndFab(sheetH, snapDefault, kbVisible, scroll.value / density > 100, { scope.launch { scroll.animateScrollTo(0) } }) {
            Box(
                Modifier.size(56.dp).shadow(8.dp, CircleShape).background(ui.accent, CircleShape).touchable { vm.addNewTab(); closeOverlay() },
                contentAlignment = Alignment.Center,
            ) { Ion("add", 32f, Color.White) }
        }
    }
}

/** Shared delete animation: scale 1→0, opacity [0,0.3,1]→[0,1,1] over 150ms, then delete. */
@Composable
fun rememberDeleteAnim(): Pair<Animatable<Float, *>, (() -> Unit) -> Unit> {
    val anim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return anim to { done -> scope.launch { anim.animateTo(0f, timing(150)); done() } }
}

fun Modifier.deleteAnim(v: Float) = graphicsLayer { scaleX = v; scaleY = v; alpha = interp(v, listOf(0f, 0.3f, 1f), listOf(0f, 1f, 1f)) }

@Composable
fun FaviconBox(url: String?, active: Boolean, show: Boolean) {
    val ui = LocalUi.current
    val r = ui.radius
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(if (r == 22) 22.dp else (r / 2f).dp))
            .background(if (active) ui.accent else ui.text.copy(alpha = if (ui.p.isDark) 0.1f else 0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        val globe = @Composable { Ion("globe-outline", 32f, if (active) Color.White else ui.text) }
        if (show && url != null) NetImage(faviconUrl(url), Modifier.size(36.dp).clip(RoundedCornerShape(if (r == 22) 18.dp else (r / 2.5f).dp)), fallback = globe)
        else globe()
    }
}

@Composable
fun SwipeActions(height: Dp, onRename: () -> Unit, onDelete: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.radius.dp)
    Box(Modifier.padding(start = 10.dp).size(70.dp, height).clip(shape).background(ui.textSec).touchable(onClick = onRename), contentAlignment = Alignment.Center) { Ion("pencil", 24f, Color.White) }
    Box(Modifier.padding(start = 10.dp).size(70.dp, height).clip(shape).background(Red).touchable(onClick = onDelete), contentAlignment = Alignment.Center) { Ion("trash", 24f, Color.White) }
}

@Composable
private fun TabRow(tab: TabItem, active: Boolean, height: Dp, showLogo: Boolean, onPress: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val ui = LocalUi.current
    val (anim, del) = rememberDeleteAnim()
    val shape = RoundedCornerShape(ui.radius.dp)
    SwipeRevealRow(160.dp, { SwipeActions(height, onRename) { del(onDelete) } }, Modifier.height(height).deleteAnim(anim.value)) {
        Row(
            Modifier.fillMaxWidth().height(height).clip(shape).background(ui.card)
                .border(2.dp, if (active) ui.accent else Color.Transparent, shape)
                .touchable(activeOpacity = 0.9f, onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaviconBox(tab.url, active, showLogo)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Txt(tab.title.ifEmpty { "New Tab" }, 16f, ui.text, ui.bold, Modifier.padding(bottom = 2.dp), maxLines = 1)
                Txt(displayHost(tab.url).ifEmpty { "Home" }, 12f, ui.textSec, ui.semibold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TabCard(tab: TabItem, active: Boolean, width: Dp, showLogo: Boolean, showPreview: Boolean, onPress: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val ui = LocalUi.current
    val (anim, del) = rememberDeleteAnim()
    val shape = RoundedCornerShape(ui.radius.dp)
    Box(Modifier.padding(vertical = 8.dp).width(width).aspectRatio(0.85f).deleteAnim(anim.value)) {
        Box(
            Modifier.fillMaxSize().shadow(2.dp, shape).clip(shape).background(ui.card)
                .border(2.dp, if (active) ui.accent else Color.Transparent, shape)
                .touchable(activeOpacity = 0.9f, onClick = onPress),
        ) {
            if (showPreview && tab.previewImage != null) {
                NetImage(tab.previewImage, Modifier.fillMaxSize(), alpha = 0.6f)
                Box(Modifier.fillMaxSize().background(if (ui.p.isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)))
            }
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    FaviconBox(tab.url, active, showLogo)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape((ui.radius / 2f).dp)).background(ui.bg).touchable { del(onDelete) },
                        contentAlignment = Alignment.Center,
                    ) { Ion("close", 16f, ui.text) }
                }
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                    Txt(tab.title.ifEmpty { "New Tab" }, 14f, ui.text, ui.bold, Modifier.padding(bottom = 4.dp), maxLines = 2)
                    Txt(displayHost(tab.url).ifEmpty { "Home" }, 12f, ui.textSec, ui.semibold, Modifier.alpha(0.7f), maxLines = 1)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(24.dp).alpha(0.6f)
                    .border(1.dp, ui.textSec, RoundedCornerShape((ui.radius / 2f).dp)).touchable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) { Ion("pencil", 12f, ui.textSec) }
        }
    }
}


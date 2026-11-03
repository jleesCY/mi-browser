// Port of legacy/src/components/Tabs/{TabsView,TabCard,SwipeableTabRow}.tsx
package com.jlees.mibrowser.ui.tabs

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
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
            IconButton(onClick = { open = !open }) { Sym("more_vert", 24f, ui.cs.onSurfaceVariant) }
        })
        if (open) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 60.dp).width(IntrinsicSize.Max).widthIn(min = 200.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainer, RoundedCornerShape(16.dp)).padding(vertical = 8.dp, horizontal = 4.dp),
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
                SmallFloatingActionButton(onClick = onScrollTop, modifier = Modifier.padding(bottom = 16.dp), containerColor = ui.cs.secondaryContainer) {
                    Sym("arrow_upward", 24f, ui.cs.onSecondaryContainer)
                }
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
                Sym("delete", 18f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
                Txt("Clear all tabs", 14f, ui.cs.onSurface, ui.semibold)
            }
        }

        ScrollTopAndFab(sheetH, snapDefault, kbVisible, scroll.value / density > 100, { scope.launch { scroll.animateScrollTo(0) } }) {
            FloatingActionButton(onClick = { vm.addNewTab(); closeOverlay() }, containerColor = ui.cs.primaryContainer) {
                Sym("add", 28f, ui.cs.onPrimaryContainer)
            }
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

/** Favicon in an expressive shape: a cookie for the active tab, a circle otherwise. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FaviconBox(url: String?, active: Boolean, show: Boolean) {
    val ui = LocalUi.current
    val shape = if (active) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Box(
        Modifier.size(44.dp).clip(shape).background(if (active) ui.cs.primaryContainer else ui.cs.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        val globe = @Composable { Sym("public", 26f, if (active) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant) }
        if (show && url != null) NetImage(faviconUrl(url), Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)), fallback = globe)
        else globe()
    }
}

@Composable
fun SwipeActions(height: Dp, onRename: () -> Unit, onDelete: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.radius.dp)
    Box(Modifier.padding(start = 8.dp).size(72.dp, height).clip(shape).background(ui.cs.secondaryContainer).touchable(onClick = onRename), contentAlignment = Alignment.Center) { Sym("edit", 24f, ui.cs.onSecondaryContainer) }
    Box(Modifier.padding(start = 8.dp).size(72.dp, height).clip(shape).background(ui.cs.errorContainer).touchable(onClick = onDelete), contentAlignment = Alignment.Center) { Sym("delete", 24f, ui.cs.onErrorContainer) }
}

@Composable
private fun TabRow(tab: TabItem, active: Boolean, height: Dp, showLogo: Boolean, onPress: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val ui = LocalUi.current
    val (anim, del) = rememberDeleteAnim()
    val shape = RoundedCornerShape(ui.radius.dp)
    SwipeRevealRow(160.dp, { SwipeActions(height, onRename) { del(onDelete) } }, Modifier.height(height).deleteAnim(anim.value)) {
        Row(
            Modifier.fillMaxWidth().height(height).clip(shape).background(ui.cs.surfaceContainerHighest)
                .border(2.dp, if (active) ui.cs.primary else Color.Transparent, shape)
                .touchable(onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaviconBox(tab.url, active, showLogo)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Txt(tab.title.ifEmpty { "New Tab" }, 16f, ui.cs.onSurface, ui.bold, Modifier.padding(bottom = 2.dp), maxLines = 1)
                Txt(displayHost(tab.url).ifEmpty { "Home" }, 12f, ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1)
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
            Modifier.fillMaxSize().shadow(2.dp, shape).clip(shape).background(ui.cs.surfaceContainerHighest)
                .border(2.dp, if (active) ui.cs.primary else Color.Transparent, shape)
                .touchable(onClick = onPress),
        ) {
            if (showPreview && tab.previewImage != null) {
                NetImage(tab.previewImage, Modifier.fillMaxSize(), alpha = 0.6f)
                Box(Modifier.fillMaxSize().background(ui.cs.surfaceContainerHighest.copy(alpha = 0.7f)))
            }
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    FaviconBox(tab.url, active, showLogo)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(ui.cs.surfaceContainerLow).touchable { del(onDelete) },
                        contentAlignment = Alignment.Center,
                    ) { Sym("close", 16f, ui.cs.onSurface) }
                }
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                    Txt(tab.title.ifEmpty { "New Tab" }, 14f, ui.cs.onSurface, ui.bold, Modifier.padding(bottom = 4.dp), maxLines = 2)
                    Txt(displayHost(tab.url).ifEmpty { "Home" }, 12f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.alpha(0.7f), maxLines = 1)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(32.dp)
                    .clip(CircleShape).background(ui.cs.surfaceContainerLow).touchable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) { Sym("edit", 12f, ui.cs.onSurfaceVariant) }
        }
    }
}


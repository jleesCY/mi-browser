// Tab switcher: swipe up on the search pill. A cover-flow pager of tab cards (tap to open, swipe a card up to close,
// long-press to rename) over a bottom bar: [Library] ( + · ⋮ ) [Settings]. Swiping up on the pill drives
// vm.switcher.anim: the live page shrinks into its card under the finger (switcherZoom); tapping a card zooms it back.
package com.jlees.mibrowser.tabs



import com.jlees.mibrowser.web.WebError





import com.jlees.mibrowser.ui.*

import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.data.HomeLogo

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.jlees.mibrowser.ui.SWITCHER_DRAG
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the page sits at progress [p]: its full-size bounds scaled uniformly toward the active card. */
internal fun zoomRect(src: Rect, target: Rect, p: Float): Rect {
    val s = lerp(1f, target.width / src.width, p)
    val left = lerp(src.left, target.left, p)
    val top = lerp(src.top, target.top, p)
    return Rect(left, top, left + src.width * s, top + src.height * s)
}

/** Card position before the pager has measured (first frame of a drag): centered, as wide as the pager's page. */
internal fun Density.estimatedCard(src: Rect): Rect {
    val w = src.width - 112.dp.toPx()
    val left = src.left + 56.dp.toPx()
    val top = src.top + 64.dp.toPx()
    return Rect(left, top, left + w, top + w * src.height / src.width)
}

/** The page's on-screen scale during the zoom, or null when not zooming. */
private fun Density.zoomScale(vm: BrowserViewModel): Float? {
    val src = vm.switcher.pageBounds
    if (!vm.switcherVisible || src == null || src.width <= 0f) return null
    val target = vm.switcher.target ?: estimatedCard(src)
    return (zoomRect(src, target, vm.switcher.anim.value).width / src.width).coerceAtLeast(0.05f)
}

/** graphicsLayer for the page content: shrinks the live page into its switcher card as vm.switcher.anim goes 0 → 1. */
fun switcherZoom(vm: BrowserViewModel, layer: GraphicsLayerScope) = with(layer) {
    val src = vm.switcher.pageBounds
    if (!vm.switcherVisible || src == null || src.width <= 0f) return@with
    val p = vm.switcher.anim.value.coerceIn(0f, 1f) // the spring can overshoot slightly; radii must stay ≥ 0
    val r = zoomRect(src, vm.switcher.target ?: estimatedCard(src), p)
    val sc = (r.width / src.width).coerceAtLeast(0.05f) // a card's rect half off screen is cut short: never divide by ~0
    shadowElevation = 12.dp.toPx() * p
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = sc; scaleY = sc
    translationX = r.left - src.left; translationY = r.top - src.top
    shape = RoundedCornerShape((28.dp.toPx() * p / sc).coerceIn(0f, size.minDimension / 2)) // unscaled px: reads as 28dp on screen
    clip = true
    alpha = if (vm.switcher.settled) 0f else 1f // settled: the switcher (and this tab's card) is on top
}

/**
 * Drawn inside the zooming page's layer (so it stays below the pill): while a tapped card expands, its snapshot
 * covers the page (the WebView may not have drawn yet); then the active card's outline, so it lands like the card.
 */
fun switcherOverlay(vm: BrowserViewModel, scope: ContentDrawScope, color: Color, hasError: (String) -> Boolean = { false }) = with(scope) {
    drawContent()
    val sc = zoomScale(vm) ?: return@with
    // a tab showing mi's error page has no snapshot to cover it with (the page's own error page shows through)
    // scaled to the page's width from the top (stretching it to the page's shape squashed snapshots taken at another height)
    cachedImage(vm.tabs.list.firstOrNull { it.id == vm.switcher.flyingTabId && !hasError(it.id) }?.previewImage)?.let {
        val h = (size.width * it.height / it.width.coerceAtLeast(1)).roundToInt()
        drawImage(it, dstSize = IntSize(size.width.roundToInt(), h))
    }
    val p = vm.switcher.anim.value.coerceIn(0f, 1f)
    val w = 3.dp.toPx() / sc
    drawRoundRect(
        color.copy(alpha = p), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius((28.dp.toPx() * p / sc - w / 2).coerceAtLeast(0f)), style = Stroke(w),
    )
}

@Composable
/** @param errorFor the error page a tab is showing (mi's own, drawn on its card), if its page failed */
fun TabSwitcher(
    vm: BrowserViewModel, navDp: Float, statusDp: Float, errorFor: (String) -> WebError?,
    dragSheet: (Sheet, Float) -> Unit, releaseSheet: (Boolean) -> Unit,
) {
    if (!vm.switcherVisible) return
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val anim = vm.switcher.anim
    // App-switcher order: least recently used on the left, the current tab on the right. Fixed when the switcher
    // opens (so cards don't jump while it closes); tabs added meanwhile (new, undo) are placed by recency.
    val order = remember {
        vm.tabs.list.withIndex().sortedWith(compareBy({ it.value.lastUsed }, { -it.index })).map { it.value.id }
    }
    val tabs = vm.tabs.list.let { all ->
        order.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filter { it.id !in order }.sortedBy { it.lastUsed }
    }
    val pager = rememberPagerState(initialPage = tabs.indexOfFirst { it.id == vm.tabs.activeId }.coerceAtLeast(0)) { tabs.size }
    // Undo brings a closed tab back: once its card is in the pager, scroll to it
    var refocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refocus, tabs.size) {
        val i = tabs.indexOfFirst { it.id == refocus }.takeIf { it >= 0 } ?: return@LaunchedEffect
        withFrameNanos {} // let the pager lay out the list first: its keep-the-same-card adjustment would cancel the scroll
        pager.animateScrollToPage(i)
        refocus = null // after: it's this effect's key, so clearing it earlier would cancel the scroll
    }
    val centered = tabs.getOrNull(pager.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
    val cardRects = remember { mutableStateMapOf<String, Rect>() }
    val pageAspect = cardAspect(vm)

    /** Sets up the zoom back into [open] (or a new tab, null); [close] then animates it, a swipe down drives it. */
    fun beginClose(open: String?) {
        vm.isSubMenuVisible = false
        if (open != null) vm.setActiveTab(open)
        vm.switcher.target = open?.let { cardRects[it] }
        vm.switcher.flyingTabId = open
        vm.switcher.settled = false
    }
    /** Animate back to browsing. [open] zooms that tab back to full screen; null (new tab) zooms from the center. */
    fun close(open: String?) {
        beginClose(open)
        scope.launch {
            anim.animateTo(0f, Motion.switcher)
            vm.switcherVisible = false
            vm.switcher.flyingTabId = null
        }
    }
    // a swipe down (on the card, below it or on the Pill: pullDownToOpen) zooms the centered tab back to full screen with
    // the finger; let go short and it springs back
    var swipingDown by remember { mutableStateOf(false) }
    fun dragDown(p: Float) {
        if (!swipingDown) { swipingDown = true; beginClose(centered?.id) }
        scope.launch { anim.snapTo(1f - p) }
    }
    fun releaseDown(open: Boolean) {
        swipingDown = false
        scope.launch {
            anim.animateTo(if (open) 0f else 1f, Motion.switcher)
            if (open) vm.switcherVisible = false else vm.switcher.settled = true
            vm.switcher.flyingTabId = null
        }
    }
    BackHandler(enabled = vm.activeView == Sheet.NONE) { if (vm.isSubMenuVisible) vm.isSubMenuVisible = false else close(centered?.id) }

    // While zooming the switcher sits behind the page; once settled it moves on top and takes touches.
    // background: the accent colour from darker (top-left) to lighter (bottom-right), so the cards stand out (black in Pure Black)
    val gradient = remember(ui.cs, ui.isDark, ui.pureBlack) { switcherGradient(ui) }
    Box(Modifier.fillMaxSize().zIndex(if (vm.switcher.settled) 150f else -1f).graphicsLayer { alpha = anim.value }.background(gradient).blockTouches()) {
        Column(Modifier.fillMaxSize().pullDownToOpen(SWITCHER_DRAG, ::dragDown, ::releaseDown).padding(top = statusDp.dp + 16.dp, bottom = navDp.dp + 12.dp)) {
            Txt("${tabs.size} ${if (tabs.size == 1) "tab" else "tabs"}", 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.align(Alignment.CenterHorizontally))
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 56.dp),
                pageSpacing = 12.dp,
                key = { tabs.getOrNull(it)?.id ?: it },
            ) { page ->
                val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                TabCardView(
                    tab, tab.id == vm.tabs.activeId, vm.settings.showTabLogo, pageAspect, errorFor(tab.id),
                    hidden = !vm.switcher.settled && tab.id == vm.tabs.activeId, // the zooming page stands in for it
                    logoShown = vm.settings.homeLogoType != HomeLogo.NONE,
                    // cover-flow: neighbours shrink and fade
                    Modifier.graphicsLayer {
                        val off = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val sc = 1f - 0.12f * off; scaleX = sc; scaleY = sc; alpha = 1f - 0.4f * off
                    },
                    onRect = { r ->
                        cardRects[tab.id] = r
                        if (tab.id == vm.tabs.activeId && !vm.switcher.settled && vm.switcher.flyingTabId == null) vm.switcher.target = r
                    },
                    onOpen = { close(tab.id) },
                    onRename = { vm.renameTabId = tab.id },
                    onClosed = { vm.deleteTab(tab.id) },
                )
            }
            val closedTab = vm.tabs.closed
            UndoBar(remember(closedTab) { closedTab?.let { (t, _) -> Undo("Tab closed") { vm.tabs.undoClose(); refocus = t.id } } }, onTimeout = { vm.tabs.dismissUndo() }, showMs = 2500) // shorter: closing tabs comes in quick runs
            // bottom bar: [Library] ( + · ⋮ ) [Settings]
            // (positions are published so the search pill can morph into the centre pill as the switcher opens). Shown
            // The side buttons appear in place as the page shrinks (one button the whole way: no hand-over that could
            // shift it); the centre pill shows once settled, the morphing search Pill standing in for it until then
            // (both at once doubled its icons and shadow).
            val edgeIn = Modifier.graphicsLayer {
                val a = ((anim.value - 0.2f) / 0.45f).coerceIn(0f, 1f)
                alpha = a; scaleX = 0.6f + 0.4f * a; scaleY = 0.6f + 0.4f * a
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SwitcherEdgeButton("library_books", { vm.openView(Sheet.BOOKMARKS) }, label = "Library", modifier = edgeIn)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape, color = ui.cs.surfaceContainerHigh, shadowElevation = 6.dp,
                    modifier = Modifier.onGloballyPositioned { vm.switcher.barPill = it.boundsInWindow() }
                        .graphicsLayer { alpha = if (vm.switcher.settled) 1f else 0f }
                        .switcherPillSwipes(
                            LocalConfiguration.current.screenHeightDp.toFloat(), // History rises with the finger, up the screen
                            onDrag = { dir, p ->
                                vm.isSubMenuVisible = false
                                when (dir) {
                                    PillSwipe.RIGHT -> dragSheet(Sheet.BOOKMARKS, p)
                                    PillSwipe.LEFT -> dragSheet(Sheet.SETTINGS, p)
                                    PillSwipe.UP -> dragSheet(Sheet.HISTORY, p)
                                }
                            },
                            onRelease = { dir, open ->
                                releaseSheet(open)
                            },
                        ),
                ) {
                    SwitcherPillButtons(onNew = { vm.addNewTab(); close(null) }, onHistory = { vm.openView(Sheet.HISTORY) }, onMenu = {
                        centered?.let { vm.setActiveTab(it.id) } // the tab menu acts on the centered tab
                        vm.isSubMenuVisible = !vm.isSubMenuVisible
                    })
                }
                Spacer(Modifier.weight(1f))
                SwitcherEdgeButton("settings", { vm.openView(Sheet.SETTINGS) }, label = "Settings", modifier = edgeIn)
            }
        }
        // tab menu: a tall card above the bottom bar, growing out of the ⋮ button
        if (vm.isSubMenuVisible) Box(Modifier.fillMaxSize().blockTouches { vm.isSubMenuVisible = false })
        androidx.compose.animation.AnimatedVisibility(
            vm.isSubMenuVisible, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(120)) + scaleIn(Motion.fastSpatial(), initialScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            TabMenu(vm, Modifier.fillMaxSize().padding(top = statusDp.dp + 16.dp, bottom = navDp.dp + 88.dp, start = 12.dp, end = 12.dp))
        }
    }
}

// Tab switcher: swipe up on the search pill. A cover-flow pager of tab cards (tap to open, swipe a card up to close,
// long-press to rename) over a bottom bar: [Library] ( + · ⋮ ) [Settings]. Swiping up on the pill drives
// vm.switcherAnim: the live page shrinks into its card under the finger (switcherZoom); tapping a card zooms it back.
package com.jlees.mibrowser.ui

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the page sits at progress [p]: its full-size bounds scaled uniformly toward the active card. */
private fun zoomRect(src: Rect, target: Rect, p: Float): Rect {
    val s = lerp(1f, target.width / src.width, p)
    val left = lerp(src.left, target.left, p)
    val top = lerp(src.top, target.top, p)
    return Rect(left, top, left + src.width * s, top + src.height * s)
}

/** Card position before the pager has measured (first frame of a drag): centered, as wide as the pager's page. */
private fun Density.estimatedCard(src: Rect): Rect {
    val w = src.width - 112.dp.toPx()
    val left = src.left + 56.dp.toPx()
    val top = src.top + 64.dp.toPx()
    return Rect(left, top, left + w, top + w * src.height / src.width)
}

/** The page's on-screen scale during the zoom, or null when not zooming. */
private fun Density.zoomScale(vm: BrowserViewModel): Float? {
    val src = vm.pageBounds
    if (!vm.switcherVisible || src == null || src.width <= 0f) return null
    val target = vm.switcherTarget ?: estimatedCard(src)
    return zoomRect(src, target, vm.switcherAnim.value).width / src.width
}

/** graphicsLayer for the page content: shrinks the live page into its switcher card as vm.switcherAnim goes 0 → 1. */
fun switcherZoom(vm: BrowserViewModel, layer: GraphicsLayerScope) = with(layer) {
    val src = vm.pageBounds
    if (!vm.switcherVisible || src == null || src.width <= 0f) return@with
    val p = vm.switcherAnim.value.coerceIn(0f, 1f) // the spring can overshoot slightly; radii must stay ≥ 0
    val r = zoomRect(src, vm.switcherTarget ?: estimatedCard(src), p)
    val sc = r.width / src.width
    shadowElevation = 12.dp.toPx() * p
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = sc; scaleY = sc
    translationX = r.left - src.left; translationY = r.top - src.top
    shape = RoundedCornerShape(28.dp.toPx() * p / sc) // radius in unscaled px, so it reads as 28dp on screen
    clip = true
    alpha = if (vm.switcherSettled) 0f else 1f // settled: the switcher (and this tab's card) is on top
}

/**
 * Drawn inside the zooming page's layer (so it stays below the pill): while a tapped card expands, its snapshot
 * covers the page (the WebView may not have drawn yet); then the active card's outline, so it lands like the card.
 */
fun switcherOverlay(vm: BrowserViewModel, scope: ContentDrawScope, color: Color) = with(scope) {
    drawContent()
    val sc = zoomScale(vm) ?: return@with
    cachedImage(vm.tabs.firstOrNull { it.id == vm.flyingTabId }?.previewImage)?.let {
        drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
    val p = vm.switcherAnim.value.coerceIn(0f, 1f)
    val w = 3.dp.toPx() / sc
    drawRoundRect(
        color.copy(alpha = p), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius((28.dp.toPx() * p / sc - w / 2).coerceAtLeast(0f)), style = Stroke(w),
    )
}

@Composable
fun TabSwitcher(vm: BrowserViewModel, navDp: Float, statusDp: Float) {
    if (!vm.switcherVisible) return
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val anim = vm.switcherAnim
    // App-switcher order: least recently used on the left, the current tab on the right. Fixed when the switcher
    // opens (so cards don't jump while it closes); tabs added meanwhile (new, undo) are placed by recency.
    val order = remember {
        vm.tabs.withIndex().sortedWith(compareBy({ it.value.lastUsed }, { -it.index })).map { it.value.id }
    }
    val tabs = vm.tabs.let { all ->
        order.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filter { it.id !in order }.sortedBy { it.lastUsed }
    }
    val pager = rememberPagerState(initialPage = tabs.indexOfFirst { it.id == vm.activeTabId }.coerceAtLeast(0)) { tabs.size }
    val centered = tabs.getOrNull(pager.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
    val cardRects = remember { mutableStateMapOf<String, Rect>() }
    val pageAspect = vm.pageBounds?.let { it.width / it.height } ?: LocalConfiguration.current.let { it.screenWidthDp.toFloat() / it.screenHeightDp }

    /** Animate back to browsing. [open] zooms that tab back to full screen; null (new tab) zooms from the center. */
    fun close(open: String?) {
        vm.isSubMenuVisible = false
        if (open != null) vm.setActiveTab(open)
        vm.switcherTarget = open?.let { cardRects[it] }
        vm.flyingTabId = open
        vm.switcherSettled = false
        scope.launch {
            anim.animateTo(0f, Motion.switcher)
            vm.switcherVisible = false
            vm.flyingTabId = null
        }
    }
    BackHandler(enabled = vm.activeView == "none") { if (vm.isSubMenuVisible) vm.isSubMenuVisible = false else close(centered?.id) }

    // While zooming the switcher sits behind the page; once settled it moves on top and takes touches.
    // background: the accent colour from darker (top-left) to lighter (bottom-right), so the cards stand out
    val cs = ui.cs
    val gradient = remember(cs, ui.isDark) {
        val dark = if (ui.isDark) lerp(lerp(cs.surfaceContainerLowest, Color.Black, 0.2f), cs.primary, 0.05f)
            else lerp(cs.primaryContainer, cs.primary, 0.28f) // light: a real mid-tone of the accent, not the pastel
        val light = if (ui.isDark) lerp(cs.surfaceContainer, cs.primaryContainer, 0.38f)
            else lerp(cs.surfaceContainerLowest, cs.primaryContainer, 0.6f)
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(dark, light), start = Offset.Zero, end = Offset.Infinite) // top-left → bottom-right
    }
    Box(Modifier.fillMaxSize().zIndex(if (vm.switcherSettled) 150f else -1f).graphicsLayer { alpha = anim.value }.background(gradient).blockTouches()) {
        Column(Modifier.fillMaxSize().padding(top = statusDp.dp + 16.dp, bottom = navDp.dp + 12.dp)) {
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
                    tab, tab.id == vm.activeTabId, vm.settings.showTabLogo, pageAspect,
                    hidden = !vm.switcherSettled && tab.id == vm.activeTabId, // the zooming page stands in for it
                    logoShown = vm.settings.homeLogoType != "None",
                    // cover-flow: neighbours shrink and fade
                    Modifier.graphicsLayer {
                        val off = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val sc = 1f - 0.12f * off; scaleX = sc; scaleY = sc; alpha = 1f - 0.4f * off
                    },
                    onRect = { r ->
                        cardRects[tab.id] = r
                        if (tab.id == vm.activeTabId && !vm.switcherSettled && vm.flyingTabId == null) vm.switcherTarget = r
                    },
                    onOpen = { close(tab.id) },
                    onRename = { vm.renameTabId = tab.id },
                    onClosed = { vm.deleteTab(tab.id) },
                )
            }
            UndoBar(vm)
            // bottom bar: [Library] ( + · ⋮ ) [Settings]
            // (positions are published so the search pill can morph into the centre pill as the switcher opens)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SwitcherEdgeButton("library_books", { vm.openView("bookmarks") }, Modifier.onGloballyPositioned { vm.switcherBarLeft = it.boundsInWindow() })
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape, color = ui.cs.surfaceContainerHigh, shadowElevation = 6.dp,
                    modifier = Modifier.onGloballyPositioned { vm.switcherBarPill = it.boundsInWindow() },
                ) {
                    SwitcherPillButtons(onNew = { vm.addNewTab(); close(null) }, onMenu = {
                        centered?.let { vm.setActiveTab(it.id) } // the tab menu acts on the centered tab
                        vm.isSubMenuVisible = !vm.isSubMenuVisible
                    })
                }
                Spacer(Modifier.weight(1f))
                SwitcherEdgeButton("settings", { vm.openView("settings") }, Modifier.onGloballyPositioned { vm.switcherBarRight = it.boundsInWindow() })
            }
        }
        // tab menu: a tall card above the bottom bar, growing out of the ⋮ button
        if (vm.isSubMenuVisible) Box(Modifier.fillMaxSize().blockTouches { vm.isSubMenuVisible = false })
        androidx.compose.animation.AnimatedVisibility(
            vm.isSubMenuVisible, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(120)) + scaleIn(Motion.fastSpatial(), initialScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            SubMenu(vm, Modifier.fillMaxSize().padding(top = statusDp.dp + 16.dp, bottom = navDp.dp + 88.dp, start = 12.dp, end = 12.dp))
        }
    }
}

@Composable
private fun TabCardView(
    tab: TabItem, active: Boolean, showLogo: Boolean, aspect: Float, hidden: Boolean, logoShown: Boolean, modifier: Modifier,
    onRect: (Rect) -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onClosed: () -> Unit,
) {
    val ui = LocalUi.current
    var heightPx by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(28.dp)
    // Swipe up to close, with a threshold: until the finger passes 30% of the card's height the card moves at a
    // third of the finger's speed (resistance); past it, a haptic tick and the card catches up to follow 1:1.
    // Released past the threshold (or flung hard) closes the tab; otherwise it springs back.
    var rawY by remember { mutableFloatStateOf(0f) }   // finger travel
    var armed by remember { mutableStateOf(false) }
    val follow = remember { Animatable(0f) }            // 0 = resisted, 1 = follows the finger
    val threshold = { heightPx * 0.3f }
    fun offsetY() = if (rawY > 0) rawY * 0.2f else rawY * lerp(0.35f, 1f, follow.value)
    val drag = Modifier.draggable(
        rememberDraggableState { d ->
            rawY += d
            val nowArmed = !tab.pinned && rawY < -threshold() // a pinned tab gives a little, then springs back
            if (nowArmed != armed) {
                armed = nowArmed
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                scope.launch { follow.animateTo(if (nowArmed) 1f else 0f, Motion.switcher) }
            }
        },
        Orientation.Vertical,
        onDragStopped = { v ->
            val dismiss = !tab.pinned && (armed || (v < -2000f && rawY < -threshold() * 0.4f))
            if (dismiss) {
                follow.snapTo(1f)
                animate(rawY, -heightPx * 1.2f, animationSpec = tween(140, easing = FastOutLinearInEasing)) { value, _ -> rawY = value }
                scope.launch { onClosed() }
            } else {
                armed = false
                scope.launch { follow.animateTo(0f, Motion.switcher) }
                animate(rawY, 0f, v, Motion.switcher) { value, _ -> rawY = value }
            }
        },
    )
    Column(
        modifier.fillMaxSize().padding(vertical = 12.dp)
            .offset { IntOffset(0, offsetY().roundToInt()) }
            .graphicsLayer { alpha = if (hidden) 0f else 1f - (-offsetY() / heightPx).coerceIn(0f, 1f) * 0.8f }
            .then(drag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        TabHeader(tab, active, showLogo, Modifier.padding(bottom = 10.dp))
        Box(
            Modifier.weight(1f)
                .aspectRatio(aspect, matchHeightConstraintsFirst = true) // same shape as the page, so the zoom lands exactly
                .onGloballyPositioned { heightPx = it.size.height.coerceAtLeast(1); if (rawY == 0f) onRect(it.boundsInWindow()) }
                .clip(shape).background(ui.cs.surfaceContainer)
                .then(if (active) Modifier.border(3.dp, ui.cs.primary, shape) else Modifier)
                .combinedClickable(onClick = onOpen, onLongClick = if (tab.incognito) null else onRename),
            contentAlignment = Alignment.Center,
        ) {
            if (tab.incognito || tab.url == null) BlankPageMini(tab.incognito, showLogo = tab.incognito || logoShown)
            else if (tab.previewImage != null && tab.url != null) NetImage(tab.previewImage, Modifier.fillMaxSize(), ContentScale.Crop, alignmentTop = true)
            else Txt(if (tab.url == null) "mi." else displayHost(tab.url), if (tab.url == null) 40f else 16f, ui.cs.onSurfaceVariant, ui.extrabold, align = TextAlign.Center, font = if (tab.url == null) sansFlex(true) else null)
        }
    }
}

/** A blank page (or an incognito tab's mask) drawn exactly as on screen, scaled down to the card. */
@Composable
private fun BlankPageMini(incognito: Boolean, showLogo: Boolean) {
    val windowW = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    // the whole page-sized box scales about its centre by card width / screen width (read at draw time, no extra pass)
    Box(
        Modifier.fillMaxSize().background(com.jlees.mibrowser.ui.home.homeBackground(incognito))
            .graphicsLayer { val sc = size.width / windowW; scaleX = sc; scaleY = sc },
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) { if (incognito) com.jlees.mibrowser.ui.home.IncognitoMark() else com.jlees.mibrowser.ui.home.MiLogo() }
    }
}

/** A card's icon and title row. An incognito tab gives nothing away: no title, site or icon. */
@Composable
private fun TabHeader(tab: TabItem, active: Boolean, showLogo: Boolean, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (active) ui.cs.primaryContainer else ui.cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            val globe = @Composable { Sym(if (tab.incognito) "incognito" else "public", 18f, if (active) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant) }
            if (showLogo && tab.url != null && !tab.incognito) NetImage(Favicons.src(tab.url), Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)), fallback = globe) else globe()
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            // an incognito tab gives nothing away: no title, site, icon or preview
            Txt(if (tab.incognito) "Incognito tab" else tab.title.ifEmpty { "New Tab" }, 15f, ui.cs.onSurface, ui.semibold, maxLines = 1)
            if (!tab.incognito) Txt(displayHost(tab.url).ifEmpty { "Home" }, 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
        }
        if (tab.pinned) Sym("keep", 18f, ui.cs.primary, Modifier.padding(start = 8.dp))
    }
}

/**
 * The active tab's icon and title while its live page zooms into (or out of) its card: drawn above the page, riding
 * just over its top edge, so they're there from the first moment of the gesture. Once settled, the card's own take over.
 */
@Composable
fun ZoomingTabHeader(vm: BrowserViewModel) {
    val tab = vm.currentTab ?: return
    val src = vm.pageBounds
    if (!vm.switcherVisible || vm.switcherSettled || src == null || src.width <= 0f) return
    // the animation value is read only in layout and drawing (not composition), so frames don't recompose this
    TabHeader(
        tab, true, vm.settings.showTabLogo,
        Modifier.layout { m, _ ->
            val r = zoomRect(src, vm.switcherTarget ?: estimatedCard(src), vm.switcherAnim.value.coerceIn(0f, 1f))
            val p = m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(r.width.roundToInt().coerceAtLeast(1)))
            layout(p.width, p.height) { p.place(r.left.roundToInt(), (r.top - 10.dp.toPx() - p.height).roundToInt()) }
        }.graphicsLayer { alpha = vm.switcherAnim.value.coerceIn(0f, 1f) },
    )
}

/** "Tab closed · Undo" for a few seconds after closing a tab. */
@Composable
private fun UndoBar(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val closed = vm.closedTab
    LaunchedEffect(closed) { if (closed != null) { delay(4000); vm.dismissUndo() } }
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(closed != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = CircleShape, color = ui.cs.inverseSurface) {
                Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Tab closed", 14f, ui.cs.inverseOnSurface, ui.semibold)
                    TextButton(onClick = { vm.undoCloseTab() }) { Txt("Undo", 14f, ui.cs.inversePrimary, ui.bold) }
                }
            }
        }
    }
}

/** Bookmarks / Settings buttons on either side of the switcher's bar. */
@Composable
fun SwitcherEdgeButton(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    IconButton(onClick = onClick, modifier.size(52.dp)) { Sym(icon, 26f, ui.cs.onSurface) } // icon only, on the gradient
}

/** The contents of the switcher's centre pill: new tab and the tab menu. */
@Composable
fun SwitcherPillButtons(onNew: () -> Unit, onMenu: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = onNew, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ui.cs.primaryContainer),
        ) { Sym("add", 30f, ui.cs.onPrimaryContainer) }
        IconButton(onClick = onMenu, Modifier.padding(horizontal = 4.dp).size(52.dp)) { Sym("more_vert", 24f, ui.cs.onSurface) }
    }
}

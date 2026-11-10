// A tab's card in the switcher (its title row, its page or snapshot, swipe up to close), and the title riding the zooming page.
package com.jlees.mibrowser.tabs

import androidx.compose.ui.draw.clipToBounds
import com.jlees.mibrowser.ui.ErrorPage
import com.jlees.mibrowser.web.WebError
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.jlees.mibrowser.home.IncognitoMark
import com.jlees.mibrowser.home.MiLogo
import com.jlees.mibrowser.home.homeBackground
import com.jlees.mibrowser.ui.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun TabCardView(
    tab: TabItem, active: Boolean, showLogo: Boolean, aspect: Float, error: WebError?, hidden: Boolean, logoShown: Boolean, modifier: Modifier,
    onRect: (Rect) -> Unit, onOpen: () -> Unit, onRename: () -> Unit, onClosed: () -> Unit,
    /** Can be swiped away: not a pinned tab (nor the Gesture Walkthrough's blank tab, which it returns to). */
    closable: Boolean = !tab.pinned,
) {
    val ui = LocalUi.current
    var heightPx by remember { mutableIntStateOf(1) }
    var cardWidthPx by remember { mutableIntStateOf(0) } // the title row matches it (the card keeps the page's shape, so it can be narrower than its column)
    val scope = rememberCoroutineScope()
    val haptic = LocalHaptics.current
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
            val nowArmed = closable && rawY < -threshold() // a pinned tab gives a little, then springs back
            if (nowArmed != armed) {
                armed = nowArmed
                haptic?.tick()
                scope.launch { follow.animateTo(if (nowArmed) 1f else 0f, Motion.switcher) }
            }
        },
        Orientation.Vertical,
        onDragStopped = { v ->
            val dismiss = closable && (armed || (v < -2000f && rawY < -threshold() * 0.4f))
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
            .then(drag)
            // swipe up to close, for screen readers (a pinned tab can't be closed this way either)
            .semantics { if (closable) customActions = listOf(CustomAccessibilityAction("Close tab") { onClosed(); true }) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        TabHeader(tab, active, showLogo, Modifier.padding(bottom = 10.dp).then(if (cardWidthPx > 0) Modifier.width(with(LocalDensity.current) { cardWidthPx.toDp() }) else Modifier))
        Box(
            Modifier.weight(1f)
                .aspectRatio(aspect, matchHeightConstraintsFirst = true) // same shape as the page, so the zoom lands exactly
                // its whole rect, even partly off screen (bounds in the window would be cut short there)
                .onGloballyPositioned { heightPx = it.size.height.coerceAtLeast(1); cardWidthPx = it.size.width; if (rawY == 0f) onRect(Rect(it.positionInWindow(), it.size.toSize())) }
                .clip(shape).background(ui.cs.surfaceContainer)
                .then(when {
                    active -> Modifier.border(3.dp, ui.cs.primary, shape)
                    ui.pureBlack -> Modifier.border(1.dp, ui.cs.outlineVariant, shape) // a black page on black: show its edge
                    else -> Modifier
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (error != null && tab.url != null) PageMini { ErrorPage(error, tab.url, onRetry = {}, onUpgrade = null, onProceed = {}, onContinueHttp = {}) }
            // a blank page (or an incognito tab's mask), drawn as on screen
            else if (tab.incognito || tab.url == null) PageMini(Modifier.background(homeBackground(tab.incognito))) {
                if (tab.incognito) IncognitoMark() else if (logoShown) MiLogo()
            }
            // scaled to the card's width from the top, as the page is: never stretched, and text keeps its size
            else if (tab.previewImage != null) NetImage(tab.previewImage, Modifier.fillMaxSize(), ContentScale.FillWidth, alignmentTop = true)
            else Txt(displayHost(tab.url), 16f, ui.cs.onSurfaceVariant, ui.extrabold, align = TextAlign.Center)
            // taps go to the card, above whatever it shows (a shrunk error page has buttons of its own)
            Box(Modifier.matchParentSize().combinedClickable(onClick = onOpen, onLongClick = if (tab.incognito) null else onRename))
        }
    }
}

/**
 * Something drawn as it is on the full screen, scaled down to the card: laid out at the window's size, then scaled
 * about its centre by card width / window width (read at draw time, no extra pass).
 */
@Composable
internal fun PageMini(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val d = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val w = window.width.coerceAtLeast(1)
        val h = window.height.coerceAtLeast(1)
        val sc = constraints.maxWidth.toFloat() / w
        Box(
            Modifier.wrapContentSize(unbounded = true).requiredSize(with(d) { w.toDp() }, with(d) { h.toDp() })
                .graphicsLayer { scaleX = sc; scaleY = sc }.then(modifier),
            contentAlignment = Alignment.Center, content = content,
        )
    }
}

/** A card's icon and title row. An incognito tab gives nothing away: no title, site or icon. */
@Composable
internal fun TabHeader(tab: TabItem, active: Boolean, showLogo: Boolean, modifier: Modifier = Modifier) {
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
    val tab = vm.tabs.current ?: return
    val src = vm.switcher.pageBounds
    if (!vm.switcherVisible || vm.switcher.settled || src == null || src.width <= 0f) return
    // the animation value is read only in layout and drawing (not composition), so frames don't recompose this
    TabHeader(
        tab, true, vm.settings.showTabLogo,
        Modifier.layout { m, _ ->
            val r = zoomRect(src, vm.switcher.target ?: estimatedCard(src), vm.switcher.anim.value.coerceIn(0f, 1f))
            val p = m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(r.width.roundToInt().coerceAtLeast(1)))
            layout(p.width, p.height) { p.place(r.left.roundToInt(), (r.top - 10.dp.toPx() - p.height).roundToInt()) }
        }.graphicsLayer { alpha = vm.switcher.anim.value.coerceIn(0f, 1f) },
    )
}

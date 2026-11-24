// An edge-to-edge panel sliding up from the bottom (a followed product, an extension). It opens as tall as its top part;
// scrolling up grows it to the whole screen before its page scrolls, bringing the rest (its settings) into view; at the
// top of its page, dragging down shrinks it, and on past its first size closes it. Its bar of buttons stays pinned at
// the bottom throughout. Behind it the screen dims; a tap there closes it.
package com.jlees.mibrowser.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Shows [item] in an [ExpandingPanel] while it's non-null, sliding in and (still drawn with the last one) out.
 * [panel] gets the item and how far it's slid away (0 in place, 1 below the screen).
 */
@Composable
fun <T : Any> BoxScope.SlidingPanel(item: T?, panel: @Composable BoxScope.(T, Float) -> Unit) {
    val last = remember { arrayOfNulls<Any>(1) }
    if (item != null) last[0] = item
    val slide = remember { Animatable(1f) }
    LaunchedEffect(item != null) { slide.animateTo(if (item != null) 0f else 1f, if (item != null) Motion.enter(300) else Motion.exit(180)) }
    @Suppress("UNCHECKED_CAST")
    val shown = item ?: last[0] as T? ?: return
    if (item == null && slide.value >= 1f) { last[0] = null; return }
    panel(shown, slide.value)
}

/**
 * The panel itself. [key]: what it shows (its size and scroll start over for a new one). [top]: shown at first; [rest]:
 * below it, reached by scrolling up; [bar]: pinned at the bottom (pass it the modifier, for its measured height).
 */
@Composable
fun BoxScope.ExpandingPanel(
    key: Any, hidden: Float, onClose: () -> Unit,
    top: @Composable ColumnScope.() -> Unit,
    rest: @Composable ColumnScope.() -> Unit,
    bar: @Composable (Modifier) -> Unit,
) {
    val ui = LocalUi.current
    val density = LocalDensity.current
    BackHandler(hidden < 1f) { onClose() }

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(300f)) {
        val statusPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val maxPx = constraints.maxHeight - statusPx
        var topPx by remember(key) { mutableFloatStateOf(0f) }
        var barPx by remember { mutableFloatStateOf(0f) }
        val handlePx = with(density) { 28.dp.toPx() }
        val minPx = (handlePx + topPx + barPx).coerceAtMost(maxPx)
        var heightPx by remember(key) { mutableFloatStateOf(0f) }
        if (heightPx == 0f && topPx > 0f && barPx > 0f) heightPx = minPx
        val min by rememberUpdatedState(minPx)
        val max by rememberUpdatedState(maxPx)
        val dismiss by rememberUpdatedState(onClose)
        // let go: closed if pulled well below its first size (or flicked down there), else settled open or at its first
        // size, the way it was moving
        suspend fun settle(velocity: Float) {
            if (heightPx < min && (heightPx < min * 0.75f || velocity > 1500f)) { dismiss(); return }
            val target = when {
                heightPx <= min -> min
                velocity < -300f -> max
                velocity > 300f -> min
                else -> if (heightPx > (min + max) / 2) max else min
            }
            if (target != heightPx) animate(heightPx, target, animationSpec = tween(220)) { v, _ -> heightPx = v }
        }
        val scroll = remember(key) { ScrollState(0) }
        val grow = remember(key) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy < 0 && heightPx < max) { val take = min(-dy, max - heightPx); heightPx += take; return Offset(0f, -take) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    val floor = if (source == NestedScrollSource.UserInput) 0f else min // a fling stops at the first size
                    if (dy > 0 && heightPx > floor) { val take = min(dy, heightPx - floor); heightPx -= take; return Offset(0f, take) }
                    return Offset.Zero
                }
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (heightPx != max) settle(available.y)
                    return available
                }
            }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - hidden }.background(Color.Black.copy(alpha = 0.5f)).blockTouches { onClose() })

        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth()
                .then(if (heightPx > 0f) Modifier.height(with(density) { heightPx.toDp() }) else Modifier.heightIn(max = with(density) { maxPx.toDp() }))
                .graphicsLayer { translationY = hidden * size.height }
                .background(ui.cs.surfaceContainerLow, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp))
                .blockTouches(),
        ) {
            // the handle: drag it (or the page) up for the whole screen, down to close
            val dragScope = rememberCoroutineScope()
            Box(
                Modifier.fillMaxWidth().height(28.dp).draggable(
                    rememberDraggableState { d -> heightPx = (heightPx - d).coerceIn(0f, max) },
                    Orientation.Vertical,
                    onDragStopped = { v -> dragScope.launch { settle(v) } },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(ui.cs.outlineVariant, RoundedCornerShape(2.dp)))
            }
            Column(Modifier.weight(1f, fill = false).nestedScroll(grow).verticalScroll(scroll).padding(horizontal = 20.dp)) {
                Column(Modifier.onSizeChanged { topPx = it.height.toFloat() }, content = top)
                rest()
                Spacer(Modifier.height(24.dp))
            }
            bar(Modifier.onSizeChanged { barPx = it.height.toFloat() })
        }
    }
}

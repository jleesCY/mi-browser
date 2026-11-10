package com.jlees.mibrowser.tabs

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.ui.common.LocalHaptics
import kotlin.math.abs

/** Which way a swipe on the switcher's Pill went: RIGHT opens the Library, LEFT Settings, UP History. */
enum class PillSwipe { RIGHT, LEFT, UP }

/**
 * The switcher Pill's swipes. The panel each one opens follows the finger: [onDrag] reports how far open it is
 * (0..1: a screen width sideways, [upDistanceDp] upward), and [onRelease] says whether to finish opening it (pulled past
 * [OPEN_AT], or flicked toward it) or slide it back. The direction is fixed once the finger passes the touch slop. The + and ⋮ buttons still take taps (a drag cancels their click). Each swipe is also a screen-reader action.
 */
@Composable
fun Modifier.switcherPillSwipes(
    upDistanceDp: Float, onDrag: (PillSwipe, Float) -> Unit, onRelease: (PillSwipe, Boolean) -> Unit,
): Modifier {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val upPx = with(density) { upDistanceDp.dp.toPx() }
    val slop = with(density) { 8.dp.toPx() }
    return pointerInput(widthPx, upPx) {
        var total = Offset.Zero
        var dir: PillSwipe? = null
        var progress = 0f
        val velocity = VelocityTracker()
        detectDragGestures(
            onDragStart = { total = Offset.Zero; dir = null; progress = 0f; velocity.resetTracking() },
            onDragEnd = {
                dir?.let { d ->
                    // a flick toward the panel opens it too, however short
                    val v = velocity.calculateVelocity()
                    val toward = when (d) { PillSwipe.RIGHT -> v.x; PillSwipe.LEFT -> -v.x; PillSwipe.UP -> -v.y }
                    onRelease(d, progress >= OPEN_AT || (toward > FLICK_PX_PER_S && progress > 0.03f))
                }
            },
            onDragCancel = { dir?.let { onRelease(it, false) } },
        ) { change, amount ->
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            total += amount
            if (dir == null && total.getDistance() > slop) dir = when {
                abs(total.y) > abs(total.x) -> if (total.y < 0) PillSwipe.UP else return@detectDragGestures // down: pullDownToOpen
                total.x > 0 -> PillSwipe.RIGHT
                else -> PillSwipe.LEFT
            }
            val d = dir ?: return@detectDragGestures
            val p = when (d) { PillSwipe.RIGHT -> total.x / widthPx; PillSwipe.LEFT -> -total.x / widthPx; PillSwipe.UP -> -total.y / upPx }.coerceIn(0f, 1f)
            if ((p >= OPEN_AT) != (progress >= OPEN_AT)) haptics?.tick() // crossed the point where letting go opens it
            progress = p
            onDrag(d, p)
        }
    }.semantics {
        customActions = listOf(PillSwipe.RIGHT to "Open Library", PillSwipe.LEFT to "Open Settings", PillSwipe.UP to "Open History").map { (d, label) ->
            CustomAccessibilityAction(label) { onDrag(d, 1f); onRelease(d, true); true }
        }
    }
}

/** How far a panel has to be pulled open for letting go to open it. */
const val OPEN_AT = 0.2f
/** A release at least this fast toward the panel opens it, however far it was pulled. */
private const val FLICK_PX_PER_S = 700f

/**
 * A downward swipe anywhere on the tab switcher (the centered card, the space below it, its Pill) zooms the centered
 * tab back to full screen with the finger. Read before the cards and the Pill see the touch, and claimed only once it's
 * clearly a swipe down, so swiping a card up to close it, paging sideways and the Pill's other swipes are unaffected.
 * [onDrag] reports how far (0..1 of [distanceDp]); [onRelease] whether to finish (past [OPEN_AT], or flicked).
 */
@Composable
fun Modifier.pullDownToOpen(distanceDp: Float, onDrag: (Float) -> Unit, onRelease: (Boolean) -> Unit): Modifier {
    val haptics = LocalHaptics.current
    val distancePx = with(LocalDensity.current) { distanceDp.dp.toPx() }
    return pointerInput(distancePx) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val velocity = VelocityTracker()
            var claimed = false
            var progress = 0f
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                val d = c.position - down.position
                if (!claimed) {
                    if (abs(d.x) > slop && abs(d.x) >= abs(d.y)) break // sideways: paging or the Pill's
                    if (d.y < -slop) break                             // up: closing a card, or History
                    if (d.y > slop) claimed = true
                }
                if (claimed) {
                    c.consume()
                    velocity.addPosition(c.uptimeMillis, c.position)
                    val p = ((d.y - slop) / distancePx).coerceIn(0f, 1f)
                    if ((p >= OPEN_AT) != (progress >= OPEN_AT)) haptics?.tick()
                    progress = p
                    if (c.pressed) onDrag(p)
                }
                if (!c.pressed) {
                    if (claimed) onRelease(progress >= OPEN_AT || (velocity.calculateVelocity().y > FLICK_PX_PER_S && progress > 0.03f))
                    break
                }
            }
        }
    }.semantics { customActions = listOf(CustomAccessibilityAction("Open this tab") { onDrag(1f); onRelease(true); true }) }
}

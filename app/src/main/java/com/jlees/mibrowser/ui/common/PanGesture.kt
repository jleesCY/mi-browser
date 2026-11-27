// A drag read in raw screen coordinates, for controls that move with the finger (the Pill, sheets, handles).
package com.jlees.mibrowser.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * PanResponder-like gesture: claims after [slop] dp of movement in any direction (Initial pass so it wins over
 * the text field), reports cumulative dx/dy in dp and release velocity in dp/ms.
 */
fun Modifier.panGesture(
    enabled: Boolean,
    slopDp: Float? = null,
    claimImmediately: Boolean = false,
    onStart: () -> Unit = {},
    onMove: (Float, Float) -> Unit,
    onRelease: (Float, Float, Float, Float) -> Unit,
    onTap: (() -> Unit)? = null,
): Modifier = if (!enabled) this else pointerInput(enabled, claimImmediately) {
    val dens = density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tracker = VelocityTracker()
        // Track the finger in raw screen coordinates. The dragged element (pill, sheet header,
        // logo) moves under the finger, so local positions/deltas would feed its own movement back into the
        // gesture: it lags at half speed and oscillates (jitter).
        val first = currentEvent.motionEvent
        val origin = if (first != null) Offset(first.rawX, first.rawY) else null
        var total = Offset.Zero
        tracker.addPosition(down.uptimeMillis, total)
        var claimed = claimImmediately
        var released = false
        if (claimed) { down.consume(); onStart() }
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                val me = e.motionEvent
                total = if (me != null && origin != null) Offset(me.rawX, me.rawY) - origin else total + c.positionChangeIgnoreConsumed()
                tracker.addPosition(c.uptimeMillis, total)
                if (!c.pressed) {
                    if (claimed) {
                        c.consume()
                        val v = tracker.calculateVelocity()
                        released = true
                        onRelease(total.x / dens, total.y / dens, v.x / dens / 1000f, v.y / dens / 1000f)
                    } else if (onTap != null) onTap()
                    break
                }
                val slop = slopDp?.let { it * dens } ?: viewConfiguration.touchSlop
                if (!claimed && (abs(total.x) > slop || abs(total.y) > slop)) { claimed = true; onStart() }
                if (claimed) { c.consume(); onMove(total.x / dens, total.y / dens) }
            }
        } finally {
            // Interrupted (pointer lost, node removed/restarted, multi-touch): always settle so nothing is left half-way.
            if (claimed && !released) onRelease(total.x / dens, total.y / dens, 0f, 0f)
        }
    }
}

/**
 * Holding a Pill still (anywhere on it, its buttons too) calls [onLongPress] once the long-press time passes,
 * with a haptic; moving first leaves the touch to the Pill's swipes. Seen before the buttons see the touch, and the rest
 * of a long press is kept from them, so the button under the thumb doesn't act as well. The switcher's Pill searches
 * tabs with it, and the Gesture Walkthrough's copy of that Pill teaches it.
 */
@Composable
fun Modifier.pillLongPress(onLongPress: () -> Unit): Modifier {
    val haptics = LocalHaptics.current
    val action by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            // false: let go or moved in time (a tap or a swipe); null: still held, still, when the time ran out
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed || (c.position - down.position).getDistance() > slop) break
                }
                false
            }
            if (ended != null) return@awaitEachGesture
            haptics?.longPress()
            action()
            do { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
        }
    } // each Pill gives its screen-reader action with its swipe actions: one node holds one list
}

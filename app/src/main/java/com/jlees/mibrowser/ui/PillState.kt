// The Pill's animated state (shown/hidden, loading progress, search drawer height) and its drag gesture.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.Sheet
import kotlinx.coroutines.CoroutineStart
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Height of the search pill, dp. */
const val PILL_HEIGHT = 70f
const val SWITCHER_DRAG = 220f // dp of upward drag that takes the page all the way into the tab switcher
const val HIDDEN_TRANSLATE_Y = 110f

fun <T> timing(ms: Int) = tween<T>(ms, easing = FastOutSlowInEasing)

/** Clamped linear interpolation over piecewise ranges. */
fun interp(v: Float, input: List<Float>, output: List<Float>): Float {
    if (v <= input.first()) return output.first()
    if (v >= input.last()) return output.last()
    for (i in 0 until input.size - 1) {
        if (v <= input[i + 1]) {
            val t = (v - input[i]) / (input[i + 1] - input[i])
            return output[i] + (output[i + 1] - output[i]) * t
        }
    }
    return output.last()
}

/** All the pill Animated.Values + the actions that drive them. */
class PillState(private val scope: CoroutineScope, private val vm: BrowserViewModel) {
    val scrollY = Animatable(0f)          // 0..110 hide offset
    val horizontalDrag = Animatable(0f)
    val focusAnim = Animatable(0f)        // isPillFocusedAnim
    val handleAnim = Animatable(0f)       // handleVisibleAnim
    val progress = Animatable(0f)
    val recentHeight = Animatable(0f)
    var isBarHidden by mutableStateOf(false)
    var ignoreNextScroll = false
    var lastScrollY = 0f

    fun showBar() {
        isBarHidden = false
        scope.launch { scrollY.animateTo(0f, timing(100)) }
    }

    fun hideBar() {
        isBarHidden = true
        scope.launch { scrollY.animateTo(HIDDEN_TRANSLATE_Y, timing(100)) }
    }

    fun onScroll(y: Float) {
        if (ignoreNextScroll) { lastScrollY = y; ignoreNextScroll = false; return }
        if (vm.isInputFocused || vm.activeView != Sheet.NONE || y < 0) return
        val dy = y - lastScrollY
        if (abs(dy) > 1) {
            if (dy > 0 && !isBarHidden) hideBar() else if (dy < 0 && isBarHidden) showBar()
        }
        lastScrollY = y
    }

    fun loadStart() {
        ignoreNextScroll = true
        showBar()
        scope.launch { progress.snapTo(0f); progress.animateTo(0.1f, timing(300)) }
    }

    fun loadProgress(p: Float) = scope.launch { progress.animateTo(p, timing(200)) }

    fun loadEnd() = scope.launch {
        progress.animateTo(1f, timing(200))
        kotlinx.coroutines.delay(200)
        progress.snapTo(0f)
    }

    fun tabSwitched() {
        ignoreNextScroll = true
        scope.launch { progress.snapTo(if (vm.tabs.current?.loading == true) 0.2f else 0f) }
    }

    // ---- pill pan responder ----
    // Gesture updates run undispatched (immediately), so a queued move can never land after — and cancel —
    // the release animation, which used to leave the pill stuck half-way.
    private fun now(block: suspend CoroutineScope.() -> Unit) = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)

    private var toSwitcher = false // this drag went up first: it now drives the switcher transition

    fun onPanMove(dx: Float, dy: Float) = now {
        if (!toSwitcher && abs(dx) > abs(dy)) horizontalDrag.snapTo(dx)
        else if (toSwitcher || dy < 0) {
            if (!toSwitcher) { toSwitcher = true; vm.beginSwitcher() }
            vm.switcher.anim.snapTo((-dy / SWITCHER_DRAG).coerceIn(0f, 1f))
        } else scrollY.snapTo(dy.coerceIn(0f, HIDDEN_TRANSLATE_Y))
    }

    fun onPanRelease(dx: Float, dy: Float, vx: Float, vy: Float) {
        scope.launch { horizontalDrag.animateTo(0f, Motion.spatial()) }
        if (toSwitcher) {
            toSwitcher = false
            val open = vy < -0.3f || (vy < 0.3f && vm.switcher.anim.value > 0.15f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                vm.switcher.anim.animateTo(if (open) 1f else 0f, Motion.switcher, initialVelocity = -vy * 1000f / SWITCHER_DRAG)
                if (open) vm.switcher.settled = true else vm.switcherVisible = false
            }
            return
        }
        if (abs(dx) > abs(dy) && (abs(dx) > 32 || (abs(dx) > 12 && abs(vx) > 0.35f))) {
            if (dx > 0) vm.goBack() else vm.goForward()
            showBar()
        } else if (vy > 0.3f || scrollY.value > HIDDEN_TRANSLATE_Y * 0.35f) hideBar() else showBar()
    }

    fun recallMove(dy: Float) = now { scrollY.snapTo((HIDDEN_TRANSLATE_Y + dy).coerceIn(0f, HIDDEN_TRANSLATE_Y)) }
    fun recallRelease(dy: Float, vy: Float) {
        if (dy < -10 || vy < -0.3f) showBar()
        else scope.launch { scrollY.animateTo(HIDDEN_TRANSLATE_Y, Motion.spatial()) }
    }

    // recentSearchesPanResponder
    var recentStart = 0f
    var recentMin = 0f
    var recentMax = 0f
    /** The search drawer is open (past half height): only then are engine suggestions fetched and shown. */
    val recentExpanded get() = recentMax > 0f && recentHeight.value > recentMax / 2

    fun recentGrab() { recentStart = recentHeight.value; scope.launch { recentHeight.stop() } }

    fun recentDrag(dy: Float) {
        val h = max(recentMin, min(recentStart - dy, recentMax))
        now { recentHeight.snapTo(h) }
    }

    fun recentRelease(dy: Float, vy: Float) {
        val target = when {
            dy < -32 || vy < -0.3f -> recentMax
            dy > 32 || vy > 0.3f -> recentMin
            else -> if (recentHeight.value > recentMax / 2) recentMax else recentMin
        }
        animateRecent(target)
    }

    fun animateRecent(target: Float, spring: Boolean = true) = scope.launch {
        if (spring) recentHeight.animateTo(target, Motion.noBounce()) else recentHeight.animateTo(target, timing(200))
    }
}

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
        // Track the finger in raw screen coordinates. The dragged element (pill, sheet header, drawer handle,
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

/** Bottom floating area: the search pill + recent searches drawer. Swipe up opens the tab switcher. */

// The Pill's animated state (shown/hidden, loading progress, search drawer height) and its drag gesture.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.Sheet
import kotlinx.coroutines.CoroutineStart
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
class PillState(private val scope: CoroutineScope, private val vm: BrowserViewModel, private val haptics: Haptics?) {
    val scrollY = Animatable(0f)          // 0..110 hide offset
    val horizontalDrag = Animatable(0f)
    /** Where the recall button is (window px), published by it: hiding morphs the Pill into it. */
    var recallBounds: androidx.compose.ui.geometry.Rect? = null
    val focusAnim = Animatable(0f)        // isPillFocusedAnim
    /** 0 = the Pill shows the address, 1 = it has turned into the find bar. */
    val findAnim = Animatable(0f)
    val handleAnim = Animatable(0f)       // handleVisibleAnim
    val progress = Animatable(0f)
    val recentHeight = Animatable(0f)
    var isBarHidden by mutableStateOf(false)
    var ignoreNextScroll = false
    var lastScrollY = 0f

    /** [springy]: let go after a drag, it bounces into place; otherwise (scrolling) it slides. */
    fun showBar(springy: Boolean = false) {
        isBarHidden = false
        scope.launch { scrollY.animateTo(0f, if (springy) Motion.springBack() else timing(100)) }
    }

    /** Opens the tab switcher without a drag (the screen-reader action on the Pill). */
    fun openSwitcher() {
        vm.beginSwitcher()
        scope.launch {
            vm.switcher.anim.animateTo(1f, Motion.switcher)
            vm.web.tryEmit(com.jlees.mibrowser.WebCmd.CapturePreview(vm.tabs.activeId)) // the card's snapshot, as it settles
            androidx.compose.runtime.withFrameNanos {}
            vm.switcher.settled = true
        }
    }

    fun hideBar() {
        // while finding in page the Pill is the find bar: it stays pinned (scrolling the results never hides it)
        if (vm.find.visible) return
        isBarHidden = true
        scope.launch { scrollY.animateTo(HIDDEN_TRANSLATE_Y, timing(100)) }
    }

    fun onScroll(y: Float) {
        if (ignoreNextScroll) { lastScrollY = y; ignoreNextScroll = false; return }
        if (vm.isInputFocused || vm.activeView != Sheet.NONE || y < 0) return
        if (!vm.settings.hidePillOnScroll) { lastScrollY = y; return }
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

    // a tick when a drag crosses the point where releasing would act (open the switcher, go back/forward)
    private var switcherArmed = false
    private var navArmed = false

    fun onPanMove(dx: Float, dy: Float) = now {
        if (!toSwitcher && abs(dx) > abs(dy)) {
            horizontalDrag.snapTo(dx)
            val canGo = if (dx > 0) vm.canGoBackUi else vm.canGoForwardUi
            val armed = canGo && abs(dx) > 28
            if (armed != navArmed) { navArmed = armed; haptics?.tick() }
        } else if (toSwitcher || dy < 0) {
            if (!toSwitcher) { toSwitcher = true; vm.beginSwitcher() }
            vm.switcher.anim.snapTo((-dy / SWITCHER_DRAG).coerceIn(0f, 1f))
            val armed = vm.switcher.anim.value > 0.12f
            if (armed != switcherArmed) { switcherArmed = armed; haptics?.tick() }
        } else scrollY.snapTo(dy.coerceIn(0f, HIDDEN_TRANSLATE_Y))
    }

    fun onPanRelease(dx: Float, dy: Float, vx: Float, vy: Float) {
        switcherArmed = false; navArmed = false
        scope.launch { horizontalDrag.animateTo(0f, Motion.spatial()) }
        if (toSwitcher) {
            toSwitcher = false
            val open = vy < -0.2f || (vy < 0.2f && vm.switcher.anim.value > 0.12f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                // no bounce: an overshoot would leave the Pill's icons, the edge buttons and the card's title moving after
                // the finger lets go (and none at all if it's already all the way)
                vm.switcher.anim.animateTo(if (open) 1f else 0f, Motion.switcher, initialVelocity = -vy * 1000f / SWITCHER_DRAG)
                if (open) {
                    // the card shows a snapshot once settled: retaken now, so it's exactly the page as it stands
                    vm.web.tryEmit(com.jlees.mibrowser.WebCmd.CapturePreview(vm.tabs.activeId))
                    androidx.compose.runtime.withFrameNanos {}
                    vm.switcher.settled = true
                } else vm.switcherVisible = false
            }
            return
        }
        if (abs(dx) > abs(dy) && (abs(dx) > 28 || (abs(dx) > 8 && abs(vx) > 0.25f))) {
            if (dx > 0) vm.goBack() else vm.goForward()
            showBar(springy = true)
        } else if (vy > 0.2f || scrollY.value > HIDDEN_TRANSLATE_Y * 0.3f) hideBar() else showBar(springy = true)
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

/** Bottom floating area: the search pill + recent searches drawer. Swipe up opens the tab switcher. */

// Port of the pill: legacy/app/index.tsx (animations 535-706, gestures 1167-1350, interpolations 1369-1472, render 1867-2656)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.ui

import kotlinx.coroutines.CoroutineStart
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val SWAP_DISTANCE = 80f
const val HIDDEN_TRANSLATE_Y = 110f

fun <T> timing(ms: Int) = tween<T>(ms, easing = FastOutSlowInEasing)

/** Clamped linear interpolation over piecewise ranges (Animated.interpolate with extrapolate: clamp). */
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
    val animVal = Animatable(0f)          // 0 = search pill, -80 = menu pill
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

    fun snapToSearch() {
        vm.isSearchActive = true
        scope.launch { animVal.animateTo(0f, Motion.fastSpatial()) }
    }

    fun onScroll(y: Float) {
        if (ignoreNextScroll) { lastScrollY = y; ignoreNextScroll = false; return }
        if (vm.isInputFocused || vm.activeView != "none" || y < 0) return
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
        scope.launch { progress.snapTo(if (vm.currentTab?.loading == true) 0.2f else 0f) }
    }

    // ---- pill pan responder ----
    // Gesture updates run undispatched (immediately), so a queued move can never land after — and cancel —
    // the release animation, which used to leave the pill stuck half-way.
    private fun now(block: suspend CoroutineScope.() -> Unit) = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)

    fun onPanMove(dx: Float, dy: Float) = now {
        if (vm.isSearchActive) {
            if (abs(dx) > abs(dy)) horizontalDrag.snapTo(dx)
            else if (dy < 0) animVal.snapTo(max(dy, -SWAP_DISTANCE))
            else scrollY.snapTo(dy.coerceIn(0f, HIDDEN_TRANSLATE_Y))
        } else animVal.snapTo((-SWAP_DISTANCE + dy).coerceIn(-SWAP_DISTANCE, 0f)) // never past the search position
    }

    fun onPanRelease(dx: Float, dy: Float, vx: Float, vy: Float) {
        if (vm.isSearchActive) {
            if (abs(dx) > abs(dy) && (abs(dx) > 32 || (abs(dx) > 12 && abs(vx) > 0.35f))) {
                val goBack = dx > 0
                if (goBack) vm.goBack() else vm.goForward()
                scope.launch { horizontalDrag.animateTo(0f, Motion.spatial()) }
                scope.launch { animVal.animateTo(0f, Motion.spatial()) }
                showBar()
                return
            }
            scope.launch { horizontalDrag.animateTo(0f, Motion.spatial()) }
            if (dy < -16 || vy < -0.3f) {
                vm.isSearchActive = false
                scope.launch { animVal.animateTo(-SWAP_DISTANCE, Motion.fastSpatial()) }
            } else {
                scope.launch { animVal.animateTo(0f, Motion.spatial()) }
                if (vy > 0.3f || scrollY.value > HIDDEN_TRANSLATE_Y * 0.35f) hideBar() else showBar()
            }
        } else {
            if (dy > 16 || vy > 0.3f) {
                vm.isSearchActive = true
                scope.launch { animVal.animateTo(0f, Motion.fastSpatial()) }
            } else scope.launch { animVal.animateTo(-SWAP_DISTANCE, Motion.fastSpatial()) }
        }
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

/** Bottom floating area: submenu + menu/search pills + recent searches drawer (index.tsx 1906-2654). */
@Composable
fun BottomArea(vm: BrowserViewModel, pill: PillState, imeDp: Float, navInsetDp: Float, recent: @Composable () -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusManager = LocalFocusManager.current
    val k = imeDp * pill.focusAnim.value // effectiveKeyboardHeight
    val pillRadius = if (s.cornerRadius >= 20) s.pillHeight / 2f else s.cornerRadius * 2f
    val bottomRadius = interp(k, listOf(0f, 100f), listOf(pillRadius, 0f))
    val elevation = interp(k, listOf(0f, 10f), listOf(10f, 0f))
    val padH = interp(k, listOf(0f, 100f), listOf(10f, 0f))
    val padB = interp(k, listOf(0f, 100f), listOf(max(navInsetDp + 10f, 10f), 0f))
    val focusedAdd = pill.handleAnim.value * 25f
    val totalPillHeight = s.pillHeight + focusedAdd
    val sy = pill.scrollY.value
    val containerScale = interp(sy, listOf(0f, HIDDEN_TRANSLATE_Y), listOf(1f, 0.6f))
    val containerOpacity = interp(sy, listOf(0f, HIDDEN_TRANSLATE_Y * 0.75f, HIDDEN_TRANSLATE_Y), listOf(1f, 0.5f, 0f))
    val av = pill.animVal.value
    val shape = RoundedCornerShape(pillRadius.dp, pillRadius.dp, bottomRadius.dp, bottomRadius.dp)

    Box(Modifier.fillMaxSize()) {
        if (vm.isSubMenuVisible) Box(Modifier.fillMaxSize().panGesture(true, claimImmediately = true, onMove = { _, _ -> }, onRelease = { _, _, _, _ -> vm.isSubMenuVisible = false }))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = padH.dp, end = padH.dp, bottom = (padB + k).dp)
                .alpha(containerOpacity),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .graphicsLayer { translationY = sy * density; scaleX = containerScale; scaleY = containerScale; transformOrigin = TransformOrigin.Center },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // gesture area
                    Box(
                        Modifier.fillMaxWidth().height(totalPillHeight.dp)
                            .panGesture(
                                enabled = !vm.isInputFocused && !pill.isBarHidden,
                                onMove = { dx, dy -> pill.onPanMove(dx, dy) },
                                onRelease = { dx, dy, vx, vy -> pill.onPanRelease(dx, dy, vx, vy) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // MENU PILL
                        val menuScale = interp(av, listOf(-SWAP_DISTANCE, 0f), listOf(1f, 0.9f))
                        val menuOpacity = interp(av, listOf(-SWAP_DISTANCE, -10f, 0f), listOf(1f, 0f, 0f))
                        Box(
                            Modifier.fillMaxWidth().height(s.pillHeight.dp).zIndex(1f)
                                .graphicsLayer { scaleX = menuScale; scaleY = menuScale; alpha = menuOpacity }
                                .shadow(elevation.dp, shape, clip = false).clip(shape).background(ui.cs.surfaceContainerHigh),
                        ) {
                            MenuItems(vm, pill)
                        }
                        // SEARCH PILL
                        val searchOpacity = interp(av, listOf(-SWAP_DISTANCE, -SWAP_DISTANCE / 2, 0f), listOf(0f, 0f, 1f))
                        Box(
                            Modifier.fillMaxWidth().height(totalPillHeight.dp).zIndex(2f)
                                .offset { IntOffset(0, (av * density).roundToInt()) }
                                .alpha(searchOpacity)
                                .shadow(elevation.dp, shape, clip = false).clip(shape).background(ui.cs.surfaceContainerHigh),
                        ) {
                            // pointerEvents none while the menu pill is active: only compose the interactive content when visible
                            if (vm.isSearchActive || av > -SWAP_DISTANCE / 2) SearchPillContent(vm, pill, focusedAdd, focusManager)
                        }
                    }
                    // recent searches drawer
                    Box(Modifier.fillMaxWidth().height(pill.recentHeight.value.dp).zIndex(5f).clip(RoundedCornerShape(0.dp)).background(ui.cs.surfaceContainerLow)) {
                        recent()
                    }
                }
                // position: absolute; bottom: pillHeight + 4; right: 0 — zero-size in layout so it never moves the pill
                if (vm.isSubMenuVisible) SubMenu(
                    vm,
                    Modifier.align(Alignment.BottomEnd).layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(0, 0) { p.place(-p.width, -p.height - (s.pillHeight + 4).dp.roundToPx()) }
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuItems(vm: BrowserViewModel, pill: PillState) {
    val ui = LocalUi.current
    val config = mapOf(
        "tabs" to Triple("tab", "Tabs") { vm.openView("tabs") },
        "bookmarks" to Triple("bookmarks", "Bookmarks") { vm.openView("bookmarks") },
        "history" to Triple("history", "History") { vm.openView("history") },
        "settings" to Triple("settings", "Settings") { vm.openView("settings") },
        "menu" to Triple("menu", "Menu") { vm.isSubMenuVisible = !vm.isSubMenuVisible },
    )
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
        vm.settings.menuBarOrder.forEach { id ->
            val (icon, label, onPress) = config[id] ?: return@forEach
            Column(Modifier.weight(1f).fillMaxHeight().touchable(enabled = !vm.isSearchActive, onClick = onPress), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Sym(icon, 24f, ui.cs.onSurface)
                Txt(label, 11f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPillContent(vm: BrowserViewModel, pill: PillState, focusedAdd: Float, focusManager: androidx.compose.ui.focus.FocusManager) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusRequester = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(vm.inputUrl)) }
    // selectTextOnFocus: the tap that focuses the field places the cursor right after focus; re-select everything
    // once that settles (this also syncs the keyboard's selection, so typing replaces the URL).
    LaunchedEffect(vm.isInputFocused) {
        if (vm.isInputFocused) { kotlinx.coroutines.delay(120); tfv = tfv.copy(selection = TextRange(0, tfv.text.length)) }
    }
    LaunchedEffect(vm.inputUrl) { if (tfv.text != vm.inputUrl) tfv = TextFieldValue(vm.inputUrl, TextRange(vm.inputUrl.length)) }
    LaunchedEffect(Unit) {
        vm.ui.collect { if (it == com.jlees.mibrowser.UiEvent.FocusInput) { focusRequester.requestFocus() } }
    }
    val imeVisible = WindowInsets.isImeVisible
    val hd = pill.horizontalDrag.value
    val backOpacity = interp(hd, listOf(0f, 32f), listOf(0f, 1f))
    val fwdOpacity = interp(hd, listOf(-32f, 0f), listOf(1f, 0f))
    val contentOpacity = interp(hd, listOf(-32f, 0f, 32f), listOf(0f, 1f, 0f))
    val tab = vm.currentTab

    Box(Modifier.fillMaxSize()) {
        if (vm.isInputFocused && imeVisible) {
            Box(
                Modifier.fillMaxWidth().height(24.dp).zIndex(10f).panGesture(
                    true, claimImmediately = true,
                    onStart = { pill.recentGrab() },
                    onMove = { _, dy -> pill.recentDrag(dy) },
                    onRelease = { _, dy, _, vy -> pill.recentRelease(dy, vy) },
                ),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(40.dp, 4.dp).alpha(0.5f).background(ui.cs.onSurfaceVariant, RoundedCornerShape(2.dp))) }
        }
        Row(
            Modifier.fillMaxSize().padding(top = focusedAdd.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).height((s.pillHeight * 0.7f).dp).alpha(contentOpacity)
                    .clip(RoundedCornerShape((s.cornerRadius * 1.5f).dp)).background(ui.cs.surfaceContainerHighest),
            ) {
                if (s.progressBarMode != "none" && vm.isLoading) {
                    val p = pill.progress.value.coerceIn(0f, 1f)
                    if (s.progressBarMode == "center") Box(Modifier.fillMaxSize().graphicsLayer { scaleX = p }.alpha(0.2f).background(ui.cs.primary))
                    else LinearWavyProgressIndicator(
                        progress = { p }, color = ui.cs.primary, trackColor = ui.cs.primary.copy(alpha = 0.15f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
                    )
                }
                Row(Modifier.fillMaxSize().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = tfv,
                        onValueChange = { tfv = it; vm.inputUrl = it.text },
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp).focusRequester(focusRequester)
                            .onFocusChanged { st ->
                                if (st.isFocused && !vm.isInputFocused) {
                                    vm.isInputFocused = true
                                    val full = vm.activeUrl ?: ""
                                    vm.inputUrl = full
                                    tfv = TextFieldValue(full, TextRange(0, full.length)) // selectTextOnFocus
                                } else if (!st.isFocused && vm.isInputFocused) {
                                    vm.isInputFocused = false
                                    vm.inputUrl = displayHost(vm.activeUrl)
                                }
                            },
                        singleLine = true,
                        textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = FontWeight.W600, fontSize = (16 * s.fontScale).sp),
                        cursorBrush = SolidColor(ui.cs.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); vm.submitInput(tfv.text) }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (tfv.text.isEmpty()) Txt("Search", 16f, ui.cs.onSurfaceVariant, FontWeight.W600)
                                inner()
                            }
                        },
                    )
                    Row(Modifier.padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.isInputFocused && tfv.text.isNotEmpty()) {
                            Box(Modifier.padding(end = 8.dp).touchable { vm.inputUrl = ""; tfv = TextFieldValue(""); focusRequester.requestFocus() }) { Sym("cancel", 20f, ui.cs.onSurfaceVariant) }
                        }
                        Box(Modifier.padding(end = 8.dp).touchable { vm.updateSettings { copy(forceSearchMode = !forceSearchMode) } }) {
                            EngineIcon(s.searchEngineIndex, 22f, active = s.forceSearchMode)
                        }
                        if (vm.isInputFocused) {
                            Box(Modifier.touchable { focusManager.clearFocus(); vm.submitInput(tfv.text) }) { Sym("search", 22f, ui.cs.primary) }
                        } else {
                            val hasUrl = vm.activeUrl != null
                            Box(Modifier.alpha(if (hasUrl) 1f else 0.3f).touchable(enabled = hasUrl) {
                                if (vm.isLoading) vm.web.tryEmit(WebCmd.Stop(vm.activeTabId)) else vm.web.tryEmit(WebCmd.Reload(vm.activeTabId))
                            }) { Sym(if (vm.isLoading) "close" else "refresh", 22f, ui.cs.onSurface) }
                        }
                    }
                }
            }
        }
        // nav arrows (pointerEvents none)
        if (tab != null && vm.canGoBackUi) Box(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = focusedAdd.dp).alpha(backOpacity)) { Sym("arrow_back", 28f, ui.cs.onSurface) }
        if (tab != null && vm.canGoForwardUi) Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, top = focusedAdd.dp).alpha(fwdOpacity)) { Sym("arrow_forward", 28f, ui.cs.onSurface) }
    }
}


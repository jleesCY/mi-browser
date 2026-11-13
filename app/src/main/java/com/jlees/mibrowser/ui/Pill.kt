// The Pill: the bottom search/address bar with back, forward and the tab menu, and the area around it.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.tabs.switcherLayout
import androidx.compose.ui.semantics.CustomAccessibilityAction

import androidx.compose.ui.semantics.customActions

import androidx.compose.ui.semantics.semantics

import com.jlees.mibrowser.find.FindPillContent

import com.jlees.mibrowser.search.BangPillTransformation

import com.jlees.mibrowser.incognito.incognitoSupported
import com.jlees.mibrowser.incognito.toggleIncognito
import com.jlees.mibrowser.web.connectionNotSecure
import com.jlees.mibrowser.web.explainNotSecure

import com.jlees.mibrowser.tabs.SwitcherPillButtons

import com.jlees.mibrowser.data.LoadingBar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import com.jlees.mibrowser.search.typedBangLength
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BottomArea(vm: BrowserViewModel, pill: PillState, imeDp: () -> Float, navInsetDp: Float, suggestionsMaxDp: Float) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusManager = LocalFocusManager.current
    // All animated values (keyboard focus, hide/show) are read inside layout/draw lambdas, so animating them never
    // recomposes the pill, its text field or its suggestions.
    // effective keyboard height: the Pill rides above the keyboard while its field or the find field has focus
    val k = { imeDp() * max(pill.focusAnim.value, pill.findAnim.value) }
    // Find in page turns the Pill into the find bar: the address content slides left and fades as the find field,
    // count and buttons slide in from the right, on an expressive spring (reverses on close).
    LaunchedEffect(vm.find.visible) {
        if (vm.find.visible) pill.showBar()
        pill.findAnim.animateTo(if (vm.find.visible) 1f else 0f, Motion.spatial())
    }
    val findShown = vm.find.visible || pill.findAnim.value > 0.01f
    val pillRadius = if (s.cornerRadius >= 20) PILL_HEIGHT / 2f else s.cornerRadius * 2f
    val padH = { interp(k(), listOf(0f, 100f), listOf(10f, 0f)) }
    val padB = { interp(k(), listOf(0f, 100f), listOf(max(navInsetDp + 10f, 10f), 0f)) + k() }
    // Opening the tab switcher morphs the search pill into the switcher's centre pill: its sides draw in to that
    // pill's size and position, the search content fades out early, and + and ⋮ fade in late (the edge buttons appear in
    // the switcher itself). Driven by the same progress as the page zoom, so it follows the finger and reverses on close.
    val morph = { if (vm.switcherVisible) vm.switcher.anim.value.coerceIn(0f, 1f) else 0f }
    val hidden = { (pill.scrollY.value / HIDDEN_TRANSLATE_Y).coerceIn(0f, 1f) } // 0 shown .. 1 the recall button
    val pillOrigin = remember { OriginHolder() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .paddingDp(start = padH, end = padH, bottom = padB)
                // hiding morphs the Pill into the recall button (below); once it's there, the button takes over
                .graphicsLayer { alpha = if (hidden() >= 1f) 0f else 1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // gesture area
            Box(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { pillOrigin.at = it.positionInWindow() }
                    // the Pill's swipes, for screen readers (which can't swipe)
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction("Show tabs") { pill.openSwitcher(); true },
                            if (vm.canGoBackUi) CustomAccessibilityAction("Back") { vm.goBack() } else null,
                            if (vm.canGoForwardUi) CustomAccessibilityAction("Forward") { vm.goForward() } else null,
                            CustomAccessibilityAction("Hide toolbar") { pill.hideBar(); true },
                        )
                    }
                    .panGesture(
                        // off once the switcher is open: its own Pill takes the swipes there (not while opening:
                        // that would cancel the very drag that opens it)
                        enabled = !vm.isInputFocused && !pill.isBarHidden && !vm.find.visible && !vm.switcher.settled,
                        onMove = { dx, dy -> pill.onPanMove(dx, dy) },
                        onRelease = { dx, dy, vx, vy -> pill.onPanRelease(dx, dy, vx, vy) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // SEARCH PILL
                Box(
                    Modifier.fillMaxWidth().zIndex(2f)
                        .morphInto(morph, { vm.switcher.barPill }, pillOrigin)
                        .morphInto(hidden, { pill.recallBounds }, pillOrigin) // hiding: shrinks into the recall button
                        .graphicsLayer {
                            val kk = k()
                            val m = maxOf(morph(), hidden()) // the switcher's pill and the recall button are both fully rounded
                            val round = size.height / 2
                            val top = lerp(pillRadius.dp.toPx(), round, m)
                            val bottom = lerp(interp(kk, listOf(0f, 100f), listOf(pillRadius, 0f)).dp.toPx(), round, m)
                            shape = RoundedCornerShape(top, top, bottom, bottom)
                            shadowElevation = lerp(lerp(interp(kk, listOf(0f, 10f), listOf(10f, 0f)), 6f, morph()), 3f, hidden()).dp.toPx()
                            clip = true
                        }
                        .drawBehind { drawRect(lerp(ui.pillBar, ui.cs.secondaryContainer, hidden())) },
                ) { Column {
                    // typing: suggestions inside the Pill, above the field. The Pill grows up (and shrinks back)
                    // to fit them as they come and go, never jumping. Only this part animates its size: the
                    // morph into the switcher's Pill sizes the field directly, so it keeps pace with the outside
                    Box(Modifier.fillMaxWidth().animateContentSize(Motion.noBounce(), alignment = Alignment.BottomCenter)) {
                        if (vm.isInputFocused) PillSuggestions(vm, suggestionsMaxDp)
                    }
                    Box(Modifier.fillMaxWidth().height(PILL_HEIGHT.dp)) {
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val f = pill.findAnim.value
                            alpha = (1f - (maxOf(morph(), hidden()) * 3f).coerceIn(0f, 1f)) * (1f - f * 1.6f).coerceIn(0f, 1f)
                            translationX = -40.dp.toPx() * f
                        }) {
                            SearchPillContent(vm, pill, focusManager)
                        }
                        if (findShown) Box(Modifier.fillMaxSize().graphicsLayer {
                            val f = pill.findAnim.value
                            alpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            translationX = 60.dp.toPx() * (1f - f)
                        }) {
                            if (vm.find.visible) FindPillContent(vm.find, s.cornerRadius)
                        }
                        if (vm.switcherVisible) Box(
                            Modifier.matchParentSize().graphicsLayer { alpha = ((morph() - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.Center,
                        ) { SwitcherPillButtons({}, {}, up = vm.settings.switcherLayout.up) }
                    }
                } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPillContent(vm: BrowserViewModel, pill: PillState, focusManager: androidx.compose.ui.focus.FocusManager) {
    val ui = LocalUi.current
    val s = vm.settings
    val focusRequester = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(vm.inputUrl)) }
    // On focus the host is swapped for the full URL, but the focusing tap then reports its cursor placement together
    // with the pre-swap text; that report must not put the host back. Cleared once focus settles.
    var staleText by remember { mutableStateOf<String?>(null) }
    // selectTextOnFocus: the tap that focuses the field places the cursor right after focus; re-select everything
    // once that settles (this also syncs the keyboard's selection, so typing replaces the URL).
    LaunchedEffect(vm.isInputFocused) {
        if (vm.isInputFocused) {
            val before = tfv.text
            kotlinx.coroutines.delay(120)
            // only if nothing was typed meanwhile, or the first keystroke would be selected and overwritten
            if (tfv.text == before) tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
            staleText = null
        }
    }
    LaunchedEffect(vm.inputUrl) { if (tfv.text != vm.inputUrl) tfv = TextFieldValue(vm.inputUrl, TextRange(vm.inputUrl.length)) }
    // The field only takes focus from a touch on it or an explicit request (FocusInput) — never on its own, e.g. when
    // switching tabs removes the focused WebView and Compose hands focus to the first focusable control.
    var allowFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.ui.collect { if (it == com.jlees.mibrowser.UiEvent.FocusInput) { allowFocus = true; focusRequester.requestFocus() } }
    }
    // back/forward swipe feedback, read at draw time (no recomposition while dragging)
    val backOpacity = { interp(pill.horizontalDrag.value, listOf(0f, 32f), listOf(0f, 1f)) }
    val fwdOpacity = { interp(pill.horizontalDrag.value, listOf(-32f, 0f), listOf(1f, 0f)) }
    // the field gives way to an arrow only where there's somewhere to go: with no back (or forward) it stays put
    val contentOpacity = {
        val d = pill.horizontalDrag.value
        if ((d > 0f && vm.canGoBackUi) || (d < 0f && vm.canGoForwardUi)) interp(d, listOf(-32f, 0f, 32f), listOf(0f, 1f, 0f)) else 1f
    }
    val tab = vm.tabs.current

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // incognito: the field gets an outline, always, so the mode is clear at a glance
            val incognitoRing by androidx.compose.animation.core.animateFloatAsState(if (vm.tabs.current?.incognito == true) 1f else 0f, Motion.spatial(), label = "incognito ring")
            val fieldShape = RoundedCornerShape((s.cornerRadius * 1.5f).dp)
            Box(
                Modifier.weight(1f).height((PILL_HEIGHT * 0.7f).dp).graphicsLayer { alpha = contentOpacity() }
                    .clip(fieldShape).background(ui.pillField)
                    .then(if (incognitoRing > 0.01f) Modifier.border((2f * incognitoRing).dp, ui.cs.onSurface.copy(alpha = 0.7f * incognitoRing), fieldShape) else Modifier),
            ) {
                // loading progress: a faint accent fill behind the text, growing left to right or from the center out
                if (s.progressBarMode != LoadingBar.HIDDEN && vm.isLoading) {
                    val fromLeft = s.progressBarMode == LoadingBar.STANDARD
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = pill.progress.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(if (fromLeft) 0f else 0.5f, 0.5f)
                            alpha = 0.2f
                        }.background(ui.cs.primary),
                    )
                }
                Row(Modifier.fillMaxSize().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // "Not secure": plain http off the device, or a site allowed past a certificate error. Tap to explain.
                    val incognito = vm.tabs.current?.incognito == true
                    val notSecure = !vm.isInputFocused && vm.connectionNotSecure
                    if (notSecure) Row(
                        Modifier.padding(start = 10.dp).clip(CircleShape).background(ui.cs.errorContainer)
                            .touchable { vm.explainNotSecure() }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym("warning", 15f, ui.cs.onErrorContainer, Modifier.padding(end = 4.dp))
                        Txt("Not secure", 12f, ui.cs.onErrorContainer, ui.bold, maxLines = 1)
                    }
                    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    BasicTextField(
                        value = tfv,
                        onValueChange = { v ->
                            if (v.text == staleText && v.text != tfv.text) return@BasicTextField // stale pre-focus text (see staleText)
                            if (v.text != tfv.text) vm.suggestions.onTyping(v.text)
                            tfv = v; vm.inputUrl = v.text
                        },
                        modifier = Modifier.weight(1f).padding(start = if (notSecure) 8.dp else 16.dp, end = 16.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    // a touch on the field unlocks focus for the tap; a swipe (e.g. up to the switcher) locks it again.
                                    // Not while it's focused: there a swipe scrolls the text, and locking would drop focus and the text
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val wasFocused = vm.isInputFocused
                                    allowFocus = true
                                    var dragged = false
                                    while (true) {
                                        val c = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                                        if (!c.pressed) break
                                    }
                                    if (dragged && !wasFocused) allowFocus = false
                                }
                            }
                            .focusProperties { canFocus = allowFocus && !vm.switcherVisible }
                            .focusRequester(focusRequester)
                            .onFocusChanged { st ->
                                if (st.isFocused && !vm.isInputFocused) {
                                    vm.isInputFocused = true
                                    staleText = tfv.text
                                    val full = vm.activeUrl ?: ""
                                    vm.inputUrl = full
                                    tfv = TextFieldValue(full, TextRange(0, full.length)) // selectTextOnFocus
                                } else if (!st.isFocused && vm.isInputFocused) {
                                    allowFocus = false
                                    vm.isInputFocused = false
                                    vm.suggestions.clear()
                                    vm.inputUrl = displayHost(vm.activeUrl)
                                }
                            },
                        singleLine = true,
                        textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = FontWeight.W600, fontSize = (16 * s.fontScale).sp),
                        // a search shortcut ("!w ") becomes a mini pill once it's typed at the start with a space after it:
                        // bold accent text, padded by a thin space on each side, with the pill drawn behind it (decorationBox)
                        visualTransformation = run {
                            val bangs = vm.bangs
                            val hl = SpanStyle(color = ui.cs.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            remember(bangs, hl) { BangPillTransformation(bangs, hl) }
                        },
                        onTextLayout = { textLayout = it },
                        cursorBrush = SolidColor(ui.cs.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); vm.submitInput(tfv.text) }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (tfv.text.isEmpty()) Txt(if (incognito) "Search privately" else "Search", 16f, ui.cs.onSurfaceVariant, FontWeight.W600)
                                val pillColor = ui.cs.primaryContainer
                                val bangs = vm.bangs
                                val n = remember(tfv.text, bangs) { typedBangLength(tfv.text, bangs) }
                                Box(Modifier.drawBehind {
                                    val l = textLayout ?: return@drawBehind
                                    // transformed text: thin space (0), "!key" (1..n), en space (n+1). The pill wraps "!key" with the
                                    // same padding each side; skipped while the text is wider than the field and scrolled
                                    if (n == 0 || l.layoutInput.text.length < n + 2 || l.size.width > size.width + 1) return@drawBehind
                                    val thin = l.getBoundingBox(0)
                                    val pad = thin.width + 3.dp.toPx()
                                    val left = thin.right - pad
                                    val right = l.getBoundingBox(n).right + pad
                                    val top = l.getLineTop(0) + 1.dp.toPx(); val bottom = l.getLineBottom(0) - 1.dp.toPx()
                                    drawRoundRect(pillColor, Offset(left, top), Size(right - left, bottom - top), CornerRadius((bottom - top) / 2))
                                }) { inner() }
                            }
                        },
                    )
                    Row(Modifier.padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.isInputFocused && tfv.text.isNotEmpty()) {
                            Box(Modifier.padding(end = 8.dp).touchable { vm.inputUrl = ""; tfv = TextFieldValue(""); focusRequester.requestFocus() }) { Sym("cancel", 20f, ui.cs.onSurfaceVariant, label = "Clear") }
                        }
                        if (vm.isInputFocused) {
                            Box(Modifier.touchable { focusManager.clearFocus(); vm.submitInput(tfv.text) }) { Sym("search", 22f, ui.cs.primary, label = "Search") }
                        } else if (vm.tabs.current?.isFresh == true && incognitoSupported) {
                            // a fresh tab has nothing to reload: here the button switches the tab between normal and incognito
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(if (incognito) ui.cs.inverseSurface else Color.Transparent)
                                    .touchable { vm.toggleIncognito() },
                                contentAlignment = Alignment.Center,
                            ) { Sym("incognito", 20f, if (incognito) ui.cs.inverseOnSurface else ui.cs.onSurface, label = if (incognito) "Leave incognito" else "Make this tab incognito") }
                        } else {
                            val hasUrl = vm.activeUrl != null
                            Box(Modifier.alpha(if (hasUrl) 1f else 0.3f).touchable(enabled = hasUrl) {
                                if (vm.isLoading) vm.web.tryEmit(WebCmd.Stop(vm.tabs.activeId)) else vm.web.tryEmit(WebCmd.Reload(vm.tabs.activeId))
                            }) { Sym(if (vm.isLoading) "close" else "refresh", 22f, ui.cs.onSurface, label = if (vm.isLoading) "Stop loading" else "Reload") }
                        }
                    }
                }
            }
        }
        // nav arrows (pointerEvents none)
        if (tab != null && vm.canGoBackUi) Box(Modifier.align(Alignment.CenterStart).padding(start = 20.dp).graphicsLayer { alpha = backOpacity() }) { Sym("arrow_back", 28f, ui.cs.onSurface) }
        if (tab != null && vm.canGoForwardUi) Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).graphicsLayer { alpha = fwdOpacity() }) { Sym("arrow_forward", 28f, ui.cs.onSurface) }
    }
}

/** A window position recorded at layout time and read in layout/draw lambdas (no recomposition when it changes). */
private class OriginHolder { var at = Offset.Zero }

/**
 * Lays the pill out between its own place ([origin], progress 0) and [target] in the window (progress 1), without
 * changing the space it takes in its parent. It only morphs while not typing, so at its own size: full width, PILL_HEIGHT.
 */
private fun Modifier.morphInto(progress: () -> Float, target: () -> Rect?, origin: OriginHolder) = layout { m, c ->
    val p = progress()
    val t = target()
    if (p <= 0f || t == null || !c.hasBoundedWidth) {
        val pl = m.measure(c)
        return@layout layout(pl.width, pl.height) { pl.place(0, 0) }
    }
    val w = lerp(c.maxWidth.toFloat(), t.width, p).roundToInt().coerceAtLeast(1)
    val ownH = PILL_HEIGHT.dp.roundToPx()
    val h = lerp(ownH.toFloat(), t.height, p).roundToInt().coerceAtLeast(1)
    val pl = m.measure(Constraints.fixed(w, h))
    layout(c.maxWidth, ownH) {
        pl.place(lerp(0f, t.left - origin.at.x, p).roundToInt(), lerp(0f, t.top - origin.at.y, p).roundToInt())
    }
}

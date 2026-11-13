// Pieces shared by the Library (bookmarks/history) sheets and the tab switcher.
package com.jlees.mibrowser.ui.common

import androidx.compose.ui.unit.Dp

import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import com.jlees.mibrowser.ui.common.SheetSearchField

/** A sheet's back swipe: [drag] reports how far (0..1 of the width), [release] whether to finish or spring back. */
class BackSwipe(val drag: (Float) -> Unit, val release: (Boolean) -> Unit)

/**
 * What swiping a sheet's search Pill does: pushes the sheet back out the way it came ([towardEnd]: to the right; else
 * to the left; [downward]: down), unless a view with levels of its own (Settings' sections, Library folders) has set
 * [inner]: then a swipe right goes up a level, wherever the sheet came from. Provided by OverlaySheet.
 */
class SheetBack(
    val towardEnd: Boolean, val close: BackSwipe,
    /** How far the sheet is slid out (0 shown, ±1 off screen). */
    val slide: () -> Float = { 0f },
    /** The tab switcher's Pill (window px) when the sheet opened from the switcher: the search Pill grows out of it as
     *  the sheet slides in, and shrinks back into it as it slides out. Null: it just slides with the sheet. */
    val morphFrom: () -> androidx.compose.ui.geometry.Rect? = { null },
    /** What the switcher's Pill shows (+ and ⋮), faded in as the search Pill becomes it. */
    val morphContent: @Composable () -> Unit = {},
    /** The sheet came up from the bottom (History): swiping its Pill down goes back. */
    val downward: Boolean = false,
    /** The sheet's current slide (px), which the Pill cancels while morphing so it holds still. */
    val offset: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset.Zero },
    /** How far the sheet slides to close (px): its width, or its height when [downward]. */
    val extent: () -> Float = { 1f },
) { var inner: BackSwipe? = null }

val LocalSheetBack = staticCompositionLocalOf<SheetBack?> { null }

/** The room a sheet view's list leaves at its end for the search Pill. */
val SheetPillSpace = 112.dp

/**
 * A sheet view's search field, in a Pill at the bottom (above the keyboard while typing). Swiping the Pill goes back,
 * following the finger (see [SheetBack]); not while typing in it, when a swipe moves through the text. [menu] adds a
 * ⋮ button: its menu opens inside the Pill, above the field, the Pill growing to fit it.
 */
@Composable
fun BoxScope.SheetSearchPill(
    value: String, onChange: (String) -> Unit,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val ui = LocalUi.current
    val back = LocalSheetBack.current
    val haptics = LocalHaptics.current
    var open by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    // the keyboard closed (Back, its own hide key): stop typing too, so the cursor goes and the Pill's swipes work again
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    @OptIn(ExperimentalLayoutApi::class) val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp && typing) focusManager.clearFocus() }
    var swipe by remember { mutableStateOf<BackSwipe?>(null) } // the handler this drag started with
    var upLevel by remember { mutableStateOf(false) } // it's [SheetBack.inner]: always a swipe right
    var decided by remember { mutableStateOf(false) } // this drag's direction has been checked
    val screenPx = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var progress by remember { mutableFloatStateOf(0f) }
    // the swipe below outlives this composition: it reads the sheet's current way back (it changes when the Gestures
    // setting moves the page while it's open), not the one it started with
    val latestBack = rememberUpdatedState(back)
    val density = LocalDensity.current
    // morphing with the switcher's Pill: m = 0 the search Pill .. 1 the switcher's Pill, from how far the sheet is out
    val from = back?.morphFrom?.invoke()
    val m = if (from != null) kotlin.math.abs(back.slide()).coerceIn(0f, 1f) else 0f
    var rest by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) } // this Pill's own place (window px)
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(1000f)
            // while morphing, the Pill holds still as the sheet slides (it moves only to meet the switcher's Pill)
            .graphicsLayer { if (from != null) { val o = back.offset(); translationX = -o.x; translationY = -o.y } }
            .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
    ) {
        if (m > 0f && open) open = false // the menu folds away before the Pill morphs
        // the Pill: its ⋮ menu (when open) above the search field, both inside the darker outer area, which grows to fit
        Column(
            Modifier.fillMaxWidth()
                // its own slot (the sheet's slide is cancelled above, so this holds still from the first frame)
                .onGloballyPositioned { rest = it.boundsInWindow() }
                .morphToward(if (rest != null) from else null, rest, m)
                .panGesture(
                    enabled = back != null && !typing,
                    onStart = { val b = latestBack.value; upLevel = b?.inner != null; swipe = b?.inner ?: b?.close; progress = 0f; decided = false },
                    onMove = { dx, dy ->
                        // in a sub-level only a swipe right does anything (up a level); down or left is ignored there,
                        // rather than starting the level's slide at nothing (which flickered)
                        if (upLevel && !decided) { decided = true; if (dx <= 0f || kotlin.math.abs(dy) > kotlin.math.abs(dx)) swipe = null }
                        val b = latestBack.value
                        val sign = if (b?.towardEnd == true) 1f else -1f
                        val p = if (upLevel) (dx * density.density / screenPx).coerceIn(0f, 1f)
                        else ((if (b?.downward == true) dy else dx * sign) * density.density / (b?.extent?.invoke() ?: 1f)).coerceIn(0f, 1f)
                        if ((p > 0.25f) != (progress > 0.25f)) haptics?.tick() // past here, letting go goes back
                        progress = p; swipe?.drag?.invoke(p)
                    },
                    onRelease = { _, _, vx, vy ->
                        val b = latestBack.value
                        val toward = if (upLevel) vx else if (b?.downward == true) vy else vx * (if (b?.towardEnd == true) 1f else -1f) // dp/ms
                        swipe?.release?.invoke(progress > 0.25f || (toward > 0.5f && progress > 0.02f)); swipe = null
                    },
                )
                .graphicsLayer {
                    alpha = if (from != null && rest == null && m > 0f) 0f else 1f // the one frame before its place is known
                    // the Pill's rounding (still a pill when the menu makes it taller); fully round while morphing
                    val pillRadius = if (ui.radius >= 20) 35.dp.toPx() else (ui.radius * 2).dp.toPx()
                    shape = RoundedCornerShape(if (m > 0f) size.height / 2 else minOf(pillRadius, size.height / 2))
                    shadowElevation = androidx.compose.ui.util.lerp(10f, 6f, m).dp.toPx(); clip = true
                }
                .background(lerp(ui.pillBar, ui.cs.surfaceContainerHigh, m))
                .padding(horizontal = 10.dp), // as the main Pill: its field sits 10dp inside the bar
        ) {
            androidx.compose.animation.AnimatedVisibility(
                open && menu != null,
                enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom) + androidx.compose.animation.fadeOut(),
            ) { Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) { menu?.invoke(this) { open = false } } }
            Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { alpha = (1f - m * 2f).coerceIn(0f, 1f) }) {
                    SheetSearchField(value, onChange, onFocus = { typing = it; if (it) open = false /* typing closes the menu */ }, trailing = menu?.let { {
                        IconButton(onClick = { open = !open }) { Sym(if (open) "close" else "more_vert", 24f, ui.cs.onSurfaceVariant, label = if (open) "Close menu" else "More options") }
                    } })
                }
                if (m > 0.5f) Box(Modifier.wrapContentSize(unbounded = true).graphicsLayer { alpha = (m - 0.5f) * 2f }) { back?.morphContent?.invoke() }
            }
        }
    }
}

/**
 * A Library page with nothing to show: its icon and a line ("No History Yet", or "No matches" while searching).
 * [top] is the space above it; pages whose list starts lower pass less, so the message sits at the same height on all.
 */
@Composable
fun EmptyState(icon: String, text: String, top: Dp = 50.dp) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(top = top), horizontalAlignment = Alignment.CenterHorizontally) {
        Sym(icon, 50f, ui.cs.onSurfaceVariant)
        Txt(text, 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
    }
}

/** A row in a [SearchHeaderWithMenu] menu: an icon and a label, and [trailing] (e.g. an expand arrow). */
@Composable
fun SheetMenuItem(icon: String, label: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().touchable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 18f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
        Txt(label, 14f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * Sizes and places a Pill [m] of the way from its own place ([rest], window px) to [target]'s: the same size and spot
 * as the target at 1. Without a target (or before its own place is known) it lays out as usual.
 */
private fun Modifier.morphToward(target: androidx.compose.ui.geometry.Rect?, rest: androidx.compose.ui.geometry.Rect?, m: Float) = layout { measurable, c ->
    if (target == null || rest == null || m <= 0f) {
        val p = measurable.measure(c)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val w = androidx.compose.ui.util.lerp(rest.width, target.width, m).roundToInt().coerceAtLeast(1)
    val h = androidx.compose.ui.util.lerp(rest.height, target.height, m).roundToInt().coerceAtLeast(1)
    val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(rest.width.roundToInt(), rest.height.roundToInt()) {
        p.place(((target.left - rest.left) * m).roundToInt(), ((target.top - rest.top) * m).roundToInt())
    }
}

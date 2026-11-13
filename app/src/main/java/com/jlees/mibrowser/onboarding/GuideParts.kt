// The Gesture Walkthrough's pieces: its steps and tasks, and the copies of mi's controls and pages it's played on.
package com.jlees.mibrowser.onboarding

import androidx.compose.animation.animateColorAsState
import com.jlees.mibrowser.data.SwitcherPanel
import com.jlees.mibrowser.tabs.PillSwipe
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.panGesture
import com.jlees.mibrowser.ui.common.pillBar
import com.jlees.mibrowser.ui.common.pillField
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.common.SwipeActionRow
import com.jlees.mibrowser.ui.common.Txt
import com.jlees.mibrowser.ui.common.blockTouches
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The word for the swipe that opens a page from [s] ("right": the page comes in from the left). */
internal fun openWord(s: PillSwipe) = when (s) { PillSwipe.RIGHT -> "right"; PillSwipe.LEFT -> "left"; PillSwipe.UP -> "up" }
/** The word for the swipe on a page's search Pill that sends it back the way it came. */
internal fun backWord(s: PillSwipe) = when (s) { PillSwipe.RIGHT -> "left"; PillSwipe.LEFT -> "right"; PillSwipe.UP -> "down" }
internal fun openMove(s: PillSwipe) = when (s) { PillSwipe.RIGHT -> Move.RIGHT; PillSwipe.LEFT -> Move.LEFT; PillSwipe.UP -> Move.UP }
internal fun backMove(s: PillSwipe) = when (s) { PillSwipe.RIGHT -> Move.LEFT; PillSwipe.LEFT -> Move.RIGHT; PillSwipe.UP -> Move.DOWN }
/** How far a drag [dx]/[dy] (px) has pulled the page from [s] open: a screen [w]idth sideways, [h] up. */
internal fun openPull(s: PillSwipe, dx: Float, dy: Float, w: Float, h: Float) = when (s) { PillSwipe.RIGHT -> dx / w; PillSwipe.LEFT -> -dx / w; PillSwipe.UP -> -dy / h }
/** How far a drag on the page's search Pill has pushed it back out: a screen [w]idth sideways, [down] px down. */
internal fun backPullOf(s: PillSwipe, dx: Float, dy: Float, w: Float, down: Float) = when (s) { PillSwipe.RIGHT -> -dx / w; PillSwipe.LEFT -> dx / w; PillSwipe.UP -> dy / down }

/** One thing a step asks you to try, as a line in the caption's checklist. [leaves]: going back out of the step's page. */
internal enum class Task(val label: String, val leaves: Boolean = false) {
    GO_BACK("Swipe right to go back"), GO_FORWARD("Swipe left to go forward"),
    OTHER_TABS("Swipe right to see your other tabs"), CLOSE_TAB("Swipe a tab up to close it"),
    SEARCH_TABS("Hold the Pill to search your tabs"), // only while Settings › Gestures › Long Press to Search is on
    DELETE_BOOKMARK("Swipe a bookmark left to delete it"), EDIT_BOOKMARK("Swipe a bookmark right to edit it"),
    // going back: the direction depends on where the page sits (see [labelFor])
    LEAVE_LIBRARY("", leaves = true), LEAVE_SETTINGS("", leaves = true),
    DELETE_ENTRY("Swipe an entry left to delete it"),
    LEAVE_HISTORY("", leaves = true);

    /** The checklist line; [swipeOf] gives a page's place (for going back out of it). */
    fun labelFor(swipeOf: (SwitcherPanel) -> PillSwipe) = when (this) {
        LEAVE_LIBRARY -> "Swipe the search Pill ${backWord(swipeOf(SwitcherPanel.LIBRARY))} to go back"
        LEAVE_SETTINGS -> "Swipe the search Pill ${backWord(swipeOf(SwitcherPanel.SETTINGS))} to go back"
        LEAVE_HISTORY -> "Swipe the search Pill ${backWord(swipeOf(SwitcherPanel.HISTORY))} to go back"
        else -> label
    }
}

/**
 * The tour, in order: four steps on the blank tab, three that open a page from the switcher and practise its gestures,
 * then back to the tab. [text] is what to do; a page step shows [inside] once its page is open. [chips] are the tasks
 * the step needs, each ticked off once done.
 */
internal enum class Step(val title: String, val text: String, val inside: String = "", val chips: List<Task> = emptyList(), val panel: SwitcherPanel? = null) {
    INTRO("Meet the Pill", "It's your search bar for the web: type an address, or anything you want to find."),
    NAVIGATE("Page Navigation", "Swipe to navigate forward or backward while web browsing.", chips = listOf(Task.GO_BACK, Task.GO_FORWARD)),
    HIDE("Hide the Pill", "Swipe it down to hide it while you browse."),
    TABS("Your tabs", "Swipe up on the Pill for your tabs."),
    CARDS("Navigating your tabs", "Your open tabs, the newest on the right. Pinned tabs can't be closed.", chips = listOf(Task.OTHER_TABS, Task.CLOSE_TAB, Task.SEARCH_TABS)),
    // %s: the swipe that opens the page (it follows Settings › Tabs › Gestures)
    LIBRARY("Library", "Swipe the Pill %s to open your bookmarks and downloads.", "Your bookmarks and downloads. Try these:",
        listOf(Task.DELETE_BOOKMARK, Task.EDIT_BOOKMARK, Task.LEAVE_LIBRARY), SwitcherPanel.LIBRARY),
    SETTINGS("Settings", "Swipe the Pill %s to open Settings.", "Inside a section, swipe the search Pill right to go up a level.",
        listOf(Task.LEAVE_SETTINGS), SwitcherPanel.SETTINGS),
    HISTORY("History", "Swipe the Pill %s for History and recently closed tabs.", "Pages you visited and tabs you closed. Try these:",
        listOf(Task.DELETE_ENTRY, Task.LEAVE_HISTORY), SwitcherPanel.HISTORY),
    OPEN("Back to your tab", "Swipe down or tap the current tab to open it."),
    DONE("You're all set", "That's every gesture. Enjoy mi."),
}

/** Which way the fingertip hint moves. */
internal enum class Move { TAP, UP, DOWN, RIGHT, LEFT, SIDEWAYS }

/** The id of the switcher copy's blank tab: the one the tour started on, and returns to. */
internal const val GUIDE_HOME = "guide_home"

/**
 * Taps and drags on a part of the copy: mi's own pan gesture (raw screen coordinates, so a part moving under the
 * finger doesn't skew it), in the px and px/s the copy works in. [onDrag] gets the distance so far; [onRelease] the
 * distance and release velocity (also when cut off: the part was removed mid-swipe).
 */
@Composable
internal fun Modifier.guideDrag(onTap: () -> Unit = {}, onDrag: (Offset) -> Unit, onRelease: (Offset, Velocity) -> Unit): Modifier {
    val px = LocalDensity.current.density
    return panGesture(
        enabled = true, onTap = onTap,
        onMove = { dx, dy -> onDrag(Offset(dx * px, dy * px)) },
        onRelease = { dx, dy, vx, vy -> onRelease(Offset(dx * px, dy * px), Velocity(vx * px * 1000f, vy * px * 1000f)) },
    )
}

/** The search field inside a copied Pill, with the main Pill's back/forward feedback while swiping it sideways. */
@Composable
internal fun GuidePillField(navDragDp: Float, modifier: Modifier) {
    val ui = LocalUi.current
    val field = RoundedCornerShape((ui.radius * 1.5f).dp)
    val content = 1f - (abs(navDragDp) / 32f).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(49.dp).clip(field)
            .background(ui.pillField)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.graphicsLayer { alpha = content }, verticalAlignment = Alignment.CenterVertically) {
            Txt("Search", 17f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.weight(1f), maxLines = 1)
            Sym("incognito", 20f, ui.cs.onSurface)
        }
        if (navDragDp > 0f) Sym("arrow_back", 24f, ui.cs.onSurface, Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = 1f - content })
        if (navDragDp < 0f) Sym("arrow_forward", 24f, ui.cs.onSurface, Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = 1f - content })
    }
}

/**
 * A copy of the Library, Settings or History page, [p] of the way in (from the left, the right, or the bottom: [from]), with a
 * sample row to swipe (not in Settings) and its search Pill at the bottom. [rowSwipe] and [pillSwipe] make them work.
 */
@Composable
internal fun GuidePage(
    panel: SwitcherPanel, from: PillSwipe, p: Float, onRow: (Rect) -> Unit, onPill: (Rect) -> Unit, onDelete: () -> Unit, onEdit: (() -> Unit)?, pillSwipe: Modifier,
) {
    val ui = LocalUi.current
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            when (from) { // the side its place in the switcher brings it in from
                PillSwipe.RIGHT -> translationX = -size.width * (1f - p)
                PillSwipe.LEFT -> translationX = size.width * (1f - p)
                PillSwipe.UP -> translationY = size.height * (1f - p)
            }
        }.background(ui.cs.surfaceContainerLow).blockTouches().windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp)) {
            // as the real page: its title and ✕, then (Library, History) its two sections
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(panel.label, 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { Sym("close", 24f, ui.cs.onSecondaryContainer) }
            }
            val sections = when (panel) { SwitcherPanel.LIBRARY -> listOf("Bookmarks", "Downloads"); SwitcherPanel.HISTORY -> listOf("History", "Recently Closed"); SwitcherPanel.SETTINGS -> null }
            if (sections != null) com.jlees.mibrowser.ui.common.ConnectedChoice(sections.map { it to it }, sections[0], Modifier.padding(top = 12.dp, end = 4.dp)) {}
            if (panel != SwitcherPanel.SETTINGS) {
                val (icon, title, sub) = if (panel == SwitcherPanel.LIBRARY) Triple("bookmarks", "mi browser", "github.com") else Triple("history", "Example Domain", "example.com")
                // the app's own swipe row: red with a trash can to delete, the accent with a pencil to edit
                SwipeActionRow(64.dp, onDelete, Modifier.padding(top = 20.dp, end = 12.dp).onGloballyPositioned { onRow(it.boundsInRoot()) }, onEdit = onEdit, confirmDelete = true) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(ui.radius.dp)).background(ui.cs.surfaceContainerHighest).padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(end = 15.dp).size(44.dp).clip(CircleShape).background(ui.cs.surfaceContainer), contentAlignment = Alignment.Center) { Sym(icon, 22f, ui.cs.onSurfaceVariant) }
                        Column(Modifier.weight(1f)) {
                            Txt(title, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                            Txt(sub, 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                        }
                    }
                }
            } else Txt("Theme, Pill, Search, Privacy & Security…", 15f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 12.dp))
        }
        // its search Pill, two-toned like the main one
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(70.dp)
                .onGloballyPositioned { onPill(it.boundsInRoot()) }.then(pillSwipe)
                .shadow(10.dp, CircleShape).clip(CircleShape).background(ui.pillBar).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { GuidePillField(0f, Modifier) }
    }
}

/** A translucent fingertip acting out [move] over [at], on a loop. */
@Composable
internal fun FingerHint(move: Move, at: Rect) {
    val ui = LocalUi.current
    val phases = if (move == Move.SIDEWAYS) 2 else 1
    val t by rememberInfiniteTransition("hint").animateFloat(0f, 1f, infiniteRepeatable(tween(1400 * phases, easing = LinearEasing)), "t")
    val density = LocalDensity.current
    val phase = (t * phases).toInt().coerceAtMost(phases - 1)
    val f = t * phases - phase
    val (x, y) = when (move) {
        Move.TAP -> 0f to 0f
        Move.UP -> 0f to -110f * f
        Move.DOWN -> 0f to 90f * f
        Move.RIGHT -> 90f * f to 0f
        Move.LEFT -> -90f * f to 0f
        Move.SIDEWAYS -> (if (phase == 0) 80f else -80f) * f to 0f // right (go back), then left (go forward)
    }
    val size = 30.dp
    Box(
        Modifier.graphicsLayer {
            val half = size.toPx() / 2
            translationX = at.center.x - half + x * density.density
            translationY = at.center.y - half + y * density.density
            val s = if (move == Move.TAP) 1f - 0.25f * sin(PI * t).toFloat() else 1f
            scaleX = s; scaleY = s
            alpha = if (move == Move.TAP) 0.7f else 0.8f * sin(PI * f).toFloat()
        }.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)).border(2.dp, ui.cs.primary, CircleShape),
    )
}

/** One part of a step, in the caption's checklist: an empty circle, then a check once it's done. */
@Composable
internal fun Chip(label: String, on: Boolean) {
    val ui = LocalUi.current
    val circle by animateColorAsState(if (on) ui.cs.primary else Color.Transparent, label = "check")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(circle).border(2.dp, if (on) ui.cs.primary else ui.cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Sym("check", 16f, ui.cs.onPrimary) }
        Txt(label, 14f, if (on) ui.cs.onSurface else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(start = 10.dp))
    }
}

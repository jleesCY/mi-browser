// Settings' screen parts: the top bar lists scroll under, search-result headers, the default-browser banner, segmented rows.
package com.jlees.mibrowser.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*

/**
 * A top bar (search field, or a section's back arrow and title) that the list scrolls under: the bar sits on the sheet's
 * colour with a soft fade below it, so rows slide behind it and fade out instead of being cut off. [content] gets the
 * top padding that keeps its first row clear of the bar at rest.
 */
@Composable
internal fun TopBarOver(bg: Color, bar: @Composable () -> Unit, content: @Composable (top: androidx.compose.ui.unit.Dp) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barPx by remember { mutableIntStateOf(0) }
    val fade = 20.dp
    Box(Modifier.fillMaxSize()) {
        content(with(density) { barPx.toDp() } + fade)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().background(bg).padding(bottom = 6.dp).onSizeChanged { barPx = it.height }) { bar() }
            Box(Modifier.fillMaxWidth().height(fade).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
        }
    }
}

/** A section's title above its rows in search results. */
@Composable
internal fun SectionHeader(icon: String?, title: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Sym(icon, 18f, ui.cs.primary, Modifier.padding(end = 8.dp))
        Txt(title, 15f, ui.cs.primary, ui.bold)
    }
}

/** Shown at the top until mi is the default browser. */
@Composable
internal fun DefaultBrowserCard(onSet: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 20.dp).clip(RoundedCornerShape(ui.radius.coerceAtLeast(12).dp)).background(ui.cs.primaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("public", 28f, ui.cs.onPrimaryContainer, Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Txt("Make mi your default browser", 16f, ui.cs.onPrimaryContainer, ui.bold)
            Txt("Use mi to open links by default", 13f, ui.cs.onPrimaryContainer, ui.regular, Modifier.padding(top = 2.dp))
        }
        FilledTonalButton(onClick = onSet, modifier = Modifier.padding(start = 8.dp)) { Txt("Set", 14f, ui.cs.onSecondaryContainer, ui.bold) }
    }
}

/** Row [i] of [count] in a segmented card: full width, a 2dp gap below, rounded outer corners. */
@Composable
internal fun Modifier.segment(i: Int, count: Int): Modifier {
    val ui = LocalUi.current
    return fillMaxWidth().padding(bottom = if (i < count - 1) 2.dp else 0.dp).clip(segmentShape(i, count, ui.radius)).background(ui.cs.surfaceContainerHighest)
}

/**
 * A heading (or, without one, the same space) over [rows] in one segmented card, the way Settings groups its rows;
 * nothing when there are no rows. Panels and sheets outside Settings group their rows with it too.
 */
@Composable
internal fun SegmentedGroup(title: String?, rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    if (title != null) SectionHeader(null, title) else Spacer(Modifier.height(20.dp))
    rows.forEachIndexed { i, row -> Box(Modifier.segment(i, rows.size)) { row() } }
}

internal fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}

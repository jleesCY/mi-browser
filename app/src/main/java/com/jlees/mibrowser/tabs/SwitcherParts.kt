// The tab switcher's background, its cards' shape, and the buttons on its bottom bar (shared with the Gesture Walkthrough's copy).
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*

/**
 * The switcher's background: the accent from darker (top-left) to lighter (bottom-right), so the cards stand out. Dark
 * mode: a quiet grey corner and a muted, dim touch of the accent; light: a real mid-tone of the accent, not the pastel;
 * Pure Black: black (pixels off). Also behind the Gesture Walkthrough's copy of the switcher.
 */
fun switcherGradient(ui: com.jlees.mibrowser.ui.common.UiConfig): androidx.compose.ui.graphics.Brush {
    val cs = ui.cs
    if (ui.pureBlack) return androidx.compose.ui.graphics.SolidColor(Color.Black)
    val dark = if (ui.isDark) lerp(cs.surfaceContainerLow, cs.primary, 0.02f) else lerp(cs.primaryContainer, cs.primary, 0.28f)
    val light = if (ui.isDark) lerp(cs.surfaceContainer, cs.primaryContainer, 0.16f) else lerp(cs.surfaceContainerLowest, cs.primaryContainer, 0.6f)
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(dark, light), start = Offset.Zero, end = Offset.Infinite)
}

/** A tab card's shape: the page's (so a page zooms exactly into its card), or the screen's before it's been measured. */
@Composable
fun cardAspect(vm: BrowserViewModel): Float =
    vm.switcher.pageBounds?.let { it.width / it.height } ?: LocalConfiguration.current.let { it.screenWidthDp.toFloat() / it.screenHeightDp }

/** Bookmarks / Settings buttons on either side of the switcher's bar. */
@Composable
fun SwitcherEdgeButton(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val ui = LocalUi.current
    IconButton(onClick = onClick, modifier.size(52.dp)) { Sym(icon, 26f, ui.cs.onSurface, label = label) } // icon only, on the gradient
}

/** The contents of the switcher's centre pill: History (also a swipe up), new tab and the tab menu. */
@Composable
fun SwitcherPillButtons(onNew: () -> Unit, onMenu: () -> Unit, onHistory: () -> Unit = {}) {
    val ui = LocalUi.current
    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onHistory, Modifier.padding(start = 4.dp, end = 4.dp).size(52.dp)) { Sym("history", 24f, ui.cs.onSurface, label = "History") }
        FilledIconButton(
            onClick = onNew, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ui.cs.primaryContainer),
        ) { Sym("add", 30f, ui.cs.onPrimaryContainer, label = "New tab") }
        IconButton(onClick = onMenu, Modifier.padding(horizontal = 4.dp).size(52.dp)) { Sym("more_vert", 24f, ui.cs.onSurface, label = "Tab menu") }
    }
}

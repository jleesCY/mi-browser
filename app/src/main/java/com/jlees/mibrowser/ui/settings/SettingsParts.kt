// Settings' building blocks: the top bar lists scroll under, search-result headers, segmented rows, and the About items.
package com.jlees.mibrowser.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.logic.ACCENTS
import com.jlees.mibrowser.logic.DYNAMIC_ACCENT
import com.jlees.mibrowser.logic.hexColor
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
internal fun SectionHeader(icon: String, title: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 18f, ui.cs.primary, Modifier.padding(end = 8.dp))
        Txt(title, 15f, ui.cs.primary, ui.bold)
    }
}

// ---------------------------------------------------------------- specific rows

@Composable
internal fun AccentItem(vm: BrowserViewModel) {
    val ui = LocalUi.current
    // adaptive (wallpaper colors, Android 12+), gray, blue, green, red, orange, yellow, purple — horizontally scrollable
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val list = (if (dynamicOk) listOf(DYNAMIC_ACCENT) else emptyList()) + ACCENTS
    Item("palette", "Accent Color", if (dynamicOk) "Your wallpaper's colors, or pick one" else null, below = {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { hex ->
                val selected = vm.settings.accentColor == hex
                val scale by animateFloatAsState(if (selected) 0.86f else 1f, Motion.fastSpatial(), label = "swatch")
                val isDyn = hex == DYNAMIC_ACCENT
                Box(
                    Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(if (selected) RoundedCornerShape(30) else CircleShape)
                        .background(if (isDyn) ui.cs.tertiaryContainer else Color(hexColor(hex)))
                        .touchable { vm.updateSettings { copy(accentColor = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDyn) Sym("wallpaper", 22f, ui.cs.onTertiaryContainer)
                    if (selected && !isDyn) Sym("check", 22f, Color.White)
                }
            }
        }
    })
}

@Composable
internal fun CreditsItem() {
    val ui = LocalUi.current
    ExpandItem("info", "Credits", "Open-source projects mi is built with", null) { _ ->
        listOf(
            "Ad & tracker blocklist" to "StevenBlack/hosts, MIT License",
            "Font" to "Google Sans Flex, © Google LLC",
            "Icons" to "Material Symbols, Apache License 2.0",
            "UI" to "Jetpack Compose & Material 3, Apache License 2.0",
            "QR codes" to "ZXing, Apache License 2.0",
            "Image cropping" to "Android Image Cropper, Apache License 2.0",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
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
            Txt("Open links from other apps in mi", 13f, ui.cs.onPrimaryContainer, ui.regular, Modifier.padding(top = 2.dp))
        }
        FilledTonalButton(onClick = onSet, modifier = Modifier.padding(start = 8.dp)) { Txt("Set", 14f, ui.cs.onSecondaryContainer, ui.bold) }
    }
}

/** Expressive segmented list: large outer corners, small inner corners, 2dp gaps. */
/** Row [i] of [count] in a segmented card: full width, a 2dp gap below, rounded outer corners. */
@Composable
internal fun Modifier.segment(i: Int, count: Int): Modifier {
    val ui = LocalUi.current
    return fillMaxWidth().padding(bottom = if (i < count - 1) 2.dp else 0.dp).clip(segmentShape(i, count, ui.radius)).background(ui.cs.surfaceContainerHighest)
}

internal fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}

// The full-screen sheet that Library and Settings open in.
package com.jlees.mibrowser.ui


import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.jlees.mibrowser.ui.common.*
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer

// ---------------------------------------------------------------- overlay sheet
/**
 * Full-screen sheet (Library, Settings, History). It slides in from the side of the button that opened it, or up from
 * the bottom ([fromBottom], History): [slide] is the offset in sheet widths (or heights). No handle, no partial height.
 *
 * Swiping a view's search Pill goes back, following the finger: toward the side the sheet came from (right for
 * Settings, [backTowardEnd]; left for the Library; down for History). [onBackDrag] reports how far (0..1 of the width) and
 * [onBackRelease] whether to finish (close the sheet) or spring back, unless a view inside has levels of its own and
 * has taken the swipe ([SheetBack.inner]).
 */
@Composable
fun OverlaySheet(
    slide: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    topInsetDp: Float, imeDp: () -> Float, onClose: () -> Unit,
    backTowardEnd: Boolean, onBackDrag: (Float) -> Unit, onBackRelease: (Boolean) -> Unit, fromBottom: Boolean = false,
    morphFrom: () -> androidx.compose.ui.geometry.Rect? = { null }, morphContent: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null, belowHeader: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val back = remember(backTowardEnd, fromBottom) {
        SheetBack(
            backTowardEnd, BackSwipe(onBackDrag, onBackRelease), { slide.value }, morphFrom, morphContent, downward = fromBottom,
            offset = { if (fromBottom) androidx.compose.ui.geometry.Offset(0f, slide.value * sizePx.height) else androidx.compose.ui.geometry.Offset(slide.value * sizePx.width, 0f) },
            // sideways the finger can cross the whole screen; down from a Pill at the bottom it can't, so a short pull does it
            extent = { if (fromBottom) 220 * density else sizePx.width.toFloat() },
        )
    }
    Box(Modifier.fillMaxSize().zIndex(100f).onSizeChanged { sizePx = it }) {
        // dims the page while the sheet slides; also keeps taps off the page during the slide
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - abs(slide.value).coerceIn(0f, 1f) }.background(ui.cs.scrim.copy(alpha = 0.32f)).blockTouches())
        Column(
            Modifier.fillMaxSize().graphicsLayer { if (fromBottom) translationY = slide.value * size.height else translationX = slide.value * size.width }
                .background(ui.cs.surfaceContainerLow).blockTouches().padding(top = topInsetDp.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (header != null) Box(Modifier.weight(1f).padding(end = 12.dp)) { header() }
                else Txt("Settings", 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f))
                FilledTonalIconButton(onClick = onClose) { Sym("close", 24f, ui.cs.onSecondaryContainer, label = "Close") }
            }
            // under the title: the Library's Bookmarks / Downloads choice
            if (belowHeader != null) Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) { belowHeader() }
            // a tap on nothing in particular (an empty page, the space between rows) puts the keyboard away
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxWidth().weight(1f).paddingDp(bottom = imeDp).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
                CompositionLocalProvider(LocalSheetBack provides back) { content() }
            }
        }
    }
}


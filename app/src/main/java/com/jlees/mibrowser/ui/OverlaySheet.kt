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
import com.jlees.mibrowser.ui.common.*
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer

// ---------------------------------------------------------------- overlay sheet
/**
 * Full-screen sheet (Library, Settings). It slides in from the side of the button that opened it ([slide] is the
 * horizontal offset in sheet widths) and has a fixed size: no handle, no partial height.
 */
@Composable
fun OverlaySheet(
    slide: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    topInsetDp: Float, imeDp: () -> Float, onClose: () -> Unit,
    header: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    Box(Modifier.fillMaxSize().zIndex(100f)) {
        // dims the page while the sheet slides; also keeps taps off the page during the slide
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - abs(slide.value).coerceIn(0f, 1f) }.background(ui.cs.scrim.copy(alpha = 0.32f)).blockTouches())
        Column(
            Modifier.fillMaxSize().graphicsLayer { translationX = slide.value * size.width }
                .background(ui.cs.surfaceContainerLow).blockTouches().padding(top = topInsetDp.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (header != null) Box(Modifier.weight(1f).padding(end = 12.dp)) { header() }
                else Txt("Settings", 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f))
                FilledTonalIconButton(onClick = onClose) { Sym("close", 24f, ui.cs.onSecondaryContainer, label = "Close") }
            }
            Box(Modifier.fillMaxWidth().weight(1f).paddingDp(bottom = imeDp)) { content() }
        }
    }
}

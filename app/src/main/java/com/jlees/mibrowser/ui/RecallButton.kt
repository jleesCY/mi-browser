// The handle that brings the hidden Pill back.
package com.jlees.mibrowser.ui


import com.jlees.mibrowser.data.RecallPosition
import kotlinx.coroutines.CoroutineStart
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

// ---------------------------------------------------------------- recall handle
/**
 * Shown while the pill is hidden. Tap or swipe up → show the pill. Swipe left/right → the handle follows the finger
 * and pins to the left, center or right third it is released in (persisted as Settings.recallPosition).
 */
@Composable
fun RecallButton(vm: BrowserViewModel, pill: PillState, navInsetDp: Float, screenW: Float, modifier: Modifier) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val w = 56f
    val margin = 16f
    fun baseX(pos: RecallPosition) = when (pos) { RecallPosition.LEFT -> margin; RecallPosition.RIGHT -> screenW - margin - w; RecallPosition.CENTER -> (screenW - w) / 2 }
    val x = remember { Animatable(baseX(vm.settings.recallPosition)) }
    LaunchedEffect(screenW) { x.snapTo(baseX(vm.settings.recallPosition)) }
    var horizontal by remember { mutableStateOf<Boolean?>(null) }
    var startX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.offset { IntOffset((x.value * density).roundToInt(), 0) }.padding(bottom = maxOf(navInsetDp + 10f, 10f).dp)
            .graphicsLayer { alpha = interp(pill.scrollY.value, listOf(0f, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y), listOf(0f, 0f, 1f)) },
    ) {
        Box(
            Modifier.size(w.dp, 32.dp).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(ui.cs.secondaryContainer)
                .panGesture(
                    enabled = pill.isBarHidden, slopDp = 4f,
                    onStart = { startX = x.value; horizontal = null },
                    onMove = { dx, dy ->
                        if (horizontal == null) horizontal = abs(dx) > abs(dy)
                        if (horizontal == true) scope.launch(start = CoroutineStart.UNDISPATCHED) { x.snapTo((startX + dx).coerceIn(margin, screenW - margin - w)) }
                        else pill.recallMove(dy)
                    },
                    onRelease = { _, dy, _, vy ->
                        if (horizontal == true) {
                            val center = x.value + w / 2
                            val pos = when { center < screenW / 3 -> RecallPosition.LEFT; center > screenW * 2 / 3 -> RecallPosition.RIGHT; else -> RecallPosition.CENTER }
                            vm.updateSettings { copy(recallPosition = pos) }
                            scope.launch { x.animateTo(baseX(pos), Motion.spatial()) }
                        } else pill.recallRelease(dy, vy)
                        horizontal = null
                    },
                    onTap = { pill.showBar() },
                ),
            contentAlignment = Alignment.Center,
        ) { Sym("keyboard_arrow_up", 24f, ui.cs.onSecondaryContainer, label = "Show toolbar") }
    }
}

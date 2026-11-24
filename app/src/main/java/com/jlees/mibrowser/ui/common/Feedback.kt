// Small physical touches that make taps and results feel answered: a press that sinks and springs back, a pop when
// something turns on, a shake when there's nothing to find. Short (under ~300ms) and only on moments that matter.
package com.jlees.mibrowser.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Pressed, it sinks a little (to 94%) within 100ms; let go, it springs back with a small bounce. Put it first in the
 * modifier chain (so its background and clip shrink with it), with the same [source] as its click.
 */
fun Modifier.pressScale(source: InteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, if (pressed) tween(100) else spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Something just turned [on] (a favorite, a pin): it pops to 125% and settles. Not on first showing, nor turning off. */
@Composable
fun popScale(on: Boolean): Float {
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        if (seen && on) { scale.animateTo(1.25f, tween(100)); scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
        seen = true
    }
    return scale.value
}

/** An expander's arrow: points down closed, turns (200ms) to point up [open]. */
@Composable
fun Chevron(open: Boolean, size: Float, color: androidx.compose.ui.graphics.Color) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chevron")
    Sym("keyboard_arrow_down", size, color, Modifier.graphicsLayer { rotationZ = turn })
}

/** A sideways shake (dp offset) each time [wrong] turns true: three swings over 300ms, as a "no" nod. */
@Composable
fun shakeOffset(wrong: Boolean): Float {
    val x = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) x.animateTo(0f, keyframes { durationMillis = 300; -8f at 50; 8f at 100; -6f at 150; 6f at 200; -3f at 250 })
    }
    return x.value
}

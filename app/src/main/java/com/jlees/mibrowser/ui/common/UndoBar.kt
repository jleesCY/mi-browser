package com.jlees.mibrowser.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Something just deleted that can be put back: [message] ("Tab closed") and how to [undo] it; or, with another [action]
 * ("View"), something just done and where to go next; or, with no action, just a notice ("Link copied"). [ok]: a success
 * (it gets a check) rather than a problem. [ms]: how long it stays (else the bar's own time).
 */
class Undo(val message: String, val action: String? = "Undo", val ok: Boolean = action != "Undo", val ms: Long? = null, val undo: () -> Unit = {})

/** mi's notices ("Link copied", "Added to favorites"): one at a time, in the pill App shows above the Pill. */
object Notices {
    var current by mutableStateOf<Undo?>(null)
    /**
     * Shows [message]. [done]: it's finished, at once, and it's good news ("Link copied"): it gets a check. Not for
     * something undone or removed ("Removed from favorites"), something still going ("Downloading…"), or a problem.
     * [long]: worth a little more time to read.
     */
    fun show(message: String, done: Boolean = false, long: Boolean = false) { current = Undo(message, action = null, ok = done, ms = if (long) 4000 else 2500) }
}

/**
 * "Tab closed · Undo": grows out of its middle, shown for [showMs] after something is deleted, then [onTimeout] (the deletion stands).
 * Each new [undo] restarts the timer. Nothing shows while it's null.
 */
@Composable
fun UndoBar(undo: Undo?, onTimeout: () -> Unit, modifier: Modifier = Modifier, showMs: Long = 4000) {
    val ui = LocalUi.current
    LaunchedEffect(undo) { if (undo != null) { delay(undo.ms ?: showMs); onTimeout() } }
    // what it says stays while it shrinks away (by then [undo] is already null)
    var shown by remember { mutableStateOf(undo) }
    if (undo != null) shown = undo
    Box(modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        // it grows sideways out of its middle, and shrinks back into it as it goes (quicker); clipped to the pill's shape
        // all the while, so its round ends stay round
        AnimatedVisibility(
            undo != null, Modifier.clip(CircleShape),
            enter = expandHorizontally(Motion.enter(280), expandFrom = Alignment.CenterHorizontally) + fadeIn(Motion.enter(150)),
            exit = shrinkHorizontally(Motion.exit(160), shrinkTowards = Alignment.CenterHorizontally) + fadeOut(Motion.exit(160)),
        ) {
            Surface(Modifier.widthIn(max = 520.dp), shape = CircleShape, color = ui.cs.inverseSurface) {
                Row(Modifier.heightIn(min = 48.dp).padding(start = 20.dp, end = if (shown?.action == null) 20.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (shown?.ok == true) {
                        val pop = remember(shown) { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(shown) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f)) }
                        Sym("check", 18f, ui.cs.inversePrimary, Modifier.padding(end = 8.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                    }
                    Txt(shown?.message.orEmpty(), 14f, ui.cs.inverseOnSurface, ui.semibold, Modifier.weight(1f, fill = false), maxLines = 1)
                    shown?.action?.let { action -> TextButton(onClick = { undo?.undo?.invoke(); onTimeout() }) { Txt(action, 14f, ui.cs.inversePrimary, ui.bold) } }
                }
            }
        }
    }
}

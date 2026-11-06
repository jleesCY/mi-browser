package com.jlees.mibrowser.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Something just deleted that can be put back: [message] ("Tab closed") and how to [undo] it. */
class Undo(val message: String, val undo: () -> Unit)

/**
 * "Tab closed · Undo": shown for a few seconds after something is deleted, then [onTimeout] (the deletion stands).
 * Each new [undo] restarts the timer. Nothing shows while it's null.
 */
@Composable
fun UndoBar(undo: Undo?, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    LaunchedEffect(undo) { if (undo != null) { delay(4000); onTimeout() } }
    Box(modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(undo != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = CircleShape, color = ui.cs.inverseSurface) {
                Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(undo?.message.orEmpty(), 14f, ui.cs.inverseOnSurface, ui.semibold)
                    TextButton(onClick = { undo?.undo?.invoke(); onTimeout() }) { Txt("Undo", 14f, ui.cs.inversePrimary, ui.bold) }
                }
            }
        }
    }
}

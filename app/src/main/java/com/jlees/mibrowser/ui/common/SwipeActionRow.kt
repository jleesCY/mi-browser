package com.jlees.mibrowser.ui.common

import androidx.compose.ui.semantics.CustomAccessibilityAction

import androidx.compose.ui.semantics.customActions

import androidx.compose.ui.semantics.semantics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.ui.interp
import com.jlees.mibrowser.ui.timing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A list row that acts on a swipe. Left past a quarter of its width (or a quick fling) deletes: the row slides away
 * and [onDelete] runs. Right edits ([onEdit], when given): the row springs back and the editor opens.
 *
 * Behind the row, the side it uncovers is a coloured panel with the action's icon: red with a trash can for delete,
 * the accent colour with a pencil for edit. Both grow the same way as the row moves, and the icon pops once the swipe
 * has gone far enough to act.
 *
 * @param confirmDelete the delete swipe asks first: the row springs back and [onDelete] runs (it shows the
 *   question and deletes on yes) instead of the row sliding away
 * @param rowHeight the row's height, or null to fit its content (rows that grow, like a download with a progress bar)
 * @param slotHeight the row plus the gap below it; when given, it closes up after a delete (lists that
 *   position or animate their rows themselves leave it out)
 */
@Composable
fun SwipeActionRow(
    rowHeight: Dp?, onDelete: () -> Unit, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null,
    slotHeight: Dp? = null, confirmDelete: Boolean = false, content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    val corner = ui.radius.dp // the Library rows' shape (libraryRowShape)
    var tx by remember { mutableFloatStateOf(0f) }
    val shrink = remember { Animatable(1f) }
    var deleting by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    var armed by remember { mutableStateOf(false) } // past the point where releasing acts: a tick when this changes
    val deleteBg = ui.cs.errorContainer
    val editBg = ui.cs.primaryContainer
    val slot = if (slotHeight != null) Modifier.heightDp { slotHeight.value * shrink.value }.graphicsLayer { alpha = shrink.value } else Modifier
    BoxWithConstraints(modifier.fillMaxWidth().then(slot)) {
        val wPx = constraints.maxWidth.toFloat()
        val reach = { abs(tx) / (wPx * 0.25f) } // 1 = far enough to act
        Box(Modifier.fillMaxWidth().then(if (rowHeight != null) Modifier.height(rowHeight) else Modifier)) {
            // the panel behind the row: only the side being uncovered, in that action's colour
            Box(
                Modifier.matchParentSize().drawBehind {
                    if (tx == 0f) return@drawBehind
                    val c = if (tx < 0) deleteBg else editBg
                    drawRoundRect(c.copy(alpha = interp(reach(), listOf(0f, 0.3f), listOf(0.4f, 1f))), cornerRadius = CornerRadius(corner.toPx()))
                },
            ) {
                ActionIcon("delete", ui.cs.onErrorContainer, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) {
                    if (tx >= 0) 0f else interp(reach(), listOf(0f, 0.35f, 1f, 1.15f), listOf(0.4f, 1f, 1f, 1.25f))
                }
                if (onEdit != null) ActionIcon("edit", ui.cs.onPrimaryContainer, Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                    if (tx <= 0) 0f else interp(reach(), listOf(0f, 0.35f, 1f, 1.15f), listOf(0.4f, 1f, 1f, 1.25f))
                }
            }
            Box(
                // screen readers can't swipe: the same actions, as accessibility actions on the row
                Modifier.semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction("Delete") { onDelete(); true },
                        onEdit?.let { edit -> CustomAccessibilityAction("Edit") { edit(); true } },
                    )
                }.offset { IntOffset(tx.roundToInt(), 0) }.draggable(
                    rememberDraggableState { d ->
                        if (deleting) return@rememberDraggableState
                        tx = (tx + d).coerceAtMost(if (onEdit != null) Float.MAX_VALUE else 0f)
                        val now = abs(tx) > wPx * 0.25f
                        if (now != armed) { armed = now; haptics?.tick() }
                    },
                    Orientation.Horizontal, enabled = !deleting,
                    onDragStopped = { v ->
                        armed = false
                        when {
                            confirmDelete && (tx < -wPx * 0.25f || (v < -1200f && tx < -wPx * 0.08f)) -> {
                                onDelete()
                                settle(tx, v) { tx = it }
                            }
                            tx < -wPx * 0.25f || (v < -1200f && tx < -wPx * 0.08f) -> {
                                deleting = true
                                val slide = launch { animate(tx, -wPx, animationSpec = timing(200)) { value, _ -> tx = value } }
                                if (slotHeight != null) shrink.animateTo(0f, timing(200)) else slide.join()
                                onDelete()
                            }
                            onEdit != null && (tx > wPx * 0.25f || (v > 1200f && tx > wPx * 0.08f)) -> {
                                onEdit()
                                settle(tx, v) { tx = it }
                            }
                            else -> settle(tx, v) { tx = it }
                        }
                    },
                ),
            ) { content() }
        }
    }
}

/**
 * Springs the row back to rest without crossing it: an overshoot would flash the other side's panel (or move a row
 * toward a side that has no action, like history's right).
 */
private suspend fun settle(from: Float, velocity: Float, set: (Float) -> Unit) {
    if (from == 0f) return
    val v = if ((velocity < 0) == (from < 0)) 0f else velocity // a fling away from rest isn't carried into the return
    animate(from, 0f, v, Motion.spatial()) { value, _ -> set(if (from < 0) value.coerceAtMost(0f) else value.coerceAtLeast(0f)) }
}

/** A swipe action's icon on its panel; [scale] is read at draw time, so swiping never recomposes the row. */
@Composable
private fun ActionIcon(name: String, color: Color, modifier: Modifier, scale: () -> Float) {
    Box(modifier.graphicsLayer { val s = scale(); scaleX = s; scaleY = s; alpha = if (s == 0f) 0f else 1f }, contentAlignment = Alignment.Center) {
        Sym(name, 24f, color)
    }
}

// A followed product's price history as a line graph: a dot for every check (the lowest ringed), faint guide lines with the prices they
// mark, the first and latest dates beneath. Hold and drag along it to read any point: its price and when it was checked.
package com.jlees.mibrowser.follow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.library.smartDate
import com.jlees.mibrowser.ui.common.LocalHaptics
import com.jlees.mibrowser.ui.common.LocalUi

/** Where a price sits between the graph's guide lines (0: the lower, 1: the upper). A price that never changed sits a little above the lower third. */
internal fun chartLevel(price: Double, lo: Double, hi: Double): Float =
    if (hi - lo < 0.005) 0.35f else (0.15f + 0.7f * ((price - lo) / (hi - lo))).toFloat()

/** The check nearest [x] across a graph [width] wide, for times [t0]…[t1]. */
internal fun nearestPoint(times: List<Long>, x: Float, width: Float, t0: Long, t1: Long): Int =
    times.indices.minByOrNull { kotlin.math.abs((times[it] - t0).toFloat() / (t1 - t0).coerceAtLeast(1) * width - x) } ?: 0

@Composable
fun PriceChart(history: List<PricePoint>, currency: String, modifier: Modifier) {
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val points = history.filter { it.amount != null }
    val measurer = rememberTextMeasurer()
    var held by remember(points.size) { mutableStateOf<Int?>(null) }
    val line = ui.cs.primary
    val grid = ui.cs.outlineVariant
    val label = TextStyle(color = ui.cs.onSurfaceVariant, fontSize = 10.sp, fontFamily = ui.font)
    val tip = TextStyle(color = ui.cs.inverseOnSurface, fontSize = 12.sp, fontFamily = ui.font, fontWeight = FontWeight.SemiBold)
    val tipBg = ui.cs.inverseSurface
    if (points.isEmpty()) return
    val t0 = points.first().at
    val t1 = maxOf(System.currentTimeMillis(), t0 + 1)
    val lo = points.minOf { it.amount!! }
    val hi = points.maxOf { it.amount!! }

    Canvas(modifier.pointerInput(points) {
        // hold, then drag along it: the nearest check is shown, with a tick each time that changes
        fun pick(x: Float) {
            val i = nearestPoint(points.map { it.at }, x - CHART_LEFT.toPx(), size.width - CHART_LEFT.toPx(), t0, t1)
            if (i != held) { held = i; haptics?.tick() }
        }
        detectDragGesturesAfterLongPress(
            onDragStart = { pick(it.x) },
            onDrag = { change, _ -> change.consume(); pick(change.position.x) },
            onDragEnd = { held = null }, onDragCancel = { held = null },
        )
    }) {
        val left = CHART_LEFT.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 16.dp.toPx() // room for the dates
        val w = size.width - left
        fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * w
        fun y(p: Double) = bottom - chartLevel(p, lo, hi) * (bottom - top)

        // guide lines at the lowest and highest prices (one, if it never changed), labelled on the left
        val levels = if (hi - lo < 0.005) listOf(lo) else listOf(lo, hi)
        levels.forEach { p ->
            drawLine(grid, Offset(left, y(p)), Offset(size.width, y(p)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(compactPrice(p, currency), label)
            drawText(t, topLeft = Offset(0f, y(p) - t.size.height / 2))
        }
        drawLine(grid, Offset(left, bottom), Offset(size.width, bottom), 1.dp.toPx())
        // the first and latest dates
        val first = measurer.measure(smartDate(t0).substringBefore(','), label)
        drawText(first, topLeft = Offset(left, bottom + 3.dp.toPx()))
        val now = measurer.measure("Now", label)
        drawText(now, topLeft = Offset(size.width - now.size.width, bottom + 3.dp.toPx()))

        // the line from check to check, on to now, with a soft fill below and a dot at each check
        val path = Path().apply {
            moveTo(x(points.first().at), y(points.first().amount!!))
            points.drop(1).forEach { lineTo(x(it.at), y(it.amount!!)) }
            lineTo(size.width, y(points.last().amount!!))
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, bottom); lineTo(x(points.first().at), bottom); close() }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), line.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val dot = if (points.size > 60) 1.5.dp.toPx() else 3.dp.toPx()
        points.forEach { drawCircle(line, dot, Offset(x(it.at), y(it.amount!!))) }
        // the lowest it has been (the latest time, if more than once): ringed
        if (hi - lo >= 0.005) points.last { it.amount!! <= lo }.let { p ->
            drawCircle(line, 5.dp.toPx(), Offset(x(p.at), y(p.amount!!)), style = Stroke(1.5.dp.toPx()))
        }

        // held: a marker at that check, and its price and time above it
        held?.let { i ->
            val p = points[i]
            val px = x(p.at); val py = y(p.amount!!)
            drawLine(line.copy(alpha = 0.5f), Offset(px, top), Offset(px, bottom), 1.dp.toPx())
            drawCircle(line, 5.dp.toPx(), Offset(px, py))
            val text = measurer.measure("${formatPrice(p.amount, currency)} · ${smartDate(p.at)}", tip)
            val pad = 6.dp.toPx()
            val bw = text.size.width + pad * 2; val bh = text.size.height + pad
            val bx = (px - bw / 2).coerceIn(0f, size.width - bw)
            val by = if (py - bh - 8.dp.toPx() > 0f) py - bh - 8.dp.toPx() else py + 8.dp.toPx()
            drawRoundRect(tipBg, Offset(bx, by), Size(bw, bh), CornerRadius(8.dp.toPx()))
            drawText(text, topLeft = Offset(bx + pad, by + pad / 2))
        }
    }
}

/** Room on the left for the price labels. */
private val CHART_LEFT = 44.dp

/** "$39" or "$39.99" ("39 €", "39,99 €"), short enough for the graph's side: whole amounts lose their cents. */
private fun compactPrice(p: Double, currency: String): String = if (p % 1.0 != 0.0) formatPrice(p, currency) else runCatching {
    java.text.NumberFormat.getCurrencyInstance().apply { this.currency = java.util.Currency.getInstance(currency); maximumFractionDigits = 0 }.format(p)
}.getOrDefault(formatPrice(p, currency))

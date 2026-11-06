// Shared list widgets: drag-to-reorder lists, swipe-to-reveal rows, and a small cached image loader.
package com.jlees.mibrowser.ui.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Fixed-slot drag-to-reorder container (grid when columns > 1, horizontal row when [horizontal]).
 * Long-press [longPressMs] starts dragging; [onDragEnd] receives the pointer position in window px.
 */
@Composable
fun <T> SortableSlots(
    items: List<T>,
    key: (T) -> String,
    slotW: Dp,
    slotH: Dp,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    horizontal: Boolean = false,
    longPressMs: Long = 200,
    dragScale: Float = 1.02f,
    dragShadow: Boolean = true,
    enabled: Boolean = true,
    onReorder: (Int, Int) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: (T, Offset) -> Unit = { _, _ -> },
    content: @Composable (T) -> Unit,
) {
    val d = LocalDensity.current
    val wPx = with(d) { slotW.toPx() }
    val hPx = with(d) { slotH.toPx() }
    val keys = items.map(key)
    val order = remember(keys) { mutableStateMapOf<String, Int>().apply { keys.forEachIndexed { i, k -> put(k, i) } } }
    var dragging by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHaptics.current
    val count = items.size
    fun slot(i: Int) = if (horizontal) Offset(i * wPx, 0f) else Offset((i % columns) * wPx, (i / columns) * hPx)
    val rows = if (horizontal) 1 else (count + columns - 1) / columns
    val width = if (horizontal) slotW * count else slotW * columns
    Box(modifier.size(width, slotH * rows)) {
        items.forEachIndexed { index, item ->
            val k = key(item)
            key(k) {
                val scope = rememberCoroutineScope()
                val pos = remember { Animatable(slot(index), Offset.VectorConverter) }
                var drag by remember { mutableStateOf(Offset.Zero) }
                var windowOrigin by remember { mutableStateOf(Offset.Zero) }
                val isDragging = dragging == k
                val target = slot(order[k] ?: index)
                LaunchedEffect(target, isDragging) { if (!isDragging) pos.animateTo(target, Motion.spatial()) }
                val scale by animateFloatAsState(if (isDragging) dragScale else 1f, Motion.fastSpatial(), label = "scale")
                Box(
                    Modifier
                        .offset { IntOffset((pos.value.x + drag.x).roundToInt(), (pos.value.y + drag.y).roundToInt()) }
                        .size(slotW, slotH)
                        .zIndex(if (isDragging) 100f else 1f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .then(if (isDragging && dragShadow) Modifier.shadow(8.dp, RoundedCornerShape(LocalUi.current.radius.dp)) else Modifier)
                        .onGloballyPositioned { windowOrigin = it.positionInWindow() - Offset(pos.value.x + drag.x, pos.value.y + drag.y) }
                        .pointerInput(k, enabled, count) {
                            if (!enabled) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // raw screen position of the touch-down (the dragged item moves under the finger)
                                val downMe = currentEvent.motionEvent
                                val origin = if (downMe != null) Offset(downMe.rawX, downMe.rawY) else null
                                // wait for the long-press delay; abort if the finger moves or lifts first
                                val held = withTimeoutOrNull(longPressMs) {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                        if (!c.pressed) return@withTimeoutOrNull false
                                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                                    }
                                    @Suppress("UNREACHABLE_CODE") false
                                }
                                if (held != null) return@awaitEachGesture
                                val startOrder = order[k] ?: index
                                dragging = k
                                haptics?.longPress() // picked up
                                onDragStart()
                                var total = Offset.Zero
                                var last = down.position
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) { last = c.position; break }
                                    val me = e.motionEvent
                                    total = if (me != null && origin != null) Offset(me.rawX, me.rawY) - origin else total + c.positionChange()
                                    last = c.position
                                    c.consume()
                                    drag = total
                                    val start = slot(startOrder)
                                    val x = start.x + total.x
                                    val y = start.y + total.y
                                    val newOrder = if (horizontal) (x / wPx).roundToInt()
                                    else (y / hPx).roundToInt().coerceAtLeast(0) * columns + (x / wPx).roundToInt().coerceIn(0, columns - 1)
                                    val safe = newOrder.coerceIn(0, count - 1)
                                    val cur = order[k] ?: index
                                    if (safe != cur) {
                                        for ((kk, v) in order.toMap()) {
                                            if (cur < safe && v in (cur + 1)..safe) order[kk] = v - 1
                                            else if (cur > safe && v in safe until cur) order[kk] = v + 1
                                        }
                                        order[k] = safe
                                    }
                                }
                                val to = order[k] ?: index
                                val visual = pos.value + drag
                                val endWindow = windowOrigin + visual + last
                                drag = Offset.Zero
                                scope.launch { pos.snapTo(visual); dragging = null; pos.animateTo(slot(to), Motion.spatial()) }
                                if (to != index) onReorder(index, to)
                                onDragEnd(item, endWindow)
                            }
                        },
                ) { content(item) }
            }
        }
    }
}

// ---------------------------------------------------------------- images

private val imageCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private const val MAX_IMAGE_PX = 2048

/** An already-loaded image (e.g. a favicon), as an Android bitmap. */
/** Seeds the image cache so a just-captured preview shows on the next frame, before its file is written. */
fun cacheImage(key: String, img: ImageBitmap) { imageCache.put(key, img) }

fun cachedImage(src: String?) = src?.let { imageCache.get(it) }

/** Clear Browsing Data: drop every decoded image held in memory (page snapshots, site icons). */
fun clearImageCache() = imageCache.evictAll()

/** Loads http(s) URLs or local file paths, downsampling big images (backgrounds). */
@Composable
fun rememberImage(src: String?): State<ImageBitmap?> {
    val state = remember(src) { mutableStateOf(src?.let { imageCache.get(it) }) }
    var failed by remember(src) { mutableStateOf(false) }
    LaunchedEffect(src) {
        if (src == null || state.value != null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            try {
                if (src.startsWith("favicon")) return@withContext com.jlees.mibrowser.data.Favicons.load(src)?.asImageBitmap()
                val bytes = if (src.startsWith("http")) {
                    (URL(src).openConnection() as HttpURLConnection).run {
                        connectTimeout = 10000; readTimeout = 10000
                        inputStream.use { it.readBytes() }
                    }
                } else File(src.removePrefix("file://")).readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > MAX_IMAGE_PX || bounds.outHeight / sample > MAX_IMAGE_PX) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            } catch (_: Exception) { null }
        }
        if (bmp != null) { imageCache.put(src, bmp); state.value = bmp } else failed = true
    }
    return state
}

/** Image with a fallback while it loads or if it fails. */
@Composable
fun NetImage(
    src: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, alpha: Float = 1f,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null, alignmentTop: Boolean = false, fallback: @Composable () -> Unit = {},
) {
    val img by rememberImage(src)
    val b = img
    if (b != null) Image(b, null, modifier, alignment = if (alignmentTop) Alignment.TopCenter else Alignment.Center, contentScale = contentScale, alpha = alpha, colorFilter = colorFilter)
    else fallback()
}

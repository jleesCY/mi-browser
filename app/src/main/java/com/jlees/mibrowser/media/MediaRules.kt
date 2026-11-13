// Decisions for background playback and picture-in-picture: plain functions (tested in LogicTest).
package com.jlees.mibrowser.media

/** What a frame reports about the audio or video it follows (assets/js/media.js). [duration] is -1 for live streams. */
data class PageMedia(
    val token: String, val playing: Boolean, val muted: Boolean, val duration: Double,
    val width: Int, val height: Int, val title: String, val artist: String,
    val position: Double = 0.0, val rate: Double = 1.0,
)

/** Media worth a notification and background playback: audible, and longer than a short clip or sound effect. */
fun isSessionMedia(m: PageMedia) = !m.muted && (m.duration < 0 || m.duration >= 5)

/** Whether tab [id]'s page keeps running: the visible tab while mi is on screen, and the tab playing media always. */
fun keepsRunning(id: String, activeId: String?, appVisible: Boolean, playingTab: String?) =
    (appVisible && id == activeId) || id == playingTab

/** The picture-in-picture window's shape for a [w]×[h] video: Android allows 1:2.39 to 2.39:1; unknown size → 16:9. */
fun pipAspect(w: Int, h: Int): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return 16 to 9
    val r = w.toDouble() / h
    return when { r > 2.39 -> 239 to 100; r < 1 / 2.39 -> 100 to 239; else -> w to h }
}

// Accent seed colors. Color schemes are generated from the accent seed (Material 3).
package com.jlees.mibrowser.logic

/** accentColor value meaning "use wallpaper colors" (Material You dynamic color, Android 12+). */
const val DYNAMIC_ACCENT = "dynamic"

/** Seed colors offered in Settings (M3 schemes are generated from the seed). "Adaptive" = wallpaper colors. */
const val GRAY_ACCENT = "#8E8E93"
val ACCENTS = listOf(
    GRAY_ACCENT, // gray (monochrome scheme)
    "#007AFF",   // blue
    "#34C759",   // green
    "#FF3B30",   // red
    "#FF9500",   // orange
    "#FFCC00",   // yellow
    "#AF52DE",   // purple
)

/** "#RRGGBB" → ARGB long, default blue when invalid. */
fun hexColor(hex: String?): Long {
    val h = hex?.removePrefix("#") ?: return 0xFF007AFF
    return h.toLongOrNull(16)?.takeIf { h.length == 6 }?.let { 0xFF000000 or it } ?: 0xFF007AFF
}

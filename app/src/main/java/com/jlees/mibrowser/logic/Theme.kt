// port of legacy/src/constants.ts COLORS/ACCENTS and legacy/src/utils.ts generateAdaptiveTheme
package com.jlees.mibrowser.logic

data class Palette(
    val bg: Long, val surface: Long, val glass: Long, val text: Long, val textSec: Long,
    val card: Long, val sheetHeader: Long, val inputBg: Long, val placeholder: Long, val isDark: Boolean,
)

val LIGHT = Palette(0xFFE5E5EA, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF666666, 0xFFF0F0F0, 0xFFF2F2F7, 0xFFF2F2F7, 0xFF999999, false)
val DARK = Palette(0xFF000000, 0xFF1C1C1E, 0xFF1C1C1E, 0xFFFFFFFF, 0xFF888888, 0xFF2C2C2E, 0xFF252527, 0xFF2C2C2E, 0xFFAAAAAA, true)
private val FALLBACK = Palette(0xFF000000, 0xFF1C1C1E, 0xFF1C1C1E, 0xFFFFFFFF, 0xFF888888, 0xFF2C2C2E, 0xFF252527, 0xFF2C2C2E, 0xFFAAAAAA, true)

val ACCENTS = listOf(
    "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#AF52DE",
    "#FF8E86", "#FFC565", "#FFF060", "#69E6B5", "#75D6FF", "#E4A9FF",
    "#8A0F0F", "#8F4D00", "#946C00", "#156625", "#002E99", "#40136E",
)

/** "#RRGGBB" → ARGB long, default blue when invalid. */
fun hexColor(hex: String?): Long {
    val h = hex?.removePrefix("#") ?: return 0xFF007AFF
    return h.toLongOrNull(16)?.takeIf { h.length == 6 }?.let { 0xFF000000 or it } ?: 0xFF007AFF
}

fun adaptivePalette(accentHex: String): Palette {
    val safe = if (accentHex.startsWith("#") && accentHex.length == 7) accentHex else "#007AFF"
    val r = safe.substring(1, 3).toIntOrNull(16)
    val g = safe.substring(3, 5).toIntOrNull(16)
    val b = safe.substring(5, 7).toIntOrNull(16)
    if (r == null || g == null || b == null) return FALLBACK
    fun mix(base: Int, ch: Int, s: Double) = kotlin.math.floor(base * (1 - s) + ch * s).toInt()
    fun rgb(base: Int, s: Double) = Triple(mix(base, r, s), mix(base, g, s), mix(base, b, s))
    fun argb(t: Triple<Int, Int, Int>) = 0xFF000000 or (t.first.toLong() shl 16) or (t.second.toLong() shl 8) or t.third.toLong()
    val bg = rgb(10, 0.15)
    val luminance = 0.299 * bg.first + 0.587 * bg.second + 0.114 * bg.third
    val card = argb(rgb(45, 0.2))
    return Palette(
        bg = argb(bg), surface = argb(rgb(25, 0.2)), glass = argb(rgb(30, 0.15)),
        text = 0xFFEAEAEA, textSec = 0xFFAAAAAA, card = card, sheetHeader = card,
        inputBg = argb(rgb(50, 0.15)), placeholder = 0xFF888888, isDark = luminance < 128,
    )
}

fun paletteFor(themeMode: String, accent: String) = when (themeMode) {
    "light" -> LIGHT
    "dark" -> DARK
    else -> adaptivePalette(accent)
}

// Shared UI primitives: Material 3 Expressive theme glue (color roles, Google Sans Flex, Material Symbols, motion).
package com.jlees.mibrowser.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background

import androidx.compose.ui.Alignment

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.drawBehind

import androidx.compose.foundation.layout.padding


import androidx.compose.foundation.interaction.MutableInteractionSource

import com.jlees.mibrowser.data.Spacing

import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.R
import com.jlees.mibrowser.data.AppFont

/** Google Sans Flex (M3 Expressive typeface). The heaviest weight uses the rounded (ROND) axis for display text. */
private fun gsf(w: Int, rond: Float = 0f) = Font(
    R.font.google_sans_flex, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("ROND", rond)),
)

private val SansFlexStandard = FontFamily(gsf(400), gsf(500), gsf(600), gsf(700), gsf(800, 100f))
private val SansFlexRounded = FontFamily(gsf(400, 100f), gsf(500, 100f), gsf(600, 100f), gsf(700, 100f), gsf(800, 100f))

/** Google Sans Flex; [rounded] turns on the ROND axis (rounded stroke ends) for every weight. */
fun sansFlex(rounded: Boolean) = if (rounded) SansFlexRounded else SansFlexStandard

/** The family for the user's Font setting: SYSTEM is the phone's own font. */
fun appFont(font: AppFont) = when (font) {
    AppFont.STANDARD -> SansFlexStandard
    AppFont.ROUNDED -> SansFlexRounded
    AppFont.SYSTEM -> FontFamily.Default
}

private val typographyCache = HashMap<AppFont, androidx.compose.material3.Typography>()

/** The M3 type scale set in the user's font. */
fun appTypography(font: AppFont) = typographyCache.getOrPut(font) { androidx.compose.material3.Typography().run {
    val family = appFont(font)
    fun TextStyle.f() = copy(fontFamily = family)
    copy(
        displayLarge.f(), displayMedium.f(), displaySmall.f(), headlineLarge.f(), headlineMedium.f(), headlineSmall.f(),
        titleLarge.f(), titleMedium.f(), titleSmall.f(), bodyLarge.f(), bodyMedium.f(), bodySmall.f(),
        labelLarge.f(), labelMedium.f(), labelSmall.f(),
    )
} }

/** Material 3 Expressive motion scheme springs (MotionScheme.expressive()). */
object Motion {
    /** Page ⇄ tab switcher: quick and settles fast (no long spring tail). */
    val switcher = spring(dampingRatio = 0.9f, stiffness = 900f, visibilityThreshold = 0.001f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f) // pill swap, sheet open
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)     // settles, resets
    fun <T> noBounce(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 600f)      // settles without overshoot (the Pill growing, the switcher's search)
    fun <T> playful(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 300f)     // fidget logo
    fun <T> springBack(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.45f, stiffness = 500f) // the Pill settling after a drag
}

/** App-wide styling inputs: the M3 color scheme plus the user's shape/size/density/weight settings. */
@Immutable
data class UiConfig(
    val cs: ColorScheme = darkColorScheme(),
    val isDark: Boolean = true,
    val radius: Int = 28,
    val fontScale: Float = 1f,
    val padding: Spacing = Spacing.NORMAL,
    val font: FontFamily = sansFlex(false),
    /** Pure Black is on and showing: backgrounds that fill the screen are true black. */
    val pureBlack: Boolean = false,
    val listStyle: com.jlees.mibrowser.data.ListStyle = com.jlees.mibrowser.data.ListStyle.CARDS,
) {
    // Type weights
    val regular get() = FontWeight.W400
    val semibold get() = FontWeight.W500
    val bold get() = FontWeight.W600
    val extrabold get() = FontWeight.W800
}

val LocalUi = staticCompositionLocalOf { UiConfig() }

/**
 * Whether one-line text that doesn't fit pans here ([panIfLong]) or ends in "…". Each place that may pan (Settings, the
 * tab menu, the Library's pages, History, the Pill's suggestions) provides it from Look & Feel › Pan Overflowed Text.
 */
val LocalPan = compositionLocalOf { false }

/**
 * Whether a list's rows pan (Bookmarks, Downloads, Following, History each have their own switch). The page around a
 * list (its menus, pop-ups, settings) pans as Settings does ([LocalPan]); only its rows ([ListRowPan]) use this.
 */
val LocalListPan = compositionLocalOf { false }

/** A list row's text pans as its list's switch says ([LocalListPan]), not as the page around it. */
@Composable
fun ListRowPan(content: @Composable () -> Unit) = CompositionLocalProvider(LocalPan provides LocalListPan.current, content = content)

/**
 * One-line text that doesn't fit pans to show the rest instead of ending in "…": it waits at the start, slides slowly to
 * the end, carries on round to the start, and waits again. Text that fits stays still (nothing runs).
 */
fun Modifier.panIfLong(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 2000,
    spacing = MarqueeSpacing.fractionOfContainer(1f / 4f), velocity = 30.dp,
)

/** Text in mi's font and sizes; with [maxLines] 1, text too long for its line pans where that's on ([LocalPan]), else ends in "…". */
@Composable
fun Txt(
    text: String,
    size: Float,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    scaled: Boolean = true,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    underline: Boolean = false,
    /** Crossed out (an old price). */
    strike: Boolean = false,
    font: FontFamily? = null,
    /** False: never pans, even where panning is on (web addresses: the site is what matters, so "…" is fine). */
    pan: Boolean = true,
) {
    val fs = if (scaled) size * LocalUi.current.fontScale else size
    val oneLine = maxLines == 1 && LocalPan.current && pan
    BasicText(
        text = text,
        modifier = if (oneLine) modifier.panIfLong() else modifier,
        maxLines = maxLines,
        softWrap = !oneLine,
        overflow = if (oneLine) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = fs.sp, fontFamily = font ?: LocalUi.current.font, fontWeight = weight,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
            letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
            textDecoration = when { underline -> TextDecoration.Underline; strike -> TextDecoration.LineThrough; else -> null },
        ),
    )
}

/** Material Symbols Rounded icon, sized in dp. */
@Composable
fun Sym(name: String, size: Float, color: Color, modifier: Modifier = Modifier, label: String? = null) {
    val res = SymbolRes[name] ?: return
    // [label]: what a screen reader says for an icon-only control; icons beside their own text stay silent
    Icon(painterResource(res), label, modifier.size(size.dp), tint = color)
}

/** Clickable with the Material ripple. Clip the modifier first for a shaped ripple. */
fun Modifier.touchable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = ripple(), enabled = enabled, onClick = onClick)

/** M3 switch with the expressive check thumb icon. */
@Composable
fun MiSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        thumbContent = if (checked) { { Sym("check", 16f, MaterialTheme.colorScheme.onPrimaryContainer) } } else null,
        colors = SwitchDefaults.colors(),
    )
}

/** Spacing-mode helpers shared by tabs/history rows. */
fun rowHeight(padding: Spacing, fontScale: Float): Float = fontScale * when (padding) { Spacing.COMPACT -> 48f; Spacing.AIRY -> 72f; Spacing.NORMAL -> 58f }

fun rowMargin(padding: Spacing): Float = when (padding) { Spacing.COMPACT -> 8f; Spacing.AIRY -> 25f; Spacing.NORMAL -> 15f }

/** The shape of a row in the Library lists (bookmarks, history, downloads), so all three look alike. */
@androidx.compose.runtime.Composable
fun libraryRowShape() = androidx.compose.foundation.shape.RoundedCornerShape(LocalUi.current.radius.dp)

/** Look & Feel › List Style is Table: History, Downloads and Following show as a table of rows, not separate cards. */
val UiConfig.tableLists get() = listStyle == com.jlees.mibrowser.data.ListStyle.TABLE

/**
 * A row of History, Downloads or Following: its own rounded card (List Style: Cards), or (Table) flat on the page, edge
 * to edge, with a thin line under it, flush with the rows around it. Bookmarks keep their cards (folders need the
 * structure). Swiping a table row reddens the whole row, rounded by the Corners setting like the cards.
 */
@Composable
fun Modifier.listRow(): Modifier {
    val ui = LocalUi.current
    if (!ui.tableLists) return clip(libraryRowShape()).background(ui.cs.surfaceContainerHighest)
    val line = ui.cs.outlineVariant.copy(alpha = 0.6f)
    // edge to edge, the line too; the row's content comes in a little more, in line with the date headers
    return drawBehind { drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f) }
        .padding(horizontal = 5.dp)
}

/** The space between those rows: List Spacing's in Cards, none in Table (they meet at their lines). */
@Composable
fun listGap(): Float = LocalUi.current.let { if (it.tableLists) 0f else rowMargin(it.padding) }

/** The space either side of those lists: the cards sit in from the edges; a table runs edge to edge. */
@Composable
fun listSides() = if (LocalUi.current.tableLists) 0.dp else 20.dp

/** What else is in those lists (date headers, a choice, notes) keeps the cards' margin in a table, off the screen's edges. */
@Composable
fun Modifier.listInset(): Modifier = if (LocalUi.current.tableLists) padding(horizontal = 20.dp) else this

/** The Pill's outer bar (and every Pill-shaped bar copying it: the sheets' search Pill, the guide's). */
val UiConfig.pillBar: Color get() = if (isDark) cs.surfaceContainerHigh else androidx.compose.ui.graphics.lerp(cs.surfaceContainerHighest, cs.primaryContainer, 0.45f)
/** The lighter field inside the Pill's bar (the search field, the find field). */
val UiConfig.pillField: Color get() = if (isDark) cs.surfaceContainerHighest else androidx.compose.ui.graphics.lerp(cs.surfaceDim, cs.primaryContainer, 0.4f)

/** A row tile's fill: a step darker than the row, but not Pure Black's black (a hole in a grey row). */
@androidx.compose.runtime.Composable
fun rowTileColor() = LocalUi.current.let { if (it.pureBlack) it.cs.surfaceContainer else it.cs.surfaceContainerLow }

/** A Library row's leading tile: a 44dp circle (a folder passes its own [shape] and colour) holding an icon. */
@androidx.compose.runtime.Composable
fun RowIconTile(
    modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.CircleShape,
    color: Color = rowTileColor(), content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) = androidx.compose.foundation.layout.Box(
    modifier.padding(end = 15.dp).size(44.dp).clip(shape).background(color),
    contentAlignment = Alignment.Center, content = content,
)

// ---- animation without recomposition: these read their value during layout only, so an animating value re-lays-out
// the element each frame instead of recomposing (re-running) everything inside it.

/** Height in dp from an animated value. */
fun Modifier.heightDp(dp: () -> Float) = layout { m, c ->
    val h = dp().coerceAtLeast(0f).dp.roundToPx()
    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
    layout(p.width, h) { p.place(0, 0) }
}

/** Padding in dp from animated values. */
fun Modifier.paddingDp(start: () -> Float = { 0f }, top: () -> Float = { 0f }, end: () -> Float = { 0f }, bottom: () -> Float = { 0f }) = layout { m, c ->
    val s = start().dp.roundToPx(); val t = top().dp.roundToPx(); val e = end().dp.roundToPx(); val b = bottom().dp.roundToPx()
    val p = m.measure(c.offset(-(s + e), -(t + b)))
    layout(c.constrainWidth(p.width + s + e), c.constrainHeight(p.height + t + b)) { p.place(s, t) }
}

/** Full-screen catcher that swallows touches (TouchableWithoutFeedback backdrop). */
fun Modifier.blockTouches(onTap: () -> Unit = {}) = this.clickable(MutableInteractionSource(), null, onClick = onTap)

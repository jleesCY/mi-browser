// Shared UI primitives: Material 3 Expressive theme glue (color roles, Google Sans Flex, Material Symbols, motion).
package com.jlees.mibrowser.ui.common

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

/** Google Sans Flex (M3 Expressive typeface). The heaviest weight uses the rounded (ROND) axis for display text. */
private fun gsf(w: Int, rond: Float = 0f) = Font(
    R.font.google_sans_flex, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("ROND", rond)),
)

private val SansFlexStandard = FontFamily(gsf(400), gsf(500), gsf(600), gsf(700), gsf(800, 100f))
private val SansFlexRounded = FontFamily(gsf(400, 100f), gsf(500, 100f), gsf(600, 100f), gsf(700, 100f), gsf(800, 100f))

/** Google Sans Flex; [rounded] turns on the ROND axis (rounded stroke ends) for every weight. */
fun sansFlex(rounded: Boolean) = if (rounded) SansFlexRounded else SansFlexStandard

private val typographyCache = HashMap<Boolean, androidx.compose.material3.Typography>()

/** M3 type scale set in Google Sans Flex. */
fun sansFlexTypography(rounded: Boolean) = typographyCache.getOrPut(rounded) { androidx.compose.material3.Typography().run {
    val family = sansFlex(rounded)
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
    fun <T> noBounce(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 600f)      // drawer: never past max
    fun <T> playful(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 300f)     // fidget logo
}

/** App-wide styling inputs: the M3 color scheme plus the user's shape/size/density/weight settings. */
@Immutable
data class UiConfig(
    val cs: ColorScheme = darkColorScheme(),
    val isDark: Boolean = true,
    val radius: Int = 28,
    val fontScale: Float = 1f,
    val padding: String = "normal",
    val font: FontFamily = sansFlex(false),
) {
    // Type weights
    val regular get() = FontWeight.W400
    val semibold get() = FontWeight.W500
    val bold get() = FontWeight.W600
    val extrabold get() = FontWeight.W800
}

val LocalUi = staticCompositionLocalOf { UiConfig() }

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
    font: FontFamily? = null,
) {
    val fs = if (scaled) size * LocalUi.current.fontScale else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = fs.sp, fontFamily = font ?: LocalUi.current.font, fontWeight = weight,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
            letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
            textDecoration = if (underline) TextDecoration.Underline else null,
        ),
    )
}

/** Material Symbols Rounded icon, sized in dp. */
@Composable
fun Sym(name: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    val res = SymbolRes[name] ?: return
    Icon(painterResource(res), null, modifier.size(size.dp), tint = color)
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
fun rowHeight(kind: String, padding: String, fontScale: Float): Float = fontScale * when (kind) {
    "tab" -> when (padding) { "compact" -> 64f; "airy" -> 88f; else -> 74f }
    else -> when (padding) { "compact" -> 48f; "airy" -> 72f; else -> 58f }
}

fun rowMargin(padding: String): Float = when (padding) { "compact" -> 8f; "airy" -> 25f; else -> 15f }


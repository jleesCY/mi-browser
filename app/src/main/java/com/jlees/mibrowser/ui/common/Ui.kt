// Shared UI primitives. Replaces legacy/src/design-system/tokens.ts + RN Text/TouchableOpacity/Switch/Ionicons.
package com.jlees.mibrowser.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.R
import com.jlees.mibrowser.logic.DARK
import com.jlees.mibrowser.logic.Palette

val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.W400),
    Font(R.font.nunito_600, FontWeight.W600),
    Font(R.font.nunito_700, FontWeight.W700),
    Font(R.font.nunito_800, FontWeight.W800),
)
private val Ionicons = FontFamily(Font(R.font.ionicons))

fun c(argb: Long) = Color(argb)

val Red = Color(0xFFFF3B30)

/** Everything the RN components received as theme/accentColor/cornerRadius/fontScale/uiPadding props. */
@Immutable
data class UiConfig(
    val p: Palette = DARK,
    val accent: Color = Color(0xFF007AFF),
    val radius: Int = 22,
    val fontScale: Float = 1f,
    val padding: String = "normal",
    val fontWeight: String = "normal",
) {
    val bg get() = c(p.bg)
    val surface get() = c(p.surface)
    val glass get() = c(p.glass)
    val text get() = c(p.text)
    val textSec get() = c(p.textSec)
    val card get() = c(p.card)
    val sheetHeader get() = c(p.sheetHeader)
    val inputBg get() = c(p.inputBg)

    // theme.fonts.* mapping (useBrowserSettings effectiveTheme)
    val regular get() = when (fontWeight) { "bold" -> FontWeight.W600; else -> FontWeight.W400 }
    val semibold get() = when (fontWeight) { "bold" -> FontWeight.W700; "light" -> FontWeight.W400; else -> FontWeight.W600 }
    val bold get() = when (fontWeight) { "bold" -> FontWeight.W800; "light" -> FontWeight.W600; else -> FontWeight.W700 }
    val extrabold get() = when (fontWeight) { "light" -> FontWeight.W700; else -> FontWeight.W800 }
}

val LocalUi = staticCompositionLocalOf { UiConfig() }

/** RN <Text>: Nunito, size in sp (scaled by the app font-scale setting only when [scaled]). */
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
) {
    val fs = if (scaled) size * LocalUi.current.fontScale else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color, fontSize = fs.sp, fontFamily = Nunito, fontWeight = weight,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            letterSpacing = letterSpacing?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            textDecoration = if (underline) TextDecoration.Underline else null,
        ),
    )
}

/** Ionicons glyph, sized in dp like @expo/vector-icons. */
@Composable
fun Ion(name: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    val glyph = IonGlyphs[name] ?: return
    val fontSize = with(LocalDensity.current) { size.dp.toSp() }
    BasicText(
        text = String(Character.toChars(glyph)),
        modifier = modifier,
        style = TextStyle(
            color = color, fontFamily = Ionicons, fontSize = fontSize, lineHeight = fontSize,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
    )
}

/** TouchableOpacity: fades to [activeOpacity] while pressed, no ripple. */
fun Modifier.touchable(enabled: Boolean = true, activeOpacity: Float = 0.2f, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = OpacityIndication(activeOpacity), enabled = enabled, onClick = onClick)

private data class OpacityIndication(val active: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = OpacityNode(interactionSource, active)
}

private class OpacityNode(val source: InteractionSource, val active: Float) : Modifier.Node(), DrawModifierNode {
    private var pressed = false
    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> { pressed = true; invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> { pressed = false; invalidateDraw() }
                }
            }
        }
    }
    override fun ContentDrawScope.draw() {
        if (!pressed) { drawContent(); return }
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = active })
        drawContent()
        drawContext.canvas.restore()
    }
}

/** RN shadow/elevation helper. */
fun Modifier.elevated(elevation: Dp, radius: Dp): Modifier =
    if (elevation <= 0.dp) this else shadow(elevation, RoundedCornerShape(radius), clip = false)

/** Android RN <Switch>: thin track + round thumb (track off #767577, on accent; thumb #f4f3f4). */
@Composable
fun MiSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    val accent = LocalUi.current.accent
    val track by animateColorAsState(if (checked) accent else Color(0xFF767577), tween(150), label = "track")
    val x by animateFloatAsState(if (checked) 16f else 0f, tween(150), label = "thumb")
    Box(
        Modifier.size(width = 40.dp, height = 24.dp)
            .then(if (onChange != null) Modifier.clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 3.dp).size(34.dp, 14.dp).clip(RoundedCornerShape(7.dp)).background(track))
        Box(Modifier.offset(x = x.dp).padding(horizontal = 2.dp).size(20.dp).shadow(2.dp, CircleShape).background(Color(0xFFF4F3F4), CircleShape))
    }
}

/** Spacing-mode helpers shared by tabs/history rows. */
fun rowHeight(kind: String, padding: String, fontScale: Float): Float = fontScale * when (kind) {
    "tab" -> when (padding) { "compact" -> 64f; "airy" -> 88f; else -> 74f }
    else -> when (padding) { "compact" -> 48f; "airy" -> 72f; else -> 58f }
}

fun rowMargin(padding: String): Float = when (padding) { "compact" -> 8f; "airy" -> 25f; else -> 15f }

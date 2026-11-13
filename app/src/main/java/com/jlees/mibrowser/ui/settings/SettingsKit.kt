// The pieces Settings is built from: sections and rows, and the row layouts every setting uses. Features provide their
// own sections from these (e.g. adblock/AdBlockSettings.kt), so Settings itself only arranges them.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.data.Choice
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.ui.common.*

/** A setting row: [label] and [keywords] are what search matches; [content] draws it. [header]: see [SettingsHeading]. */
class SettingsRow(val label: String, val keywords: String = "", val header: Boolean = false, val content: @Composable () -> Unit)

/** A heading inside a section (e.g. Gestures › Tab Switcher Pill): the rows after it, to the next heading, are its block. */
fun SettingsHeading(label: String) = SettingsRow(label, header = true) {}

/** A row of a section with the heading it sits under (null before any) and its place in its block. */
internal class PlacedRow(val row: SettingsRow, val heading: String?, val index: Int, val count: Int)

/** [rows] split into their headings' blocks; heading rows come through with index 0 / count 0. */
internal fun placeRows(rows: List<SettingsRow>): List<PlacedRow> {
    val out = ArrayList<PlacedRow>(rows.size)
    var heading: String? = null
    var i = 0
    while (i < rows.size) {
        if (rows[i].header) { heading = rows[i].label; out += PlacedRow(rows[i], heading, 0, 0); i++; continue }
        val end = (i until rows.size).firstOrNull { rows[it].header } ?: rows.size
        for (j in i until end) out += PlacedRow(rows[j], heading, j - i, end - i)
        i = end
    }
    return out
}
/** A section: its own page from the main list ([summary] says what's in it). */
class SettingsSection(val id: String, val title: String, val icon: String, val summary: String, val rows: List<SettingsRow>)

/** A full page opened from a row (it slides in over its section, like a section over the list); [content] fills it. */
class SettingsSubPage(val id: String, val title: String, val icon: String, val content: @Composable () -> Unit)

/** Opens and closes [SettingsSubPage]s; provided by SettingsView to the rows it shows. */
class SettingsNav(val open: (SettingsSubPage) -> Unit, val close: () -> Unit)
val LocalSettingsNav = androidx.compose.runtime.staticCompositionLocalOf { SettingsNav({}, {}) }

// ---------------------------------------------------------------- row building blocks

/**
 * The one row layout every setting uses: icon in a tonal circle, title, optional explanation, optional trailing
 * control, and optional content underneath (choices, slider, swatches). [onClick] makes the whole row the target.
 */
@Composable
fun Item(
    icon: String, title: String, subtitle: String? = null, danger: Boolean = false,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, below: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.touchable(onClick = onClick) else Modifier)
                .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (danger) ui.cs.errorContainer else ui.cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Sym(icon, 22f, if (danger) ui.cs.onErrorContainer else ui.cs.onSecondaryContainer) }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Txt(title, 16f, if (danger) ui.cs.error else ui.cs.onSurface, ui.semibold)
                if (subtitle != null) Txt(subtitle, 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 2.dp), lineHeight = 18f)
            }
            trailing?.invoke()
        }
        if (below != null) Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { below() }
    }
}

@Composable
fun SwitchItem(icon: String, title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) =
    Item(icon, title, subtitle, onClick = { onChange(!value) }, trailing = { MiSwitch(value, null) })

@Composable
fun ChoiceItem(icon: String, title: String, subtitle: String?, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) =
    Item(icon, title, subtitle, below = { ConnectedChoice(options, value, onPick = onPick) })

/** One of an enum's choices, as a connected button group. */
@Composable
inline fun <reified T> ChoiceItem(icon: String, title: String, subtitle: String?, value: T, crossinline onPick: (T) -> Unit) where T : Enum<T>, T : Choice {
    val entries = enumValues<T>()
    ChoiceItem(icon, title, subtitle, entries.map { it.name to it.label }, value.name) { n -> onPick(entries.first { it.name == n }) }
}

/** Stepped slider (0.1 steps within [min, max]) with the value shown as a percentage. */
@Composable
fun SliderItem(icon: String, title: String, subtitle: String?, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val ui = LocalUi.current
    Item(
        icon, title, subtitle,
        trailing = { Txt("${Math.round(value * 100)}%", 14f, ui.cs.onSurfaceVariant, ui.bold, scaled = false) },
        below = {
            val state = rememberSliderState(value, Math.round((max - min) / 0.1f) - 1, trackRange = min..max)
            state.value = value // follows the setting (a reset, a restore)
            Slider(state, onValueChange = { onChange(Math.round(it * 10f) / 10f) })
        },
    )
}

/** A row that opens a short list of options under itself (search engine, shortcut action, clear history). */
@Composable
fun ExpandItem(icon: String, title: String, subtitle: String?, value: String?, danger: Boolean = false, options: @Composable (close: () -> Unit) -> Unit) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Item(icon, title, subtitle, danger = danger, onClick = { open = !open }, trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) Txt(value, 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(end = 4.dp))
                Sym(if (open) "keyboard_arrow_up" else "keyboard_arrow_down", 22f, ui.cs.onSurfaceVariant)
            }
        })
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { options { open = false } }
    }
}

/** An option inside an [ExpandItem], indented to line up with the row's title. */
@Composable
fun OptionRow(label: String, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().touchable(onClick = onClick).heightIn(min = 48.dp).padding(start = 72.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.padding(end = 12.dp)) { leading() }
        Txt(label, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        if (selected) Sym("check", 20f, ui.cs.primary)
    }
}

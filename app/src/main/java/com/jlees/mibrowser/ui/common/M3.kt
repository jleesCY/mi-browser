// Material 3 Expressive building blocks shared across screens.
package com.jlees.mibrowser.ui.common

import com.jlees.mibrowser.search.SearchEngine

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.ui.common.blockTouches

/** M3 dialog container: surfaceContainerHigh, extra-large (28dp) corners, 24dp padding, headline title, end-aligned actions. */
@Composable
fun DialogCard(
    title: String?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalUi.current
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 560.dp).padding(horizontal = 24.dp).blockTouches(),
        shape = MaterialTheme.shapes.extraLarge,
        color = ui.cs.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        // title and actions stay put; the content between them shrinks and scrolls when space is short (keyboard open)
        // text buttons carry their own 48dp touch height, so the actions row sits close and the card's bottom padding is small
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (actions != null) 12.dp else 24.dp)) {
            if (title != null) Txt(title, 24f, ui.cs.onSurface, ui.regular, Modifier.padding(bottom = 16.dp), scaled = false)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            if (actions != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = actions)
        }
    }
}

/** A row of a dialog's list: a [mark] (symbol, badge), a label, and a check on the one that's [on], or a [trailing] control. */
@Composable
fun DialogRow(label: String, on: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, mark: @Composable () -> Unit, trailing: (@Composable () -> Unit)? = null, onPress: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).touchable(onClick = onPress).heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { mark() }
        Txt(label, 15f, color ?: ui.cs.onSurface, ui.semibold, Modifier.padding(start = 16.dp).weight(1f), maxLines = 1)
        if (trailing != null) trailing() else if (on) Sym("check", 20f, ui.cs.primary)
    }
}

/** Dialog text action (M3 dialogs use text buttons); [destructive] uses the error color. */
@Composable
fun DialogAction(text: String, destructive: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = LocalUi.current.cs
    TextButton(onClick, modifier) {
        Text(text, color = if (destructive) cs.error else cs.primary, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

/** A connected group's button [i] of [last]+1: rounded on the outside ends only. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(i: Int, last: Int) = when (i) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/** Expressive connected button group (single choice). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoice(options: List<Pair<String, String>>, value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (v, label) ->
            ToggleButton(
                checked = v == value,
                onCheckedChange = { onPick(v) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = connectedShapes(i, options.lastIndex),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                // a long choice pans where that's on, else ends in "…"
                val pan = LocalPan.current
                Text(label, if (pan) Modifier.panIfLong() else Modifier, maxLines = 1, softWrap = !pan,
                    overflow = if (pan) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Connected icon buttons (single choice) where only the chosen one shows its name, so many choices fit one row (the
 * Library's pages). The chosen one widens to fit its name as the choice changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedIconChoice(options: List<Triple<String, String, String>>, value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (v, label, icon) ->
            val on = v == value
            val weight by androidx.compose.animation.core.animateFloatAsState(if (on) 3f else 1f, Motion.enter(220), label = "choice width")
            ToggleButton(
                checked = on,
                onCheckedChange = { onPick(v) },
                modifier = Modifier.weight(weight).semantics { role = Role.RadioButton },
                shapes = connectedShapes(i, options.lastIndex),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                // the icon names its page for screen readers while the name is hidden
                Sym(icon, 20f, androidx.compose.material3.LocalContentColor.current, label = if (on) null else label)
                androidx.compose.animation.AnimatedVisibility(on, enter = androidx.compose.animation.expandHorizontally(Motion.enter(220)) + androidx.compose.animation.fadeIn(Motion.enter(220)),
                    exit = androidx.compose.animation.shrinkHorizontally(Motion.exit(110)) + androidx.compose.animation.fadeOut(Motion.exit(110))) {
                    Text(label, Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Search engine logo (its favicon). Greyed out unless [active]. */
@Composable
fun EngineIcon(e: SearchEngine, size: Float, active: Boolean = true, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    androidx.compose.foundation.layout.Box(modifier.size(size.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        NetImage(Favicons.src(e.url), Modifier.size(size.dp).alpha(if (active) 1f else 0.55f), colorFilter = if (active) null else grey) {
            Sym("travel_explore", size, if (active) ui.cs.primary else ui.cs.onSurfaceVariant)
        }
    }
}

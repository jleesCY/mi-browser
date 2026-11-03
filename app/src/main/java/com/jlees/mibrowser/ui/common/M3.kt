// Material 3 Expressive building blocks shared across screens.
package com.jlees.mibrowser.ui.common

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.logic.SEARCH_ENGINES
import com.jlees.mibrowser.logic.faviconUrl
import com.jlees.mibrowser.ui.blockTouches

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
        Column(Modifier.padding(24.dp)) {
            if (title != null) Txt(title, 24f, ui.cs.onSurface, ui.regular, Modifier.padding(bottom = 16.dp), scaled = false)
            content()
            if (actions != null) Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = actions)
        }
    }
}

/** Dialog text action (M3 dialogs use text buttons); [destructive] uses the error color. */
@Composable
fun DialogAction(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val cs = LocalUi.current.cs
    TextButton(onClick) {
        Text(text, color = if (destructive) cs.error else cs.primary, style = MaterialTheme.typography.labelLarge)
    }
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
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Search engine logo (its favicon). Greyed out unless [active]. */
@Composable
fun EngineIcon(index: Int, size: Float, active: Boolean = true, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val e = SEARCH_ENGINES.getOrElse(index) { SEARCH_ENGINES[0] }
    val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    androidx.compose.foundation.layout.Box(modifier.size(size.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        NetImage(faviconUrl(e.url), Modifier.size(size.dp).alpha(if (active) 1f else 0.55f), colorFilter = if (active) null else grey) {
            Sym("travel_explore", size, if (active) ui.cs.primary else ui.cs.onSurfaceVariant)
        }
    }
}

// Tab search in the switcher: long-pressing the switcher's Pill (Settings › Gestures) turns it into a search field, like the other pages' search Pills, and the cards narrow to the tabs whose
// title or address matches (matchesTab). The ✕ clears the search, or with nothing typed closes it; the keyboard going
// away (Back, its own key) or the field losing focus ends it too, back to the normal switcher.
package com.jlees.mibrowser.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.SheetSearchField
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.common.Txt

/** The switcher's bar (its Pill and edge buttons) is this tall. */
internal val BAR_HEIGHT = 68.dp
/**
 * What the switcher's Pill shows while searching: the search field, filling the Pill (as tall as its buttons, so the
 * Pill only grows sideways). [onFocus] reports the field gaining or losing focus (the keyboard going away).
 */
@Composable
fun SwitcherSearchBar(query: String, onChange: (String) -> Unit, onClose: () -> Unit, onFocus: (Boolean) -> Unit) {
    val ui = LocalUi.current
    // a field reports "not focused" once as it appears, before it takes focus: only a loss after having it counts
    var hadFocus by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        SheetSearchField(
            query, onChange, autoFocus = true, onFocus = { f -> if (f) hadFocus = true else if (hadFocus) onFocus(false) },
            trailing = { IconButton(onClick = onClose) { Sym("close", 22f, ui.cs.onSurfaceVariant, label = "Close search") } },
        )
    }
}

/** In place of the cards when nothing matches the search: near the top, clear of the keyboard (the cards' space doesn't shrink for it). */
@Composable
fun NoTabsFound(query: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Column(modifier.padding(start = 32.dp, end = 32.dp, top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(ui.cs.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            Sym("tab", 28f, ui.cs.onSurfaceVariant)
        }
        Txt("No tabs found", 20f, ui.cs.onSurface, ui.extrabold, Modifier.padding(top = 14.dp))
        Txt("Nothing open matches \u201c${query.trim()}\u201d", 15f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 6.dp),
            align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// Pieces shared by the Library (bookmarks/history) sheets and the tab switcher.
package com.jlees.mibrowser.ui.common

import androidx.compose.ui.unit.Dp

import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.ui.common.SheetSearchField

/** Search header with ⋮ overflow menu (SearchHeader in TabsView/HistoryView). */
@Composable
fun SearchHeaderWithMenu(
    value: String, onChange: (String) -> Unit, placeholder: String,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().zIndex(1000f).padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
        SheetSearchField(value, onChange, placeholder, trailing = {
            IconButton(onClick = { open = !open }) { Sym("more_vert", 24f, ui.cs.onSurfaceVariant, label = "More options") }
        })
        if (open) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 60.dp).width(IntrinsicSize.Max).widthIn(min = 200.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainer, RoundedCornerShape(16.dp)).padding(vertical = 8.dp, horizontal = 4.dp),
            ) { menu { open = false } }
        }
    }
}

/**
 * A Library page with nothing to show: its icon and a line ("No History Yet", or "No matches" while searching).
 * [top] is the space above it; pages whose list starts lower pass less, so the message sits at the same height on all.
 */
@Composable
fun EmptyState(icon: String, text: String, top: Dp = 50.dp) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(top = top), horizontalAlignment = Alignment.CenterHorizontally) {
        Sym(icon, 50f, ui.cs.onSurfaceVariant)
        Txt(text, 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
    }
}

/** A row in a [SearchHeaderWithMenu] menu: an icon and a label, and [trailing] (e.g. an expand arrow). */
@Composable
fun SheetMenuItem(icon: String, label: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().touchable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 18f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
        Txt(label, 14f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ScrollTopAndFab(kbVisible: Boolean, showScrollTop: Boolean, onScrollTop: () -> Unit, fabs: @Composable () -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp).alpha(if (kbVisible) 0f else 1f),
            horizontalAlignment = Alignment.End,
        ) {
            if (showScrollTop && !kbVisible) {
                SmallFloatingActionButton(onClick = onScrollTop, modifier = Modifier.padding(bottom = 16.dp), containerColor = ui.cs.secondaryContainer) {
                    Sym("arrow_upward", 24f, ui.cs.onSecondaryContainer, label = "Scroll to top")
                }
            }
            if (!kbVisible) fabs()
        }
    }
}

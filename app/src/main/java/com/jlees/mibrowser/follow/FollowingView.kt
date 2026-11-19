// Library › Following: every product you follow, searchable, those that changed since you last looked pinned at the top with a dot.
package com.jlees.mibrowser.follow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.moved
import com.jlees.mibrowser.ui.common.*

@Composable
fun FollowingView(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()
    // one list: what changed since you last looked pinned at the top (with a dot and the change), the rest under it
    val follows = pinnedFirst(vm.follows.follows, vm.follows.updates).filter { q.isEmpty() || it.title.lowercase().contains(q) || displayHost(it.url).contains(q) }
    // opening Following checks whatever is due (the background check may be off, or a shop may only answer mi in front)
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        vm.follows.follows.filter { isDue(it, now) }.map { it.id }.toSet().takeIf { it.isNotEmpty() }?.let { vm.checkNow(it) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sides = listSides()
        // every row the same height (room for the picture), so they can be held and dragged into a new order, as bookmarks are
        val rowH = maxOf(rowHeight(ui.padding, ui.fontScale), 68f)
        val slotW = maxWidth - sides * 2
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = sides, end = sides, top = 12.dp, bottom = SheetPillSpace)) {
            when {
                vm.follows.follows.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState("sell", "Nothing Followed Yet", top = 30.dp)
                    Txt("On a product page, open the tab menu and tap \"Follow Price\". mi alerts you when it changes.", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp), align = TextAlign.Center)
                }
                follows.isEmpty() -> EmptyState("sell", "No matches", top = 30.dp)
                // hold and drag to rearrange (not while searching: the order is of the whole list)
                else -> SortableSlots(
                    items = follows, key = { it.id }, slotW = slotW, slotH = (rowH + listGap()).dp,
                    onReorder = { from, to -> if (q.isEmpty()) vm.follows.reorder(moved(follows, from, to).map { it.id }) },
                ) { f ->
                    // swipe right to rename, left to unfollow (it asks first: the price history goes with it)
                    SwipeActionRow(null, onDelete = { confirmUnfollow(vm, f) }, Modifier, onEdit = { FollowFlow.renaming = f.id }, askDelete = { answer -> confirmUnfollow(vm, f, answer) }) {
                        ProductRow(f, productLine(f, vm.follows.updates), rowH) { FollowFlow.open = f.id }
                    }
                }
            }
        }
        SheetSearchPill(search, { search = it }) { close ->
            SheetMenuItem("refresh", "Check All Now") { close(); vm.checkNow() }
            SheetMenuItem("check", "Mark All Read") { close(); vm.follows.markRead() }
        }
    }
}

@Composable
/** A followed product: picture, name (with a dot when it changed since you last looked) and its line (productLine, as the widget shows it); price, sale and change since followed. */
private fun ProductRow(f: Follow, line: ProductLine, height: Float, onPress: () -> Unit) = ListRowPan {
    val ui = LocalUi.current
    fun color(t: Tone) = when (t) { Tone.PLAIN -> ui.cs.onSurfaceVariant; Tone.GOOD -> ui.cs.primary; Tone.BAD -> ui.cs.error }
    Row(
        Modifier.fillMaxWidth().height(height.dp).listRow()
            .touchable(onClick = onPress).padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(f.image ?: Favicons.src(f.url), 48.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // changed since you last looked: a dot, until you open it (or Mark All Read)
                if (line.changed) Box(Modifier.padding(end = 6.dp).size(8.dp).background(ui.cs.primary, CircleShape))
                Txt(line.title, 15f, ui.cs.onSurface, ui.bold, Modifier.weight(1f, fill = false), maxLines = 1)
            }
            // a change or what needs you pans to be read; a line ending in the site never does
            Txt(line.detail, 12f, color(line.tone), if (line.tone == Tone.PLAIN) ui.semibold else ui.bold, maxLines = 1, pan = line.changed || f.status != FollowStatus.OK)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Txt(line.price, 16f, ui.cs.onSurface, ui.extrabold, maxLines = 1)
            line.off?.let { Txt(it, 12f, ui.cs.primary, ui.bold, maxLines = 1) } // on sale
            line.since?.let { (text, tone) -> Txt(text, 12f, color(tone), ui.bold) }
        }
    }
}

internal fun confirmUnfollow(vm: BrowserViewModel, f: Follow, answer: (Boolean) -> Unit = {}) = vm.showAlert(
    "Unfollow?", "\u201c${f.title.ifBlank { displayHost(f.url) }}\u201d and its price history will be removed.",
    listOf(
        com.jlees.mibrowser.AlertButton("Cancel", com.jlees.mibrowser.ButtonStyle.CANCEL) { answer(false) },
        com.jlees.mibrowser.AlertButton("Unfollow", com.jlees.mibrowser.ButtonStyle.DESTRUCTIVE) { answer(true); vm.unfollow(f.id) },
    ),
    onCancel = { answer(false) },
)

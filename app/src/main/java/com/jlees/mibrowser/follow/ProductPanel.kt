// A followed product's panel: edge to edge, sliding up from the bottom (as History does from the tab switcher). It opens
// as tall as its top part (picture and price history, name, price, stock, when it was last checked); scrolling up grows
// it to the whole screen and brings its settings into view, grouped as Settings shows them. Open Page, Unfollow and Done
// stay pinned at the bottom throughout.
package com.jlees.mibrowser.follow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.SettingsTarget
import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.library.smartDate
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.SegmentedGroup
import com.jlees.mibrowser.ui.settings.SwitchItem
import kotlin.math.abs

@Composable
fun BoxScope.ProductPanel(vm: BrowserViewModel) =
    SlidingPanel(FollowFlow.open?.let { vm.follows[it] }) { f, hidden -> Panel(vm, f, hidden) }

@Composable
private fun BoxScope.Panel(vm: BrowserViewModel, f: Follow, hidden: Float) {
    val o = remember(f.id) { Options(f) }
    val checking = f.id in FollowFlow.checking
    fun close() {
        // its settings apply on closing
        vm.follows.update(f.id) { o.applyTo(this) }
        vm.follows.markRead(f.id)
        FollowFlow.open = null
    }
    ExpandingPanel(f.id, hidden, ::close, top = { Top(vm, f, checking) }, rest = { Settings(vm, f, o, ::close) }) { m -> Bar(vm, f, ::close, m) }
}

/** The picture and the price history side by side; under them the name, price and stock; then when it was last checked. */
@Composable
private fun Top(vm: BrowserViewModel, f: Follow, checking: Boolean) {
    val ui = LocalUi.current
    when (f.status) {
        FollowStatus.CONFIRM -> Notice("This price looks unusual: ${f.pending?.amount?.let { formatPrice(it, f.currency) } ?: "?"}. Is it right?",
            "It's right" to { vm.follows.confirmPending(f.id) }, "Pick again" to { vm.repick(f.id) })
        FollowStatus.NEEDS_ATTENTION -> when (val p = f.problem ?: Problem.NO_PRICE) {
            Problem.NO_PRICE -> Notice(p.detail, "Pick it again" to { vm.repick(f.id) }, null)
            Problem.GONE -> Notice(p.detail, "Check Now" to { vm.checkNow(setOf(f.id)) }, "Unfollow" to { FollowFlow.open = null; confirmUnfollow(vm, f) })
            else -> Notice(p.detail, "Check Now" to { vm.checkNow(setOf(f.id)) }, null)
        }
        FollowStatus.OK -> {}
    }
    Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductImage(f.image ?: Favicons.src(f.url), 120.dp)
        PriceChart(f.history, f.currency, Modifier.padding(start = 12.dp).weight(1f).fillMaxHeight())
    }
    // the lowest and highest it has been, and when
    priceRange(f.history)?.let { (low, high) ->
        Row(Modifier.padding(top = 6.dp).fillMaxWidth()) {
            Txt("Lowest ${formatPrice(low.amount!!, f.currency)} · ${smartDate(low.at).substringBefore(',')}", 12f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.weight(1f), maxLines = 1)
            Txt("Highest ${formatPrice(high.amount!!, f.currency)} · ${smartDate(high.at).substringBefore(',')}", 12f, ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1)
        }
    }
    // the name is yours to change: tap it (the pencil says so)
    Row(Modifier.padding(top = 16.dp).touchable { FollowFlow.renaming = f.id }, verticalAlignment = Alignment.CenterVertically) {
        Txt(f.title.ifBlank { displayHost(f.url) }, 17f, ui.cs.onSurface, ui.bold, Modifier.weight(1f, fill = false), maxLines = 2)
        Sym("edit", 16f, ui.cs.onSurfaceVariant, Modifier.padding(start = 8.dp), label = "Rename")
    }
    Txt(displayHost(f.url).removePrefix("www."), 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1, pan = false)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Txt(f.current?.let { (if (f.startingPrice) "From " else "") + formatPrice(it, f.currency) } ?: "No price", 30f, ui.cs.onSurface, ui.extrabold)
        val change = f.first?.let { first -> f.current?.let { it - first } }
        if (change != null && abs(change) >= 0.005)
            Txt((if (change < 0) "↓ " else "↑ ") + formatPrice(abs(change), f.currency), 14f, if (change < 0) ui.cs.primary else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(start = 10.dp, bottom = 5.dp))
    }
    // on sale: the price the shop shows crossed out beside it
    percentOff(f.was, f.current)?.let { off -> Txt("Was ${formatPrice(f.was!!, f.currency)} · $off% off", 13f, ui.cs.primary, ui.semibold, maxLines = 1) }
    f.inStock?.let { Txt(if (it) "In stock" else "Out of stock", 14f, if (it) ui.cs.primary else ui.cs.error, ui.bold) } // out of stock: always red
    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(if (checking) "Checking…" else "Last Checked ${smartDate(f.lastChecked)}", 13f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.weight(1f), maxLines = 1)
        TextButton({ vm.checkNow(setOf(f.id)) }, enabled = !checking) { Txt("Check Now", 14f, ui.cs.primary, ui.bold) }
    }
}

/** Its settings, grouped and styled as Settings' are: what alerts you and how, then how it's checked. */
@Composable
private fun Settings(vm: BrowserViewModel, f: Follow, o: Options, close: () -> Unit) {
    SegmentedGroup("Alerts", alertRows(o))
    SegmentedGroup("Checking", checkingRows(o) + listOf<@Composable () -> Unit>({
        SwitchItem("pause", "Pause Checking", "Keep its price history but stop checking it", o.paused) { o.paused = it }
    }))
    SegmentedGroup("More", listOf(
        { Item("refresh", "Pick the Price Again", "Tap the price on the page yourself", onClick = { vm.repick(f.id) }) },
        // background checks, and notifications on or off for every product, are in Settings
        { Item("settings", "Following Settings", "Background checks and notifications", onClick = {
            close(); vm.settingsTarget = SettingsTarget("library", "Following", "Background Checks"); vm.openView(Sheet.SETTINGS)
        }) },
    ))
}

/** Pinned at the bottom: Open Page across the whole width, Unfollow and Done side by side under it. */
@Composable
private fun Bar(vm: BrowserViewModel, f: Follow, close: () -> Unit, modifier: Modifier) {
    val ui = LocalUi.current
    Column(modifier.fillMaxWidth().background(ui.cs.surfaceContainerLow).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ close(); vm.openFollowed(f.url) }, Modifier.fillMaxWidth().height(52.dp)) {
            Sym("open_in_new", 18f, ui.cs.onPrimary, Modifier.padding(end = 8.dp)); Txt("Open Page", 16f, ui.cs.onPrimary, ui.bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                { FollowFlow.open = null; confirmUnfollow(vm, f) }, Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = ui.cs.errorContainer, contentColor = ui.cs.onErrorContainer),
            ) { Txt("Unfollow", 15f, ui.cs.onErrorContainer, ui.bold) }
            FilledTonalButton(close, Modifier.weight(1f).height(48.dp)) { Txt("Done", 15f, ui.cs.onSecondaryContainer, ui.bold) }
        }
    }
}

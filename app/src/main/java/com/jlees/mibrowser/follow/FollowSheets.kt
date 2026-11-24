// Following's sheets, over whatever is showing: following a product (confirm its price, set alerts), a followed product
// (its price history, settings and actions), and the banner while you tap the price on the page.
package com.jlees.mibrowser.follow

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.ChoiceItem
import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.OptionRow
import com.jlees.mibrowser.ui.settings.SwitchItem
import com.jlees.mibrowser.ui.settings.SegmentedGroup
import java.net.URL

/** All of Following's sheets and banners; App draws this once, over everything. */
@Composable
fun BoxScope.FollowOverlays(vm: BrowserViewModel) {
    FollowSheet(vm)
    ProductPanel(vm)
    RenameDialog(vm)
    PickBanner()
    // just followed: it's in the Library (View opens it there)
    UndoBar(
        remember(FollowFlow.followed) { FollowFlow.followed?.let { id -> Undo("Price followed", "View") { vm.viewFollowed(id) } } },
        onTimeout = { FollowFlow.followed = null },
        Modifier.align(Alignment.BottomCenter).zIndex(250f).navigationBarsPadding().padding(bottom = 88.dp),
    )
}

/** A follow's own settings (Settings › Following › Notifications can still turn every notification off). */
internal class Options(f: Follow?) {
    var price by mutableStateOf(f?.alerts?.price ?: PriceAlert.DROPS)
    var stockChanges by mutableStateOf(f?.alerts?.stockChanges ?: true)
    var every by mutableStateOf(f?.every ?: CheckEvery.SIX_HOURS)
    var notify by mutableStateOf(f?.notify ?: NotifyMode.NOW)
    var minChange by mutableStateOf(MinChange.of(f?.minChange ?: 0))
    var useLogin by mutableStateOf(f?.useLogin ?: false)
    var paused by mutableStateOf(f?.paused ?: false)
    val alerts get() = Alerts(price, stockChanges)
    fun applyTo(f: Follow) = f.copy(alerts = alerts, every = every, useLogin = useLogin, notify = notify, minChange = minChange.percent, paused = paused)
}

/** A follow's alert settings, as rows (the product panel's Alerts; the follow sheet's More options, after its price choice). */
internal fun alertRows(o: Options): List<@Composable () -> Unit> = listOf(
    { ChoiceItem("trending_down", "Alert me when the price", null, o.price) { o.price = it } },
    { ChoiceItem("percent", "Percentage Change", "The smallest change that alerts you", o.minChange) { o.minChange = it } },
    { SwitchItem("sell", "Alert me when this item's stock changes", null, o.stockChanges) { o.stockChanges = it } },
    { ChoiceItem("notifications", "Alert me", null, o.notify) { o.notify = it } },
)

internal fun checkingRows(o: Options): List<@Composable () -> Unit> = listOf(
    { ChoiceItem("history", "Check the price every", null, o.every) { o.every = it } },
    { SwitchItem("lock", "Use My Login", "For member or local prices", o.useLogin) { o.useLogin = it } },
)

/** The product's picture (fitted, on white, as shops show them), or a price tag while there's none. */
@Composable
fun ProductImage(src: String?, size: Dp) {
    val ui = LocalUi.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 5)).background(if (src != null) Color.White else ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        NetImage(src, Modifier.fillMaxSize().padding(size / 16), contentScale = ContentScale.Fit) {
            Box(Modifier.fillMaxSize().background(ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Sym("sell", size.value * 0.4f, ui.cs.onSurfaceVariant) }
        }
    }
}

/** Picture, name, the page's address and the price, side by side: the top of the follow sheet. */
@Composable
private fun ProductHeader(image: String?, title: String, url: String, price: String, was: String?, inStock: Boolean?, onRename: () -> Unit) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProductImage(image, 76.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            // the name is yours to choose: tap it (the pencil says so), as in the product's panel
            Row(Modifier.touchable(onClick = onRename), verticalAlignment = Alignment.CenterVertically) {
                Txt(title, 14f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f, fill = false), maxLines = 2)
                Sym("edit", 14f, ui.cs.onSurfaceVariant, Modifier.padding(start = 6.dp), label = "Rename")
            }
            Txt(displayHost(url).removePrefix("www."), 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1, pan = false)
            Txt(price, 28f, ui.cs.onSurface, ui.extrabold)
            was?.let { Txt(it, 13f, ui.cs.primary, ui.semibold, maxLines = 1) }
            // its own line, so out of stock is always red (and in stock the accent), whatever the line above says
            inStock?.let { Txt(if (it) "In stock" else "Out of stock", 13f, if (it) ui.cs.primary else ui.cs.error, ui.bold, maxLines = 1) }
        }
    }
}

/**
 * Following a product (tab menu › Follow Price): its own menu, styled like the product's panel and Settings (a panel up
 * from the bottom, grouped cards). The product; the prices found (the likeliest large, a couple of others smaller under
 * it with more on request, each choosable, or pick it yourself); the price alert, with the rest under More Options; and
 * Cancel and Follow pinned at the bottom.
 */
@Composable
private fun BoxScope.FollowSheet(vm: BrowserViewModel) {
    val d = FollowFlow.draft
    // the last one shown stays drawn while the panel slides away
    val last = remember { arrayOfNulls<Draft>(1) }
    if (d != null) last[0] = d
    val slide = remember { Animatable(1f) } // 1: below the screen, 0: in place
    LaunchedEffect(d != null) { slide.animateTo(if (d != null) 0f else 1f, if (d != null) Motion.enter(300) else Motion.exit(180)) }
    LaunchedEffect(d == null) { if (d == null) FollowFlow.renamingDraft = false } // closed mid-rename: not carried to the next one
    val shown = d ?: last[0] ?: return
    if (d == null && slide.value >= 1f) { last[0] = null; return }
    FollowPanel(vm, shown, slide.value)
}

@Composable
private fun BoxScope.FollowPanel(vm: BrowserViewModel, d: Draft, hidden: Float) {
    val ui = LocalUi.current
    // kept while the draft is updated (a sale found around a price), reset for another page
    val o = remember(d.url) { Options(null) }
    var chosen by remember(d.url) { mutableStateOf(0) }
    // the chosen price's surroundings are checked for a sale (an old price, a percentage off)
    LaunchedEffect(d.url, chosen) { vm.checkAround(d, chosen) }
    // alerts need notification permission on Android 13+: asked at the first follow that would notify (Android stops
    // asking after two refusals); the follow goes ahead whatever the answer
    val ctx = LocalContext.current
    val askNotify = rememberNotifyAsk()
    val cancel = { FollowFlow.draft = null }
    BackHandler(FollowFlow.draft != null) { cancel() }

    val choice = d.options[chosen.coerceIn(d.options.indices)]
    val currency = choice.reading.currency ?: "USD"
    val image = (d.image ?: choice.reading.image)?.let { runCatching { URL(URL(d.url), it).toString() }.getOrNull() }

    // behind it, the page dims; a tap there cancels
    Box(Modifier.fillMaxSize().zIndex(300f).graphicsLayer { alpha = 1f - hidden }.background(Color.Black.copy(alpha = 0.5f)).blockTouches { cancel() })
    Column(
        Modifier.align(Alignment.BottomCenter).zIndex(301f).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth().statusBarsPadding().padding(top = 24.dp)
            .graphicsLayer { translationY = hidden * size.height }
            .background(ui.cs.surfaceContainerLow, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp))
            .blockTouches(),
    ) {
        Txt("Follow Price", 20f, ui.cs.onSurface, ui.bold, Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ProductHeader(image, d.title, d.url, choice.reading.amount?.let { formatPrice(it, currency) } ?: "Price not shown",
                percentOff(choice.reading.was, choice.reading.amount)?.let { "Was ${formatPrice(choice.reading.was!!, currency)} · $it% off" }, choice.reading.inStock) { FollowFlow.renamingDraft = true }
            // the prices found, best first: the likeliest large; the others opening under a row, as Settings' choices do
            val rest = d.options.drop(1)
            SegmentedGroup("Price", listOf<@Composable () -> Unit>({ Item("sell", priceText(d.options[0], currency), d.options[0].note, onClick = { chosen = 0 }, titleSize = 22f,
                    trailing = { if (chosen == 0) Sym("check", 20f, ui.cs.primary) }) }) +
                if (rest.isEmpty()) emptyList() else listOf<@Composable () -> Unit>({
                    ExpandItem("sell", "Other Prices Found", "${rest.size} more on this page",
                        value = if (chosen > 0) d.options[chosen].reading.amount?.let { formatPrice(it, currency) } else null) { close ->
                        rest.forEachIndexed { j, opt -> OptionRow(priceText(opt, currency), chosen == j + 1, detail = opt.note, strike = opt.old) { chosen = j + 1; close() } }
                    }
                }))
            SegmentedGroup(null, listOf { Item("refresh", "Pick It Myself", "Tap the price on the page yourself", onClick = { vm.startPick(d.tabId, null, d.image) }) })
            // the one choice up front; the rest opening under More Options, as Settings' rows do
            SegmentedGroup("Alerts", listOf(alertRows(o).first()) + listOf<@Composable () -> Unit>({
                ExpandItem("settings", "More Options", "Percentage change, stock, notifications, checking", value = null) {
                    (alertRows(o).drop(1) + checkingRows(o)).forEach { it() }
                }
            }))
            Spacer(Modifier.height(24.dp))
        }
        // pinned: Cancel and Follow, side by side, the same width
        Row(Modifier.fillMaxWidth().background(ui.cs.surfaceContainerLow).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(cancel, Modifier.weight(1f).height(48.dp)) { Txt("Cancel", 15f, ui.cs.primary, ui.bold) }
            // Follow stands out: filled with the accent color (Cancel stays an outline)
            Button({
                vm.follow(d, choice, o)
                if (o.notify != NotifyMode.OFF && vm.settings.followNotifications) askNotify(false)
            }, Modifier.weight(1f).height(48.dp)) { Txt("Follow", 15f, ui.cs.onPrimary, ui.bold) }
        }
    }
}

/** A price found, written out ("$199.99"). */
private fun priceText(o: PriceOption, currency: String) = o.reading.amount?.let { formatPrice(it, o.reading.currency ?: currency) } ?: "?"

/** Renaming a followed product: its name in your list (checks don't change it back). */
@Composable
private fun RenameDialog(vm: BrowserViewModel) {
    // a followed product, or (from the follow menu) the one about to be followed
    val f = FollowFlow.renaming?.let { vm.follows[it] }
    val draft = FollowFlow.draft?.takeIf { FollowFlow.renamingDraft }
    val current = f?.title ?: draft?.title
    var name by remember(f?.id, draft != null) { mutableStateOf(current.orEmpty()) }
    val close = { FollowFlow.renaming = null; FollowFlow.renamingDraft = false }
    ModalFrame(current != null, close) {
        if (current == null) return@ModalFrame
        DialogCard("Rename", actions = {
            DialogAction("Cancel", onClick = close)
            DialogAction("Save") {
                if (name.isNotBlank()) {
                    if (f != null) vm.follows.update(f.id) { copy(title = name.trim()) }
                    else FollowFlow.draft = FollowFlow.draft?.copy(title = name.trim())
                }
                close()
            }
        }) {
            LabeledField("Name", name, { name = it })
        }
    }
}

@Composable
internal fun Notice(text: String, first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>?) {
    val ui = LocalUi.current
    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().background(ui.cs.errorContainer, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Txt(text, 14f, ui.cs.onErrorContainer, ui.semibold)
        Row {
            TextButton(first.second) { Txt(first.first, 14f, ui.cs.onErrorContainer, ui.bold) }
            second?.let { TextButton(it.second) { Txt(it.first, 14f, ui.cs.onErrorContainer, ui.bold) } }
        }
    }
}

/** While you tap the price on the page: what to do, and Cancel. */
@Composable
private fun BoxScope.PickBanner() {
    val ui = LocalUi.current
    androidx.compose.animation.AnimatedVisibility(
        FollowFlow.picking != null || FollowFlow.repickPending != null, Modifier.align(Alignment.TopCenter).zIndex(200f),
        enter = androidx.compose.animation.slideInVertically(Motion.enter(280)) { -it } + androidx.compose.animation.fadeIn(Motion.enter(200)),
        exit = androidx.compose.animation.slideOutVertically(Motion.exit(160)) { -it } + androidx.compose.animation.fadeOut(Motion.exit(140)),
    ) {
    Row(
        Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()
            .background(ui.cs.inverseSurface, RoundedCornerShape(ui.radius.dp)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("sell", 20f, ui.cs.inverseOnSurface, Modifier.padding(end = 12.dp))
        Txt(if (FollowFlow.repickPending != null) "Loading the page…" else "Tap the price on the page", 15f, ui.cs.inverseOnSurface, ui.semibold, Modifier.weight(1f))
        TextButton(::cancelPick) { Txt("Cancel", 14f, ui.cs.inversePrimary, ui.bold) }
    }
    }
}

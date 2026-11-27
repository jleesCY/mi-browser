// Following, from the browser: follow this page's price, pick the price yourself, check now, unfollow. The sheets'
// state (which one shows) lives in FollowFlow.
package com.jlees.mibrowser.follow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.json
import com.jlees.mibrowser.logic.uniqueId
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A follow being set up: its tab and page, the prices it offers to choose from (best first; see [priceOptions]), and its picture. */
data class Draft(val tabId: String, val url: String, val title: String, val options: List<PriceOption>, val image: String? = null)

/** Choosing the price on tab [tabId]'s page, for a new follow (with the page's [image]) or (with [followId]) to fix an existing one. */
data class Picking(val tabId: String, val followId: String?, val image: String? = null)

/** Which Following sheet or banner shows (app-wide, over the page or the Library). */
object FollowFlow {
    var draft by mutableStateOf<Draft?>(null)
    var picking by mutableStateOf<Picking?>(null)
    /** Just followed (its id): "Price followed · View" shows for a moment. */
    var followed by mutableStateOf<String?>(null)
    /** A product's sheet. */
    var open by mutableStateOf<String?>(null)
    /** Picking again, once its page (opened in a new tab) has loaded. */
    var repickPending by mutableStateOf<Picking?>(null)
    /** Renaming this product (its name in your list). */
    var renaming by mutableStateOf<String?>(null)
    /** Naming the product being followed, before it's followed (the follow menu's pencil). */
    var renamingDraft by mutableStateOf(false)
    /** Check Now running for these. */
    var checking by mutableStateOf(setOf<String>())
}

private val localCurrency get() = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")

/**
 * Tab menu › Follow Price: the prices this page offers (its product data, and the likeliest shown on it), to choose
 * from in the follow sheet; with none, you're offered picking it yourself. A page already followed opens its sheet instead.
 */
fun BrowserViewModel.followThisPage() {
    val tab = tabs.current ?: return
    val url = tab.url ?: return
    follows.following(url)?.let { FollowFlow.open = it.id; return }
    FollowPage.detect(getApplication(), tab.id) { d ->
        val title = d?.product?.title ?: d?.title?.takeIf { it.isNotBlank() } ?: tab.title
        val options = d?.let { priceOptions(it.product, it.candidates, localCurrency, it.stock) }.orEmpty()
        when {
            options.isNotEmpty() -> FollowFlow.draft = Draft(tab.id, url, title, options, d!!.image)
            d == null -> showAlert("Can't Read This Page", "mi couldn't look at this page. Try again once it has finished loading.")
            // nothing that looks like a price (maybe not a product page at all): asked, not dropped into picking
            else -> showAlert("No Price Found", "mi couldn't find a price on this page. If there is one, pick it yourself: tap Pick It, then tap the price.",
                listOf(com.jlees.mibrowser.AlertButton("Cancel", com.jlees.mibrowser.ButtonStyle.CANCEL), com.jlees.mibrowser.AlertButton("Pick It") { startPick(tab.id, null, d.image) }))
        }
    }
}

/** The next tap on the page picks the price: for a new follow (the follow sheet opens with it) or to fix [followId]. */
fun BrowserViewModel.startPick(tabId: String, followId: String?, image: String? = null) {
    FollowFlow.draft = null
    FollowFlow.picking = Picking(tabId, followId, image)
    val started = FollowPage.pick(getApplication(), tabId) { spot ->
        val p = FollowFlow.picking ?: return@pick
        FollowFlow.picking = null
        val extraction = spot.extraction()
        val existing = p.followId?.let { follows[it] }
        val reading = parsePrice(spot.text, existing?.currency ?: localCurrency)
        if (reading == null) {
            showAlert("That's Not a Price", "“${spot.text.take(40)}” doesn't look like a price. Tap Follow Price again to pick another spot.")
            return@pick
        }
        if (existing != null) { // fixed: it counts from now (and is checked again from the new spot)
            follows.update(existing.id) { copy(extraction = extraction, needsRender = true, status = FollowStatus.OK, pending = null) }
            follows.record(existing.id, reading, System.currentTimeMillis(), trusted = true)
            follows.markRead(existing.id)
            FollowFlow.open = existing.id
        } else {
            val tab = tabs.list.firstOrNull { it.id == p.tabId } ?: return@pick
            FollowFlow.draft = Draft(tab.id, tab.url ?: return@pick, tab.title, listOf(PriceOption(reading, extraction, "The price you picked")), p.image)
        }
    }
    if (!started) {
        FollowFlow.picking = null
        showAlert("Can't Pick on This Page", "The page isn't open yet. Try again once it has loaded.")
    }
}

/**
 * The follow sheet's price [i] was chosen: the area around it on the page is checked for a sale (a crossed-out price, a
 * percentage off, an amount saved), and the old price it gives is kept with that price.
 */
fun BrowserViewModel.checkAround(d: Draft, i: Int) {
    val o = d.options.getOrNull(i) ?: return
    val price = o.reading.amount ?: return
    if (o.old) return
    FollowPage.around(getApplication(), d.tabId, o) { near ->
        val cur = o.reading.currency ?: localCurrency
        val was = near?.let { saleFrom(price, parsePrice(it.was, cur)?.amount, it.percent, parsePrice(it.save, cur)?.amount) } ?: return@around
        val now = FollowFlow.draft?.takeIf { it.url == d.url } ?: return@around
        FollowFlow.draft = now.copy(options = now.options.mapIndexed { j, x -> if (j == i) x.copy(reading = x.reading.copy(was = was)) else x })
    }
}

fun cancelPick() { FollowPage.cancelPick(); FollowFlow.picking = null; FollowFlow.repickPending = null }

/** The follow sheet's Follow: the product is followed from the price you chose ([choice]) on, set as [o] (its picture is fetched to keep). */
internal fun BrowserViewModel.follow(d: Draft, choice: PriceOption, o: Options) {
    val now = System.currentTimeMillis()
    val id = uniqueId()
    follows.add(o.applyTo(Follow(
        id = id, url = d.url, title = d.title, currency = choice.reading.currency ?: localCurrency, extraction = choice.extraction,
        followedAt = now, lastChecked = now, history = listOf(PricePoint(now, choice.reading.amount, choice.reading.inStock)),
        needsRender = choice.extraction.from == ReadFrom.SPOT, was = choice.reading.was, startingPrice = choice.reading.startingPrice,
    )))
    FollowFlow.draft = null
    FollowFlow.followed = id
    scheduleChecks()
    (d.image ?: choice.reading.image)?.let { image -> keepImage(id, image, d.url) }
}

/** Fetches follow [id]'s picture to keep on the phone. */
internal fun BrowserViewModel.keepImage(id: String, image: String, pageUrl: String) = viewModelScope.launch {
    val path = withContext(Dispatchers.IO) { FollowImages.save(getApplication(), id, image, pageUrl) }
    if (path != null) { if (follows[id] != null) follows.update(id) { copy(image = path) } else FollowImages.delete(getApplication(), id) } // unfollowed meanwhile
}

/**
 * "Price followed · View": Library › Following with the product open, over the tab switcher (opened at once beneath it),
 * so leaving the Library lands in the switcher, as when the Library is opened from there.
 */
fun BrowserViewModel.viewFollowed(id: String) = viewModelScope.launch {
    if (!switcherVisible) {
        beginSwitcher()
        switcher.anim.snapTo(1f)
        web.tryEmit(com.jlees.mibrowser.WebCmd.CapturePreview(tabs.activeId)) // the tab's card
        switcher.settled = true
    }
    openView(com.jlees.mibrowser.Sheet.FOLLOWING)
    FollowFlow.open = id
}

fun BrowserViewModel.unfollow(id: String) {
    follows.remove(id)
    FollowImages.delete(getApplication(), id)
    if (FollowFlow.open == id) FollowFlow.open = null
    if (follows.follows.isEmpty()) scheduleChecks()
}

/** Background checks, as Settings › Following has them, while anything is followed. */
fun BrowserViewModel.scheduleChecks() = FollowJob.schedule(getApplication(), settings.followChecks && follows.follows.isNotEmpty(), settings.followWifiOnly)

/** Checks [ids] now (all followed products when null), whatever their interval. */
fun BrowserViewModel.checkNow(ids: Set<String>? = null) {
    val targets = ids ?: follows.follows.filterNot { it.paused }.map { it.id }.toSet() // Check All Now leaves paused ones be
    FollowFlow.checking = FollowFlow.checking + targets
    viewModelScope.launch {
        try { FollowJob.runChecks(getApplication(), targets) } finally { FollowFlow.checking = FollowFlow.checking - targets }
    }
}

/** Opens a followed product's page in a new tab, in front (out of the Library, and out of the tab switcher it may have come from). */
fun BrowserViewModel.openFollowed(url: String) {
    FollowFlow.open = null
    onOverlayClosed()
    addNewTab(url)
    switcherVisible = false
}

/** Pick Price Again: its page opens in a new tab and, once it has loaded, the next tap picks the price. */
fun BrowserViewModel.repick(id: String) {
    val f = follows[id] ?: return
    openFollowed(f.url)
    val pending = Picking(tabs.activeId, id)
    FollowFlow.repickPending = pending
    viewModelScope.launch {
        val loaded = FollowPage.waitUntilLoaded(pending.tabId)
        if (FollowFlow.repickPending != pending) return@launch // cancelled meanwhile
        FollowFlow.repickPending = null
        if (loaded) { delay(400); startPick(pending.tabId, id) } // the page's own scripts settle first
        else showAlert("Page Didn't Load", "The product page didn't finish loading. Open it and try Pick the Price Again.")
    }
}

/** For a backup (Back Up): your followed products, with their price history and settings. */
fun BrowserViewModel.followsBackup(): JsonElement =
    json.encodeToJsonElement(ListSerializer(Follow.serializer()), follows.follows)

/** The followed products in a backup's [data] (none in one from before they were saved). */
fun backedUpFollows(data: JsonObject): List<Follow> = data["follows"]?.let {
    runCatching { json.decodeFromJsonElement(ListSerializer(Follow.serializer()), it) }.getOrNull()
}.orEmpty()

/** A backup's followed products join yours (see FollowStore.restore); how many did. */
fun BrowserViewModel.restoreFollows(restored: List<Follow>): Int = follows.restore(restored).also { scheduleChecks() }

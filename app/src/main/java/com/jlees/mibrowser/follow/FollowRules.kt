// Following rules: what a followed product is, reading a price (and stock) from a page, and which changes are worth
// telling you about. Plain functions, unit-tested (FollowTest).
package com.jlees.mibrowser.follow

import com.jlees.mibrowser.data.Choice
import com.jlees.mibrowser.data.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

@Serializable enum class CheckEvery(override val label: String, val hours: Int) : Choice {
    @SerialName("1h") HOURLY("1 hour", 1), @SerialName("6h") SIX_HOURS("6 hours", 6), @SerialName("24h") DAILY("Day", 24),
}

@Serializable enum class NotifyMode(override val label: String) : Choice {
    @SerialName("now") NOW("Right away"), @SerialName("digest") DIGEST("Daily"), @SerialName("off") OFF("Never"),
}

/** Where a follow's price is read from: the product data the shop publishes, or a spot on the page you picked. */
@Serializable enum class ReadFrom { @SerialName("data") PRODUCT_DATA, @SerialName("spot") SPOT }

/**
 * How to find the price again on every check. [selector] and [classes] describe a picked spot (follow-detect.js), and
 * [label] the words beside it ("Price:", "Your price"), which outlast a page being rearranged.
 */
@Serializable
data class Extraction(val from: ReadFrom, val selector: String? = null, val classes: String? = null, val label: String? = null)

/** Which price changes tell you about a product: only drops, or any change (up too). */
@Serializable enum class PriceAlert(override val label: String) : Choice { @SerialName("drops") DROPS("Drops"), @SerialName("changes") CHANGES("Changes") }

/** What tells you about a product: price drops or any change, and its stock changing (back in, or out of, stock). */
@Serializable
data class Alerts(val price: PriceAlert = PriceAlert.DROPS, @SerialName("backInStock") val stockChanges: Boolean = true)

/** The smallest change worth a notification, a product's choices (Percentage Change). */
enum class MinChange(override val label: String, val percent: Int) : Choice {
    ANY("Any", 0), FIVE("5%", 5), TEN("10%", 10), TWENTY("20%", 20);
    companion object { fun of(p: Int) = entries.firstOrNull { it.percent == p } ?: ANY }
}

/** One check's reading: the price ([amount], null when none was found but stock was) and whether it's in stock. */
@Serializable
data class PricePoint(val at: Long, val amount: Double?, val inStock: Boolean? = null)

@Serializable enum class FollowStatus {
    @SerialName("ok") OK,
    /** The price couldn't be found (the page changed): pick it again. */
    @SerialName("attention") NEEDS_ATTENTION,
    /** A price that looks wrong ([Follow.pending]) waits for you to confirm it before it counts. */
    @SerialName("confirm") CONFIRM,
}

/** Why a check couldn't read the price: [text] for the Updates list, [detail] for the product's panel. */
@Serializable enum class Problem(val text: String, val detail: String) {
    @SerialName("price") NO_PRICE("Couldn't find the price: tap to pick it again", "mi couldn't find the price on the page last time. The page may have changed."),
    @SerialName("blocked") BLOCKED("The shop turned mi's check away", "The shop turned mi's check away last time. Turning on Use My Login below often helps."),
    @SerialName("gone") GONE("The page is gone", "The page is gone. The shop may have removed this product."),
    @SerialName("load") NOT_LOADED("The page didn't load", "The page didn't load last time. mi will try again at the next check."),
}

/**
 * A followed product. [history] (oldest first): a point per check (scheduled or Check now), checks within 5 minutes
 * of the last point sharing it ([withPoint]). [notify]: how its changes notify (Settings › Following › Notifications can turn them all off);
 * [minChange]: the smallest change worth one (percent; 0: any). [image]: the product's picture, kept on the phone (FollowImages). [needsRender]: the shop's
 * page only shows the price to a real page load (or blocks plain requests). [was]: the crossed-out price the shop shows
 * beside the current one (on sale), if any ([wasFor]). [problem]: why the last check failed, while it needs attention;
 * [failures]: checks in a row that failed (you're asked to look after [FAILS_BEFORE_ASKING]). [startingPrice]: a "From"
 * price (it depends on the size or model). [paused]: kept, with its history, but not checked.
 */
@Serializable
data class Follow(
    val id: String,
    val url: String,
    val title: String,
    val currency: String,
    val extraction: Extraction,
    val alerts: Alerts = Alerts(),
    val every: CheckEvery = CheckEvery.SIX_HOURS,
    val notify: NotifyMode = NotifyMode.NOW,
    val minChange: Int = 0,
    val useLogin: Boolean = false,
    val history: List<PricePoint> = emptyList(),
    val followedAt: Long = 0,
    val lastChecked: Long = 0,
    val status: FollowStatus = FollowStatus.OK,
    val pending: PricePoint? = null,
    val needsRender: Boolean = false,
    val image: String? = null,
    val was: Double? = null,
    val problem: Problem? = null,
    val failures: Int = 0,
    val startingPrice: Boolean = false,
    val paused: Boolean = false,
) {
    val current get() = history.lastOrNull { it.amount != null }?.amount
    val inStock get() = history.lastOrNull { it.inStock != null }?.inStock
    val lowest get() = history.mapNotNull { it.amount }.minOrNull()
    val first get() = history.firstOrNull { it.amount != null }?.amount
}

@Serializable enum class UpdateKind {
    @SerialName("drop") DROP, @SerialName("rise") RISE, @SerialName("stock") BACK_IN_STOCK, @SerialName("gone") OUT_OF_STOCK,
    @SerialName("attention") NEEDS_ATTENTION, @SerialName("confirm") CONFIRM,
}

/**
 * One line in the Updates list: [text] is ready to show. [alert]: it's one you asked to hear about (the product's
 * alerts and the smallest change worth it), so it notifies, as Settings › Following says.
 */
@Serializable
data class FollowUpdate(
    val id: String, val followId: String, val at: Long, val kind: UpdateKind, val text: String,
    val read: Boolean = false, val notified: Boolean = false, val alert: Boolean = false,
)

/** A price ([was]: the crossed-out price beside it) and whether it's in stock, as read from a page (before it's compared with what was there), and the product's picture. */
data class Reading(val amount: Double?, val currency: String?, val inStock: Boolean?, val title: String? = null, val image: String? = null, val was: Double? = null, val startingPrice: Boolean = false) {
    val found get() = amount != null || inStock != null
}

// ---- reading prices

private val SYMBOLS = linkedMapOf(
    "R$" to "BRL", "A$" to "AUD", "C$" to "CAD", "CA$" to "CAD", "AU$" to "AUD", "NZ$" to "NZD", "HK$" to "HKD", "US$" to "USD",
    "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₩" to "KRW", "₽" to "RUB", "₺" to "TRY", "₪" to "ILS", "₫" to "VND",
    "zł" to "PLN", "kr" to "SEK", "Kč" to "CZK", "Ft" to "HUF", "CHF" to "CHF", "$" to "USD",
)
private val NUMBER = Regex("""\d[\d.,\s  ']*""")
private val CODE = Regex("""\b[A-Z]{3}\b""")

/**
 * The price in [text] ("$1,299.00", "1.299,00 €", "1 299,00 zł", "¥1,299", "129.99"), with its currency when the text
 * shows one, else [currency]. A separator followed by exactly three digits groups thousands; the last one followed by one
 * or two digits is the decimal point, whichever character it is.
 */
fun parsePrice(text: String?, currency: String? = null): Reading? {
    val t = text?.trim()?.takeIf { it.isNotEmpty() && it.length < 80 } ?: return null
    val raw = NUMBER.find(t)?.value?.trimEnd(' ', ' ', ' ', '.', ',', '\'') ?: return null
    val cleaned = raw.replace(Regex("""[\s  ']"""), "")
    val lastSep = cleaned.indexOfLast { it == '.' || it == ',' }
    val number = if (lastSep < 0) cleaned else {
        val decimals = cleaned.length - lastSep - 1
        if (decimals == 3 || decimals == 0) cleaned.replace(".", "").replace(",", "") // only thousands separators
        else cleaned.substring(0, lastSep).replace(".", "").replace(",", "") + "." + cleaned.substring(lastSep + 1)
    }
    val amount = number.toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 } ?: return null
    val code = SYMBOLS.entries.firstOrNull { (s, _) -> s in t }?.value
        ?.let { found -> if (found == "USD" && currency != null && currency != "USD" && "$" in currency.let { c -> symbolOf(c) }) currency else found }
        ?: CODE.find(t)?.value?.takeIf { runCatching { Currency.getInstance(it) }.isSuccess }
        ?: currency
    return Reading(amount, code, null)
}

private fun symbolOf(code: String) = runCatching { Currency.getInstance(code).getSymbol(Locale.US) }.getOrDefault(code)

/** In stock (true), out of stock (false) or unknown (null), from a page's stated availability. */
fun stockOf(availability: String?): Boolean? {
    // schema.org's "InStock", OpenGraph's "in stock", "out_of_stock", "sold-out"…
    val a = availability?.substringAfterLast('/')?.lowercase()?.replace(Regex("""[\s_-]"""), "") ?: return null
    return when {
        a.startsWith("instock") || a.startsWith("limitedavailability") || a.startsWith("onlineonly") || a.startsWith("instoreonly") || a.startsWith("preorder") || a.startsWith("presale") || a.startsWith("backorder") -> true
        a.startsWith("outofstock") || a.startsWith("soldout") || a.startsWith("discontinued") -> false
        else -> null
    }
}

/**
 * The product a page describes in its structured data ([jsonLd]: the text of its `application/ld+json` scripts; [meta]:
 * its meta tags, name → content): price, currency, stock and name. Null when it describes none (an article, a list).
 */
fun productFrom(jsonLd: List<String>, meta: Map<String, String>): Reading? {
    for (block in jsonLd) {
        val root = runCatching { json.parseToJsonElement(block) }.getOrNull() ?: continue
        findProduct(root)?.let { return it.copy(image = it.image ?: meta["og:image"]) }
    }
    // OpenGraph / Facebook product tags
    val amount = meta["product:price:amount"] ?: meta["og:price:amount"] ?: meta["twitter:data1"]?.takeIf { it.any(Char::isDigit) }
    val currency = meta["product:price:currency"] ?: meta["og:price:currency"]
    val price = parsePrice(amount, currency) ?: return null
    return price.copy(inStock = stockOf(meta["product:availability"] ?: meta["og:availability"]), title = meta["og:title"], image = meta["og:image"])
}

private fun JsonElement.str(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

private fun isType(o: JsonObject, type: String): Boolean = when (val t = o["@type"]) {
    is JsonPrimitive -> t.contentOrNull?.substringAfterLast('/') == type
    is JsonArray -> t.any { (it as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/') == type }
    else -> false
}

private fun findProduct(e: JsonElement): Reading? = when (e) {
    is JsonArray -> e.firstNotNullOfOrNull(::findProduct)
    is JsonObject -> when {
        isType(e, "Product") || isType(e, "ProductGroup") -> productReading(e)
        else -> (e["@graph"] ?: e["mainEntity"])?.let(::findProduct)
    }
    else -> null
}

private fun productReading(p: JsonObject): Reading? {
    val offers = when (val o = p["offers"]) { is JsonArray -> o.filterIsInstance<JsonObject>(); is JsonObject -> listOf(o); else -> emptyList() }
    // a product group (sizes, colours) lists its variants' offers under hasVariant
    val variants = (p["hasVariant"] as? JsonArray)?.filterIsInstance<JsonObject>()?.mapNotNull { productReading(it) }.orEmpty()
    val readings = offers.mapNotNull { o ->
        // price specifications: the price, and maybe the crossed-out one (a StrikethroughPrice or ListPrice)
        val specs = when (val s = o["priceSpecification"]) { is JsonArray -> s.toList(); null -> emptyList(); else -> listOf(s) }
        val isWas = { s: JsonElement -> s.str("priceType")?.let { "Strikethrough" in it || "ListPrice" in it } == true }
        val spec = specs.firstOrNull { !isWas(it) }
        val raw = o.str("price") ?: o.str("lowPrice") ?: spec?.str("price") ?: return@mapNotNull null
        val cur = o.str("priceCurrency") ?: spec?.str("priceCurrency")
        // a range of prices (an AggregateOffer's low to high): the lowest, as a starting price
        val range = o.str("price") == null && o.str("lowPrice") != null && o.str("highPrice")?.let { it != o.str("lowPrice") } == true
        parsePrice(raw, cur)?.copy(inStock = stockOf(o.str("availability")), was = specs.firstOrNull(isWas)?.str("price")?.let { parsePrice(it, cur)?.amount }, startingPrice = range)
    } + variants
    if (readings.isEmpty()) return null
    // several offers (sellers, conditions): the cheapest in stock, else the cheapest
    val best = readings.filter { it.inStock != false && it.amount != null }.minByOrNull { it.amount!! } ?: readings.filter { it.amount != null }.minByOrNull { it.amount!! } ?: return null
    return best.copy(startingPrice = best.startingPrice || readings.any { it.startingPrice }, inStock = if (readings.any { it.inStock == true }) true else best.inStock, title = p.str("name") ?: best.title, image = imageOf(p["image"]) ?: best.image)
}

/** A schema.org image: a URL, a list of them (the first), or an ImageObject ({url} / {contentUrl}). */
private fun imageOf(e: JsonElement?): String? = when (e) {
    is JsonPrimitive -> e.contentOrNull
    is JsonArray -> e.firstNotNullOfOrNull(::imageOf)
    is JsonObject -> e.str("url") ?: e.str("contentUrl")
    else -> null
}

private val LD_JSON = Regex("""<script[^>]+type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTR = Regex("""([\w:-]+)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)

/** The structured data in a fetched page's [html]: its JSON-LD blocks and meta tags (property/name/itemprop → content). */
fun structuredData(html: String): Pair<List<String>, Map<String, String>> {
    val ld = LD_JSON.findAll(html).map { it.groupValues[1].trim() }.toList()
    val meta = META.findAll(html).mapNotNull { m ->
        val attrs = ATTR.findAll(m.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
        val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase() ?: return@mapNotNull null
        attrs["content"]?.let { key to htmlDecode(it) }
    }.toMap()
    return ld to meta
}

private fun htmlDecode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

private val BLOCKED = Regex("""captcha|robot check|are you a (human|robot)|unusual traffic|access denied|request blocked|verify you are human|enable javascript and cookies""", RegexOption.IGNORE_CASE)

/** A response that's a shop turning an automated request away (blocked, rate-limited, a captcha) rather than the page. */
fun isTurnedAway(code: Int, html: String) = code == 403 || code == 429 || code == 503 || (html.length < 200_000 && BLOCKED.containsMatchIn(html.take(50_000)))

// ---- what's worth telling you

/** "$1,299.00", "1.299,00 €" (in your phone's style), for [amount] in [currency]. */
fun formatPrice(amount: Double, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(amount)
}.getOrDefault("%.2f %s".format(amount, currency))

/**
 * A reading that looks wrong rather than a real change: a price that moved by more than 70% at once, or into another
 * currency (a different variant or region shown, a mis-read spot). It waits for you to confirm it.
 */
fun isSuspicious(f: Follow, r: Reading): Boolean {
    val old = f.current ?: return false
    val new = r.amount ?: return false
    return (r.currency != null && r.currency != f.currency) || abs(new - old) / old > 0.7
}

/**
 * The updates a new reading [r] of [f] brings, and the point to add to its history (if anything changed, or one is due
 * the point every check adds to its history). Every price and stock change is listed; the ones you asked for are [FollowUpdate.alert]s:
 * drops (or any change, by the product's alert) of at least the smallest change worth it, and its stock changing.
 */
fun changesFor(f: Follow, r: Reading, now: Long, id: () -> String): Pair<PricePoint?, List<FollowUpdate>> {
    val old = f.current
    val new = r.amount
    val priceChanged = new != null && old != null && abs(new - old) >= 0.005
    val stockChanged = r.inStock != null && f.inStock != null && r.inStock != f.inStock
    // every check is a point in the history (the chart shows each one), changed or not
    val point = PricePoint(now, new ?: old, r.inStock ?: f.inStock)
    val updates = mutableListOf<FollowUpdate>()
    fun add(kind: UpdateKind, text: String, alert: Boolean) { updates += FollowUpdate(id(), f.id, now, kind, text, alert = alert) }
    if (priceChanged) {
        val bigEnough = abs(new!! - old!!) / old * 100 >= f.minChange
        val move = priceMove(old, new, f.currency)
        if (new < old) {
            val lowest = f.lowest?.let { new < it - 0.005 } == true
            add(UpdateKind.DROP, "Dropped: $move${if (lowest) ", the lowest yet" else ""}", bigEnough)
        } else add(UpdateKind.RISE, "Went up: $move", bigEnough && f.alerts.price == PriceAlert.CHANGES)
    }
    if (stockChanged) {
        if (r.inStock == true) add(UpdateKind.BACK_IN_STOCK, "Back in stock" + (new?.let { " at ${formatPrice(it, f.currency)}" } ?: ""), f.alerts.stockChanges)
        else add(UpdateKind.OUT_OF_STOCK, "Out of stock", f.alerts.stockChanges)
    }
    return point to updates
}

/**
 * The crossed-out price to show for [f] after reading [r]: the one the page shows now, else (when the page said nothing
 * about it, as a quick fetch of product data often can't) the one from before while the price hasn't moved. Only a price
 * above the current one counts as a "was".
 */
fun wasFor(f: Follow, r: Reading): Double? {
    val now = r.amount ?: f.current ?: return null
    val unchanged = f.current?.let { abs(it - now) < 0.005 } == true
    return (r.was ?: f.was.takeIf { unchanged })?.takeIf { it > now + 0.005 }
}

/**
 * Why a check couldn't read the price: the page's HTTP [status] (0: none came, or it was fine), whether it [loaded], its
 * [title] (a shop's block page often says so there), and whether the page looked like a captcha or a "continue shopping"
 * stand-in ([blocked], follow-detect.js).
 */
fun problemOf(status: Int, loaded: Boolean, title: String?, blocked: Boolean = false): Problem = when {
    status == 404 || status == 410 -> Problem.GONE
    blocked || status == 403 || status == 429 || status == 503 || (title != null && BLOCKED.containsMatchIn(title)) -> Problem.BLOCKED
    !loaded -> Problem.NOT_LOADED
    else -> Problem.NO_PRICE
}

/**
 * The old price of a sale around a [price] (follow-detect.js `around`): the crossed-out price shown ([was]), else worked
 * out from the percentage off ([percent]: 33 for "-33%") or the amount saved ([save]). Null when there's no sale.
 */
fun saleFrom(price: Double, was: Double?, percent: Int?, save: Double?): Double? = when {
    was != null && was > price + 0.005 -> was
    percent != null && percent in 1..90 -> Math.round(price / (1 - percent / 100.0) * 100) / 100.0
    save != null && save > 0 && save < price * 10 -> Math.round((price + save) * 100) / 100.0
    else -> null
}

/** How much off a crossed-out [was] price [now] is, in whole percent (at least 1); null when it isn't below it. */
fun percentOff(was: Double?, now: Double?): Int? =
    if (was == null || now == null || now >= was - 0.005) null else ((was - now) / was * 100).toInt().coerceAtLeast(1)

/** "$120.00 → $99.00 (−18%)": a change from [old] to [new], in [currency]. */
fun priceMove(old: Double, new: Double, currency: String): String {
    val percent = (abs(new - old) / old * 100).let { if (it < 1) "<1" else it.toInt().toString() }
    return "${formatPrice(old, currency)} → ${formatPrice(new, currency)} (${if (new < old) "−" else "+"}$percent%)"
}

/** The lowest and highest prices in [history] (the latest of each, if it was seen more than once); null if it never changed. */
fun priceRange(history: List<PricePoint>): Pair<PricePoint, PricePoint>? {
    val priced = history.filter { it.amount != null }
    val low = priced.lastOrNull { p -> priced.none { it.amount!! < p.amount!! } } ?: return null
    val high = priced.last { p -> priced.none { it.amount!! > p.amount!! } }
    return if (high.amount!! - low.amount!! < 0.005) null else low to high
}

/**
 * Library › Following's order: products with changes you haven't seen ([updates] not read) first, the latest change
 * first; then the rest as they are (newest followed first).
 */
fun pinnedFirst(follows: List<Follow>, updates: List<FollowUpdate>): List<Follow> {
    val latest = updates.filter { !it.read }.groupBy { it.followId }.mapValues { (_, u) -> u.maxOf { it.at } }
    val (changed, rest) = follows.partition { it.id in latest }
    return changed.sortedByDescending { latest[it.id] } + rest
}

/** Checks in a row that can fail before you're asked to look (one slow page or timeout isn't worth a "pick it again"). */
const val FAILS_BEFORE_ASKING = 3

/** Whether [f] is due a check at [now] (never while paused). */
fun isDue(f: Follow, now: Long) = !f.paused && now - f.lastChecked >= f.every.hours * 3_600_000L - 5 * 60_000L // a few minutes early is fine

private val WORD = Regex("""\p{L}{3}""")

/** Where this spot's price is read from on later checks: its path, classes and (when it's words, not "-8%") the label beside it. */
fun Spot.extraction() = Extraction(ReadFrom.SPOT, selector, classes, label.takeIf { WORD.containsMatchIn(it) })

/** A price to follow, as offered to choose from: what it reads, where it's read from, why it's likely (or not), and whether it's crossed out ([old]). */
data class PriceOption(val reading: Reading, val extraction: Extraction, val note: String, val old: Boolean = false)

/**
 * What a page offers to follow, best first: the shop's product data (when it has a price), then the prices shown on the
 * page ([candidates], best first) that the product data doesn't already give, each with a note on why it's likely or
 * not. [stock] is the page's own say, used where the product data has none. Text that isn't a price is left out.
 */
fun priceOptions(product: Reading?, candidates: List<Spot>, currency: String, stock: Boolean?): List<PriceOption> {
    // the crossed-out prices shown, best first: a price's "was" is the first above it
    val struck = candidates.filter { it.struck }.mapNotNull { parsePrice(it.text)?.amount }
    fun wasOf(amount: Double?) = amount?.let { a -> struck.firstOrNull { it > a + 0.005 } }
    val fromData = product?.takeIf { it.amount != null }?.let {
        PriceOption(it.copy(currency = it.currency ?: currency, inStock = it.inStock ?: stock, was = it.was ?: wasOf(it.amount)), Extraction(ReadFrom.PRODUCT_DATA),
            if (it.startingPrice) "From the shop's product data: a starting price, depending on the option you choose" else "From the shop's product data (most reliable)")
    }
    val shown = candidates.mapNotNull { s ->
        val r = parsePrice(s.text, product?.currency ?: currency) ?: return@mapNotNull null
        if (fromData != null && abs((r.amount ?: 0.0) - fromData.reading.amount!!) < 0.005) return@mapNotNull null // the same price
        val note = listOfNotNull(
            "“${s.label}”".takeIf { WORD.containsMatchIn(s.label) }, // words ("List Price:"), not just "-8%"
            "The shop's price for this product".takeIf { s.shop },
            "Crossed out: probably the old price".takeIf { s.struck },
            "Marked as the sale price".takeIf { s.sale && !s.struck },
            "Next to Add to Cart".takeIf { s.nearBuy },
            "Large".takeIf { s.big && !s.struck },
            "Away from the product (maybe related items)".takeIf { s.aside },
            "A starting price: it depends on the option you choose".takeIf { s.from },
        ).joinToString(" · ").ifEmpty { "Shown on the page" }
        PriceOption(r.copy(inStock = stock, was = if (s.struck) null else wasOf(r.amount), startingPrice = s.from),
            s.extraction(), note, old = s.struck)
    }.distinctBy { it.reading.amount }
    return listOfNotNull(fromData) + shown
}

/** Checks closer together than this share a point in the price history (spamming Check Now doesn't add a pile of them). */
const val POINT_EVERY_MS = 5 * 60_000L

/**
 * [history] with a check's [point] added: a new point if the last is at least [POINT_EVERY_MS] old, else the last one
 * updated to what this check read (keeping its time, so a new point still comes 5 minutes after it). A run of checks
 * that read the same keeps only its first and latest points: the graph's line is the same, the file much smaller.
 */
fun withPoint(history: List<PricePoint>, point: PricePoint): List<PricePoint> {
    val last = history.lastOrNull() ?: return listOf(point)
    if (point.at - last.at < POINT_EVERY_MS) return history.dropLast(1) + point.copy(at = last.at)
    val same = { p: PricePoint -> p.amount == point.amount && p.inStock == point.inStock }
    val run = history.size >= 2 && same(last) && same(history[history.size - 2])
    return compactHistory((if (run) history.dropLast(1) else history) + point, point.at)
}

/** Every point is kept this long; older ones are thinned out ([compactHistory]). */
const val DETAIL_DAYS = 30

/**
 * [history] with points older than [DETAIL_DAYS] (before [now]) thinned to one a day, the day's lowest price (so the
 * lowest ever stays true). Hourly checks would otherwise add some 8,700 points a year to each product's saved file.
 */
fun compactHistory(history: List<PricePoint>, now: Long): List<PricePoint> {
    val cutoff = now - DETAIL_DAYS * 86_400_000L
    if (history.firstOrNull()?.let { it.at >= cutoff } != false) return history
    val (old, recent) = history.partition { it.at < cutoff }
    return old.groupBy { it.at / 86_400_000L }.values.map { day -> day.minByOrNull { it.amount ?: Double.MAX_VALUE }!! } + recent
}

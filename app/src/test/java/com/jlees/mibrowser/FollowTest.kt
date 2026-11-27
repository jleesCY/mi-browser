package com.jlees.mibrowser

import androidx.test.core.app.ApplicationProvider
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.follow.*
import com.jlees.mibrowser.logic.moved
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.nio.file.Files

/** Following prices (follow/): reading prices from pages, and which changes are worth telling you about. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowTest {
    private fun price(t: String, cur: String? = null) = parsePrice(t, cur)?.let { it.amount to it.currency }

    @Test fun pricesInEveryStyle() {
        assertEquals(1299.0 to "USD", price("$1,299.00"))
        assertEquals(1299.0 to "EUR", price("1.299,00 €"))
        assertEquals(1299.0 to "PLN", price("1 299,00 zł"))
        assertEquals(1299.0 to "JPY", price("¥1,299"))
        assertEquals(129.99 to "GBP", price("£129.99"))
        assertEquals(12.5 to "EUR", price("12,5 EUR"))
        assertEquals(129.99 to "CAD", price("$129.99", "CAD")) // a $ on a Canadian shop
        assertEquals(129.99 to "USD", price("129.99", "USD")) // no symbol: the shop's currency
        assertEquals(1999.0 to "USD", price("US$1,999"))
        listOf("", "Add to cart", "4.5 out of 5 stars and a long description of the product that goes on and on and on").forEach { assertNull(it, price(it)) }
    }

    @Test fun productDataInItsUsualShapes() {
        val simple = """{"@context":"https://schema.org","@type":"Product","name":"Desk Lamp","offers":{"@type":"Offer","price":"49.99","priceCurrency":"USD","availability":"https://schema.org/InStock"}}"""
        assertEquals(Reading(49.99, "USD", true, "Desk Lamp"), productFrom(listOf(simple), emptyMap()))
        // inside a @graph, several sellers: the cheapest in stock
        val graph = """{"@graph":[{"@type":"WebPage"},{"@type":["Product"],"name":"Kettle","offers":[
            {"price":30,"priceCurrency":"EUR","availability":"OutOfStock"},{"price":35,"priceCurrency":"EUR","availability":"InStock"},{"price":40,"priceCurrency":"EUR","availability":"InStock"}]}]}"""
        assertEquals(Reading(35.0, "EUR", true, "Kettle"), productFrom(listOf(graph), emptyMap()))
        // an AggregateOffer's lowest price; a price specification
        assertEquals(19.0, productFrom(listOf("""{"@type":"Product","offers":{"@type":"AggregateOffer","lowPrice":"19.00","priceCurrency":"GBP"}}"""), emptyMap())?.amount)
        assertEquals(7.5, productFrom(listOf("""{"@type":"Product","offers":{"priceSpecification":{"price":7.5,"priceCurrency":"USD"}}}"""), emptyMap())?.amount)
        // sold out everywhere
        assertEquals(false, productFrom(listOf("""{"@type":"Product","offers":{"price":"10","priceCurrency":"USD","availability":"https://schema.org/SoldOut"}}"""), emptyMap())?.inStock)
        // no product (an article), broken JSON: nothing; then the meta tags
        assertNull(productFrom(listOf("""{"@type":"NewsArticle"}""", "{broken"), emptyMap()))
        assertEquals(Reading(12.0, "USD", true, "Mug"), productFrom(emptyList(), mapOf("product:price:amount" to "12.00", "product:price:currency" to "USD", "product:availability" to "in stock", "og:title" to "Mug")))
    }

    @Test fun structuredDataFromAFetchedPage() {
        val html = """<html><head><meta property="og:title" content="Chair &amp; Table"><meta content="99.00" property="product:price:amount">
            <script type="application/ld+json">{"@type":"Product","offers":{"price":"95","priceCurrency":"USD"}}</script></head><body>…</body></html>"""
        val (ld, meta) = structuredData(html)
        assertEquals(1, ld.size)
        assertEquals("Chair & Table", meta["og:title"])
        assertEquals("99.00", meta["product:price:amount"])
        assertEquals(95.0, productFrom(ld, meta)?.amount) // JSON-LD first
    }

    @Test fun aShopTurningAwayAnAutomatedRequest() {
        assertTrue(isTurnedAway(503, ""))
        assertTrue(isTurnedAway(200, "<title>Robot Check</title> Enter the characters you see below"))
        assertEquals(false, isTurnedAway(200, "<h1>Desk Lamp</h1> \$49.99"))
    }

    private fun follow(price: Double, alerts: Alerts = Alerts(), inStock: Boolean? = true) =
        Follow("f", "https://shop.test/lamp", "Lamp", "USD", Extraction(ReadFrom.PRODUCT_DATA), alerts, history = listOf(PricePoint(0, price, inStock), PricePoint(1, price, inStock)))

    private fun changes(f: Follow, amount: Double?, inStock: Boolean? = true) = changesFor(f, Reading(amount, "USD", inStock), 10) { "u" }
    private fun alerts(f: Follow, amount: Double?, inStock: Boolean? = true) = changes(f, amount, inStock).second.map { it.kind to it.alert }

    @Test fun whatAChangeTellsYou() {
        val (p, drop) = changes(follow(100.0), 90.0)
        assertEquals(90.0, p?.amount)
        assertEquals(listOf(UpdateKind.DROP to true), drop.map { it.kind to it.alert })
        assertTrue(drop.single().text, drop.single().text.contains("10%") && drop.single().text.contains("lowest"))
        // a rise is listed either way; it notifies only when the product is set to any change
        assertEquals(listOf(UpdateKind.RISE to false), alerts(follow(100.0), 110.0))
        assertEquals(listOf(UpdateKind.RISE to true), alerts(follow(100.0, Alerts(PriceAlert.CHANGES)), 110.0))
        assertEquals(listOf(UpdateKind.DROP to true), alerts(follow(100.0, Alerts(PriceAlert.CHANGES)), 90.0))
        // the product's smallest change worth a notification: smaller ones are listed, quietly
        assertEquals(listOf(UpdateKind.DROP to false), alerts(follow(100.0).copy(minChange = 10), 95.0))
        assertEquals(listOf(UpdateKind.DROP to true), alerts(follow(100.0).copy(minChange = 10), 85.0))
        assertEquals(listOf(UpdateKind.BACK_IN_STOCK to true), alerts(follow(100.0, inStock = false), 100.0, inStock = true))
        assertEquals(listOf(UpdateKind.BACK_IN_STOCK to false), alerts(follow(100.0, Alerts(stockChanges = false), inStock = false), 100.0, inStock = true))
        // its stock changing either way alerts you (going out of stock too), unless you turned that off
        assertEquals(listOf(UpdateKind.OUT_OF_STOCK to true), alerts(follow(100.0), 100.0, inStock = false))
        assertEquals(listOf(UpdateKind.OUT_OF_STOCK to false), alerts(follow(100.0, Alerts(stockChanges = false)), 100.0, inStock = false))
        assertEquals(emptyList<FollowUpdate>(), changes(follow(100.0), 100.0).second) // nothing changed: nothing to tell
    }

    @Test fun aPriceThatLooksWrongWaitsForYou() {
        assertTrue(isSuspicious(follow(100.0), Reading(9.0, "USD", true)))     // -91%: probably a mis-read
        assertTrue(isSuspicious(follow(100.0), Reading(100.0, "EUR", true)))   // another currency
        assertEquals(false, isSuspicious(follow(100.0), Reading(60.0, "USD", true)))
        val store = FollowStore(JsonStore(Files.createTempDirectory("follow").toFile()))
        store.add(follow(100.0))
        val updates = store.record("f", Reading(9.0, "USD", true), 10)
        assertEquals(listOf(UpdateKind.CONFIRM), updates.map { it.kind })
        assertEquals(FollowStatus.CONFIRM, store["f"]!!.status)
        assertEquals(100.0, store["f"]!!.current) // not counted yet
        store.confirmPending("f")
        assertEquals(9.0, store["f"]!!.current)
        assertEquals(FollowStatus.OK, store["f"]!!.status)
    }

    @Test fun aPriceThatCantBeFoundAsksOnce() {
        val store = FollowStore(JsonStore(Files.createTempDirectory("follow").toFile()))
        store.add(follow(100.0))
        // the background check tries a few times before asking (one slow page isn't worth a "pick it again")
        assertEquals(emptyList<FollowUpdate>(), store.failed("f", 5, Problem.NO_PRICE))
        assertEquals(emptyList<FollowUpdate>(), store.failed("f", 6, Problem.NO_PRICE))
        assertEquals(FollowStatus.OK, store["f"]!!.status)
        assertEquals(listOf(UpdateKind.NEEDS_ATTENTION), store.failed("f", 10, Problem.NO_PRICE).map { it.kind }) // the third in a row
        assertEquals(emptyList<FollowUpdate>(), store.failed("f", 20, Problem.NO_PRICE)) // not again on every check
        assertEquals(listOf("The page is gone"), store.failed("f", 25, Problem.GONE).map { it.text }) // a new reason: told again
        assertEquals(Problem.GONE, store["f"]!!.problem)
        store.record("f", Reading(100.0, "USD", true), 30) // found again
        assertEquals(FollowStatus.OK, store["f"]!!.status)
        assertNull(store["f"]!!.problem)
        assertEquals(0, store["f"]!!.failures)
        assertEquals(1, store.failed("f", 40, Problem.NOT_LOADED, atOnce = true).size) // Check Now: told at once
    }

    @Test fun dueByEachProductsInterval() {
        val f = follow(1.0).copy(every = CheckEvery.SIX_HOURS, lastChecked = 0)
        assertEquals(false, isDue(f, 3 * 3_600_000L))
        assertTrue(isDue(f, 6 * 3_600_000L))
    }

    @Test fun aCheckReadsTheShopsProductDataWithoutLoadingThePage() = runBlocking {
        var price = "49.99"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/lamp") { ex ->
                val body = """<html><script type="application/ld+json">{"@type":"Product","name":"Lamp","offers":{"price":"$price","priceCurrency":"USD","availability":"InStock"}}</script></html>""".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val f = follow(49.99).copy(url = "http://127.0.0.1:${server.address.port}/lamp")
            val checker = PriceChecker(ApplicationProvider.getApplicationContext())
            assertEquals(Checked(Reading(49.99, "USD", true, "Lamp"), false, f.extraction), checker.check(f))
            price = "39.99"
            assertEquals(39.99, checker.check(f).reading?.amount)
        } finally { server.stop(0) }
    }

    @Test fun everyCheckIsAPointInTheHistory() {
        val (point, updates) = changesFor(follow(100.0), Reading(100.0, "USD", true), 10) { "u" }
        assertEquals(100.0, point?.amount)                  // unchanged, still a point
        assertEquals(emptyList<FollowUpdate>(), updates)    // but nothing to tell you
    }

    @Test fun theProductsPictureInItsUsualShapes() {
        fun image(img: String, meta: Map<String, String> = emptyMap()) =
            productFrom(listOf("""{"@type":"Product","image":$img,"offers":{"price":"5","priceCurrency":"USD"}}"""), meta)?.image
        assertEquals("https://a.test/1.jpg", image("\"https://a.test/1.jpg\""))
        assertEquals("/1.jpg", image("[\"/1.jpg\",\"/2.jpg\"]"))
        assertEquals("https://a.test/o.jpg", image("""{"@type":"ImageObject","url":"https://a.test/o.jpg"}"""))
        assertEquals("https://a.test/og.jpg", image("null", mapOf("og:image" to "https://a.test/og.jpg"))) // the share picture when there's none
    }

    @Test fun thePricesToChooseFromBestFirstWithWhy() {
        val product = Reading(49.99, "USD", null, "Lamp")
        val spots = listOf(
            Spot("a", text = "$49.99", big = true),                 // the product data's price, shown: not twice
            Spot("b", text = "$69.99", struck = true),             // the old price
            Spot("c", text = "$9.99", aside = true),               // a related item
            Spot("d", text = "Add to cart"),                       // not a price
            Spot("e", text = "$69.99"),                            // the same amount again: once
        )
        val options = priceOptions(product, spots, "USD", stock = true)
        assertEquals(listOf(49.99, 69.99, 9.99), options.map { it.reading.amount })
        assertEquals(ReadFrom.PRODUCT_DATA, options[0].extraction.from)
        assertTrue(options[0].note, options[0].note.contains("product data"))
        assertTrue(options[1].note, options[1].note.contains("old price"))
        assertTrue(options[2].note, options[2].note.contains("related"))
        assertEquals(true, options[0].reading.inStock) // the page's say, where the product data has none
        // no product data: the page's prices only, best first as found
        assertEquals(listOf(69.99, 9.99), priceOptions(null, spots.drop(1), "USD", null).map { it.reading.amount })
    }

    @Test fun theGraphKeepsPricesOffItsEdges() {
        assertEquals(0.35f, chartLevel(40.0, 40.0, 40.0), 0.001f)  // never changed: a little above the lower third
        assertEquals(0.15f, chartLevel(30.0, 30.0, 50.0), 0.001f)  // the lowest: above the bottom
        assertEquals(0.85f, chartLevel(50.0, 30.0, 50.0), 0.001f)  // the highest: below the top
        // holding the graph: the check nearest the finger
        val times = listOf(0L, 100L, 1000L)
        assertEquals(1, nearestPoint(times, 12f, 100f, 0L, 1000L))
        assertEquals(2, nearestPoint(times, 95f, 100f, 0L, 1000L))
    }

    @Test fun checksWithinFiveMinutesShareAPoint() {
        val start = listOf(PricePoint(0, 100.0))
        val soon = withPoint(start, PricePoint(60_000, 90.0))           // a minute later: the same point, now 90
        assertEquals(listOf(PricePoint(0, 90.0)), soon)
        val later = withPoint(soon, PricePoint(5 * 60_000, 90.0))      // five minutes after it: a new point
        assertEquals(2, later.size)
        assertEquals(listOf(PricePoint(0, 1.0)), withPoint(emptyList(), PricePoint(0, 1.0)))
    }

    @Test fun oldHistoryIsThinnedToEachDaysLowest() {
        val day = 86_400_000L
        val now = 100 * day
        val old = listOf(PricePoint(10 * day, 50.0), PricePoint(10 * day + 3_600_000, 45.0), PricePoint(10 * day + 7_200_000, 48.0))
        val recent = listOf(PricePoint(95 * day, 40.0), PricePoint(95 * day + 3_600_000, 41.0))
        // over 30 days old: one a day, its lowest (45); the last 30 days: every point
        assertEquals(listOf(PricePoint(10 * day + 3_600_000, 45.0)) + recent, compactHistory(old + recent, now))
        assertEquals(recent, compactHistory(recent, now)) // nothing old: unchanged
    }

    @Test fun aRunOfTheSamePriceKeepsItsEnds() {
        val m = POINT_EVERY_MS
        var h = listOf(PricePoint(0, 100.0, true))
        (1..5).forEach { h = withPoint(h, PricePoint(it * m, 100.0, true)) }
        assertEquals(listOf(PricePoint(0, 100.0, true), PricePoint(5 * m, 100.0, true)), h) // first and latest only
        h = withPoint(h, PricePoint(6 * m, 90.0, true))
        h = withPoint(h, PricePoint(7 * m, 90.0, false)) // stock changed: a point of its own
        assertEquals(listOf(0L, 5 * m, 6 * m, 7 * m), h.map { it.at })
    }

    @Test fun theCrossedOutPrice() {
        val f = follow(100.0)
        assertEquals(150.0, wasFor(f, Reading(100.0, "USD", true, was = 150.0)))
        assertNull(wasFor(f, Reading(100.0, "USD", true, was = 80.0))) // below the price: not a "was"
        val onSale = f.copy(was = 150.0)
        assertEquals(150.0, wasFor(onSale, Reading(100.0, "USD", true))) // the page didn't say: kept while the price holds
        assertNull(wasFor(onSale, Reading(120.0, "USD", true)))          // the price moved: the sale's over
        // in product data, as a strikethrough price specification
        val ld = """{"@type":"Product","name":"Lamp","offers":{"@type":"Offer","price":"99","priceCurrency":"USD","priceSpecification":[{"@type":"UnitPriceSpecification","priceType":"https://schema.org/StrikethroughPrice","price":"129"}]}}"""
        assertEquals(129.0, productFrom(listOf(ld), emptyMap())!!.was)
        // on the page, from a struck candidate above the price
        val options = priceOptions(null, listOf(Spot("a", text = "$99.00", big = true), Spot("b", text = "$129.00", struck = true)), "USD", true)
        assertEquals(129.0, options.first().reading.was)
    }

    @Test fun whyACheckFailed() {
        assertEquals(Problem.GONE, problemOf(404, true, "Not Found"))
        assertEquals(Problem.BLOCKED, problemOf(403, true, null))
        assertEquals(Problem.BLOCKED, problemOf(0, true, "Robot Check"))
        assertEquals(Problem.NOT_LOADED, problemOf(0, false, null))
        assertEquals(Problem.NO_PRICE, problemOf(0, true, "Desk Lamp"))
    }

    @Test fun changesShowTheOldPriceAndPercent() {
        assertEquals("$120.00 → $99.00 (−17%)", priceMove(120.0, 99.0, "USD"))
        assertEquals("$100.00 → $100.50 (+<1%)", priceMove(100.0, 100.5, "USD"))
    }

    @Test fun lowestAndHighest() {
        val h = listOf(PricePoint(0, 100.0), PricePoint(1, 80.0), PricePoint(2, 120.0), PricePoint(3, 80.0))
        assertEquals(PricePoint(3, 80.0) to PricePoint(2, 120.0), priceRange(h)) // the latest of the lows
        assertNull(priceRange(listOf(PricePoint(0, 5.0), PricePoint(1, 5.0)))) // never changed
    }

    @Test fun restoredFollowsJoinWithoutDuplicates() {
        val store = FollowStore(JsonStore(Files.createTempDirectory("follow").toFile()))
        store.add(follow(100.0))
        val other = follow(5.0).copy(id = "g", url = "https://shop.test/chair", image = "/old/phone/g.jpg")
        assertEquals(1, store.restore(listOf(follow(1.0), other)))
        assertEquals(listOf(100.0, 5.0), store.follows.map { it.current }) // yours kept as they were
        assertNull(store["g"]!!.image) // fetched again on its next check
        assertTrue(isDue(store["g"]!!, System.currentTimeMillis())) // which is now, not whenever the old phone last checked
    }

    @Test fun amazonsPriceAndItsListPrice() {
        // as follow-detect.js reports an Amazon page: its own price (with the cents, from the screen-reader copy), and the list price
        val options = priceOptions(null, listOf(
            Spot("a", text = "$199.99", big = true, shop = true, label = "-33%"),
            Spot("b", text = "$299.99", struck = true, label = "List Price:"),
        ), "USD", true)
        assertEquals(199.99, options.first().reading.amount)
        assertEquals(299.99, options.first().reading.was)
        assertEquals(33, percentOff(options.first().reading.was, options.first().reading.amount))
        assertTrue(options.first().note, options.first().note.startsWith("The shop's price")) // "-33%" isn't a label
        assertTrue(options[1].note, options[1].note.startsWith("“List Price:”"))
        assertNull(percentOff(100.0, 100.0))
        assertEquals(Problem.BLOCKED, problemOf(200, true, "Amazon.com", blocked = true)) // a "continue shopping" page
    }

    @Test fun theSaleAroundAPrice() {
        assertEquals(299.99, saleFrom(199.99, 299.99, 33, null))         // a crossed-out price comes first
        assertEquals(24.99, saleFrom(22.99, null, 8, null))               // Amazon's "-8%" beside $22.99
        assertEquals(300.0, saleFrom(200.0, null, null, 100.0))           // "Save $100"
        assertNull(saleFrom(200.0, 150.0, null, null))                    // below the price: not a sale
        assertNull(saleFrom(200.0, null, null, null))
    }

    @Test fun changedProductsArePinnedFirst() {
        val a = follow(1.0).copy(id = "a"); val b = follow(1.0).copy(id = "b"); val c = follow(1.0).copy(id = "c")
        val updates = listOf(
            FollowUpdate("1", "c", at = 10, kind = UpdateKind.DROP, text = ""),
            FollowUpdate("2", "b", at = 20, kind = UpdateKind.DROP, text = ""),
            FollowUpdate("3", "a", at = 30, kind = UpdateKind.DROP, text = "", read = true), // seen: not pinned
        )
        assertEquals(listOf("b", "c", "a"), pinnedFirst(listOf(a, b, c), updates).map { it.id }) // latest change first, then the rest as they were
    }

    @Test fun aGuessedPriceThatMovedWaitsForYou() {
        val store = FollowStore(JsonStore(Files.createTempDirectory("follow").toFile()))
        store.add(follow(100.0))
        store.record("f", Reading(100.0, "USD", true), 10, guessed = true) // the same price: fine
        assertEquals(FollowStatus.OK, store["f"]!!.status)
        assertEquals(listOf(UpdateKind.CONFIRM), store.record("f", Reading(90.0, "USD", true), 20, guessed = true).map { it.kind })
    }

    @Test fun pausedProductsArentChecked() {
        val f = follow(1.0).copy(lastChecked = 0)
        assertTrue(isDue(f, 30 * 3_600_000L))
        assertEquals(false, isDue(f.copy(paused = true), 30 * 3_600_000L))
    }

    @Test fun startingPricesAndRearranging() {
        val range = """{"@type":"Product","name":"Phone","offers":{"@type":"AggregateOffer","lowPrice":"799","highPrice":"1099","priceCurrency":"USD"}}"""
        assertTrue(productFrom(listOf(range), emptyMap())!!.startingPrice)
        val options = priceOptions(null, listOf(Spot("a", text = "$999.00", from = true, label = "From"), Spot("b", text = "$49.00", label = "Price:")), "USD", true)
        assertTrue(options[0].reading.startingPrice)
        assertEquals("Price:", options[1].extraction.label) // the words beside it, to find it again
        assertNull(options[0].extraction.label?.takeIf { it.length < 3 })
        assertEquals(listOf("b", "c", "a"), moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("a", "b"), moved(listOf("a", "b"), 0, 5)) // out of range: unchanged
    }

    @Test fun whatTheWidgetShows() {
        val f = follow(100.0).let { it.copy(history = it.history + PricePoint(2, 80.0, true)) }
        // a change you haven't seen: a dot and the change, in place of the site; down 20% since followed
        val drop = FollowUpdate("1", "f", at = 2, kind = UpdateKind.DROP, text = "Down to $80.00")
        val l = productLine(f, listOf(drop))
        assertTrue(l.changed); assertEquals("Down to $80.00", l.detail); assertEquals(Tone.GOOD, l.tone)
        assertEquals("↓ 20%" to Tone.GOOD, l.since); assertNull(l.off)
        // seen: its site; a sale shows how much off instead
        val seen = productLine(f.copy(was = 160.0), listOf(drop.copy(read = true)))
        assertEquals(false, seen.changed); assertEquals("In stock · shop.test", seen.detail); assertEquals("50% off", seen.off)
        // what needs you comes before the site; no price yet is a dash
        val lost = productLine(f.copy(status = FollowStatus.NEEDS_ATTENTION, history = emptyList()), emptyList())
        assertEquals(Tone.BAD, lost.tone); assertEquals("—", lost.price); assertNull(lost.since)
        assertEquals("Out of stock · shop.test", productLine(follow(5.0, inStock = false), emptyList()).detail)
    }

    @Test fun theWidgetsHeading() {
        assertEquals("Following", heading(null))
        assertEquals("Checking…", heading(Progress(0, 0))) // asked for, waiting for a network
        assertEquals("Checking 1 of 12", heading(Progress(0, 12)))
        assertEquals("Checking 12 of 12", heading(Progress(12, 12))) // the last one done, a moment before it clears
    }

    @Test fun whatAHiddenCheckSkips() {
        // the price is text: no fonts, video or sound
        assertTrue(PriceChecker.checkSkips("https://cdn.shop.test/f/inter.woff2", null, "shop.test"))
        assertTrue(PriceChecker.checkSkips("https://cdn.shop.test/promo.mp4", null, "shop.test"))
        // the page's own scripts and data load (they may be what shows the price)
        assertEquals(false, PriceChecker.checkSkips("https://shop.test/app.js", null, "shop.test"))
        assertEquals(false, PriceChecker.checkSkips("https://shop.test/api/price", "application/json", "shop.test"))
    }

    @Test fun stockAndPricesInWords() {
        assertEquals(true, com.jlees.mibrowser.follow.stockOf("https://schema.org/InStock"))
        assertEquals(true, com.jlees.mibrowser.follow.stockOf("in stock"))
        assertEquals(false, com.jlees.mibrowser.follow.stockOf("out_of_stock"))
        assertEquals(false, com.jlees.mibrowser.follow.stockOf("Sold-Out"))
        assertEquals(null, com.jlees.mibrowser.follow.stockOf("ask us"))
        assertEquals(null, com.jlees.mibrowser.follow.stockOf(null))
        val locale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("$19.99", com.jlees.mibrowser.follow.formatPrice(19.99, "USD"))
            assertEquals("19.99 XYZ1", com.jlees.mibrowser.follow.formatPrice(19.99, "XYZ1")) // not a currency: the number and the code
        } finally { java.util.Locale.setDefault(locale) }
    }
}

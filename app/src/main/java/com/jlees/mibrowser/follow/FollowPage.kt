// Following, inside your open tabs: finding a page's price when you follow it, and letting you tap the price yourself.
package com.jlees.mibrowser.follow

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.data.json
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A spot on a page: where a price is (follow-detect.js `describe`). As a candidate it also says what makes it likely or
 * not: [struck] (crossed out or marked as an old price), [big], [nearBuy] (beside the buy button), [sale] (marked as a
 * sale or current price), [aside] (in a header, footer or related products), [shop] (where a shop mi knows keeps its
 * price), the words beside it ([label]: "List Price:", "With Prime"), and whether it's a starting price ([from]: "From $999").
 */
@Serializable
data class Spot(
    val selector: String, val classes: String = "", val text: String = "",
    val struck: Boolean = false, val big: Boolean = false, val nearBuy: Boolean = false, val sale: Boolean = false, val aside: Boolean = false,
    val shop: Boolean = false, val label: String = "", val from: Boolean = false,
)

/** A sale found around a price on the page (follow-detect.js `around`): a crossed-out price, a percentage off, an amount saved. */
@Serializable
data class Near(val was: String? = null, val percent: Int? = null, val save: String? = null)

/** What a page offers to follow: its product data's reading (if any), the likeliest prices shown on it, its picture, and whether the page says it's in stock. */
data class Detected(val product: Reading?, val candidates: List<Spot>, val title: String, val image: String?, val stock: Boolean?)

/**
 * Tabs' WebViews register here as they're made ([attach], from WebViewHost), so Following can run its script in the
 * one you're looking at. The script (assets/js/follow-detect.js) is only ever run on request: following, picking.
 */
object FollowPage {
    private val views = HashMap<String, WeakReference<WebView>>()
    private var script: String? = null
    private var picking: String? = null
    private var onPicked: ((Spot) -> Unit)? = null

    fun script(ctx: Context) = script ?: ctx.assets.open("js/follow-detect.js").bufferedReader().use { it.readText() }.also { script = it }

    /** Tab [id]'s WebView was made; a tap picked while choosing a price comes back through its listener (main frame only). */
    fun attach(id: String, wv: WebView) {
        views[id] = WeakReference(wv)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(wv, "miFollow", setOf("*")) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame || picking != id) return@addWebMessageListener
            val spot = runCatching { json.decodeFromString(Spot.serializer(), message.data ?: "") }.getOrNull() ?: return@addWebMessageListener
            picking = null
            onPicked?.invoke(spot); onPicked = null
        }
    }

    fun detach(id: String) { views.remove(id); if (picking == id) cancelPick() }

    /** What tab [tabId]'s page offers to follow ([done] gets null if it can't be read: no page, scripts off). */
    fun detect(ctx: Context, tabId: String, done: (Detected?) -> Unit) {
        val wv = views[tabId]?.get() ?: return done(null)
        wv.evaluateJavascript("${script(ctx)};JSON.stringify({d: window.__miFollow.data(), c: window.__miFollow.candidates()})") { result ->
            done(runCatching {
                val o = json.parseToJsonElement(json.decodeFromString(String.serializer(), result)).jsonObject
                val d = o["d"]!!.jsonObject
                val product = productIn(d)
                Detected(product, json.decodeFromJsonElement(ListSerializer(Spot.serializer()), o["c"]!!.jsonArray), d["title"]?.jsonPrimitive?.content.orEmpty(), product?.image ?: imageIn(d), stockIn(d))
            }.getOrNull())
        }
    }

    /**
     * Waits (up to [timeoutMs]) until tab [tabId]'s WebView exists and has finished loading a page; false if it never
     * did. A new tab's own "loading" flag isn't enough: it reads "not loading" for a moment before the page starts.
     */
    suspend fun waitUntilLoaded(tabId: String, timeoutMs: Long = 20_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val wv = views[tabId]?.get()
            if (wv != null && wv.progress == 100 && wv.url.let { it != null && it.startsWith("http") }) return true
            delay(300)
        }
        return false
    }

    /** The next tap on tab [tabId]'s page chooses the price; [done] gets where it was. False if the tab has no page. */
    fun pick(ctx: Context, tabId: String, done: (Spot) -> Unit): Boolean {
        val wv = views[tabId]?.get() ?: return false
        picking = tabId; onPicked = done
        wv.evaluateJavascript("${script(ctx)};window.__miFollow.pick()", null)
        return true
    }

    /** Picking is cancelled: the page's taps work as usual again (its tap catcher is removed, not just ignored). */
    fun cancelPick() {
        picking?.let { id -> views[id]?.get()?.evaluateJavascript("window.__miFollow && window.__miFollow.stopPicking()", null) }
        picking = null; onPicked = null
    }

    /** How the script finds [o]'s price on the page: its spot, or (a price from the product data) the amount it reads. */
    private fun ruleOf(o: PriceOption): String = json.encodeToString(JsonObject.serializer(), JsonObject(buildMap {
        o.extraction.selector?.let { put("selector", JsonPrimitive(it)) }
        o.extraction.classes?.let { put("classes", JsonPrimitive(it)) }
        o.reading.amount?.let { put("text", JsonPrimitive("%.2f".format(Locale.US, it))) }
    }))

    /** A sale around [o]'s price on tab [tabId]'s page ([done] gets null if the price isn't found there). */
    fun around(ctx: Context, tabId: String, o: PriceOption, done: (Near?) -> Unit) {
        val wv = views[tabId]?.get() ?: return done(null)
        wv.evaluateJavascript("${script(ctx)};JSON.stringify(window.__miFollow.around(${ruleOf(o)}))") { result ->
            done(runCatching { json.decodeFromString(Near.serializer(), json.decodeFromString(String.serializer(), result)) }.getOrNull())
        }
    }

    /** Whether the page itself says it's in stock (follow-detect.js `stock()`; null: it can't tell). */
    fun stockIn(d: JsonObject): Boolean? = (d["stock"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    /** The page's picture for a product without product data: where a shop mi knows keeps it, its share picture (og:image), or its largest near the top. */
    fun imageIn(d: JsonObject): String? {
        fun str(key: String) = (d[key] as? JsonPrimitive)?.content?.takeIf { it != "null" }
        return str("image") ?: d["meta"]?.jsonObject?.get("og:image")?.jsonPrimitive?.content ?: str("bigImage")
    }

    /** The product reading in a page's structured data, as the script reports it ({ld: [...], meta: {...}}). */
    fun productIn(d: JsonObject): Reading? {
        val ld = d["ld"]?.let { json.decodeFromJsonElement(ListSerializer(String.serializer()), it) }.orEmpty()
        val meta = d["meta"]?.let { json.decodeFromJsonElement(MapSerializer(String.serializer(), String.serializer()), it) }.orEmpty()
        return productFrom(ld, meta.mapKeys { it.key.lowercase() })
    }
}

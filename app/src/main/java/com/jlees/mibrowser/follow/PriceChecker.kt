// Checking a followed product's price: a quick fetch of the page, or (when that can't see the price) loading it in a
// hidden WebView, in a browser profile of its own.
package com.jlees.mibrowser.follow

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** A check's result: what was read (null: the price couldn't be found, for [problem]), and whether the shop needed a full page load. */
data class Checked(val reading: Reading?, val rendered: Boolean, val extraction: Extraction, val problem: Problem = Problem.NO_PRICE, val guessed: Boolean = false)

/** A hidden page load: what follow-detect.js read (null: it never loaded), the page's HTTP error (0: none), whether it finished loading. */
class Rendered(val page: JsonObject?, val status: Int, val loaded: Boolean)

class PriceChecker(private val ctx: Context) {
    /**
     * [f]'s price now. Product data is read from a quick fetch first; a shop that turns the fetch away, or only shows the
     * price once its page has run, gets a hidden page load (and is remembered as needing one). A picked spot needs the
     * page load. If the spot is gone but the page has product data, the follow switches to that (it repairs itself).
     */
    suspend fun check(f: Follow): Checked {
        if (f.extraction.from == ReadFrom.PRODUCT_DATA && !f.needsRender) {
            fetch(f)?.let { return Checked(it, false, f.extraction) }
        }
        return read(f, render(ctx, f.url, f.useLogin, f.extraction))
    }

    /** What a hidden page load [r] of [f] gives: the price (from its spot, or its product data), else why not. */
    private fun read(f: Follow, r: Rendered): Checked {
        val page = r.page
        val failed = Checked(null, true, f.extraction, problemOf(r.status, r.loaded, page?.get("title")?.jsonPrimitive?.content, page?.get("blocked")?.jsonPrimitive?.content == "true"))
        if (page == null) return failed
        // stock: the product data's say, else the page's own (its buy button); the crossed-out price: the data's, else the page's
        // the sale around the price (a crossed-out price, a percentage off, an amount saved), else the page's crossed-out price
        val near = page["near"]?.let { runCatching { json.decodeFromJsonElement(Near.serializer(), it) }.getOrNull() }
        val shown = page["spot"]?.jsonPrimitive?.content?.let { parsePrice(it, f.currency)?.amount }
        val pageWas = near?.let { n -> shown?.let { saleFrom(it, parsePrice(n.was, f.currency)?.amount, n.percent, parsePrice(n.save, f.currency)?.amount) } }
            ?: page["was"]?.jsonPrimitive?.content?.let { parsePrice(it, f.currency)?.amount }
        val product = runCatching { FollowPage.productIn(page) }.getOrNull()?.let { it.copy(inStock = it.inStock ?: FollowPage.stockIn(page), was = it.was ?: pageWas) }
        val pageStock = FollowPage.stockIn(page)
        if (f.extraction.from == ReadFrom.SPOT) {
            val spot = page["spot"]?.jsonPrimitive?.content?.takeIf { it != "null" }
            // nothing left where it was: the page's likeliest price, and the follow moves to its spot (a change waits for you)
            val guessed = page["guessed"]?.jsonPrimitive?.content == "true"
            val moved = page["repoint"]?.takeIf { guessed }?.let { runCatching { json.decodeFromJsonElement(Spot.serializer(), it) }.getOrNull() }?.extraction()
            parsePrice(spot, f.currency)?.let { return Checked(it.copy(inStock = product?.inStock ?: pageStock, image = product?.image ?: FollowPage.imageIn(page), was = pageWas), true, moved ?: f.extraction, guessed = guessed) }
            // the spot is gone: product data, if the page has it, is the better source from now on
            if (product?.found == true) return Checked(product, true, Extraction(ReadFrom.PRODUCT_DATA))
            return failed
        }
        return product?.takeIf { it.found }?.let { Checked(it, true, f.extraction) } ?: failed
    }

    /** Product data from the page fetched plainly (with your cookies only if the follow uses your login); null if not there. */
    private suspend fun fetch(f: Follow): Reading? = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(f.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000
                setRequestProperty("User-Agent", userAgent(ctx))
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                if (f.useLogin) CookieManager.getInstance().getCookie(f.url)?.let { setRequestProperty("Cookie", it) }
            }
            try {
                val code = c.responseCode
                if (code >= 400) return@runCatching null
                val html = c.inputStream.bufferedReader().use { it.readText() }
                if (isTurnedAway(code, html)) return@runCatching null
                val (ld, meta) = structuredData(html)
                productFrom(ld, meta)?.takeIf { it.found }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    companion object {
        private const val PROFILE = "mi-following"
        /** One hidden page load at a time: each is a full page renderer's worth of memory. */
        private val rendering = Mutex()

        /** Wipe All Data: price checks' own browser profile goes, with its cookies and storage (no check is running: mi is about to restart). */
        fun forgetProfile() = runCatching { if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) ProfileStore.getInstance().deleteProfile(PROFILE) }

        private fun userAgent(ctx: Context) = WebSettings.getDefaultUserAgent(ctx).replace("; wv", "")

        /**
         * Loads [url] in a hidden WebView and reads it with follow-detect.js (the structured data, and [extraction]'s spot):
         * after the page has loaded, every second until a price shows, for up to 15 s, then the crossed-out price beside
         * it (`was`). Its page is null if it never loaded. Without
         * your login it's a browser profile of its own (its own cookies), so a shop can't tie checks to you.
         */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun render(ctx: Context, url: String, useLogin: Boolean, extraction: Extraction): Rendered = rendering.withLock {
            withContext(Dispatchers.Main) {
                if (!useLogin && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return@withContext Rendered(null, 0, false) // can't be anonymous
                val wv = WebView(ctx.applicationContext)
                if (!useLogin) { ProfileStore.getInstance().getOrCreateProfile(PROFILE); WebViewCompat.setProfile(wv, PROFILE) }
                with(wv.settings) {
                    javaScriptEnabled = true; domStorageEnabled = true
                    blockNetworkImage = true // the price is text: no need to fetch pictures
                    mediaPlaybackRequiresUserGesture = true
                    userAgentString = userAgent(ctx)
                }
                // a phone-sized page, so it lays out (and lazy parts load) as it would on screen
                val m = ctx.resources.displayMetrics
                wv.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(m.heightPixels, View.MeasureSpec.EXACTLY))
                wv.layout(0, 0, m.widthPixels, m.heightPixels)
                // mi pauses page timers while it's in the background (WebViewHost); this page needs them for a moment
                wv.resumeTimers()
                val rule = json.encodeToString(Extraction.serializer(), extraction)
                var status = 0
                var started = false
                try {
                    val page = withTimeoutOrNull(35_000) {
                        suspendCancellableCoroutine<JsonObject?> { cont ->
                            val main = Handler(Looper.getMainLooper())
                            var polls = 0
                            fun poll() {
                                wv.evaluateJavascript("${FollowPage.script(ctx)};JSON.stringify(window.__miFollow.read($rule))") { result ->
                                    val page = runCatching { json.parseToJsonElement(json.decodeFromString(String.serializer(), result)).jsonObject }.getOrNull()
                                    val found = page != null && (FollowPage.productIn(page)?.found == true || (page["spot"]?.jsonPrimitive?.content?.let { parsePrice(it) } != null))
                                    // done: the crossed-out price beside it
                                    fun finish() = wv.evaluateJavascript("JSON.stringify({was: window.__miFollow.was()})") { more ->
                                        val extra = runCatching { json.parseToJsonElement(json.decodeFromString(String.serializer(), more)).jsonObject }.getOrNull()
                                        if (cont.isActive) cont.resume(if (page != null && extra != null) JsonObject(page + extra) else page)
                                    }
                                    when {
                                        !cont.isActive -> {}
                                        found || ++polls >= 15 -> finish()
                                        else -> main.postDelayed(::poll, 1000)
                                    }
                                }
                            }
                            wv.webViewClient = object : WebViewClient() {
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    if (request.isForMainFrame) status = response.statusCode
                                }
                                override fun onPageFinished(view: WebView, u: String?) { if (!started) { started = true; main.postDelayed(::poll, 1000) } }
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean { if (cont.isActive) cont.resume(null); return true }
                            }
                            wv.loadUrl(url)
                        }
                    }
                    Rendered(page, status, started)
                } finally {
                    wv.stopLoading()
                    if (!inForeground()) wv.pauseTimers() // back as mi had them (process-wide, so any WebView will do)
                    wv.destroy()
                }
            }
        }

        private fun inForeground() = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}

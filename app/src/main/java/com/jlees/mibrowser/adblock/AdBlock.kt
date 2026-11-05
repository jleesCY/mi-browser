// Ad, tracker and cookie-banner blocking: EasyList, EasyPrivacy and the EasyList Cookie List (Adblock Plus syntax) plus
// the StevenBlack hosts list. Network rules block sub-resource requests; cosmetic rules hide page elements. Lists ship
// in assets/ and are refreshed weekly (on unmetered networks) into filesDir/filters/.
package com.jlees.mibrowser.adblock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

import com.jlees.mibrowser.data.Settings

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AdBlock {
    private class Source(val id: String, val url: String)
    private val SOURCES = listOf(
        Source("easylist", "https://easylist.to/easylist/easylist.txt"),
        Source("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        Source("cookies", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"),
    )
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** One engine per setting: ads (EasyList + hosts list), trackers (EasyPrivacy), cookie banners. Null until loaded. */
    @Volatile var ads: FilterEngine? = null; private set
    @Volatile var trackers: FilterEngine? = null; private set
    @Volatile var cookies: FilterEngine? = null; private set

    /** When the lists in use were published (downloaded, or this app version's bundled copy). Shown in Settings. */
    var listsUpdated by mutableLongStateOf(0L); private set
    private val updating = kotlinx.coroutines.sync.Mutex()
    private var lastAttempt = 0L

    private fun dir(ctx: Context) = File(ctx.filesDir, "filters").apply { mkdirs() }

    /** Feeds each line of a list (downloaded copy, else the bundled one) to [f]. */
    private fun read(ctx: Context, s: Source, f: (String) -> Unit) {
        val file = File(dir(ctx), "${s.id}.txt")
        runCatching { (if (file.length() > 0) file.inputStream() else ctx.assets.open("filters/${s.id}.txt")).bufferedReader().useLines { it.forEach(f) } }
    }

    suspend fun load(ctx: Context) = withContext(Dispatchers.Default) {
        // each list parses on its own core; the hosts list joins EasyList in the ads engine
        val hosts = async { FilterEngine().apply { ctx.assets.open("blocklist.txt").bufferedReader().useLines { it.forEach(::addHost) } } }
        val (easylist, easyprivacy, cookieList) = SOURCES.map { s -> async { FilterEngine().apply { read(ctx, s, ::addRule) } } }.awaitAll()
        val adEngine = hosts.await().apply { merge(easylist) }
        listOf(adEngine, easyprivacy, cookieList).forEach { it.finish() }
        ads = adEngine; trackers = easyprivacy; cookies = cookieList
        listsUpdated = SOURCES.minOf { s -> File(dir(ctx), "${s.id}.txt").takeIf { it.length() > 0 }?.lastModified() ?: bundledTime(ctx) }
    }

    private fun bundledTime(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)

    /**
     * Downloads fresh lists when they're over a week old (or [force]: Settings → Update now) and rebuilds the engines.
     * Automatic updates wait for an unmetered network. Returns false if nothing could be downloaded.
     */
    suspend fun updateIfStale(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        updating.withLock {
            if (!force) {
                val now = System.currentTimeMillis()
                // stale lists, but a failed attempt waits a day (no re-downloading everything at every launch)
                if (now - listsUpdated < WEEK_MS || now - lastAttempt < WEEK_MS / 7) return@withContext true
                val cm = ctx.getSystemService(ConnectivityManager::class.java)
                if (cm == null || cm.isActiveNetworkMetered || cm.activeNetwork == null) return@withContext true
            }
            lastAttempt = System.currentTimeMillis()
            var got = 0
            for (s in SOURCES) {
                val out = File(dir(ctx), "${s.id}.txt")
                val tmp = File(dir(ctx), "${s.id}.tmp")
                val ok = runCatching {
                    val conn = (URL(s.url).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 30000 }
                    if (conn.responseCode != 200) return@runCatching false
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    // a real list, not an error page or captive portal
                    tmp.length() > 20_000 && tmp.bufferedReader().use { it.readLine().orEmpty().startsWith("[Adblock") }
                }.getOrDefault(false)
                if (ok && tmp.renameTo(out)) got++ else tmp.delete()
            }
            if (got > 0) load(ctx)
            got > 0
        }
    }

    /** The engines [s] turns on for a page on [pageHost]: none on a site you allowed. */
    private fun enabled(s: Settings, pageHost: String?): List<FilterEngine> {
        if (pageHost != null && s.adAllowlist.any { pageHost == it || pageHost.endsWith(".$it") }) return emptyList()
        return listOfNotNull(ads.takeIf { s.blockAds }, trackers.takeIf { s.blockTrackers }, cookies.takeIf { s.hideCookieBanners })
    }

    /** Should this sub-resource request be dropped? [pageHost]: the tab's page host (null if unknown). */
    fun shouldBlock(url: String, requestHost: String, pageHost: String?, accept: String?, s: Settings): Boolean {
        val engines = enabled(s, pageHost)
        if (engines.isEmpty()) return false
        val type = FilterEngine.typeOf(url, accept)
        // unknown page (service workers fetch for their own site): treated as first-party, so third-party rules don't
        // block a site's own worker
        val third = pageHost != null && registrable(requestHost) != registrable(pageHost)
        return engines.any { it.blocks(url, requestHost, pageHost, type, third) }
    }

    /**
     * What a page or frame on [host] should hide, as JSON for assets/js/cosmetic-filter.js: "c" = site-specific CSS ("n" rules),
     * "g"/"k" = the generic ad / cookie selectors (main frame only, never iframes), "x" = generic selectors this site
     * excepts. The selector arrays are pre-serialized once per list load.
     */
    fun cosmetics(host: String, mainFrame: Boolean, s: Settings): String {
        val on = enabled(s, host)
        val a = ads.takeIf { it in on }
        val k = cookies.takeIf { it in on }
        val css = StringBuilder()
        val except = HashSet<String>()
        val n = on.sumOf { it.specificCss(host, css, except) } // trackers: EasyPrivacy's few site-specific rules
        val g = a?.takeIf { mainFrame && it.genericAllowed(host) }?.genericJson ?: "null"
        val kk = k?.takeIf { mainFrame && it.genericAllowed(host) }?.genericJson ?: "null"
        return """{"c":${JsonPrimitive(css.toString())},"n":$n,"x":${JsonArray(except.map(::JsonPrimitive))},"g":$g,"k":$kk}"""
    }

    /**
     * A page found generic selectors its WebView can't parse (each would drop its whole group of 20): drop them from
     * the lists so later pages don't pay for it. Pages are untrusted, so only selectors that really are generic count,
     * at most [MAX_BAD] per list load; the worst a page can do is switch some generic hiding off until the next load.
     */
    fun dropInvalid(json: String) {
        if (badReports >= MAX_BAD) return
        val bad = runCatching { JSONArray(json) }.getOrNull() ?: return
        val set = HashSet<String>()
        for (i in 0 until minOf(bad.length(), MAX_BAD - badReports)) set += bad.optString(i)
        badReports += set.size
        ads?.dropGeneric(set); trackers?.dropGeneric(set); cookies?.dropGeneric(set)
    }
    @Volatile private var badReports = 0
    private const val MAX_BAD = 300

    
    /** example.co.uk → "example.co.uk", ads.example.com → "example.com", alice.github.io → itself (first-/third-party). */
    fun registrable(host: String): String {
        val labels = host.lowercase().trimEnd('.').split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels[labels.size - 2] + "." + labels.last()
        return labels.takeLast(if (lastTwo in MULTI_PART_SUFFIXES) 3 else 2).joinToString(".")
    }

    /**
     * Public suffixes with two labels: country second-level domains and shared hosting where each subdomain is a
     * different owner. ponytail: the common ones only; the full Public Suffix List (~10k entries) if misjudged sites show up.
     */
    private val MULTI_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "sch.uk", "nhs.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.nz", "net.nz", "org.nz", "govt.nz", "ac.nz",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "co.kr", "or.kr", "go.kr", "com.cn", "net.cn", "org.cn", "gov.cn",
        "com.hk", "com.tw", "com.sg", "com.my", "com.ph", "com.vn", "co.th", "co.id", "co.in", "net.in", "org.in", "gov.in",
        "co.za", "org.za", "com.br", "net.br", "org.br", "gov.br", "com.mx", "com.ar", "com.co", "com.pe", "com.tr",
        "com.ua", "com.pl", "com.eg", "com.sa", "co.il", "org.il", "com.ng", "co.ke", "com.pk", "com.bd", "gob.mx", "gouv.fr",
        "github.io", "gitlab.io", "blogspot.com", "herokuapp.com", "netlify.app", "vercel.app", "pages.dev", "workers.dev",
        "web.app", "firebaseapp.com", "appspot.com", "azurewebsites.net", "cloudfront.net", "wordpress.com", "tumblr.com",
    )
}

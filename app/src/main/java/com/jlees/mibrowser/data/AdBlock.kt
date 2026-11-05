// Ad, tracker and cookie-banner blocking: EasyList, EasyPrivacy and the EasyList Cookie List (Adblock Plus syntax) plus
// the StevenBlack hosts list. Network rules block sub-resource requests; cosmetic rules hide page elements. Lists ship
// in assets/ and are refreshed weekly (on unmetered networks) into filesDir/filters/.
package com.jlees.mibrowser.data

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
     * What a page or frame on [host] should hide, as JSON for [COSMETIC_SCRIPT]: "c" = site-specific CSS ("n" rules),
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
        return """{"c":${jsonString(css.toString())},"n":$n,"x":${jsonArray(except)},"g":$g,"k":$kk}"""
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

    /**
     * Document-start script for every frame: asks mi (window.miAdBlock) what to hide and adds one stylesheet.
     *  - Simple generic selectors (".ad-box", "#promo": ~96% of them) get a rule only once an element on the page uses
     *    that class or id: found by one scan, then a MutationObserver for added nodes and class/id changes. The page's
     *    stylesheet stays small, so style updates stay cheap (the same idea as uBlock Origin's generic filtering).
     *  - Complex generic selectors go in up front, 20 to a rule; if some group fails to parse, its bad selectors are
     *    found once (querySelector throws on them), left out, and reported so mi drops them for good.
     */
    const val COSMETIC_SCRIPT = """(() => {
  if (window.__miCos) return; window.__miCos = 1;
  const ch = window.miAdBlock; if (!ch) return;
  ch.onmessage = (e) => {
    let r; try { r = JSON.parse(e.data); } catch (_) { return; }
    const x = new Set(r.x || []), simple = new Set(), complex = [], groups = [], SIMPLE = /^[.#][\w-]+$/;
    for (const list of [r.g, r.k]) if (list) for (const q of list) if (!x.has(q)) { if (SIMPLE.test(q)) simple.add(q); else complex.push(q); }
    for (let i = 0; i < complex.length; i += 20) groups.push(complex.slice(i, i + 20));
    const css = (gs) => (r.c || '') + gs.map((p) => p.join(',') + '{display:none!important}').join('\n');
    const s = document.createElement('style');
    s.textContent = css(groups);
    (document.head || document.documentElement).appendChild(s);
    if (groups.length && s.sheet && s.sheet.cssRules.length < (r.n || 0) + groups.length) {
      const bad = [];
      for (const p of groups) for (const q of p) { try { document.querySelector(q); } catch (_) { bad.push(q); } }
      if (bad.length) {
        const b = new Set(bad);
        s.textContent = css(groups.map((p) => p.filter((q) => !b.has(q))).filter((p) => p.length));
        ch.postMessage('bad:' + JSON.stringify(bad));
      }
    }
    if (!simple.size || !s.sheet) return;
    const sheet = s.sheet, done = new Set();
    const hide = (q) => { if (simple.has(q) && !done.has(q)) { done.add(q); try { sheet.insertRule(q + '{display:none!important}', sheet.cssRules.length); } catch (_) {} } };
    const check = (el) => { if (el.id) hide('#' + el.id); const cl = el.classList; if (cl) for (let i = 0; i < cl.length; i++) hide('.' + cl[i]); };
    const scan = (root) => { if (root.nodeType !== 1) return; check(root); const all = root.querySelectorAll('[id],[class]'); for (let i = 0; i < all.length; i++) check(all[i]); };
    scan(document.documentElement);
    new MutationObserver((ms) => {
      for (const m of ms) { if (m.type === 'attributes') { if (m.target.nodeType === 1) check(m.target); } else for (const n of m.addedNodes) scan(n); }
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
  };
  ch.postMessage('');
})();"""

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

/**
 * One set of Adblock Plus rules. Network rules: plain "||host^" rules (and hosts-file entries) are hash lookups; other
 * patterns are indexed by their longest safe keyword, so a URL is only checked against rules sharing one of its words.
 * Rules needing what WebView can't offer (popups, CSP, redirects, header or parameter rewriting, scriptlets) are skipped.
 */
class FilterEngine {
    private val blockHosts = HashSet<String>()
    private val blockHostsThird = HashSet<String>()
    private val allowHosts = HashSet<String>()
    private val documentAllow = HashSet<String>()   // @@||site^$document: nothing blocked or hidden on that site
    private val elemhideAllow = HashSet<String>()   // $elemhide: nothing hidden
    private val generichideAllow = HashSet<String>() // $generichide: only site-specific hiding
    private val block = HashMap<String, MutableList<NetRule>>()
    private val allow = HashMap<String, MutableList<NetRule>>()
    private val hide = HashMap<String, MutableList<String>>()          // site → selectors
    private val hideExcept = HashMap<String, MutableSet<String>>()     // site → selectors not to hide there
    private val generic = LinkedHashSet<String>()
    private val genericExcept = HashSet<String>()
    var genericSelectors: List<String> = emptyList(); private set
    /** [genericSelectors] as a JSON array, built once (sent to every main frame). */
    @Volatile var genericJson: String = "[]"; private set

    /** A hosts-list line (already lowercase, one domain per line). */
    fun addHost(line: String) {
        if (line.isNotEmpty() && line[0] != '#') blockHosts += line.trim()
    }

    fun addRule(raw: String) {
        val line = raw.trim()
        if (line.isEmpty() || line[0] == '!' || line[0] == '[') return
        // fast path, most lines: "||host^", "||host^$third-party", "@@||host^"
        val exception = line.startsWith("@@")
        val start = if (exception) 4 else 2
        if (line.startsWith("||", if (exception) 2 else 0)) {
            val third = !exception && line.endsWith("^\$third-party")
            val end = if (third) line.length - 13 else line.length - 1
            if (end > start && line[end] == '^' && isHostChars(line, start, end)) {
                val host = line.substring(start, end)
                when { exception -> allowHosts += host; third -> blockHostsThird += host; else -> blockHosts += host }
                return
            }
        }
        val hash = line.indexOf('#')
        if (hash >= 0 && line.length > hash + 1 && (line[hash + 1] == '#' || line[hash + 1] == '@' || line[hash + 1] == '?' || line[hash + 1] == '$' || line[hash + 1] == '%')) {
            addCosmetic(line, hash); return
        }
        addNetwork(line)
    }

    private fun addCosmetic(line: String, hash: Int) {
        val except = when {
            line.startsWith("##", hash) -> false
            line.startsWith("#@#", hash) -> true
            else -> return // #?# #$# #%# #@?#: procedural, CSS injection or scriptlets
        }
        val selector = line.substring(hash + if (except) 3 else 2).trim()
        if (selector.isEmpty() || selector.startsWith("+js(") || selector[0] == '^' || (selector.indexOf(':') >= 0 && PROCEDURAL.any { selector.contains(it) })) return
        if (selector.indexOf('{') >= 0 || selector.indexOf('}') >= 0) return // only selectors, never extra CSS rules
        if (hash == 0) { if (except) genericExcept += selector else generic += selector; return }
        var included = false
        var i = 0
        while (i < hash) { // "a.com,~b.a.com,c.com"
            var j = line.indexOf(',', i).let { if (it < 0 || it > hash) hash else it }
            val neg = line[i] == '~'
            val d = line.substring(if (neg) i + 1 else i, j).trim().lowercase()
            if (d.isNotEmpty()) {
                if (neg || except) hideExcept.getOrPut(d) { HashSet() } += selector
                else { hide.getOrPut(d) { ArrayList(2) } += selector; included = true }
            }
            i = j + 1
        }
        if (!included && !except) generic += selector // only exclusions: generic, minus those sites
    }

    /** Folds [other]'s rules into this engine (lists are parsed in parallel, then merged). */
    fun merge(other: FilterEngine) {
        blockHosts += other.blockHosts; blockHostsThird += other.blockHostsThird; allowHosts += other.allowHosts
        documentAllow += other.documentAllow; elemhideAllow += other.elemhideAllow; generichideAllow += other.generichideAllow
        other.block.forEach { (k, v) -> block.getOrPut(k) { ArrayList(v.size) } += v }
        other.allow.forEach { (k, v) -> allow.getOrPut(k) { ArrayList(v.size) } += v }
        other.hide.forEach { (k, v) -> hide.getOrPut(k) { ArrayList(v.size) } += v }
        other.hideExcept.forEach { (k, v) -> hideExcept.getOrPut(k) { HashSet() } += v }
        generic += other.generic; genericExcept += other.genericExcept
    }

    private fun addNetwork(line: String) {
        val exception = line.startsWith("@@")
        var body = if (exception) line.substring(2) else line
        if (body.length > 1 && body.startsWith("/") && body.endsWith("/")) return // regex rule
        var third = 0; var include = 0; var exclude = 0; var matchCase = false; var important = false
        var inc: List<String>? = null; var exc: List<String>? = null
        var docOpt = false; var elemhide = false; var generichide = false
        val dollar = body.lastIndexOf('$')
        if (dollar >= 0 && dollar < body.length - 1 && !body.substring(dollar + 1).contains('/')) {
            for (opt in body.substring(dollar + 1).split(',')) {
                val neg = opt.startsWith("~")
                val name = opt.removePrefix("~").substringBefore('=').lowercase()
                when (name) {
                    "third-party", "3p" -> third = if (neg) 2 else 1
                    "first-party", "1p" -> third = if (neg) 1 else 2
                    "domain", "from" -> {
                        val ds = opt.substringAfter('=').split('|').map { it.lowercase() }
                        inc = ds.filterNot { it.startsWith("~") }.takeIf { it.isNotEmpty() }
                        exc = ds.filter { it.startsWith("~") }.map { it.substring(1) }.takeIf { it.isNotEmpty() }
                    }
                    "match-case" -> matchCase = true
                    "important" -> important = true
                    "all" -> {}
                    "document", "doc" -> docOpt = true
                    "elemhide", "ehide" -> elemhide = true
                    "generichide", "ghide" -> generichide = true
                    else -> {
                        val t = TYPES[name] ?: return // popup, csp, redirect, removeparam, rewrite, badfilter…: skip the rule
                        if (neg) exclude = exclude or t else include = include or t
                    }
                }
            }
            body = body.substring(0, dollar)
        }
        if (docOpt || elemhide || generichide) {
            // only meaningful as site exceptions ("@@||site^$document"); blocking whole pages isn't done
            if (!exception) return
            val site = HOST_RULE.find(body + (if (body.endsWith("^")) "" else "^"))?.groupValues?.get(1) ?: return
            when { docOpt -> documentAllow += site; elemhide -> elemhideAllow += site; else -> generichideAllow += site }
            return
        }
        if (body.isEmpty() || body == "*") return
        // plain host rules (most of EasyList / EasyPrivacy): hash lookups
        val host = HOST_RULE.matchEntire(body)?.groupValues?.get(1)
        if (host != null && include == 0 && exclude == 0 && inc == null && exc == null && !important && third != 2) {
            when {
                exception -> if (third == 0) { allowHosts += host; return }
                third == 1 -> { blockHostsThird += host; return }
                else -> { blockHosts += host; return }
            }
        }
        val rule = NetRule(if (matchCase) body else body.lowercase(), matchCase, third, include, exclude, inc?.toTypedArray(), exc?.toTypedArray(), important)
        (if (exception) allow else block).getOrPut(keyword(rule.pattern)) { ArrayList(2) } += rule
    }

    fun finish() {
        genericSelectors = generic.filterNot { it in genericExcept }
        generic.clear()
        genericJson = jsonArray(genericSelectors)
    }

    /** Leaves out generic selectors this WebView can't parse (see [AdBlock.dropInvalid]). */
    fun dropGeneric(bad: Set<String>) {
        if (bad.none { it in genericSelectors }) return
        genericSelectors = genericSelectors.filterNot { it in bad }
        genericJson = jsonArray(genericSelectors)
    }

    fun blocks(url: String, host: String, pageHost: String?, type: Int, third: Boolean): Boolean {
        if (pageHost != null && suffixIn(pageHost, documentAllow)) return false
        val h = host.lowercase()
        val lower = url.lowercase()
        var hit = suffixIn(h, blockHosts) || (third && suffixIn(h, blockHostsThird))
        if (!hit) {
            val r = find(block, url, lower, pageHost, type, third) ?: return false
            if (r.important) return true
            hit = true
        }
        return hit && !suffixIn(h, allowHosts) && find(allow, url, lower, pageHost, type, third) == null
    }

    fun genericAllowed(host: String) = !suffixIn(host, documentAllow) && !suffixIn(host, elemhideAllow) && !suffixIn(host, generichideAllow)

    /** Appends [host]'s site-specific hiding CSS to [css] and its generic exceptions to [except]; returns the rule count. */
    fun specificCss(host: String, css: StringBuilder, except: MutableSet<String>): Int {
        if (suffixIn(host, documentAllow) || suffixIn(host, elemhideAllow)) return 0
        val notHere = HashSet<String>()
        forSuffixes(host) { d -> hideExcept[d]?.let { notHere += it } }
        except += notHere
        var n = 0
        forSuffixes(host) { d ->
            hide[d]?.forEach { sel -> if (sel !in notHere) { css.append(sel).append("{display:none!important}\n"); n++ } }
        }
        return n
    }

    private fun find(index: HashMap<String, MutableList<NetRule>>, url: String, lower: String, pageHost: String?, type: Int, third: Boolean): NetRule? {
        index[""]?.firstOrNull { it.matches(url, lower, pageHost, type, third) }?.let { return it }
        var i = 0
        val n = lower.length
        while (i < n) {
            while (i < n && !isTokenChar(lower[i])) i++
            val s = i
            while (i < n && isTokenChar(lower[i])) i++
            // a repeated word is looked up again; cheaper than tracking which were seen
            if (i - s >= 2) index[lower.substring(s, i)]?.firstOrNull { it.matches(url, lower, pageHost, type, third) }?.let { return it }
        }
        return null
    }

    /** The longest word in a pattern that's sure to be a whole word in any URL it matches ("" = check always). */
    private fun keyword(p: String): String {
        val anchoredStart = p.startsWith("|")
        val anchoredEnd = p.endsWith("|") || p.endsWith("^")
        var best = ""
        var i = 0
        while (i < p.length) {
            while (i < p.length && !isTokenChar(p[i])) i++
            val s = i
            while (i < p.length && isTokenChar(p[i])) i++
            if (i == s) continue
            val before = if (s == 0) anchoredStart else p[s - 1] != '*'
            val after = if (i == p.length) anchoredEnd else p[i] != '*'
            val t = p.substring(s, i)
            if (before && after && t.length > best.length && t !in COMMON) best = t
        }
        return if (best.length >= 2) best else "" // URLs are scanned for words of 2+ characters
    }

    companion object {
        const val SCRIPT = 1; const val IMAGE = 2; const val STYLESHEET = 4; const val XHR = 8; const val SUBDOCUMENT = 16
        const val MEDIA = 32; const val FONT = 64; const val OTHER = 128; const val PING = 256; const val WEBSOCKET = 512; const val OBJECT = 1024
        private val TYPES = mapOf(
            "script" to SCRIPT, "image" to IMAGE, "stylesheet" to STYLESHEET, "css" to STYLESHEET,
            "xmlhttprequest" to XHR, "xhr" to XHR, "subdocument" to SUBDOCUMENT, "frame" to SUBDOCUMENT,
            "media" to MEDIA, "font" to FONT, "other" to OTHER, "ping" to PING, "beacon" to PING,
            "websocket" to WEBSOCKET, "object" to OBJECT, "object-subrequest" to OBJECT,
        )
        private val HOST_RULE = Regex("^\\|\\|([a-z0-9.-]+)\\^$")
        private val COMMON = setOf("http", "https", "www", "com", "net", "org", "html", "php", "js")
        private val PROCEDURAL = listOf(":-abp-", ":has-text(", ":contains(", ":xpath(", ":matches-", ":upward(", ":remove(", ":style(",
            ":min-text-length(", ":watch-attr(", ":others(", ":if(", ":if-not(", ":nth-ancestor(", ":remove-attr(", ":remove-class(")
        private val EXT_TYPES = mapOf(
            "js" to SCRIPT, "mjs" to SCRIPT, "css" to STYLESHEET,
            "png" to IMAGE, "jpg" to IMAGE, "jpeg" to IMAGE, "gif" to IMAGE, "webp" to IMAGE, "svg" to IMAGE, "ico" to IMAGE, "avif" to IMAGE, "bmp" to IMAGE,
            "woff" to FONT, "woff2" to FONT, "ttf" to FONT, "otf" to FONT, "eot" to FONT,
            "mp4" to MEDIA, "webm" to MEDIA, "mp3" to MEDIA, "m3u8" to MEDIA, "m4a" to MEDIA, "ogg" to MEDIA, "ts" to MEDIA,
        )

        fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%' || c in 'A'..'Z'

        private fun isHostChars(s: String, from: Int, to: Int): Boolean {
            for (i in from until to) { val c = s[i]; if (!(c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-')) return false }
            return true
        }

        /** WebView doesn't say what a request is for: guessed from the path's extension, then the Accept header. */
        fun typeOf(url: String, accept: String?): Int {
            val path = url.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
            EXT_TYPES[ext]?.let { return it }
            val a = accept.orEmpty()
            return when {
                a.startsWith("text/css") -> STYLESHEET
                a.startsWith("image/") -> IMAGE
                a.startsWith("text/html") -> SUBDOCUMENT
                // Accept */*: a fetch, or a script without a .js name (trackers often load like that)
                else -> SCRIPT or XHR or OTHER
            }
        }

        fun suffixIn(host: String, set: Set<String>): Boolean {
            if (set.isEmpty()) return false
            var h = host
            while (true) {
                if (h in set) return true
                val dot = h.indexOf('.')
                if (dot < 0) return false
                h = h.substring(dot + 1)
            }
        }

        private inline fun forSuffixes(host: String, f: (String) -> Unit) {
            var h = host.lowercase()
            while (true) {
                f(h)
                val dot = h.indexOf('.')
                if (dot < 0) return
                h = h.substring(dot + 1)
            }
        }
    }
}

/** JSON for strings and string lists (kept free of org.json, which unit tests can't use). */
fun jsonString(s: String): String = buildString(s.length + 2) {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c < ' ' || c == '\u2028' || c == '\u2029' -> append("\\u").append(String.format("%04x", c.code))
        else -> append(c)
    }
    append('"')
}
fun jsonArray(items: Collection<String>): String = items.joinToString(",", "[", "]", transform = ::jsonString)

/** One pattern rule. The regex is built the first time a URL shares the rule's keyword (most never are). */
class NetRule(
    val pattern: String, private val matchCase: Boolean, private val third: Int, private val include: Int, private val exclude: Int,
    private val domains: Array<String>?, private val notDomains: Array<String>?, val important: Boolean,
) {
    @Volatile private var regex: Regex? = null

    fun matches(url: String, lower: String, pageHost: String?, type: Int, isThird: Boolean): Boolean {
        if (third == 1 && !isThird || third == 2 && isThird) return false
        if (include != 0 && type and include == 0) return false
        if (exclude != 0 && type and exclude.inv() == 0) return false
        if (domains != null || notDomains != null) {
            val ph = pageHost?.lowercase() ?: return false
            if (notDomains?.any { ph == it || ph.endsWith(".$it") } == true) return false
            if (domains != null && domains.none { ph == it || ph.endsWith(".$it") }) return false
        }
        val re = regex ?: toRegex(pattern).also { regex = it }
        return re.containsMatchIn(if (matchCase) url else lower)
    }

    private fun toRegex(p: String): Regex {
        val sb = StringBuilder()
        var i = 0
        var end = p.length
        if (p.startsWith("||")) { sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); i = 2 }
        else if (p.startsWith("|")) { sb.append('^'); i = 1 }
        val anchoredEnd = end > i && p[end - 1] == '|'
        if (anchoredEnd) end--
        while (i < end) {
            when (val c = p[i]) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^a-zA-Z0-9_.%-]|$)")
                else -> { if ("\\.+?()[]{}|\$".indexOf(c) >= 0) sb.append('\\'); sb.append(c) }
            }
            i++
        }
        if (anchoredEnd) sb.append('$')
        return Regex(sb.toString())
    }
}

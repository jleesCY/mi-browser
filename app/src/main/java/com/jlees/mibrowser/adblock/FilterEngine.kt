// The Adblock Plus rule engine: parsing network and cosmetic rules, and matching requests and pages against them.
package com.jlees.mibrowser.adblock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.net.URL

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
        genericJson = JsonArray(genericSelectors.map(::JsonPrimitive)).toString()
    }

    /** Leaves out generic selectors this WebView can't parse (see [AdBlock.dropInvalid]). */
    fun dropGeneric(bad: Set<String>) {
        if (bad.none { it in genericSelectors }) return
        genericSelectors = genericSelectors.filterNot { it in bad }
        genericJson = JsonArray(genericSelectors.map(::JsonPrimitive)).toString()
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

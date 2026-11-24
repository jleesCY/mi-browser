// Decisions about web requests for extensions, as pure functions: what kind of request it is (chrome.webRequest's
// "type"), whether it's third-party, and declarativeNetRequest: its rules, how a rule's condition matches a request
// (urlFilter, regexFilter, domains, types, methods), and which rule wins (priority, then allow over block over redirect).
// Unit-tested in ExtensionNetRulesTest; ExtensionRequests applies them to real requests.
package com.jlees.mibrowser.extensions

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- requests

/** A request as rules see it. [initiator]: the page (or frame) that made it, as an address, or null. */
data class NetRequest(
    val url: String,
    val method: String,
    /** chrome.webRequest's type: main_frame, sub_frame, script, stylesheet, image, font, media, xmlhttprequest, object, ping, csp_report, other. */
    val type: String,
    val initiator: String?,
    val tabId: Int,
) {
    // worked out once per request, not once per rule tried (a list has tens of thousands)
    val host by lazy { hostOf(url) }
    val initiatorHost by lazy { hostOf(initiator) }
    val thirdParty by lazy { isThirdParty(url, initiator) }
}

/** A request's type, from the headers WebView gives (Sec-Fetch-Dest, Accept) and, failing those, its address. */
fun requestType(mainFrame: Boolean, fetchDest: String?, accept: String?, url: String): String {
    if (mainFrame) return "main_frame"
    when (fetchDest?.lowercase()) {
        "document", "iframe", "frame", "fencedframe" -> return "sub_frame"
        "script", "worker", "sharedworker", "serviceworker", "audioworklet", "paintworklet" -> return "script"
        "style" -> return "stylesheet"
        "image" -> return "image"
        "font" -> return "font"
        "audio", "video", "track" -> return "media"
        "object", "embed" -> return "object"
        "empty" -> return "xmlhttprequest"
        "report" -> return "csp_report"
        "manifest", "webidentity", "xslt" -> return "other"
    }
    val a = accept.orEmpty().lowercase()
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        a.startsWith("text/css") || path.endsWith(".css") -> "stylesheet"
        a.startsWith("image/") || Regex("""\.(png|jpe?g|gif|webp|avif|svg|ico|bmp)$""").containsMatchIn(path) -> "image"
        Regex("""\.(m?js)$""").containsMatchIn(path) -> "script"
        Regex("""\.(woff2?|ttf|otf|eot)$""").containsMatchIn(path) -> "font"
        Regex("""\.(mp4|webm|m3u8|mp3|m4a|ogg|ts)$""").containsMatchIn(path) -> "media"
        a.startsWith("text/html") -> "sub_frame"
        else -> "other"
    }
}

/** The host of [url], lowercased, or null. */
fun hostOf(url: String?): String? = url?.let { HOST.find(it)?.groupValues?.get(1)?.lowercase() }
/** [url]'s origin ("https://a.com:8080"), or null. */
fun originOf(url: String): String? = runCatching { java.net.URI(url).let { u -> u.authority?.let { "${u.scheme}://$it" } } }.getOrNull()
private val HOST = Regex("""^[a-z][a-z0-9+.-]*://(?:[^@/?#]*@)?([^:/?#]+)""", RegexOption.IGNORE_CASE)

/** Whether [url] is a third-party request for a page at [initiator] (a different site). */
fun isThirdParty(url: String, initiator: String?): Boolean {
    val a = hostOf(url) ?: return false
    val b = hostOf(initiator) ?: return false
    return com.jlees.mibrowser.adblock.AdBlock.registrable(a) != com.jlees.mibrowser.adblock.AdBlock.registrable(b)
}

/** Whether [host] is [domain] or one of its subdomains. */
/** Whether [host] is [domain] or under it ("a.example.com" in "example.com"); case aside. Allocates nothing: rules list thousands. */
fun hostIn(host: String, domain: String): Boolean {
    val n = domain.length; val at = host.length - n
    return at >= 0 && host.regionMatches(at, domain, 0, n, ignoreCase = true) && (at == 0 || host[at - 1] == '.')
}

// ---------------------------------------------------------------- declarativeNetRequest

@Serializable data class DnrHeader(val header: String, val operation: String, val value: String? = null)
@Serializable data class DnrParam(val key: String, val value: String, val replaceOnly: Boolean = false)
@Serializable data class DnrQueryTransform(val removeParams: List<String>? = null, val addOrReplaceParams: List<DnrParam>? = null)
@Serializable data class DnrTransform(
    val scheme: String? = null, val host: String? = null, val port: String? = null, val path: String? = null,
    val query: String? = null, val queryTransform: DnrQueryTransform? = null, val fragment: String? = null,
    val username: String? = null, val password: String? = null,
)
@Serializable data class DnrRedirect(val url: String? = null, val extensionPath: String? = null, val transform: DnrTransform? = null, val regexSubstitution: String? = null)
@Serializable data class DnrAction(
    val type: String,
    val redirect: DnrRedirect? = null,
    val requestHeaders: List<DnrHeader>? = null,
    val responseHeaders: List<DnrHeader>? = null,
)
@Serializable data class DnrCondition(
    val urlFilter: String? = null,
    val regexFilter: String? = null,
    val isUrlFilterCaseSensitive: Boolean = false,
    val requestDomains: List<String>? = null,
    val excludedRequestDomains: List<String>? = null,
    val initiatorDomains: List<String>? = null,
    val excludedInitiatorDomains: List<String>? = null,
    /** Older names for initiatorDomains / excludedInitiatorDomains. */
    val domains: List<String>? = null,
    val excludedDomains: List<String>? = null,
    val resourceTypes: List<String>? = null,
    val excludedResourceTypes: List<String>? = null,
    val requestMethods: List<String>? = null,
    val excludedRequestMethods: List<String>? = null,
    /** firstParty or thirdParty. */
    val domainType: String? = null,
    val tabIds: List<Int>? = null,
    val excludedTabIds: List<Int>? = null,
)
@Serializable data class DnrRule(val id: Int, val priority: Int = 1, val action: DnrAction, val condition: DnrCondition = DnrCondition())

/** A urlFilter ("||ads.example.com^", "|https://a.com/x*", "banner") as a regex: || anchors at a domain, | at an end, ^ a separator. */
fun urlFilterRegex(filter: String, caseSensitive: Boolean): Regex {
    var f = filter
    val sb = StringBuilder()
    when {
        f.startsWith("||") -> { sb.append("""^[a-z][a-z0-9+.-]*://(?:[^/?#]*\.)?"""); f = f.drop(2) }
        f.startsWith("|") -> { sb.append('^'); f = f.drop(1) }
    }
    val endAnchor = f.endsWith("|") && f.length > 0
    if (endAnchor) f = f.dropLast(1)
    for (c in f) when (c) {
        '*' -> sb.append(".*")
        '^' -> sb.append("""(?:[^a-zA-Z0-9_\-.%]|$)""")
        else -> sb.append(Regex.escape(c.toString()))
    }
    if (endAnchor) sb.append('$')
    return if (caseSensitive) Regex(sb.toString()) else Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

/** Resource types a rule applies to when it names none: everything but the main frame (as Chrome does). */
private val DEFAULT_TYPES_EXCLUDE = setOf("main_frame")

/**
 * Whether [rule]'s condition matches [r]. [regex]: the rule's compiled url pattern (cached by the caller), if it has one.
 * [domainKnown]: the request's host is already known to be in the rule's requestDomains (the index found it there).
 */
fun dnrMatches(rule: DnrRule, r: NetRequest, regex: Regex?, domainKnown: Boolean = false): Boolean {
    val c = rule.condition
    val host = r.host ?: return false
    val initiatorHost = r.initiatorHost
    if (c.resourceTypes != null) { if (r.type !in c.resourceTypes) return false }
    else if (r.type in DEFAULT_TYPES_EXCLUDE && rule.action.type != "allowAllRequests") return false
    if (c.excludedResourceTypes?.contains(r.type) == true) return false
    if (c.requestMethods != null && c.requestMethods.none { it.equals(r.method, true) }) return false
    if (c.excludedRequestMethods?.any { it.equals(r.method, true) } == true) return false
    if (c.tabIds != null && r.tabId !in c.tabIds) return false
    if (c.excludedTabIds?.contains(r.tabId) == true) return false
    if (!domainKnown && c.requestDomains != null && c.requestDomains.none { hostIn(host, it) }) return false
    if (c.excludedRequestDomains?.any { hostIn(host, it) } == true) return false
    val initiators = c.initiatorDomains ?: c.domains
    if (initiators != null && (initiatorHost == null || initiators.none { hostIn(initiatorHost, it) })) return false
    val excludedInitiators = c.excludedInitiatorDomains ?: c.excludedDomains
    if (initiatorHost != null && excludedInitiators?.any { hostIn(initiatorHost, it) } == true) return false
    when (c.domainType) {
        "firstParty" -> if (r.thirdParty) return false
        "thirdParty" -> if (!r.thirdParty) return false
    }
    if (regex != null && !regex.containsMatchIn(r.url)) return false
    return true
}

/** The compiled pattern for [rule]: its regexFilter, else its urlFilter, else none (every address). */
fun dnrRegex(rule: DnrRule): Regex? {
    val c = rule.condition
    return when {
        c.regexFilter != null -> runCatching { if (c.isUrlFilterCaseSensitive) Regex(c.regexFilter) else Regex(c.regexFilter, RegexOption.IGNORE_CASE) }.getOrNull() ?: Regex("(?!)")
        c.urlFilter != null -> urlFilterRegex(c.urlFilter, c.isUrlFilterCaseSensitive)
        else -> null
    }
}

/** What declarativeNetRequest decides for a request. */
sealed interface DnrOutcome {
    data object None : DnrOutcome
    data class Block(val ext: String) : DnrOutcome
    data class Allow(val ext: String) : DnrOutcome
    data class Redirect(val ext: String, val url: String) : DnrOutcome
    data class Headers(val ext: String, val request: List<DnrHeader>, val response: List<DnrHeader>) : DnrOutcome
}

/** Chrome's order among rules of the same priority: allow first, then block, upgrade, redirect, then header changes. */
private fun typeRank(type: String) = when (type) { "allow" -> 0; "allowAllRequests" -> 1; "block" -> 2; "upgradeScheme" -> 3; "redirect" -> 4; "modifyHeaders" -> 5; else -> 6 }

/** The winning action among [matches] (extension id to rule, all matching): highest priority, then [typeRank]. */
fun dnrDecide(matches: List<Pair<String, DnrRule>>, r: NetRequest, extensionOrigin: (String) -> String): DnrOutcome {
    val top = matches.filter { it.second.action.type != "modifyHeaders" }
        .sortedWith(compareByDescending<Pair<String, DnrRule>> { it.second.priority }.thenBy { typeRank(it.second.action.type) }.thenBy { it.second.id })
        .firstOrNull()
    val headerRules = matches.filter { it.second.action.type == "modifyHeaders" && (top == null || it.second.priority >= top.second.priority) }
    when (top?.second?.action?.type) {
        "allow", "allowAllRequests" -> return if (headerRules.any { it.second.priority > top.second.priority }) headersOf(headerRules) else DnrOutcome.Allow(top.first)
        "block" -> return DnrOutcome.Block(top.first)
        "upgradeScheme" -> if (r.url.startsWith("http://")) return DnrOutcome.Redirect(top.first, "https://" + r.url.removePrefix("http://"))
        "redirect" -> redirectTarget(top.second, r.url, extensionOrigin(top.first))?.let { if (it != r.url) return DnrOutcome.Redirect(top.first, it) }
    }
    return if (headerRules.isNotEmpty()) headersOf(headerRules) else DnrOutcome.None
}

private fun headersOf(rules: List<Pair<String, DnrRule>>) = DnrOutcome.Headers(
    rules.first().first,
    rules.sortedByDescending { it.second.priority }.flatMap { it.second.action.requestHeaders.orEmpty() },
    rules.sortedByDescending { it.second.priority }.flatMap { it.second.action.responseHeaders.orEmpty() },
)

/** Where a redirect rule sends [url]: a fixed address, a file of the extension, a regex substitution, or a transformed [url]. */
fun redirectTarget(rule: DnrRule, url: String, origin: String): String? {
    val red = rule.action.redirect ?: return null
    red.url?.let { return it }
    red.extensionPath?.let { return origin + "/" + it.trimStart('/') }
    red.regexSubstitution?.let { sub ->
        val re = rule.condition.regexFilter?.let { runCatching { Regex(it, if (rule.condition.isUrlFilterCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)) }.getOrNull() } ?: return null
        val m = re.find(url) ?: return null
        return Regex("""\\(\d)""").replace(sub) { g -> m.groupValues.getOrNull(g.groupValues[1].toInt()).orEmpty() }
    }
    val t = red.transform ?: return null
    val u = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val scheme = t.scheme ?: u.scheme
    val host = t.host ?: u.host
    val port = t.port?.let { if (it.isEmpty()) -1 else it.toIntOrNull() } ?: u.port
    val path = t.path ?: u.rawPath
    var query = t.query?.removePrefix("?") ?: u.rawQuery
    t.queryTransform?.let { qt ->
        val params = query.orEmpty().split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') to it.substringAfter('=', "") }.toMutableList()
        qt.removeParams?.let { rm -> params.removeAll { it.first in rm } }
        qt.addOrReplaceParams?.forEach { p ->
            val i = params.indexOfFirst { it.first == p.key }
            if (i >= 0) params[i] = p.key to p.value else if (!p.replaceOnly) params += p.key to p.value
        }
        query = params.joinToString("&") { if (it.second.isEmpty()) it.first else "${it.first}=${it.second}" }.ifEmpty { null }
    }
    val fragment = t.fragment?.removePrefix("#") ?: u.rawFragment
    return buildString {
        append(scheme).append("://").append(host)
        if (port != -1 && port != null) append(':').append(port)
        append(path.ifEmpty { "/" })
        if (!query.isNullOrEmpty()) append('?').append(query)
        if (!fragment.isNullOrEmpty()) append('#').append(fragment)
    }
}

/** [headers] (name to value, in order) changed by declarativeNetRequest's [ops]: set, append or remove each, names compared without case. */
fun applyHeaderOps(headers: List<Pair<String, String>>, ops: List<DnrHeader>): List<Pair<String, String>> {
    var out = headers
    for (op in ops) out = when (op.operation) {
        "remove" -> out.filterNot { it.first.equals(op.header, true) }
        "set" -> out.filterNot { it.first.equals(op.header, true) } + (op.header to op.value.orEmpty())
        "append" -> out.indexOfFirst { it.first.equals(op.header, true) }.let { i ->
            if (i < 0) out + (op.header to op.value.orEmpty())
            else out.toMutableList().also { it[i] = it[i].first to (it[i].second + (if (op.header.equals("cookie", true)) "; " else ", ") + op.value.orEmpty()) }
        }
        else -> out
    }
    return out
}

/**
 * Rules indexed for speed: a rule anchored to a domain ("||ads.example.com^") or limited to request domains is only
 * tried for requests to that domain; any other url filter only for addresses containing one of its words ("/ads/banner"
 * needs the word "banner" in the address, as uBlock Origin indexes its filters); the rest are tried for every request.
 * Large lists (uBlock Origin Lite's are tens of thousands of rules) stay fast.
 */
class DnrIndex(rules: List<Pair<String, DnrRule>>) {
    /** A rule and its compiled pattern (made the first time the rule is tried; most never are). */
    private class Entry(val ext: String, val rule: DnrRule) {
        private var compiled: Any? = UNSET
        val regex: Regex? get() { if (compiled === UNSET) compiled = dnrRegex(rule); return compiled as Regex? }
        /** Indexed under one of its requestDomains, so a request found through that key needn't search the list again. */
        var byRequestDomain = false
    }
    // a key holds its one Entry itself, or a list when several share it: lists do have tens of thousands of domains, and
    // almost every one belongs to a single rule, so a list per key used to cost tens of megabytes
    private val byDomain = HashMap<String, Any>()
    private val byWord = HashMap<String, Any>()
    private val generic = ArrayList<Entry>()
    val size = rules.size

    init {
        for ((ext, rule) in rules) {
            val e = Entry(ext, rule)
            val anchor = rule.condition.urlFilter?.takeIf { it.startsWith("||") }?.drop(2)?.takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' }?.lowercase()?.trimEnd('.')
            val domains = rule.condition.requestDomains
            when {
                !anchor.isNullOrEmpty() && anchor.contains('.') -> add(byDomain, anchor, e)
                domains != null && domains.isNotEmpty() -> { e.byRequestDomain = true; domains.forEach { add(byDomain, lower(it), e) } }
                else -> rule.condition.urlFilter?.takeIf { rule.condition.regexFilter == null }?.let(::filterWord)?.let { add(byWord, it, e) } ?: run { generic += e }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun add(map: HashMap<String, Any>, key: String, e: Entry) {
        when (val had = map[key]) {
            null -> map[key] = e
            is Entry -> map[key] = arrayListOf(had, e)
            else -> (had as ArrayList<Entry>) += e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun each(v: Any?, f: (Entry) -> Unit) { when (v) { null -> {}; is Entry -> f(v); else -> (v as List<Entry>).forEach(f) } }

    /** Every rule matching [r], as (extension id, rule). */
    fun matches(r: NetRequest): List<Pair<String, DnrRule>> {
        val host = hostOf(r.url) ?: return emptyList()
        val out = ArrayList<Pair<String, DnrRule>>()
        // the host and each domain above it ("a.b.example.com", "b.example.com", "example.com")
        var h = host
        while (true) {
            each(byDomain[h]) { if (dnrMatches(it.rule, r, it.regex, domainKnown = it.byRequestDomain)) out += it.ext to it.rule }
            val dot = h.indexOf('.'); if (dot < 0) break
            h = h.substring(dot + 1)
        }
        if (byWord.isNotEmpty()) for (w in r.url.lowercase().split(NOT_WORD).toSet()) each(byWord[w]) { if (dnrMatches(it.rule, r, it.regex)) out += it.ext to it.rule }
        generic.forEach { if (dnrMatches(it.rule, r, it.regex)) out += it.ext to it.rule }
        return out.distinctBy { it.first to it.second.id }
    }

    private companion object {
        val UNSET = Any()
        /** [s] lowercased, the same string when it already is (so a rule's domains and the keys share their text). */
        fun lower(s: String) = if (s.any { it.isUpperCase() }) s.lowercase() else s
    }
}

private val NOT_WORD = Regex("[^a-z0-9]+")
private val WORD = Regex("[a-z0-9]+")

/**
 * The longest word (letters and digits, 3 or more) a url filter's matches always contain whole: one with a plain
 * character, a separator (^) or an anchor (|, ||) on both sides, so it's a whole word of the address too ("banner" in
 * "/ads/banner.js"; not "ads" in "*ads/" nor "js" at an open end). Null: none, the rule is tried for every request.
 */
internal fun filterWord(filter: String): String? {
    val f = filter.lowercase()
    var best: String? = null
    for (m in WORD.findAll(f)) {
        val before = f.getOrNull(m.range.first - 1); val after = f.getOrNull(m.range.last + 1)
        val startOk = if (before == null) false else before != '*'      // a filter's start (no |) may begin mid-word
        val endOk = if (after == null) false else after != '*' && !(after == '|' && m.range.last + 2 < f.length)
        if (startOk && endOk && m.value.length >= 3 && m.value.length > (best?.length ?: 0)) best = m.value
    }
    return best
}

// ---------------------------------------------------------------- a site's Content Security Policy

/** The sources mi serves extensions' files from, as a CSP source. */
const val EXT_CSP_SOURCE = "https://*.ext.mi.invalid"

/** Directives that load what extensions add to pages (scripts, styles, fonts, pictures, media, frames, requests). */
private val EXT_DIRECTIVES = setOf("default-src", "script-src", "script-src-elem", "style-src", "style-src-elem", "style-src-attr",
    "font-src", "img-src", "media-src", "frame-src", "child-src", "connect-src")
private val STYLE_DIRECTIVES = setOf("style-src", "style-src-elem", "style-src-attr")
/** Nonces and hashes: where a style directive has one, 'unsafe-inline' is ignored, so they give way to it. */
private val NONCE_OR_HASH = Regex("""^'(nonce-|sha256-|sha384-|sha512-)""")

/**
 * [policy] (a Content-Security-Policy header's value) with what extensions add to a page allowed, as a browser exempts
 * extensions from a site's policy: their files load from mi's address (scripts too; a 'strict-dynamic' script policy
 * ignores addresses, so there extensions fall back to running their files through mi), and their inline styles apply
 * (Dark Reader's), so style directives allow 'unsafe-inline' without the nonces and hashes that would cancel
 * it; a policy limiting styles only through default-src gets a style-src of its own. Trusted Types are no longer required
 * (YouTube's): extensions set innerHTML and script text with plain strings, which their own world allows in a browser.
 * Everything else stays as it is. Several policies (comma-separated) are each changed.
 */
fun allowExtensionsIn(policy: String): String = policy.split(',').joinToString(",") { one ->
    val parts = one.split(';').filterNot { it.trim().substringBefore(' ').equals("require-trusted-types-for", true) }
    val names = parts.map { it.trim().substringBefore(' ').lowercase() }
    val changed = parts.map { part ->
        val d = part.trim()
        val name = d.substringBefore(' ').lowercase()
        if (name !in EXT_DIRECTIVES) return@map part
        var sources = d.substringAfter(' ', "").split(' ').filter { it.isNotBlank() && it != "'none'" }
        if (name in STYLE_DIRECTIVES) sources = sources.filterNot { NONCE_OR_HASH.containsMatchIn(it) } + listOf("'unsafe-inline'").filter { it !in sources }
        if (EXT_CSP_SOURCE !in sources) sources = sources + EXT_CSP_SOURCE
        // extensions read data: addresses (Dark Reader fetches a page's inline SVG pictures to see how dark they are)
        if (name == "connect-src" && "data:" !in sources) sources = sources + "data:"
        (if (part.startsWith(" ")) " " else "") + (listOf(name) + sources).joinToString(" ")
    }
    // styles or requests limited only by default-src: a directive of their own with the same sources, relaxed as above
    val defaultSrc = changed.firstOrNull { it.trim().startsWith("default-src") }?.trim()
    val added = if (defaultSrc == null) emptyList() else listOfNotNull(
        "style-src".takeIf { "style-src" !in names && "style-src-elem" !in names },
        "connect-src".takeIf { it !in names },
    ).map { " " + allowExtensionsIn(defaultSrc.replaceFirst("default-src", it)) }
    (changed + added).joinToString(";")
}

private val META_CSP = Regex("""<meta\b[^>]*http-equiv\s*=\s*["']?content-security-policy["']?[^>]*>""", RegexOption.IGNORE_CASE)
private val META_CONTENT = Regex("""(content\s*=\s*)(["'])(.*?)\2""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** A page's own policies, set in its HTML (`<meta http-equiv="Content-Security-Policy">`, as addons.mozilla.org does), relaxed as [allowExtensionsIn] does a header's. */
fun relaxMetaPolicies(html: String): String = META_CSP.replace(html) { tag ->
    META_CONTENT.replace(tag.value) { m ->
        val policy = m.groupValues[3].replace("&#39;", "'").replace("&apos;", "'")
        m.groupValues[1] + m.groupValues[2] + allowExtensionsIn(policy).let { if (m.groupValues[2] == "'") it.replace("'", "&#39;") else it } + m.groupValues[2]
    }
}

/** A bot check's answer to a request (Cloudflare's challenge, a 403/429/503 from an edge that checks browsers). */
fun isBotCheck(code: Int, headers: List<Pair<String, String>>): Boolean =
    headers.any { (k, v) -> k.equals("cf-mitigated", true) && v.equals("challenge", true) } ||
        (code in setOf(403, 429, 503) && headers.any { (k, v) -> k.equals("Server", true) && Regex("cloudflare|akamai|fastly|ddos-guard|sucuri", RegexOption.IGNORE_CASE).containsMatchIn(v) })

/** A bot check's page served in place of the real one (it shows "Client Challenge", "Just a moment…"). */
fun isBotCheckPage(html: String): Boolean =
    Regex("""<title>\s*(Client Challenge|Just a moment\.*|Attention Required!|Access denied|DDoS-Guard)""", RegexOption.IGNORE_CASE).containsMatchIn(html.take(4000))


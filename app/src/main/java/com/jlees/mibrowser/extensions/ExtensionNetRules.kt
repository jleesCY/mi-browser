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
)

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
fun hostOf(url: String?): String? = url?.let { Regex("""^[a-z][a-z0-9+.-]*://(?:[^@/?#]*@)?([^:/?#]+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.lowercase() }

/**
 * The site [host] belongs to (its registrable domain): the last two labels, or three under a two-part public suffix
 * (co.uk, com.au…). Good enough to tell first- from third-party; mi's ad blocker has the full list for blocking.
 */
fun siteOf(host: String): String {
    val parts = host.trimEnd('.').split('.')
    if (parts.size <= 2 || host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) return host
    val two = parts.takeLast(2).joinToString(".")
    val secondLevel = parts[parts.size - 2]
    val shortCc = parts.last().length == 2 && secondLevel in setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "nic")
    return if (shortCc) parts.takeLast(3).joinToString(".") else two
}

/** Whether [url] is a third-party request for a page at [initiator] (a different site). */
fun isThirdParty(url: String, initiator: String?): Boolean {
    val a = hostOf(url) ?: return false
    val b = hostOf(initiator) ?: return false
    return siteOf(a) != siteOf(b)
}

/** Whether [host] is [domain] or one of its subdomains. */
fun hostIn(host: String, domain: String) = host == domain.lowercase() || host.endsWith("." + domain.lowercase())

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

/** Whether [rule]'s condition matches [r]. [regex]: the rule's compiled url pattern (cached by the caller), if it has one. */
fun dnrMatches(rule: DnrRule, r: NetRequest, regex: Regex?): Boolean {
    val c = rule.condition
    val host = hostOf(r.url) ?: return false
    val initiatorHost = hostOf(r.initiator)
    if (c.resourceTypes != null) { if (r.type !in c.resourceTypes) return false }
    else if (r.type in DEFAULT_TYPES_EXCLUDE && rule.action.type != "allowAllRequests") return false
    if (c.excludedResourceTypes?.contains(r.type) == true) return false
    if (c.requestMethods != null && c.requestMethods.none { it.equals(r.method, true) }) return false
    if (c.excludedRequestMethods?.any { it.equals(r.method, true) } == true) return false
    if (c.tabIds != null && r.tabId !in c.tabIds) return false
    if (c.excludedTabIds?.contains(r.tabId) == true) return false
    if (c.requestDomains != null && c.requestDomains.none { hostIn(host, it) }) return false
    if (c.excludedRequestDomains?.any { hostIn(host, it) } == true) return false
    val initiators = c.initiatorDomains ?: c.domains
    if (initiators != null && (initiatorHost == null || initiators.none { hostIn(initiatorHost, it) })) return false
    val excludedInitiators = c.excludedInitiatorDomains ?: c.excludedDomains
    if (initiatorHost != null && excludedInitiators?.any { hostIn(initiatorHost, it) } == true) return false
    when (c.domainType) {
        "firstParty" -> if (isThirdParty(r.url, r.initiator)) return false
        "thirdParty" -> if (!isThirdParty(r.url, r.initiator)) return false
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
 * tried for requests to that domain; the rest are tried for every request. Large lists (tens of thousands of rules) stay fast.
 */
class DnrIndex(rules: List<Pair<String, DnrRule>>) {
    private class Entry(val ext: String, val rule: DnrRule) { val regex by lazy { dnrRegex(rule) } }
    private val byDomain = HashMap<String, MutableList<Entry>>()
    private val generic = ArrayList<Entry>()
    val size = rules.size

    init {
        for ((ext, rule) in rules) {
            val e = Entry(ext, rule)
            val anchor = rule.condition.urlFilter?.takeIf { it.startsWith("||") }?.drop(2)?.takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' }?.lowercase()?.trimEnd('.')
            val domains = rule.condition.requestDomains
            when {
                !anchor.isNullOrEmpty() && anchor.contains('.') -> byDomain.getOrPut(anchor) { ArrayList() } += e
                domains != null && domains.isNotEmpty() -> domains.forEach { byDomain.getOrPut(it.lowercase()) { ArrayList() } += e }
                else -> generic += e
            }
        }
    }

    /** Every rule matching [r], as (extension id, rule). */
    fun matches(r: NetRequest): List<Pair<String, DnrRule>> {
        val host = hostOf(r.url) ?: return emptyList()
        val out = ArrayList<Pair<String, DnrRule>>()
        // the host and each domain above it ("a.b.example.com", "b.example.com", "example.com")
        var h = host
        while (true) {
            byDomain[h]?.forEach { if (dnrMatches(it.rule, r, it.regex)) out += it.ext to it.rule }
            val dot = h.indexOf('.'); if (dot < 0) break
            h = h.substring(dot + 1)
        }
        generic.forEach { if (dnrMatches(it.rule, r, it.regex)) out += it.ext to it.rule }
        return out.distinctBy { it.first to it.second.id }
    }
}

// ---------------------------------------------------------------- a site's Content Security Policy

/** The sources mi serves extensions' files from, as a CSP source. */
const val EXT_CSP_SOURCE = "https://*.ext.mi.invalid"

/** Directives that load what extensions add to pages (styles, fonts, pictures, media, frames, requests). */
private val EXT_DIRECTIVES = setOf("default-src", "style-src", "style-src-elem", "font-src", "img-src", "media-src", "frame-src", "child-src", "connect-src")

/**
 * [policy] (a Content-Security-Policy header's value) with extensions' files allowed wherever it limits what loads, as a
 * browser exempts extensions from a site's policy. Scripts are left alone (mi runs extensions' scripts itself), and so
 * is everything else in it. Several policies (comma-separated) are each changed.
 */
fun allowExtensionsIn(policy: String): String = policy.split(',').joinToString(",") { one ->
    one.split(';').joinToString(";") { part ->
        val d = part.trim()
        val name = d.substringBefore(' ').lowercase()
        if (name !in EXT_DIRECTIVES || d.contains(EXT_CSP_SOURCE)) part
        else {
            val sources = d.substringAfter(' ', "").split(' ').filter { it.isNotBlank() && it != "'none'" }
            (if (part.startsWith(" ")) " " else "") + (listOf(name) + sources + EXT_CSP_SOURCE).joinToString(" ")
        }
    }
}

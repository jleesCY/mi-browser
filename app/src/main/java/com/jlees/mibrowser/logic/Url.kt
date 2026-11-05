// port of legacy/src/utils.ts, legacy/src/constants.ts (SEARCH_ENGINES), input parsing from legacy/app/index.tsx handleGoPress
package com.jlees.mibrowser.logic

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchEngine(val name: String, val url: String, val icon: String) {
    /** The search page for [query]: [url] is a prefix, or a template with %s where the query goes (custom engines). */
    fun urlFor(query: String) = if (url.contains("%s")) url.replace("%s", encodeComponent(query)) else url + encodeComponent(query)
}

/** The engine at [index]; one past the built-ins is the user's own (when it has a valid address). */
fun searchEngine(index: Int, customName: String, customUrl: String): SearchEngine =
    if (index == SEARCH_ENGINES.size && isValidSearchTemplate(customUrl)) SearchEngine(customName.ifBlank { "Custom" }, customUrl.trim(), "custom")
    else SEARCH_ENGINES.getOrElse(index) { SEARCH_ENGINES[0] }

fun isValidSearchTemplate(url: String) = url.trim().let { (it.startsWith("https://") || it.startsWith("http://")) && it.length > 10 && !it.contains(' ') }

/** A search shortcut's site and search address (%s = the query). */
data class Bang(val name: String, val url: String)

/**
 * Built-in search shortcuts: "!w mars" searches that site (like DuckDuckGo's bangs). Typed at the start only. Plus
 * [SEARCH_ENGINE_BANG] ("!se example.com"): your search engine, always searching (never opening it as an address).
 */
val BANGS = linkedMapOf(
    "w" to Bang("Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
    "yt" to Bang("YouTube", "https://www.youtube.com/results?search_query=%s"),
    "r" to Bang("Reddit", "https://www.reddit.com/search/?q=%s"),
    "a" to Bang("Amazon", "https://www.amazon.com/s?k=%s"),
    "m" to Bang("Google Maps", "https://www.google.com/maps/search/%s"),
    "i" to Bang("Google Images", "https://www.google.com/search?tbm=isch&q=%s"),
)
const val SEARCH_ENGINE_BANG = "se"

/** Every shortcut key → search address: !se ([engine]), the built-ins, then your own ([custom] wins on a clash). */
fun allBangs(custom: Map<String, String>, engine: SearchEngine): Map<String, String> =
    mapOf(SEARCH_ENGINE_BANG to engine.url) + BANGS.mapValues { it.value.url } + custom

/** A shortcut key you can add: 1–12 lowercase letters or digits. */
fun isValidBangKey(key: String) = Regex("^[a-z0-9]{1,12}$").matches(key)

private val BANG_FIRST = Regex("^!(\\w+)\\s+(.+)$")
/** "!w " at the start: the shortcut typed so far, highlighted in the Pill once a space follows a known key. */
private val BANG_TYPED = Regex("^!(\\w+)\\s")

/**
 * "!w mars" → that site's search page for "mars"; null when there's no known shortcut. Only one shortcut, and only at
 * the start: "!w !yt x" searches Wikipedia for "!yt x", and "mars !w" is an ordinary search.
 */
fun bangUrl(text: String, bangs: Map<String, String>): String? {
    val (key, query) = BANG_FIRST.find(text)?.destructured ?: return null
    val template = bangs[key.lowercase()] ?: return null
    return SearchEngine("", template, "").urlFor(query.trim())
}

/** Length of a known shortcut typed at the start ("!w " → 2: the "!w"), or 0. */
fun typedBangLength(text: String, bangs: Map<String, String>): Int {
    val m = BANG_TYPED.find(text) ?: return 0
    return if (m.groupValues[1].lowercase() in bangs) m.groupValues[1].length + 1 else 0
}

val SEARCH_ENGINES = listOf(
    SearchEngine("Google", "https://www.google.com/search?q=", "logo-google"),
    SearchEngine("Bing", "https://www.bing.com/search?q=", "grid-sharp"),
    SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=", "logo-tux"),
    SearchEngine("Brave", "https://search.brave.com/search?q=", "shield-half-outline"),
    SearchEngine("Ecosia", "https://www.ecosia.org/search?q=", "leaf"),
    SearchEngine("Yahoo", "https://search.yahoo.com/search?p=", "logo-yahoo"),
)

/** JS encodeURIComponent equivalent. */
fun encodeComponent(s: String): String = URLEncoder.encode(s, "UTF-8")
    .replace("+", "%20").replace("%21", "!").replace("%27", "'")
    .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

private val HTTP_PREFIX = Regex("^(http|https)://", RegexOption.IGNORE_CASE)
private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")
private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
private val TLD = Regex("^([a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
/** Top-level names reserved for, or conventionally used on, local networks and development machines. */
private val LOCAL_TLDS = setOf("localhost", "local", "test", "internal", "lan", "home", "localdomain", "intranet", "corp", "private")

/**
 * Hosts on this device or the local network, plus IP literals: never upgraded by HTTPS-Only (they rarely have
 * certificates), and opened over http when typed without a scheme. Covers localhost / *.localhost, IPv4 and IPv6
 * literals, single-label intranet names ("nas", "router"), *.home.arpa and the [LOCAL_TLDS].
 */
fun isLocalHost(host: String?): Boolean {
    val h = host?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
    if (h.isEmpty()) return false
    if (h == "localhost" || h.endsWith(".localhost")) return true
    if (IPV4.matches(h) || h.contains(':')) return true
    if (!h.contains('.')) return true
    if (h.endsWith(".home.arpa")) return true
    return h.substringAfterLast('.') in LOCAL_TLDS
}

/** Loopback hosts: traffic to them never leaves this device, so plain http there isn't exposed to the network. */
fun isLoopback(host: String?): Boolean {
    val h = host?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
    return h == "localhost" || h.endsWith(".localhost") || h == "::1" || (h.startsWith("127.") && IPV4.matches(h))
}

/**
 * Whether the connection to [url] should be flagged "Not secure": plain http that leaves the device (anything but
 * loopback; LAN http crosses the network unencrypted too), or https to a host the user allowed past a certificate
 * error ([certExceptions], hosts as [displayHost] gives them).
 */
fun isNotSecure(url: String?, certExceptions: Set<String>): Boolean = when {
    url == null -> false
    url.startsWith("http://", ignoreCase = true) -> !isLoopback(displayHost(url))
    url.startsWith("https://", ignoreCase = true) -> displayHost(url) in certExceptions
    else -> false
}

/** A typed address split into host and port; null if it isn't shaped like one (spaces, userinfo, bad port). */
private data class TypedHost(val host: String, val hasPort: Boolean)

private fun typedHost(text: String): TypedHost? {
    if (text.any { it.isWhitespace() }) return null
    val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isEmpty() || '@' in authority) return null // "user@example.com" is a search, not credentials
    val (host, port) = if (authority.startsWith("[")) {
        val end = authority.indexOf(']').takeIf { it > 0 } ?: return null
        authority.substring(0, end + 1) to authority.substring(end + 1).removePrefix(":").ifEmpty { null }
    } else {
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) authority.substring(0, colon) to authority.substring(colon + 1) else authority to null
    }
    if (port != null && (port.isEmpty() || port.any { !it.isDigit() } || port.length > 5 || port.toInt() !in 1..65535)) return null
    return host.takeIf { it.isNotEmpty() }?.let { TypedHost(it, port != null) }
}

/** Whether a typed host names a real place: a domain with a TLD, an IP literal, localhost, or a local name. */
private fun isNavigableHost(t: TypedHost): Boolean {
    val raw = t.host.lowercase()
    if (raw.startsWith("[")) return runCatching { java.net.InetAddress.getByName(raw.trim('[', ']')) is java.net.Inet6Address }.getOrDefault(false)
    if (IPV4.matches(raw) || raw == "localhost") return true
    val ascii = runCatching { java.net.IDN.toASCII(raw.trimEnd('.'), java.net.IDN.ALLOW_UNASSIGNED) }.getOrNull() ?: return false
    val labels = ascii.split('.')
    if (labels.any { !LABEL.matches(it) }) return false
    if (labels.size == 1) return t.hasPort // "nas:5000" opens; a bare word ("nas") is a search
    return TLD.matches(labels.last()) || isLocalHost(ascii)
}

/**
 * Address-bar text → URL to load. Returns null for blank input. Text that names a host (optionally with a port,
 * path or query) opens it: local hosts over http, everything else over https (see [isLocalHost]). An explicit
 * http:// is upgraded only when [httpsOnly] is on and the host isn't local. Anything else is searched.
 */
fun inputToUrl(raw: String, httpsOnly: Boolean, engine: SearchEngine, bangs: Map<String, String> = allBangs(emptyMap(), engine)): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    bangUrl(text, bangs)?.let { return it }
    if (HTTP_PREFIX.containsMatchIn(text)) {
        val rest = text.replaceFirst(HTTP_PREFIX, "")
        val local = isLocalHost(typedHost(rest)?.host)
        return if (httpsOnly && text.startsWith("http://", ignoreCase = true) && !local) "https://$rest" else text
    }
    val host = typedHost(text)
    if (host != null && isNavigableHost(host)) return (if (isLocalHost(host.host)) "http://" else "https://") + text
    return engine.urlFor(text)
}

/** True when [url] came from [inputToUrl] choosing https for a typed address (no scheme): it may fall back to http. */
fun isTypedHttpsUpgrade(raw: String, url: String): Boolean =
    !HTTP_PREFIX.containsMatchIn(raw.trim()) && url.startsWith("https://") && searchQueryFromUrl(url) == null

/**
 * Hostname of a URL, or the input itself when it can't be parsed. Non-ASCII (internationalized) hosts are shown in
 * punycode so look-alike domains ("аpple.com" with a Cyrillic а) can't impersonate real ones.
 */
fun displayHost(url: String?): String {
    if (url.isNullOrEmpty()) return ""
    val host = try { java.net.URL(url).host?.takeIf { it.isNotEmpty() } ?: URI(url).host } catch (_: Exception) { null } ?: return url
    return if (host.any { it.code > 127 }) runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED) }.getOrDefault(host) else host
}

/** The host whose icon represents [url] (a URL, or a bare host such as a history group title); null if none. */
fun faviconHost(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    val target = if (!url.contains("://")) "https://$url" else url
    if (!target.startsWith("http://") && !target.startsWith("https://")) return null
    return displayHost(target).lowercase().takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains(':') }
}

private fun queryParam(url: String, name: String): String? {
    val q = try { URI(url).rawQuery } catch (_: Exception) { null } ?: return null
    for (pair in q.split("&")) {
        val i = pair.indexOf('=')
        val k = if (i >= 0) pair.substring(0, i) else pair
        if (k == name) return try {
            URLDecoder.decode(if (i >= 0) pair.substring(i + 1) else "", "UTF-8")
        } catch (_: Exception) { null }
    }
    return null
}

private fun engineFor(url: String): SearchEngine? {
    val host = try { URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return null
    return SEARCH_ENGINES.firstOrNull { host.contains(URI(it.url).host.replace("www.", "")) }
}

fun searchQueryFromUrl(url: String): String? {
    engineFor(url) ?: return null
    return (queryParam(url, "q") ?: queryParam(url, "p"))?.takeIf { it.isNotEmpty() }
}

fun historyTitle(url: String, pageTitle: String?): String {
    val query = searchQueryFromUrl(url)
    if (query != null) {
        val engine = engineFor(url)
        return if (engine != null) "${engine.name} Search: $query" else query
    }
    if (!pageTitle.isNullOrEmpty() && !pageTitle.contains("://")) return pageTitle
    return displayHost(url)
}

/** Page title shown on tabs: title unless empty or a URL, else host, else "New Tab". */
fun tabTitle(title: String?, url: String?): String =
    if (!title.isNullOrEmpty() && !title.contains("://")) title
    else if (!url.isNullOrEmpty()) displayHost(url) else "New Tab"

/** Deep links: raw http(s), or mibrowser:// / mi:// / mi-browser:// carrying a URL or ?url= / ?href=. */
fun parseDeepLink(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val m = Regex("^(mibrowser|mi|mi-browser)://(.*)$").find(url) ?: return null
    val rest = m.groupValues[2]
    if (rest.startsWith("http://") || rest.startsWith("https://")) return rest
    val target = queryParam(url, "url") ?: queryParam(url, "href")
    return target?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

/** URL normalisation used to compare history entries. */
fun normalizeUrl(u: String) = u.replace(Regex("^https?://"), "").replace(Regex("^www\\."), "")
    .replace(Regex("/$"), "").replace(Regex("/index\\.html$"), "").lowercase()

fun uniqueId(): String = java.util.UUID.randomUUID().toString()

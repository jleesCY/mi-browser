// port of legacy/src/utils.ts, legacy/src/constants.ts (SEARCH_ENGINES), input parsing from legacy/app/index.tsx handleGoPress
package com.jlees.mibrowser.logic

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class SearchEngine(val name: String, val url: String, val icon: String)

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
fun inputToUrl(raw: String, forceSearch: Boolean, httpsOnly: Boolean, engineIndex: Int): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val engine = SEARCH_ENGINES.getOrElse(engineIndex) { SEARCH_ENGINES[0] }
    if (forceSearch) return engine.url + encodeComponent(text)
    if (HTTP_PREFIX.containsMatchIn(text)) {
        val rest = text.replaceFirst(HTTP_PREFIX, "")
        val local = isLocalHost(typedHost(rest)?.host)
        return if (httpsOnly && text.startsWith("http://", ignoreCase = true) && !local) "https://$rest" else text
    }
    val host = typedHost(text)
    if (host != null && isNavigableHost(host)) return (if (isLocalHost(host.host)) "http://" else "https://") + text
    return engine.url + encodeComponent(text)
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

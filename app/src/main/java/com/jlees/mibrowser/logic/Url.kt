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

private val DOMAIN = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$")
private val IP = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(/.*)?$")
private val LOCALHOST = Regex("^localhost(?::[0-9]{1,5})?(/.*)?$")
private val HTTP_PREFIX = Regex("^(http|https)://", RegexOption.IGNORE_CASE)

/** Address-bar text → URL to load. Returns null for blank input. */
fun inputToUrl(raw: String, forceSearch: Boolean, httpsOnly: Boolean, engineIndex: Int): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val engine = SEARCH_ENGINES.getOrElse(engineIndex) { SEARCH_ENGINES[0] }
    if (forceSearch) return engine.url + encodeComponent(text)
    if (HTTP_PREFIX.containsMatchIn(text)) {
        return if (httpsOnly && text.startsWith("http://")) text.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://") else text
    }
    if (!text.contains(" ") && (DOMAIN.matches(text) || IP.matches(text) || LOCALHOST.matches(text))) {
        return if (LOCALHOST.matches(text) || IP.matches(text)) "http://$text" else "https://$text"
    }
    return engine.url + encodeComponent(text)
}

/**
 * Hostname of a URL, or the input itself when it can't be parsed. Non-ASCII (internationalized) hosts are shown in
 * punycode so look-alike domains ("аpple.com" with a Cyrillic а) can't impersonate real ones.
 */
fun displayHost(url: String?): String {
    if (url.isNullOrEmpty()) return ""
    val host = try { java.net.URL(url).host?.takeIf { it.isNotEmpty() } ?: URI(url).host } catch (_: Exception) { null } ?: return url
    return if (host.any { it.code > 127 }) runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED) }.getOrDefault(host) else host
}

fun faviconUrl(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    val target = if (!url.startsWith("http") && !url.contains("://")) "https://$url" else url
    val host = try { URI(target).host } catch (_: Exception) { null } ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=128"
}

fun isIpOrLocalhost(host: String) = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(host) || host == "localhost"

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

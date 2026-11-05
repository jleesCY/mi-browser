// Search engines: the built-ins, your own (a name and an address with %s), and recognising a search results page.
package com.jlees.mibrowser.search

import java.net.URI

import com.jlees.mibrowser.logic.encodeComponent
import com.jlees.mibrowser.logic.queryParam

data class SearchEngine(val name: String, val url: String) {
    /** The search page for [query]: [url] is a prefix, or a template with %s where the query goes (custom engines). */
    fun urlFor(query: String) = if (url.contains("%s")) url.replace("%s", encodeComponent(query)) else url + encodeComponent(query)
}

/** The engine at [index]; one past the built-ins is the user's own (when it has a valid address). */
fun searchEngine(index: Int, customName: String, customUrl: String): SearchEngine =
    if (index == SEARCH_ENGINES.size && isValidSearchTemplate(customUrl)) SearchEngine(customName.ifBlank { "Custom" }, customUrl.trim())
    else SEARCH_ENGINES.getOrElse(index) { SEARCH_ENGINES[0] }

fun isValidSearchTemplate(url: String) = url.trim().let { (it.startsWith("https://") || it.startsWith("http://")) && it.length > 10 && !it.contains(' ') }

val SEARCH_ENGINES = listOf(
    SearchEngine("Google", "https://www.google.com/search?q="),
    SearchEngine("Bing", "https://www.bing.com/search?q="),
    SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q="),
    SearchEngine("Brave", "https://search.brave.com/search?q="),
    SearchEngine("Ecosia", "https://www.ecosia.org/search?q="),
    SearchEngine("Yahoo", "https://search.yahoo.com/search?p="),
)


/** The built-in engine whose results page [url] is, if any. */
fun engineFor(url: String): SearchEngine? {
    val host = try { URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return null
    return SEARCH_ENGINES.firstOrNull { host.contains(URI(it.url).host.replace("www.", "")) }
}

/** The query of a built-in engine's results page, or null (other pages on its site, e.g. a captcha, don't count). */
fun searchQueryFromUrl(url: String): String? {
    val engine = engineFor(url) ?: return null
    val path = try { URI(url).path.orEmpty() } catch (_: Exception) { return null }
    if (path.trimEnd('/') != URI(engine.url).path.orEmpty().trimEnd('/')) return null
    return (queryParam(url, "q") ?: queryParam(url, "p"))?.takeIf { it.isNotEmpty() }
}


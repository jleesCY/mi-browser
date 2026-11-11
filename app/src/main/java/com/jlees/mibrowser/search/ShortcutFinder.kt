// Finding a site's search address for a new search shortcut, so nobody has to build one by hand: from the address of a
// results page (where the search sits in a parameter like q= or _nkw=), or from the search the site declares for
// browsers (OpenSearch, which Chrome uses too). The rules are plain functions (tested); the fetching is below them.
package com.jlees.mibrowser.search

import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** The parameters sites put a search in, most telling first (q= on most; _nkw= on eBay; k= on Amazon…). */
private val SEARCH_PARAMS = listOf("q", "query", "search_query", "search", "_nkw", "k", "keyword", "keywords", "term", "searchterm", "text", "s", "p", "w", "st")

/**
 * The search address (with %s for the search) of the results page at [url], or null when its address doesn't carry a
 * search. The page's other parameters stay (some sites need them); only the search becomes %s.
 */
fun searchTemplateFrom(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "https" && uri.scheme != "http") return null
    val pairs = (uri.rawQuery ?: return null).split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') to it.substringAfter('=', "") }
    val name = SEARCH_PARAMS.firstOrNull { p -> pairs.any { it.first.equals(p, ignoreCase = true) && it.second.isNotEmpty() } } ?: return null
    val query = pairs.joinToString("&") { (k, v) -> if (k.equals(name, ignoreCase = true)) "$k=%s" else if (v.isEmpty()) k else "$k=$v" }
    return "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.orEmpty()}?$query"
}

/** Where the page in [html] (at [pageUrl]) declares its OpenSearch description, or null. */
fun openSearchLink(html: String, pageUrl: String): String? {
    val tag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
        .firstOrNull { Regex("""rel\s*=\s*["']?search""", RegexOption.IGNORE_CASE).containsMatchIn(it) && it.contains("opensearchdescription", ignoreCase = true) }
        ?: return null
    val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return null
    return runCatching { URI(pageUrl).resolve(href.replace("&amp;", "&")).toString() }.getOrNull()
}

/** The web results address in an OpenSearch description, with %s for the search; null if it has none mi can use. */
fun templateFromOpenSearch(xml: String): String? {
    val tag = Regex("""<Url\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }
        .firstOrNull { Regex("""type\s*=\s*["']text/html["']""", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: return null
    var t = Regex("""template\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return null
    t = t.replace("&amp;", "&").replace("{searchTerms}", "%s")
    t = t.replace(Regex("""[?&][^?&=]+=\{[^}]*\?}"""), "") // optional parameters ({startPage?}…) are left out
    return t.takeIf { "%s" in it && '{' !in it && isValidSearchTemplate(it) }
}

/** A short name for a shortcut to [url]'s site: "www.ebay.com" → "ebay". */
fun suggestedShortcutKey(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
    return host.substringBefore('.').filter { it.isLetterOrDigit() }.take(12)
}

/**
 * Tab menu › Add shortcut: a search shortcut for the site on screen, ready to save. Its address comes from the page (a
 * results page) or what the site declares; if neither has one, mi says to search the site first.
 */
fun BrowserViewModel.addSearchShortcutFromPage() {
    val url = activeUrl ?: return
    viewModelScope.launch {
        val template = searchTemplateFrom(url) ?: withContext(Dispatchers.IO) { declaredTemplate(url) }
        if (template != null) shortcutDraft = suggestedShortcutKey(url) to template
        else showAlert("No search found", "Search for something on this site first, then tap Add shortcut again from the results page.")
    }
}

/** The search the page at [pageUrl] declares (OpenSearch), fetched without cookies; null if there's none. */
private fun declaredTemplate(pageUrl: String): String? = runCatching {
    val html = fetchText(pageUrl) ?: return null
    val link = openSearchLink(html, pageUrl) ?: return null
    templateFromOpenSearch(fetchText(link) ?: return null)
}.getOrNull()

private fun fetchText(url: String): String? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = true }
    if (conn.responseCode !in 200..299) return null
    // at most 512 KB: enough for a page's head or a description
    return conn.inputStream.use { com.jlees.mibrowser.logic.readAtMost(it, 512 * 1024).first.decodeToString() }
}

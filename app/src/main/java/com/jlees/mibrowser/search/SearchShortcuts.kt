// Search shortcuts ("bangs"): "!w mars" searches Wikipedia. Built-ins, !se (your engine) and your own.
package com.jlees.mibrowser.search

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
    return SearchEngine("", template).urlFor(query.trim())
}

/**
 * The shortcuts to offer while one is being typed ("!" and no space yet), as key → search address: those whose key
 * starts with what's typed first, then those whose site has it in its name ("!wik" finds !w, Wikipedia). Empty
 * otherwise.
 */
fun shortcutMatches(text: String, bangs: Map<String, String>): List<Pair<String, String>> {
    if (!text.startsWith("!") || text.any { it.isWhitespace() }) return emptyList()
    val typed = text.drop(1).lowercase()
    val all = bangs.entries.map { it.key to it.value }
    val byKey = all.filter { it.first.startsWith(typed) }.sortedBy { it.first.length }
    val byName = all.filter { it !in byKey && typed.isNotEmpty() && shortcutName(it.first, it.second).contains(typed, ignoreCase = true) }
    return byKey + byName
}

/** A shortcut's site name: the built-in's ("Wikipedia"), "Search engine" for !se, else its site ("ebay.com"). */
fun shortcutName(key: String, url: String): String = when (key) {
    SEARCH_ENGINE_BANG -> "Your search engine"
    else -> BANGS[key]?.takeIf { it.url == url }?.name ?: com.jlees.mibrowser.logic.displayHost(url).removePrefix("www.")
}

/** Length of a known shortcut typed at the start ("!w " → 2: the "!w"), or 0. */
fun typedBangLength(text: String, bangs: Map<String, String>): Int {
    val m = BANG_TYPED.find(text) ?: return 0
    return if (m.groupValues[1].lowercase() in bangs) m.groupValues[1].length + 1 else 0
}

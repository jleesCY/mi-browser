// Network helpers for browsing: the ad/tracker host blocklist and search-engine suggestions.
package com.jlees.mibrowser.data

import android.content.Context
import com.jlees.mibrowser.logic.encodeComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Host blocklist (assets/blocklist.txt). A host is blocked if it or any parent domain is listed. */
object Blocklist {
    @Volatile private var hosts: Set<String> = emptySet()

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        if (hosts.isNotEmpty()) return@withContext
        hosts = ctx.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()
        }
    }

    fun isBlocked(host: String?): Boolean = isBlocked(host, hosts)

    /** Suffix match: "a.b.ads.example.com" is blocked by "ads.example.com". */
    fun isBlocked(host: String?, list: Set<String>): Boolean {
        if (host.isNullOrEmpty() || list.isEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (h in list) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false // stop at the registrable "example.com"
            h = h.substring(dot + 1)
        }
    }
}

/** Search suggestions from the selected engine (OpenSearch-style JSON: ["query", ["s1", "s2", …]]). */
object Suggest {
    private val endpoints = listOf(
        "https://suggestqueries.google.com/complete/search?client=firefox&q=",
        "https://api.bing.com/osjson.aspx?query=",
        "https://duckduckgo.com/ac/?type=list&q=",
        "https://search.brave.com/api/suggest?q=",
        "https://ac.ecosia.org/autocomplete?type=list&q=",
        "https://search.yahoo.com/sugg/os?output=fxjson&command=",
    )

    /** [cache] false (incognito): the response isn't written to the app's HTTP disk cache, so the query leaves no trace. */
    suspend fun fetch(engineIndex: Int, query: String, cache: Boolean = true): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpoints.getOrNull(engineIndex) ?: return@withContext emptyList() // custom engine: none
            val conn = (URL(endpoint + encodeComponent(query)).openConnection() as HttpURLConnection)
                .apply { connectTimeout = 4000; readTimeout = 4000; useCaches = cache }
            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            val arr = JSONArray(body).getJSONArray(1)
            List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }.take(6)
        }.getOrDefault(emptyList())
    }
}

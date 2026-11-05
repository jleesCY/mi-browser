// Search suggestions from your search engine while you type.
package com.jlees.mibrowser.search

import com.jlees.mibrowser.logic.encodeComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

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

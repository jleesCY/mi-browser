// Suggestions from the search engine while typing in the Pill.
package com.jlees.mibrowser.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchSuggestions(private val vm: BrowserViewModel) {
    var items by mutableStateOf(listOf<String>()); private set
    /** What [items] were fetched for: a moment after typing on, they're still the last text's. */
    var query by mutableStateOf(""); private set
    private var job: Job? = null

    /**
     * The Pill's text changed: fetches suggestions (debounced), when the setting is on (and its incognito one, in an incognito tab), and not for
     * the current address or anything that is already one. Responses aren't cached for tabs that keep no traces.
     */
    fun onTyping(text: String) {
        job?.cancel()
        val s = vm.settings
        val tab = vm.tabs.current
        val q = text.trim()
        if (!s.searchSuggestions || (tab?.incognito == true && !s.incognitoSuggestions) || q.isEmpty() || q == vm.activeUrl || q.contains("://")) { items = emptyList(); query = ""; return }
        job = vm.viewModelScope.launch {
            delay(200)
            items = Suggest.fetch(s.searchEngineIndex, q, cache = tab?.keepsTraces != false)
            query = q
        }
    }

    fun clear() { job?.cancel(); items = emptyList(); query = "" }
}

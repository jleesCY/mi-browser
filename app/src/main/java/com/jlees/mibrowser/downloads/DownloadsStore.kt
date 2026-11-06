// The download list: what's been downloaded and what's in progress, saved between launches.
package com.jlees.mibrowser.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import kotlinx.serialization.builtins.ListSerializer

class DownloadsStore(private val store: JsonStore) {
    /** Newest first. */
    var items by mutableStateOf(listOf<DownloadItem>()); private set

    private val serializer = ListSerializer(DownloadItem.serializer())

    fun load() { items = afterRestart(store.read("downloads", serializer) ?: emptyList()) }

    /** Incognito downloads are kept for the session only (keepsTraces). */
    val savers = listOf(Saver(1000, { items.filterNot { it.incognito } }) { store.write("downloads", serializer, it) })

    operator fun get(id: String) = items.firstOrNull { it.id == id }
    fun add(item: DownloadItem) { items = listOf(item) + items }
    fun update(id: String, f: DownloadItem.() -> DownloadItem) { items = items.map { if (it.id == id) it.f() else it } }
    fun remove(id: String) { items = items.filterNot { it.id == id } }
    /** "Clear list": finished and failed entries leave the list (their files stay). */
    fun clearFinished() { items = items.filter { it.active || it.state == DownloadState.PAUSED } }
    /** Wipe All Data: the list is emptied (files in Downloads stay; they belong to the user). */
    fun clear() { items = items.filter { it.active } }
    /** Incognito ended: its downloads leave the list (their files stay in Downloads, as the prompt said). */
    fun forgetIncognito() { items = items.filterNot { it.incognito && !it.active } }
}

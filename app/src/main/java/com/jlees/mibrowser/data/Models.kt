// The saved data types: tabs, history entries, bookmarks, favorites.
package com.jlees.mibrowser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class TabItem(
    val id: String,
    val title: String = "New Tab",
    val historyStack: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val desktopMode: Boolean? = null,
    val readerMode: Boolean? = null,
    val previewImage: String? = null,
    val isCustomTitle: Boolean = false,
    /** When the tab was last made active (ms): the switcher orders tabs by it, most recent on the right. */
    val lastUsed: Long = 0,
    /** Pinned: can't be swiped away in the tab switcher; its card shows a pin. */
    val pinned: Boolean = false,
    // runtime only
    @Transient val url: String? = null,
    @Transient val requestedUrl: String? = null,
    @Transient val loading: Boolean = false,
    @Transient val canGoBack: Boolean = false,
    @Transient val canGoForward: Boolean = false,
    @Transient val hasLoadedOnce: Boolean = false,
    /** Set for a window a page opened (window.open / target=_blank): the tab that opened it. Its page talks to the
     *  opener (window.opener, postMessage) and window.close() returns there. Not kept across restarts. */
    @Transient val openerId: String? = null,
    /** The address was typed without a scheme and opened over https; if https can't connect, retry over http. */
    @Transient val httpFallback: Boolean = false,
    /** Bumped to force the WebView to load requestedUrl even if unchanged. */
    @Transient val loadNonce: Int = 0,
    /** Incognito: the tab's own WebView profile ("incognito-<id>", its own cookies, storage, cache and permissions),
     *  deleted once no tab uses it. Pop-ups share their opener's. Never saved, so incognito tabs end with the app. */
    @Transient val profile: String? = null,
) {
    val isHome get() = openerId == null && (url == null || url == "about:blank")
    val incognito get() = profile != null
    /**
     * Whether this tab may leave anything behind on the device: history and recent searches, a restored tab, Undo,
     * its preview in the switcher, site icons, cached search suggestions. The one rule every such feature checks;
     * false for incognito tabs.
     */
    val keepsTraces get() = !incognito
    /** Nothing loaded yet: the only state in which a tab can switch between normal and incognito. */
    val isFresh get() = url == null && historyStack.isEmpty() && openerId == null
}

const val INCOGNITO_PROFILE_PREFIX = "incognito-"
fun newIncognitoProfile() = INCOGNITO_PROFILE_PREFIX + java.util.UUID.randomUUID()

fun newTab(url: String? = null, title: String = "New Tab") = TabItem(
    id = com.jlees.mibrowser.logic.uniqueId(),
    url = url, requestedUrl = url, title = title, hasLoadedOnce = true,
    historyStack = if (url != null) listOf(url) else emptyList(),
    currentIndex = if (url != null) 0 else -1,
)

@Serializable
data class HistoryItem(
    val url: String,
    val title: String = "",
    val timestamp: Long = 0,
    @Transient val id: String = "",
)

@Serializable
data class BookmarkNode(
    val id: String,
    val type: BookmarkType = BookmarkType.BOOKMARK, // an unknown value loads as a bookmark (coerceInputValues)
    val title: String,
    val url: String = "",
    val parentId: String? = null,
    val order: Int = 0,
) {
    val isFolder get() = type == BookmarkType.FOLDER
}

/** Saved by their original names, so bookmarks saved before this was an enum load unchanged. */
@Serializable enum class BookmarkType { @SerialName("bookmark") BOOKMARK, @SerialName("folder") FOLDER }

@Serializable
data class Favorite(val id: String, val url: String, val title: String)

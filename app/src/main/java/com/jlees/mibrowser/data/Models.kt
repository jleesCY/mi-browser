// port of legacy/src/types.ts, legacy/src/hooks/useBrowserSettings.ts (defaults), useFavorites.ts
package com.jlees.mibrowser.data

import com.jlees.mibrowser.logic.SEARCH_ENGINES
import com.jlees.mibrowser.logic.isValidSearchTemplate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Settings(
    val themeMode: String = "dark", // light | dark | system
    val accentColor: String = "#007AFF",
    val searchEngineIndex: Int = 0,
    /** Your own search engine (index = number of built-ins): a name and an address with %s for the query. */
    val customSearchName: String = "",
    val customSearchUrl: String = "",
    /** Pull down at the very top of a page to reload it. */
    val pullToRefresh: Boolean = true,
    /** Your own search shortcuts: key ("ebay") → search address with %s. Checked before the built-ins. */
    val customBangs: Map<String, String> = emptyMap(),
    val cornerRadius: Int = 28, // 0 | 12 | 28
    val uiPadding: String = "normal", // compact | normal | airy
    val fontScale: Float = 1f,
    val roundedFont: Boolean = false, // Google Sans Flex ROND axis
    val showStatusBar: Boolean = true,
    val progressBarMode: String = "ltr", // ltr | center | none
    val recallPosition: String = "center", // left | center | right — set by swiping the recall handle sideways
    val startupTabMode: String = "new", // new | last
    val showTabLogo: Boolean = true,
    val showBookmarkIcons: Boolean = true,
    val desktopMode: Boolean = false,
    val readerModeEnabled: Boolean = false,
    val recentSearchesExpanded: Boolean = false,
    val showFavoritesDefault: Boolean = false,
    val jsEnabled: Boolean = true,
    val httpsOnly: Boolean = false,
    val blockCookies: Boolean = false,
    val blockThirdPartyCookies: Boolean = false, // payment and sign-in iframes (PayPal, SSO) need them, as in Chrome
    val blockTrackers: Boolean = true,
    val searchSuggestions: Boolean = true,
    val incognitoSuggestions: Boolean = false,
    val historyLoadCount: Int = 10,
    val historyGrouping: String = "Time", // Time | Site
    val backgroundRefresh: Boolean = false,
    val homeClockType: String = "None", // None | 12h | 24h
    val homeDateType: String = "None", // None | Above | Below
    val homeLogoType: String = "Fidget", // None | Static | Fidget
    val showHomeShortcuts: Boolean = false,
    val homeShortcutAction: String = "qr", // newTab | qr | bookmarks | history
    val skippedUpdate: String? = null, // release version the user chose to skip
    /** Sites allowed to open pop-ups without a tap (pop-ups opened by a tap are always allowed). */
    val popupSites: List<String> = emptyList(),
    /** Text size on web pages, in percent (WebSettings.textZoom). Separate from the app's own font size. */
    val textZoom: Int = 100,
    /** In dark theme, darken sites that have no dark mode of their own (algorithmic darkening). */
    val darkenPages: Boolean = false,
    /** When the automatic update check last reached GitHub (ms); it runs at most every 12 hours. */
    val lastUpdateCheck: Long = 0,
    /** Bumped when a default changes for existing installs; see BrowserViewModel.migrate. */
    val settingsVersion: Int = 0,
) {
    /** Load-time sanitising. */
    fun sanitized() = copy(searchEngineIndex = if (searchEngineIndex in SEARCH_ENGINES.indices || (searchEngineIndex == SEARCH_ENGINES.size && isValidSearchTemplate(customSearchUrl))) searchEngineIndex else 0)
}

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
    /** Nothing loaded yet: the only state in which a tab can switch between normal and incognito. */
    val isFresh get() = url == null && historyStack.isEmpty() && openerId == null
}

const val INCOGNITO_PROFILE_PREFIX = "incognito-"
fun newIncognitoProfile() = INCOGNITO_PROFILE_PREFIX + java.util.UUID.randomUUID()

fun newTab(url: String? = null, title: String = "New Tab") = TabItem(
    id = System.currentTimeMillis().toString(),
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
    val type: String, // bookmark | folder
    val title: String,
    val url: String = "",
    val parentId: String? = null,
    val order: Int = 0,
) {
    val isFolder get() = type == "folder"
}

@Serializable
data class Favorite(val id: String, val url: String, val title: String)

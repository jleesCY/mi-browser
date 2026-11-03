// port of legacy/src/types.ts, legacy/src/hooks/useBrowserSettings.ts (defaults), useFavorites.ts
package com.jlees.mibrowser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

val DEFAULT_MENU_BAR_ORDER = listOf("tabs", "bookmarks", "history", "settings", "menu")

@Serializable
data class Settings(
    val themeMode: String = "dark", // light | dark | adaptive
    val accentColor: String = "#007AFF",
    val searchEngineIndex: Int = 0,
    val cornerRadius: Int = 22,
    val uiPadding: String = "normal", // compact | normal | airy
    val fontScale: Float = 1f,
    val fontWeight: String = "normal", // light | normal | bold
    val showStatusBar: Boolean = true,
    val pillHeight: Int = 70,
    val progressBarMode: String = "ltr", // ltr | center | none
    val recallPosition: String = "center", // persisted only (no UI, no effect) — kept for export compatibility
    val startupTabMode: String = "new", // new | last
    val tabViewMode: String = "rows", // rows | cards
    val showTabLogo: Boolean = true,
    val showTabPreview: Boolean = true,
    val expandMenus: Boolean = false,
    val showBookmarkIcons: Boolean = true,
    val desktopMode: Boolean = false,
    val forceSearchMode: Boolean = false,
    val readerModeEnabled: Boolean = false,
    val recentSearchesExpanded: Boolean = false,
    val showFavoritesDefault: Boolean = false,
    val jsEnabled: Boolean = true,
    val httpsOnly: Boolean = false,
    val blockCookies: Boolean = false,
    val historyLoadCount: Int = 10,
    val historyGrouping: String = "Time", // Time | Site
    val backgroundRefresh: Boolean = false,
    val menuBarOrder: List<String> = DEFAULT_MENU_BAR_ORDER,
    val homeClockType: String = "None", // None | 12h | 24h
    val homeDateType: String = "None", // None | Above | Below
    val homeLogoType: String = "Fidget", // None | Static | Fidget
    val homeBackgroundImage: String? = null,
    val showHomeShortcuts: Boolean = false,
    val homeShortcutAction: String = "qr", // newTab | qr | bookmarks | history
    val homeBackgroundOverlayOpacity: Float = 0.2f,
) {
    /** Load-time sanitising (useBrowserSettings loadSettings). */
    fun sanitized(): Settings {
        val known = menuBarOrder.filter { it in DEFAULT_MENU_BAR_ORDER }.distinct()
        val order = known + DEFAULT_MENU_BAR_ORDER.filter { it !in known }
        return copy(
            searchEngineIndex = if (searchEngineIndex in 0..5) searchEngineIndex else 0,
            menuBarOrder = order,
        )
    }
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
    // runtime only
    @Transient val url: String? = null,
    @Transient val requestedUrl: String? = null,
    @Transient val loading: Boolean = false,
    @Transient val canGoBack: Boolean = false,
    @Transient val canGoForward: Boolean = false,
    @Transient val hasLoadedOnce: Boolean = false,
    /** Bumped to force the WebView to load requestedUrl even if unchanged. */
    @Transient val loadNonce: Int = 0,
) {
    val isHome get() = url == null || url == "about:blank"
}

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
    val icon: String? = null,
) {
    val isFolder get() = type == "folder"
}

@Serializable
data class Favorite(val id: String, val url: String, val title: String, val icon: String? = null)

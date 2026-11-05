// App state: holds the feature stores (tabs, history, bookmarks…) and coordinates actions that span them, plus
// app-wide UI state (open sheet, dialogs, Pill text). Feature-specific actions live in their packages.
package com.jlees.mibrowser

import com.jlees.mibrowser.adblock.cleanLink
import com.jlees.mibrowser.incognito.incognitoSupported

import com.jlees.mibrowser.tabs.applyNavUpdate
import com.jlees.mibrowser.tabs.pushNavigation
import com.jlees.mibrowser.update.Updater

import com.jlees.mibrowser.search.SearchSuggestions
import com.jlees.mibrowser.tabs.SwitcherTransition

import com.jlees.mibrowser.library.BookmarksStore
import com.jlees.mibrowser.library.HistoryStore
import com.jlees.mibrowser.tabs.TabsStore

import com.jlees.mibrowser.tabs.closeTabs
import com.jlees.mibrowser.tabs.tabsToClear
import com.jlees.mibrowser.web.CertificateExceptions

import com.jlees.mibrowser.adblock.AdBlock

import androidx.compose.runtime.derivedStateOf
import com.jlees.mibrowser.search.allBangs
import androidx.compose.runtime.mutableFloatStateOf
import com.jlees.mibrowser.search.searchEngine
import com.jlees.mibrowser.ui.common.clearImageCache
import com.jlees.mibrowser.data.Favicons
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.data.*
import com.jlees.mibrowser.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import java.io.File

enum class ButtonStyle { DEFAULT, CANCEL, DESTRUCTIVE }
data class AlertButton(val text: String, val style: ButtonStyle = ButtonStyle.DEFAULT, val onPress: () -> Unit = {})
/** [onCancel] runs when the dialog is dismissed without a button (back, scrim, or replaced by another alert). */
data class AlertData(val title: String, val message: String, val buttons: List<AlertButton>, val onCancel: () -> Unit = {})
data class ContextMenuData(val url: String?, val imgUrl: String?, val text: String)
/** What a confirmation dialog confirms (see ConfirmDialog for the wording). */
enum class ConfirmAction { CLEAR_HISTORY, RESET_SETTINGS, BACKGROUND_REFRESH, WIPE_DATA, CLEAR_DATA }
data class ConfirmData(val action: ConfirmAction, val ms: Long = 0, val label: String = "")

/** What a launcher shortcut or the search widget asked for. */
enum class LaunchAction { NEW_TAB, NEW_INCOGNITO_TAB, SEARCH }

/** The full-screen sheet over the browser, if any. */
enum class Sheet { NONE, BOOKMARKS, HISTORY, SETTINGS }

/** A site asked for a username and password (HTTP authentication). */
data class AuthRequest(val host: String, val realm: String, val insecure: Boolean, val onSubmit: (String, String) -> Unit, val onCancel: () -> Unit)

/** Commands for the WebView layer (MainActivity's WebViewHost collects these). */
sealed interface WebCmd {
    data class Reload(val tabId: String) : WebCmd
    data class Stop(val tabId: String) : WebCmd
    data class GoBack(val tabId: String) : WebCmd
    data class GoForward(val tabId: String) : WebCmd
    data object Print : WebCmd
    data class CapturePreview(val tabId: String) : WebCmd
    data class Destroy(val tabIds: Set<String>) : WebCmd
    data object WipeWebData : WebCmd
    data object ClearBrowsingData : WebCmd
    data object RecreateAll : WebCmd
}

/** Events for the pill animations (UI collects these). */
sealed interface UiEvent {
    data object LoadStart : UiEvent
    data class LoadProgress(val p: Float) : UiEvent
    data object LoadEnd : UiEvent
    data class Scroll(val yDp: Float) : UiEvent
    data object ShowBar : UiEvent
    data object FocusInput : UiEvent
    data object TabSwitched : UiEvent
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JsonStore(File(app.filesDir, "store"))
    private val cacheDir = app.cacheDir
    val web = MutableSharedFlow<WebCmd>(extraBufferCapacity = 64)
    val ui = MutableSharedFlow<UiEvent>(extraBufferCapacity = 64)

    // ---- persisted state ----
    var settings by mutableStateOf(Settings())
    /** Hosts the user chose to proceed to despite certificate errors — this session only (never persisted). */
    val certExceptions = CertificateExceptions()
    val tabs = TabsStore(store) { web.tryEmit(WebCmd.Destroy(it)) }
    val history = HistoryStore(store)
    val bookmarks = BookmarksStore(store)
    var ready by mutableStateOf(false)

    // ---- UI state ----
    var activeView by mutableStateOf(Sheet.NONE)
    var inputUrl by mutableStateOf("")
    var isInputFocused by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isSubMenuVisible by mutableStateOf(false)
    var switcherVisible by mutableStateOf(false)
    var findVisible by mutableStateOf(false)
    var qrScannerVisible by mutableStateOf(false)
    var qrGeneratorVisible by mutableStateOf(false)
    var bookmarksAutoAdd by mutableStateOf(false)
    var alert by mutableStateOf<AlertData?>(null)
    var confirm by mutableStateOf<ConfirmData?>(null)
    var renameTabId by mutableStateOf<String?>(null)
    var authRequest by mutableStateOf<AuthRequest?>(null)
    var contextMenu by mutableStateOf<ContextMenuData?>(null)
    var settingsSearch by mutableStateOf("")
    var historySearch by mutableStateOf("")

    val updater = Updater(this)
    val suggestions = SearchSuggestions(this)
    val switcher = SwitcherTransition()

    val activeUrl get() = tabs.current?.url

    // ---------------------------------------------------------------- startup
    private var started = false

    fun start(deepLink: String?, shortcut: LaunchAction? = null) {
        pendingShortcut = shortcut
        if (started) return
        started = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settings = (store.read("settings", Settings.serializer()) ?: Settings()).sanitized().migrated()
                history.load()
                bookmarks.load()
                tabs.load(parseDeepLink(deepLink), settings.startupTabMode, settings.backgroundRefresh)
                sweepFiles()
            }
            ready = true
            syncInputToActiveTab()
            pendingShortcut?.let { openFromShortcut(it); pendingShortcut = null }
            savers.forEach { it.launchIn(viewModelScope) }
            updater.checkQuietly()
            AdBlock.load(getApplication())
            AdBlock.updateIfStale(getApplication()) // weekly, on unmetered networks
        }
    }

    /** Cached until the settings change (read on every keystroke and redraw of the Pill). */
    val searchEngine by derivedStateOf { searchEngine(settings.searchEngineIndex, settings.customSearchName, settings.customSearchUrl) }
    val bangs by derivedStateOf { allBangs(settings.customBangs, searchEngine) }

    /** Pull to refresh on the current page: 0 = idle, 1 = released here reloads (WebViewHost reports it). */
    var pullProgress by mutableFloatStateOf(0f)

    /** Launcher shortcuts and the search widget (MainActivity); SEARCH opens a new tab ready to type. */
    fun openFromShortcut(kind: LaunchAction) {
        switcherVisible = false; isSubMenuVisible = false; activeView = Sheet.NONE
        addNewTab(incognito = kind == LaunchAction.NEW_INCOGNITO_TAB && incognitoSupported)
        // the widget's search bar: focus the Pill once the new tab is on screen (the activity may still be starting)
        if (kind == LaunchAction.SEARCH) viewModelScope.launch { kotlinx.coroutines.delay(400); ui.emit(UiEvent.FocusInput) }
    }
    private var pendingShortcut: LaunchAction? = null

    /** Light/dark change: pages read the color scheme when their WebView is created, so rebuild and reload them. */
    fun reloadAllTabs() {
        tabs.list.filter { it.url != null }.forEach { t -> tabs.update(t.id) { copy(requestedUrl = url, loadNonce = loadNonce + 1) } }
        web.tryEmit(WebCmd.RecreateAll)
    }

    /** Startup cleanup of files that outlive their use: orphaned tab previews, camera captures for uploads, shared
     *  QR images, a downloaded update APK, and home background images (a feature that was removed). */
    private fun sweepFiles() {
        val previews = tabs.list.mapNotNull { it.previewImage }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { f ->
                val stale = (f.name.startsWith("preview_") && f.path !in previews) ||
                    f.name.startsWith("capture_") || f.name.startsWith("qrcode_share_") || f.name == "updates"
                if (stale) f.deleteRecursively()
            }
            getApplication<Application>().filesDir.listFiles { f -> f.name.startsWith("home_bg_") || f.name.startsWith("bg_") }
                ?.forEach { it.delete() }
        }
    }

    /** Every stored file; each store owns its own (incognito tabs are never written: they don't survive the app). */
    private val savers = listOf(Saver(0, { settings }) { store.write("settings", Settings.serializer(), it) }, tabs.saver) +
        history.savers + bookmarks.savers

    /** Writes everything now (before mi ends its process after incognito use). */
    fun saveNow() = savers.forEach { it.writeNow() }

    fun updateSettings(f: Settings.() -> Settings) { settings = settings.f() }

    // ---------------------------------------------------------------- tabs (state and saving: TabsStore)
    // a window a page opened and filled in itself has no URL: say so (like Chrome) instead of an empty bar it could pose behind
    private fun syncInputToActiveTab() { inputUrl = activeUrl?.let { displayHost(it) } ?: if (tabs.current?.openerId != null) "about:blank" else "" }

    /** Snapshots a tab's page for its switcher card (set by MainActivity while its WebViewHost exists). */
    var capturePreview: ((String) -> Unit)? = null

    /** Leaving the current tab outside the switcher (a link from another app, a new tab): capture its preview now,
     *  while its WebView is still on screen. The switcher already captured it on the swipe up. */
    private fun captureOutgoing() { if (!switcherVisible) capturePreview?.invoke(tabs.activeId) }

    /** The Pill and page follow the tab that now shows. */
    private fun onTabShown() {
        syncInputToActiveTab()
        isLoading = tabs.current?.loading == true
        ui.tryEmit(UiEvent.TabSwitched)
    }

    fun setActiveTab(id: String) {
        if (id == tabs.activeId) return
        captureOutgoing()
        tabs.select(id)
        onTabShown()
    }

    fun addNewTab(url: String? = null, incognito: Boolean = false) {
        captureOutgoing()
        tabs.open(newTab(url).copy(profile = if (incognito) newIncognitoProfile() else null))
        onTabShown()
    }

    /** A page opened a window: a new tab, linked to its opener, whose WebView the host has already created. */
    fun addPopupTab(id: String, openerId: String, desktopMode: Boolean?) {
        captureOutgoing()
        tabs.open(newTab(null).copy(
            id = id, openerId = openerId, desktopMode = desktopMode,
            profile = tabs[openerId]?.profile, // same profile as the opener (window.opener needs it)
        ))
        switcherVisible = false
        onTabShown()
    }

    /** Closes [ids] ([undoable]: see [TabsStore.close]) and shows whichever tab is next. */
    fun closeTabs(ids: Set<String>, undoable: Boolean = false) {
        val before = tabs.activeId
        if (tabs.close(ids, undoable) != before) onTabShown()
    }

    /** A page closed its window (window.close), or Back left a popup with no history: return to the page that opened it. */
    fun closeWindow(id: String) = closeTabs(setOf(id))

    /** Closed from the switcher or the tab menu: can be undone. */
    fun deleteTab(id: String) = closeTabs(setOf(id), undoable = true)



    fun clearHttpFallback(id: String) { if (tabs[id]?.httpFallback == true) tabs.update(id) { copy(httpFallback = false) } }

    /** Opens [url] in a new tab next to the current one without switching to it. */
    fun addBackgroundTab(url: String) {
        val current = tabs.current
        // just behind the current tab in recency, so it sits next to it in the switcher
        tabs.openInBackground(newTab(url).copy(
            hasLoadedOnce = false, // loads when first shown
            lastUsed = (current?.lastUsed ?: System.currentTimeMillis()) - 1,
            profile = if (current?.incognito == true) newIncognitoProfile() else null, // links from incognito stay incognito
        ))
    }

    // ---------------------------------------------------------------- tab switcher transition
    /** Pill drag started upward: snapshot the page (for its card) and show the switcher behind it. */
    fun beginSwitcher() {
        isSubMenuVisible = false
        switcher.settled = false
        switcher.flyingTabId = null
        switcher.target = null
        web.tryEmit(WebCmd.CapturePreview(tabs.activeId))
        switcherVisible = true
    }

    fun clearAllTabs() {
        val anyPinned = tabs.list.any { it.pinned }
        alert = AlertData("Clear All Tabs", if (anyPinned) "Close all tabs except pinned ones?" else "Are you sure you want to close all tabs?", listOf(
            AlertButton("Cancel", ButtonStyle.CANCEL),
            AlertButton("Clear All", ButtonStyle.DESTRUCTIVE) { resetTabs(keepPinned = true) },
        ))
    }

    /** Closes every tab ([keepPinned]: except pinned ones); with none left, starts over with one new tab. */
    private fun resetTabs(keepPinned: Boolean) = closeTabs(tabsToClear(tabs.list, keepPinned))

    fun renameTab(id: String, text: String) {
        tabs.rename(id, text)
        renameTabId = null
    }

    /** navigateTab: user navigation in the active tab. */
    fun navigateTab(url: String, title: String? = null) {
        val t = tabs.current ?: return
        switcherVisible = false // opened a bookmark/history entry from a sheet over the switcher: show the page
        val (stack, idx) = pushNavigation(t, url)
        tabs.update(t.id) { copy(url = url, requestedUrl = url, title = if (isCustomTitle) this.title else title ?: this.title, historyStack = stack, currentIndex = idx, loadNonce = loadNonce + 1, hasLoadedOnce = true, httpFallback = false) }
    }

    /** Load a URL in a tab without touching its history stack (manual back/forward, https upgrade, intent fallback). */
    fun loadInTab(id: String, url: String) = tabs.update(id) { copy(url = url, requestedUrl = url, loading = true, loadNonce = loadNonce + 1, httpFallback = false) }

    fun submitInput(text: String = inputUrl) {
        val url = inputToUrl(text, settings.httpsOnly, searchEngine, bangs)?.let(::cleanLink) ?: return
        navigateTab(url, text.trim())
        // like Chrome: a typed address tries https first and falls back to http if https can't connect
        if (!settings.httpsOnly && isTypedHttpsUpgrade(text, url)) tabs.update(tabs.activeId) { copy(httpFallback = true) }
    }

    /** Back/forward via WebView, falling back to the stored stack (restored sessions). */
    fun goBack(): Boolean {
        val t = tabs.current ?: return false
        if (t.currentIndex <= 0) return false
        if (t.canGoBack) web.tryEmit(WebCmd.GoBack(t.id)) else t.historyStack.getOrNull(t.currentIndex - 1)?.let { loadInTab(t.id, it) }
        ui.tryEmit(UiEvent.ShowBar)
        return true
    }

    fun goForward(): Boolean {
        val t = tabs.current ?: return false
        if (t.currentIndex >= t.historyStack.size - 1) return false
        if (t.canGoForward) web.tryEmit(WebCmd.GoForward(t.id)) else t.historyStack.getOrNull(t.currentIndex + 1)?.let { loadInTab(t.id, it) }
        ui.tryEmit(UiEvent.ShowBar)
        return true
    }

    val canGoBackUi get() = tabs.current?.let { it.canGoBack || it.currentIndex > 0 } == true
    val canGoForwardUi get() = tabs.current?.let { it.canGoForward || it.currentIndex < it.historyStack.size - 1 } == true

    /** WebView reported a navigation in tab [id]: its address, title, back/forward state and loading. */
    fun onWebNav(id: String, url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean, loading: Boolean) {
        if (url != null && (url.startsWith("intent://") || url.startsWith("android-app://") || url == "about:blank")) return
        val t = tabs.list.firstOrNull { it.id == id } ?: return
        if (t.url == url && t.title == tabTitle(title, url) && t.canGoBack == canGoBack && t.canGoForward == canGoForward && t.loading == loading) return
        val (stack, idx) = applyNavUpdate(t, url, canGoForward)
        tabs.update(id) {
            copy(url = url ?: this.url, canGoBack = canGoBack, canGoForward = canGoForward, loading = loading,
                title = if (isCustomTitle) this.title else tabTitle(title, url), historyStack = stack, currentIndex = idx)
        }
        if (id == tabs.activeId) {
            isLoading = loading
            if (url != null && !loading && t.keepsTraces) history.add(url, tabTitle(title, url))
            if (!isInputFocused && url != null) inputUrl = displayHost(url)
        }
    }

    fun setDesktopMode(on: Boolean) { tabs.update(tabs.activeId) { copy(desktopMode = on) }; web.tryEmit(WebCmd.Reload(tabs.activeId)) }
    fun setReaderMode(on: Boolean) { tabs.update(tabs.activeId) { copy(readerMode = on) }; web.tryEmit(WebCmd.Reload(tabs.activeId)) }

    /** Deep link / VIEW intent while running. */
    /** [incognito]: the link came from an incognito page, so its tab stays incognito. */
    fun handleIncomingUrl(raw: String?, incognito: Boolean = false) {
        val url = parseDeepLink(raw)?.let(::cleanLink) ?: return
        addNewTab(url, incognito)
        activeView = Sheet.NONE
    }

    // ---------------------------------------------------------------- library (state and saving: HistoryStore, BookmarksStore)

    // ---------------------------------------------------------------- overlays
    /** Opens a sheet. From the tab switcher it opens on top, so closing it returns to the switcher. */
    fun openView(v: Sheet) {
        isSubMenuVisible = false
        activeView = v
    }

    /** Called by the sheet once its close animation finishes. */
    fun onOverlayClosed() {
        activeView = Sheet.NONE
        settingsSearch = ""; historySearch = ""
    }

    /** [onCancel] runs if the dialog is dismissed without a button (Back, tapping outside, or replaced by another). */
    fun showAlert(title: String, message: String, buttons: List<AlertButton> = listOf(AlertButton("OK")), onCancel: () -> Unit = {}) {
        alert?.onCancel?.invoke() // an unanswered prompt (e.g. a permission request) counts as declined
        alert = AlertData(title, message, buttons, onCancel)
    }

    // ---------------------------------------------------------------- confirm actions
    fun confirmAction(c: ConfirmData) {
        when (c.action) {
            ConfirmAction.CLEAR_HISTORY -> history.deleteRecent(c.ms)
            ConfirmAction.RESET_SETTINGS -> resetSettings()
            ConfirmAction.BACKGROUND_REFRESH -> updateSettings { copy(backgroundRefresh = true) }
            ConfirmAction.WIPE_DATA -> wipeData()
            ConfirmAction.CLEAR_DATA -> web.tryEmit(WebCmd.ClearBrowsingData)
        }
        confirm = null
    }

    fun resetSettings() {
        settings = Settings().migrated() // current version, so the next launch doesn't migrate it again
        certExceptions.clear()
    }

    private fun wipeData() {
        resetSettings()
        history.clear(); bookmarks.clear()
        resetTabs(keepPinned = false) // erasing everything: pinned tabs go too
        tabs.dismissUndo()
        viewModelScope.launch(Dispatchers.IO) {
            delay(1200) // let the debounced savers flush the empty state first, then remove the files
            store.clear()
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        web.tryEmit(WebCmd.WipeWebData)
        activeView = Sheet.NONE
    }

    /** Applies an imported .midata. */
    fun applyImport(newSettings: Settings) { settings = newSettings.sanitized().migrated() }

    /**
     * Clear Browsing Data, beyond what WebView holds: tab snapshots (screenshots of pages), stored site icons and mi's
     * own HTTP cache (icons, suggestions), all of which reveal what was browsed. Snapshots of open tabs are retaken the
     * next time each tab is left.
     */
    fun clearBrowsingTraces() {
        certExceptions.clear() // granted this session
        tabs.forgetPreviews()
        suggestions.clear()
        clearImageCache()
        Favicons.clear()
        viewModelScope.launch(Dispatchers.IO) {
            cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
            runCatching {
                android.net.http.HttpResponseCache.getInstalled()?.delete() // uninstalls and deletes its contents
                android.net.http.HttpResponseCache.install(File(cacheDir, "http"), 16L * 1024 * 1024)
            }
        }
    }

}

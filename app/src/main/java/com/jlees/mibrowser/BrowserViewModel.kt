// State + actions. Port of legacy/app/index.tsx handlers and the hooks in legacy/src/hooks/*
package com.jlees.mibrowser

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.data.*
import com.jlees.mibrowser.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

data class AlertButton(val text: String, val style: String = "default", val onPress: () -> Unit = {})
data class AlertData(val title: String, val message: String, val buttons: List<AlertButton>)
data class ContextMenuData(val url: String?, val imgUrl: String?, val text: String)
data class ConfirmData(val type: String, val ms: Long = 0, val label: String = "")

/** Commands for the WebView layer (MainActivity's WebViewHost collects these). */
sealed interface WebCmd {
    data class Reload(val tabId: String) : WebCmd
    data class Stop(val tabId: String) : WebCmd
    data class GoBack(val tabId: String) : WebCmd
    data class GoForward(val tabId: String) : WebCmd
    data object Print : WebCmd
    data class CapturePreview(val tabId: String) : WebCmd
    data class Destroy(val tabId: String) : WebCmd
    data object WipeWebData : WebCmd
}

/** Events for the pill animations (UI collects these). */
sealed interface UiEvent {
    data object LoadStart : UiEvent
    data class LoadProgress(val p: Float) : UiEvent
    data object LoadEnd : UiEvent
    data class Scroll(val yDp: Float) : UiEvent
    data object ShowBar : UiEvent
    data object SnapToSearch : UiEvent
    data object FocusInput : UiEvent
    data object TabSwitched : UiEvent
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JsonStore(File(app.filesDir, "store"))
    private val secure = SecureStore(File(app.filesDir, "secure"))
    private val cacheDir = app.cacheDir

    // ---- persisted state ----
    var settings by mutableStateOf(Settings())
    var ignoredHosts by mutableStateOf(listOf<String>())
    var tabs by mutableStateOf(listOf<TabItem>())
    var activeTabId by mutableStateOf("")
    var history by mutableStateOf(listOf<HistoryItem>())
    var recents by mutableStateOf(listOf<HistoryItem>())
    var bookmarks by mutableStateOf(listOf<BookmarkNode>())
    var favorites by mutableStateOf(listOf<Favorite>())
    var ready by mutableStateOf(false)

    // ---- UI state ----
    var activeView by mutableStateOf("none") // none | tabs | history | settings | bookmarks
    var inputUrl by mutableStateOf("")
    var isInputFocused by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isSubMenuVisible by mutableStateOf(false)
    var isSearchActive by mutableStateOf(true)
    var findVisible by mutableStateOf(false)
    var qrScannerVisible by mutableStateOf(false)
    var qrGeneratorVisible by mutableStateOf(false)
    var bookmarksAutoAdd by mutableStateOf(false)
    var alert by mutableStateOf<AlertData?>(null)
    var confirm by mutableStateOf<ConfirmData?>(null)
    var renameTabId by mutableStateOf<String?>(null)
    var contextMenu by mutableStateOf<ContextMenuData?>(null)
    var settingsSearch by mutableStateOf("")
    var tabsSearch by mutableStateOf("")
    var historySearch by mutableStateOf("")
    // transient settings-UI state that RN kept in the settings hook

    val web = MutableSharedFlow<WebCmd>(extraBufferCapacity = 64)
    val ui = MutableSharedFlow<UiEvent>(extraBufferCapacity = 64)

    val currentTab get() = tabs.firstOrNull { it.id == activeTabId }
    val activeUrl get() = currentTab?.url

    // ---------------------------------------------------------------- startup
    private var started = false

    fun start(deepLink: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settings = (store.read("settings", Settings.serializer()) ?: Settings()).sanitized()
                ignoredHosts = secure.read("ignoredHosts", ListSerializer(String.serializer())) ?: emptyList()
                history = (store.read("history", ListSerializer(HistoryItem.serializer())) ?: emptyList()).map { it.copy(id = uniqueId()) }
                recents = (store.read("recent_searches", ListSerializer(HistoryItem.serializer())) ?: emptyList()).map { it.copy(id = uniqueId()) }
                bookmarks = store.read("bookmarks", ListSerializer(BookmarkNode.serializer())) ?: emptyList()
                favorites = store.read("favorites", ListSerializer(Favorite.serializer())) ?: emptyList()
                loadTabs(deepLink)
            }
            ready = true
            syncInputToActiveTab()
            launchSavers()
            checkForUpdate()
        }
    }

    /** Ask GitHub for a newer release once per launch and offer to install it. */
    private fun checkForUpdate() = viewModelScope.launch {
        val current = BuildConfig.VERSION_NAME
        val release = Updates.check(current) ?: return@launch
        if (release.version == settings.skippedUpdate) return@launch
        showAlert(
            "Update available",
            "mi browser ${release.version} is available (you have $current). Download and install it now?",
            listOf(
                AlertButton("Skip this version", "cancel") { updateSettings { copy(skippedUpdate = release.version) } },
                AlertButton("Later", "cancel"),
                AlertButton("Install") { installUpdate(release) },
            ),
        )
    }

    private fun installUpdate(release: Release) = viewModelScope.launch {
        val app = getApplication<Application>()
        Toast.makeText(app, "Downloading update…", Toast.LENGTH_SHORT).show()
        runCatching { Updates.downloadAndInstall(app, release) }
            .onFailure { showAlert("Update failed", it.message ?: "Could not download the update.") }
    }

    /** useTabs.loadTabs */
    private fun loadTabs(deepLink: String?) {
        val saved = store.read("tabs", ListSerializer(TabItem.serializer())) ?: emptyList()
        val savedActive = store.read("activeTabId", String.serializer())
        val existing = saved.map { t ->
            val stack = t.historyStack
            val idx = if (t.currentIndex in stack.indices) t.currentIndex else stack.size - 1
            val url = stack.getOrNull(idx)
            t.copy(currentIndex = if (stack.isEmpty()) -1 else idx, url = url, requestedUrl = url, hasLoadedOnce = settings.backgroundRefresh,
                title = t.title.ifEmpty { "New Tab" })
        }
        val target = parseDeepLink(deepLink)
        var list: List<TabItem>
        var active: String
        if (target != null) {
            val t = newTab(target, "External Link")
            list = listOf(t) + existing; active = t.id
        } else if (settings.startupTabMode == "last" && existing.isNotEmpty()) {
            val tgt = existing.firstOrNull { it.id == savedActive } ?: existing.firstOrNull { it.url != null } ?: existing[0]
            list = existing; active = tgt.id
        } else {
            val blank = existing.firstOrNull { it.url == null }
            if (blank != null) { list = existing; active = blank.id }
            else { val t = newTab(); list = listOf(t) + existing; active = t.id }
        }
        tabs = list.map { if (it.id == active) it.copy(hasLoadedOnce = true) else it }
        activeTabId = active
        // orphaned preview cleanup
        val valid = tabs.mapNotNull { it.previewImage }.toSet()
        cacheDir.listFiles { f -> f.name.startsWith("preview_") && f.name.endsWith(".png") }?.forEach { if (it.path !in valid) it.delete() }
    }

    private fun launchSavers() {
        fun <T> save(delayMs: Long, read: () -> T, write: (T) -> Unit) = viewModelScope.launch {
            snapshotFlow(read).drop(1).collectLatest { v -> delay(delayMs); withContext(Dispatchers.IO) { write(v) } }
        }
        save(0, { settings }) { store.write("settings", Settings.serializer(), it) }
        save(0, { ignoredHosts }) { secure.write("ignoredHosts", ListSerializer(String.serializer()), it) }
        save(500, { tabs to activeTabId }) {
            store.write("tabs", ListSerializer(TabItem.serializer()), it.first)
            store.write("activeTabId", String.serializer(), it.second)
        }
        save(1000, { history }) { store.write("history", ListSerializer(HistoryItem.serializer()), it) }
        save(1000, { recents }) { store.write("recent_searches", ListSerializer(HistoryItem.serializer()), it) }
        save(500, { bookmarks }) { store.write("bookmarks", ListSerializer(BookmarkNode.serializer()), it) }
        save(500, { favorites }) { store.write("favorites", ListSerializer(Favorite.serializer()), it) }
    }

    fun updateSettings(f: Settings.() -> Settings) { settings = settings.f() }

    // ---------------------------------------------------------------- tabs
    fun updateTab(id: String, f: TabItem.() -> TabItem) { tabs = tabs.map { if (it.id == id) it.f() else it } }

    private fun syncInputToActiveTab() { inputUrl = activeUrl?.let { displayHost(it) } ?: "" }

    fun setActiveTab(id: String) {
        if (id == activeTabId) return
        activeTabId = id
        updateTab(id) { copy(hasLoadedOnce = true) }
        syncInputToActiveTab()
        isLoading = currentTab?.loading == true
        ui.tryEmit(UiEvent.TabSwitched)
    }

    fun addNewTab(url: String? = null) {
        val t = newTab(url)
        tabs = listOf(t) + tabs
        activeTabId = t.id
        syncInputToActiveTab()
        isLoading = false
        ui.tryEmit(UiEvent.TabSwitched)
    }

    /** useTabState.deleteTab (+ preview cleanup from onCloseTab) */
    fun deleteTab(id: String) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        tabs[idx].previewImage?.let { File(it).delete() }
        web.tryEmit(WebCmd.Destroy(id))
        val remaining = tabs.filter { it.id != id }
        if (remaining.isEmpty()) {
            val t = newTab(); tabs = listOf(t); activeTabId = t.id; syncInputToActiveTab(); return
        }
        tabs = remaining
        if (activeTabId == id) {
            activeTabId = remaining[minOf(maxOf(0, idx - 1), remaining.size - 1)].id
            updateTab(activeTabId) { copy(hasLoadedOnce = true) }
            syncInputToActiveTab()
            ui.tryEmit(UiEvent.TabSwitched)
        }
    }

    fun reorderTabs(from: Int, to: Int) { tabs = tabs.moved(from, to) }

    fun clearAllTabs() {
        alert = AlertData("Clear All Tabs", "Are you sure you want to close all tabs?", listOf(
            AlertButton("Cancel", "cancel"),
            AlertButton("Clear All", "destructive") { resetTabs() },
        ))
    }

    private fun resetTabs() {
        tabs.forEach { t -> t.previewImage?.let { File(it).delete() }; web.tryEmit(WebCmd.Destroy(t.id)) }
        val t = newTab(); tabs = listOf(t); activeTabId = t.id; inputUrl = ""; isLoading = false
    }

    fun renameTab(id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) updateTab(id) { copy(title = trimmed, isCustomTitle = true) }
        else updateTab(id) { copy(title = url?.let { displayHost(it) } ?: "New Tab", isCustomTitle = false) }
        renameTabId = null
    }

    /** navigateTab: user navigation in the active tab. */
    fun navigateTab(url: String, title: String? = null) {
        val t = currentTab ?: return
        val (stack, idx) = pushNavigation(t, url)
        updateTab(t.id) { copy(url = url, requestedUrl = url, title = if (isCustomTitle) this.title else title ?: this.title, historyStack = stack, currentIndex = idx, loadNonce = loadNonce + 1, hasLoadedOnce = true) }
    }

    /** Load a URL in a tab without touching its history stack (manual back/forward, https upgrade, intent fallback). */
    fun loadInTab(id: String, url: String) = updateTab(id) { copy(url = url, requestedUrl = url, loading = true, loadNonce = loadNonce + 1) }

    fun submitInput(text: String = inputUrl) {
        val url = inputToUrl(text, settings.forceSearchMode, settings.httpsOnly, settings.searchEngineIndex) ?: return
        navigateTab(url, text.trim())
        ui.tryEmit(UiEvent.SnapToSearch)
    }

    fun goHome() {
        updateTab(activeTabId) { copy(url = null, requestedUrl = null, title = "New Tab", loading = false) }
        inputUrl = ""
        isLoading = false
        ui.tryEmit(UiEvent.SnapToSearch)
    }

    /** Back/forward via WebView, falling back to the stored stack (restored sessions). */
    fun goBack(): Boolean {
        val t = currentTab ?: return false
        if (t.currentIndex <= 0) return false
        if (t.canGoBack) web.tryEmit(WebCmd.GoBack(t.id)) else t.historyStack.getOrNull(t.currentIndex - 1)?.let { loadInTab(t.id, it) }
        ui.tryEmit(UiEvent.ShowBar)
        return true
    }

    fun goForward(): Boolean {
        val t = currentTab ?: return false
        if (t.currentIndex >= t.historyStack.size - 1) return false
        if (t.canGoForward) web.tryEmit(WebCmd.GoForward(t.id)) else t.historyStack.getOrNull(t.currentIndex + 1)?.let { loadInTab(t.id, it) }
        ui.tryEmit(UiEvent.ShowBar)
        return true
    }

    val canGoBackUi get() = currentTab?.let { it.canGoBack || it.currentIndex > 0 } == true
    val canGoForwardUi get() = currentTab?.let { it.canGoForward || it.currentIndex < it.historyStack.size - 1 } == true

    /** onNavigationStateChange (BrowserWebView.tsx) + handleTabUpdate + onActiveTabUpdate (index.tsx). */
    fun onWebNav(id: String, url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean, loading: Boolean) {
        if (url != null && (url.startsWith("intent://") || url.startsWith("android-app://") || url == "about:blank")) return
        val t = tabs.firstOrNull { it.id == id } ?: return
        if (t.url == url && t.title == tabTitle(title, url) && t.canGoBack == canGoBack && t.canGoForward == canGoForward && t.loading == loading) return
        val (stack, idx) = applyNavUpdate(t, url, canGoForward)
        updateTab(id) {
            copy(url = url ?: this.url, canGoBack = canGoBack, canGoForward = canGoForward, loading = loading,
                title = if (isCustomTitle) this.title else tabTitle(title, url), historyStack = stack, currentIndex = idx)
        }
        if (id == activeTabId) {
            isLoading = loading
            if (url != null && !loading) addToHistory(url, tabTitle(title, url))
            if (!isInputFocused && url != null) inputUrl = displayHost(url)
        }
    }

    fun setDesktopMode(on: Boolean) { updateTab(activeTabId) { copy(desktopMode = on) }; web.tryEmit(WebCmd.Reload(activeTabId)) }
    fun setReaderMode(on: Boolean) { updateTab(activeTabId) { copy(readerMode = on) }; web.tryEmit(WebCmd.Reload(activeTabId)) }

    /** Deep link / VIEW intent while running. */
    fun handleIncomingUrl(raw: String?) {
        val url = parseDeepLink(raw) ?: return
        addNewTab(url)
        activeView = "none"
    }

    // ---------------------------------------------------------------- history
    fun addToHistory(url: String, title: String?) {
        val (h, r) = addHistory(history, recents, url, title)
        if (h !== history) history = h
        if (r !== recents) recents = r
    }

    fun deleteHistory(ms: Long) {
        history = pruneHistory(history, ms, { it.timestamp })
        recents = pruneHistory(recents, ms, { it.timestamp })
    }

    fun deleteHistoryItem(id: String) { history = history.filter { it.id != id } }
    fun deleteRecentSearch(id: String) { recents = recents.filter { it.id != id } }

    // ---------------------------------------------------------------- favorites
    fun addFavorite(title: String, url: String) {
        if (favorites.size >= 5 || favorites.any { it.url == url }) return
        favorites = favorites + Favorite(uniqueId(), url, title.ifEmpty { "Favorite" }, faviconUrl(url))
    }

    fun removeFavorite(id: String) { favorites = favorites.filter { it.id != id } }
    fun reorderFavorites(from: Int, to: Int) { favorites = favorites.moved(from, to) }

    // ---------------------------------------------------------------- overlays
    fun openView(v: String) {
        if (v == "tabs" && settings.showTabPreview) web.tryEmit(WebCmd.CapturePreview(activeTabId))
        isSubMenuVisible = false
        activeView = v
    }

    /** Called by the sheet once its close animation finishes. */
    fun onOverlayClosed() {
        activeView = "none"
        settingsSearch = ""; tabsSearch = ""; historySearch = ""
        ui.tryEmit(UiEvent.SnapToSearch)
    }

    fun showAlert(title: String, message: String, buttons: List<AlertButton> = listOf(AlertButton("OK"))) {
        alert = AlertData(title, message, buttons)
    }

    // ---------------------------------------------------------------- confirm actions
    fun confirmAction(c: ConfirmData) {
        when (c.type) {
            "history" -> deleteHistory(c.ms)
            "resetSettings" -> resetSettings()
            "bgRefresh" -> updateSettings { copy(backgroundRefresh = true) }
            "wipeData" -> wipeData()
        }
        confirm = null
    }

    fun resetSettings() {
        settings = Settings()
        ignoredHosts = emptyList()
    }

    private fun wipeData() {
        resetSettings()
        history = emptyList(); recents = emptyList(); bookmarks = emptyList(); favorites = emptyList()
        resetTabs()
        viewModelScope.launch(Dispatchers.IO) {
            delay(1200) // let the debounced savers flush the empty state first, then remove the files
            store.clear(); secure.clear()
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        web.tryEmit(WebCmd.WipeWebData)
        activeView = "none"
        ui.tryEmit(UiEvent.SnapToSearch)
    }

    /** Applies an imported .midata (settings + ignoredHosts). */
    fun applyImport(newSettings: Settings, hosts: List<String>?) {
        settings = newSettings.sanitized()
        if (hosts != null) ignoredHosts = hosts
    }

    fun trustHost(host: String) { if (host !in ignoredHosts) ignoredHosts = ignoredHosts + host }
}

// State + actions. Port of legacy/app/index.tsx handlers and the hooks in legacy/src/hooks/*
package com.jlees.mibrowser

import com.jlees.mibrowser.ui.common.clearImageCache
import com.jlees.mibrowser.data.Favicons
import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.Animatable
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
/** [onCancel] runs when the dialog is dismissed without a button (back, scrim, or replaced by another alert). */
data class AlertData(val title: String, val message: String, val buttons: List<AlertButton>, val onCancel: () -> Unit = {})
data class ContextMenuData(val url: String?, val imgUrl: String?, val text: String)
data class ConfirmData(val type: String, val ms: Long = 0, val label: String = "")

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
    data class Destroy(val tabId: String) : WebCmd
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

    // ---- persisted state ----
    var settings by mutableStateOf(Settings())
    /** Hosts the user chose to proceed to despite certificate errors — this session only (never persisted). */
    var ignoredHosts by mutableStateOf(setOf<String>())
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
    var switcherVisible by mutableStateOf(false)
    /** Last tab closed from the switcher, for "Undo". */
    var closedTab by mutableStateOf<Pair<TabItem, Int>?>(null)
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
                // remote debugging never outlives a session: a computer authorized for USB debugging could read pages
                settings = migrate((store.read("settings", Settings.serializer()) ?: Settings()).sanitized()).copy(remoteDebugging = false)
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
            Blocklist.load(getApplication())
        }
    }

    // ---------------------------------------------------------------- search suggestions
    var suggestions by mutableStateOf(listOf<String>())
    private var suggestJob: kotlinx.coroutines.Job? = null

    /** Called as the user types in the address bar: fetches engine suggestions (debounced), only while the search
     *  drawer is open ([drawerOpen]) — suggestions never expand it. */
    fun onTyping(text: String, drawerOpen: Boolean) {
        suggestJob?.cancel()
        val q = text.trim()
        if (!drawerOpen || !settings.searchSuggestions || q.isEmpty() || q == activeUrl || q.contains("://")) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestions = Suggest.fetch(settings.searchEngineIndex, q)
        }
    }

    /** Light/dark change: pages read the color scheme when their WebView is created, so rebuild and reload them. */
    fun reloadAllTabs() {
        tabs = tabs.map { if (it.url != null) it.copy(requestedUrl = it.url, loadNonce = it.loadNonce + 1) else it }
        web.tryEmit(WebCmd.RecreateAll)
    }

    /** Ask GitHub for a newer release once per launch and offer to install it. */
    private fun checkForUpdate() = viewModelScope.launch {
        if (BuildConfig.DEBUG) return@launch // dev builds are a separate app; releases can't update them
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < 12 * 3_600_000L) return@launch // at most twice a day, not every launch
        val current = BuildConfig.VERSION_NAME
        val result = Updates.check(current)
        if (result.isSuccess) updateSettings { copy(lastUpdateCheck = now) }
        val release = result.getOrNull() ?: return@launch
        if (release.version == settings.skippedUpdate) return@launch
        offerUpdate(release)
    }

    /** Settings → About: check now, including a version the user skipped, and say so when there's nothing new. */
    fun checkForUpdatesNow() = viewModelScope.launch {
        val current = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) { showAlert("Development build", "mi dev ($current) is a separate app for development and doesn't update from GitHub releases."); return@launch }
        Updates.check(current)
            .onSuccess { release -> if (release != null) offerUpdate(release) else showAlert("Up to date", "mi browser $current is the latest version.") }
            .onFailure { showAlert("Couldn't check for updates", "GitHub couldn't be reached. Check your connection and try again.") }
    }

    private fun offerUpdate(release: Release) {
        val current = BuildConfig.VERSION_NAME
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
        tabs = list.map { if (it.id == active) it.copy(hasLoadedOnce = true, lastUsed = System.currentTimeMillis()) else it }
        activeTabId = active
        sweepFiles()
    }

    /** Startup cleanup of files that outlive their use: orphaned tab previews, camera captures for uploads, shared
     *  QR images, a downloaded update APK, and home background images (a feature that was removed). */
    private fun sweepFiles() {
        val previews = tabs.mapNotNull { it.previewImage }.toSet()
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

    private fun launchSavers() {
        fun <T> save(delayMs: Long, read: () -> T, write: (T) -> Unit) = viewModelScope.launch {
            snapshotFlow(read).drop(1).collectLatest { v -> delay(delayMs); withContext(Dispatchers.IO) { write(v) } }
        }
        save(0, { settings }) { store.write("settings", Settings.serializer(), it) }
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

    // a window a page opened and filled in itself has no URL: say so (like Chrome) instead of an empty bar it could pose behind
    private fun syncInputToActiveTab() { inputUrl = activeUrl?.let { displayHost(it) } ?: if (currentTab?.openerId != null) "about:blank" else "" }

    /** Snapshots a tab's page for its switcher card (set by MainActivity while its WebViewHost exists). */
    var capturePreview: ((String) -> Unit)? = null

    /** Leaving the current tab outside the switcher (a link from another app, a new tab): capture its preview now,
     *  while its WebView is still on screen. The switcher already captured it on the swipe up. */
    private fun captureOutgoing() { if (!switcherVisible) capturePreview?.invoke(activeTabId) }

    fun setActiveTab(id: String) {
        if (id == activeTabId) return
        captureOutgoing()
        activeTabId = id
        updateTab(id) { copy(hasLoadedOnce = true, lastUsed = System.currentTimeMillis()) }
        syncInputToActiveTab()
        isLoading = currentTab?.loading == true
        ui.tryEmit(UiEvent.TabSwitched)
    }

    fun addNewTab(url: String? = null) {
        val t = newTab(url).copy(lastUsed = System.currentTimeMillis())
        captureOutgoing()
        tabs = listOf(t) + tabs
        activeTabId = t.id
        syncInputToActiveTab()
        isLoading = false
        ui.tryEmit(UiEvent.TabSwitched)
    }

    /** useTabState.deleteTab (+ preview cleanup from onCloseTab) */
    /** Defaults changed for existing installs. v1: third-party cookies allowed (payment/sign-in iframes need them). */
    private fun migrate(s: Settings): Settings {
        var m = s
        if (m.settingsVersion < 1) m = m.copy(blockThirdPartyCookies = false, settingsVersion = 1)
        return m
    }

    private fun activate(id: String) {
        activeTabId = id
        updateTab(id) { copy(hasLoadedOnce = true, lastUsed = System.currentTimeMillis()) }
        syncInputToActiveTab()
        isLoading = currentTab?.loading == true
        ui.tryEmit(UiEvent.TabSwitched)
    }

    /** A page opened a window: a new tab, linked to its opener, whose WebView the host has already created. */
    fun addPopupTab(id: String, openerId: String, desktopMode: Boolean?) {
        val t = newTab(null).copy(id = id, openerId = openerId, desktopMode = desktopMode, hasLoadedOnce = true, lastUsed = System.currentTimeMillis())
        captureOutgoing()
        tabs = listOf(t) + tabs
        switcherVisible = false
        activate(id)
    }

    /** A page closed its window (window.close), or Back left a popup with no history: return to the page that opened it. */
    fun closeWindow(id: String) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        web.tryEmit(WebCmd.Destroy(id))
        t.previewImage?.let { File(it).delete() }
        val remaining = tabs.filter { it.id != id }
        if (remaining.isEmpty()) { val n = newTab(); tabs = listOf(n); activeTabId = n.id; syncInputToActiveTab(); return }
        tabs = remaining
        if (activeTabId == id) activate(t.openerId?.takeIf { o -> remaining.any { it.id == o } } ?: remaining.maxBy { it.lastUsed }.id)
    }

    fun allowPopups(site: String) { if (site !in settings.popupSites) updateSettings { copy(popupSites = popupSites + site) } }

    fun clearHttpFallback(id: String) { if (tabs.any { it.id == id && it.httpFallback }) updateTab(id) { copy(httpFallback = false) } }

    fun deleteTab(id: String) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        closedTab?.first?.previewImage?.let { File(it).delete() } // only the latest closed tab can be restored
        closedTab = tabs[idx] to idx
        web.tryEmit(WebCmd.Destroy(id))
        val remaining = tabs.filter { it.id != id }
        if (remaining.isEmpty()) {
            val t = newTab(); tabs = listOf(t); activeTabId = t.id; syncInputToActiveTab(); return
        }
        tabs = remaining
        if (activeTabId == id) {
            activeTabId = remaining.maxBy { it.lastUsed }.id // like an app switcher: back to the previously used tab
            updateTab(activeTabId) { copy(hasLoadedOnce = true, lastUsed = System.currentTimeMillis()) }
            syncInputToActiveTab()
            ui.tryEmit(UiEvent.TabSwitched)
        }
    }

    fun dismissUndo() {
        closedTab?.first?.previewImage?.let { File(it).delete() }
        closedTab = null
    }

    /** Restores the last closed tab where it was, reloading its current page. */
    fun undoCloseTab() {
        val (t, idx) = closedTab ?: return
        closedTab = null
        val url = t.historyStack.getOrNull(t.currentIndex)
        val restored = t.copy(url = url, requestedUrl = url, loadNonce = t.loadNonce + 1, hasLoadedOnce = true, loading = false)
        tabs = tabs.toMutableList().apply { add(idx.coerceAtMost(size), restored) }
    }

    /** Opens [url] in a new tab next to the current one without switching to it. */
    fun addBackgroundTab(url: String) {
        // just behind the current tab in recency, so it sits next to it in the switcher
        val t = newTab(url).copy(id = uniqueId(), hasLoadedOnce = false, lastUsed = (currentTab?.lastUsed ?: System.currentTimeMillis()) - 1)
        val at = tabs.indexOfFirst { it.id == activeTabId } + 1
        tabs = tabs.toMutableList().apply { add(at.coerceIn(0, size), t) }
    }

    // ---------------------------------------------------------------- tab switcher transition
    /** 0 = browsing, 1 = in the switcher. Driven by the pill drag and by the switcher's open/close animations. */
    val switcherAnim = Animatable(0f)
    /** True once fully in the switcher: the switcher sits above the page. While false, the live page is drawn
     *  on top, scaled between full size and its card, like a system app switcher. */
    var switcherSettled by mutableStateOf(false)
    /** Closing: this tab's snapshot expands over the page (its WebView may not have drawn yet). */
    var flyingTabId by mutableStateOf<String?>(null)
    /** Page area in the window (content below the status bar) and the active tab's card: the zoom's two ends. */
    var pageBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var switcherTarget by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    /** Where the switcher's bottom bar sits in the window: the search pill morphs into the centre pill and the edge
     *  buttons appear from behind it while the switcher opens (see BottomArea). */
    var switcherBarLeft by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var switcherBarPill by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var switcherBarRight by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** Pill drag started upward: snapshot the page (for its card) and show the switcher behind it. */
    fun beginSwitcher() {
        isSubMenuVisible = false
        switcherSettled = false
        flyingTabId = null
        switcherTarget = null
        web.tryEmit(WebCmd.CapturePreview(activeTabId))
        switcherVisible = true
    }

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
        switcherVisible = false // opened a bookmark/history entry from a sheet over the switcher: show the page
        val (stack, idx) = pushNavigation(t, url)
        updateTab(t.id) { copy(url = url, requestedUrl = url, title = if (isCustomTitle) this.title else title ?: this.title, historyStack = stack, currentIndex = idx, loadNonce = loadNonce + 1, hasLoadedOnce = true, httpFallback = false) }
    }

    /** Load a URL in a tab without touching its history stack (manual back/forward, https upgrade, intent fallback). */
    fun loadInTab(id: String, url: String) = updateTab(id) { copy(url = url, requestedUrl = url, loading = true, loadNonce = loadNonce + 1, httpFallback = false) }

    fun submitInput(text: String = inputUrl) {
        val url = inputToUrl(text, settings.forceSearchMode, settings.httpsOnly, settings.searchEngineIndex) ?: return
        navigateTab(url, text.trim())
        // like Chrome: a typed address tries https first and falls back to http if https can't connect
        if (!settings.httpsOnly && isTypedHttpsUpgrade(text, url)) updateTab(activeTabId) { copy(httpFallback = true) }
    }

    fun goHome() {
        updateTab(activeTabId) { copy(url = null, requestedUrl = null, title = "New Tab", loading = false) }
        inputUrl = ""
        isLoading = false
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
        favorites = favorites + Favorite(uniqueId(), url, title.ifEmpty { "Favorite" })
    }

    fun removeFavorite(id: String) { favorites = favorites.filter { it.id != id } }
    fun reorderFavorites(from: Int, to: Int) { favorites = favorites.moved(from, to) }

    // ---------------------------------------------------------------- overlays
    /** Opens a sheet. From the tab switcher it opens on top, so closing it returns to the switcher. */
    fun openView(v: String) {
        isSubMenuVisible = false
        activeView = v
    }

    /** Called by the sheet once its close animation finishes. */
    fun onOverlayClosed() {
        activeView = "none"
        settingsSearch = ""; historySearch = ""
    }

    /** [onCancel] runs if the dialog is dismissed without a button (Back, tapping outside, or replaced by another). */
    fun showAlert(title: String, message: String, buttons: List<AlertButton> = listOf(AlertButton("OK")), onCancel: () -> Unit = {}) {
        alert?.onCancel?.invoke() // an unanswered prompt (e.g. a permission request) counts as declined
        alert = AlertData(title, message, buttons, onCancel)
    }


    // ---------------------------------------------------------------- confirm actions
    fun confirmAction(c: ConfirmData) {
        when (c.type) {
            "history" -> deleteHistory(c.ms)
            "resetSettings" -> resetSettings()
            "bgRefresh" -> updateSettings { copy(backgroundRefresh = true) }
            "wipeData" -> wipeData()
            "clearData" -> web.tryEmit(WebCmd.ClearBrowsingData)
        }
        confirm = null
    }

    fun resetSettings() {
        settings = Settings()
        ignoredHosts = emptySet()
    }

    private fun wipeData() {
        resetSettings()
        history = emptyList(); recents = emptyList(); bookmarks = emptyList(); favorites = emptyList()
        resetTabs()
        viewModelScope.launch(Dispatchers.IO) {
            delay(1200) // let the debounced savers flush the empty state first, then remove the files
            store.clear()
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        web.tryEmit(WebCmd.WipeWebData)
        activeView = "none"
    }

    /** Applies an imported .midata. */
    fun applyImport(newSettings: Settings) { settings = newSettings.sanitized() }

    fun trustHost(host: String) { ignoredHosts = ignoredHosts + host }

    /**
     * Clear Browsing Data, beyond what WebView holds: tab snapshots (screenshots of pages), stored site icons and mi's
     * own HTTP cache (icons, suggestions), all of which reveal what was browsed. Snapshots of open tabs are retaken the
     * next time each tab is left.
     */
    fun clearBrowsingTraces() {
        ignoredHosts = emptySet() // certificate exceptions granted this session
        tabs = tabs.map { it.copy(previewImage = null) }
        closedTab = closedTab?.let { (t, i) -> t.copy(previewImage = null) to i }
        suggestions = emptyList()
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

    /** The visible page's connection isn't secure (see [isNotSecure]); the pill shows a "Not secure" marker. */
    val connectionNotSecure get() = isNotSecure(activeUrl, ignoredHosts)

    fun explainNotSecure() {
        val certError = activeUrl?.startsWith("https://", ignoreCase = true) == true
        showAlert(
            "Connection not secure",
            if (certError) "You chose to continue to ${displayHost(activeUrl)} despite a certificate problem, so this connection may be intercepted. Don't enter passwords, payment details or other sensitive information here."
            else "${displayHost(activeUrl)} doesn't use an encrypted connection, so others on the network could see or change what you send and receive. Don't enter passwords, payment details or other sensitive information here.",
        )
    }
}

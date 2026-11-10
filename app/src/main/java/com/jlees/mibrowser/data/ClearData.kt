// Clearing and wiping mi's data, and the startup sweep of files that outlived their use: actions across the stores,
// WebView data and mi's own caches (see BrowserViewModel).
package com.jlees.mibrowser.data

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.tabs.tabsToClear
import com.jlees.mibrowser.ui.common.clearImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Applies an imported .midata. */
fun BrowserViewModel.applyImport(newSettings: Settings) { settings = newSettings.sanitized().migrated() }
// ---------------------------------------------------------------- confirm actions
fun BrowserViewModel.confirmAction(c: ConfirmData) {
    when (c.action) {
        ConfirmAction.CLEAR_HISTORY -> { history.deleteRecent(c.ms); tabs.forgetClosed() }
        ConfirmAction.RESET_SETTINGS -> resetSettings()
        ConfirmAction.WIPE_DATA -> wipeData()
        ConfirmAction.CLEAR_DATA -> web.tryEmit(WebCmd.ClearBrowsingData)
    }
    confirm = null
}
fun BrowserViewModel.resetSettings() {
    settings = Settings().migrated() // current version, so the next launch doesn't migrate it again
    certExceptions.clear(); httpAllowed.clear()
}
internal fun BrowserViewModel.wipeData() {
    resetSettings()
    history.clear(); bookmarks.clear(); downloads.clear()
    resetTabs(keepPinned = false) // erasing everything: pinned tabs go too
    tabs.dismissUndo(); tabs.forgetClosed() // after closing them, or the wiped tabs would show as recently closed
    viewModelScope.launch(Dispatchers.IO) {
        delay(1200) // let the debounced savers flush the empty state first, then remove the files
        store.clear()
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
    web.tryEmit(WebCmd.WipeWebData)
    activeView = Sheet.NONE
}
/**
 * Clear Browsing Data, beyond what WebView holds: tab snapshots (screenshots of pages), stored site icons and mi's
 * own HTTP cache (icons, suggestions), all of which reveal what was browsed. Snapshots of open tabs are retaken the
 * next time each tab is left.
 */
fun BrowserViewModel.clearBrowsingTraces() {
    tabs.forgetClosed()
    certExceptions.clear(); httpAllowed.clear() // granted this session
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
/**
 * Settings → Privacy → Clear on Exit: all browsing data goes when mi closes: history and recent searches, open and
 * recently closed tabs (pinned tabs stay: pinning is the user saying "keep this one"), and cookies, site data and
 * cache through [clearSiteData] (the WebView host, which the caller reaches its own way at start and at exit).
 */
fun BrowserViewModel.clearOnExit(clearSiteData: () -> Unit) {
    history.clear()
    closeTabs(tabsToClear(tabs.list, keepPinned = true))
    tabs.dismissUndo(); tabs.forgetClosed()
    clearSiteData()
}
/** Startup cleanup of files that outlive their use: orphaned tab previews, camera captures for uploads, shared
 *  QR images, a downloaded update APK, and home background images (a feature that was removed). */
internal fun BrowserViewModel.sweepFiles() {
    val previews = (tabs.list + tabs.recentlyClosed).mapNotNull { it.previewImage }.toSet() // recently closed cards show theirs
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

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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Applies an imported .midata. */
fun BrowserViewModel.applyImport(newSettings: Settings) { settings = newSettings.importedOver(settings) }
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
    history.clear(); bookmarks.clear(); downloads.clear(); follows.clear()
    com.jlees.mibrowser.follow.FollowJob.schedule(getApplication(), false, false)
    com.jlees.mibrowser.follow.FollowImages.clear(getApplication())
    resetTabs(keepPinned = false) // erasing everything: pinned tabs go too
    tabs.dismissUndo(); tabs.forgetClosed() // after closing them, or the wiped tabs would show as recently closed
    web.tryEmit(WebCmd.WipeWebData)
    activeView = Sheet.NONE
    // then mi closes and opens again, fresh: nothing of the old session is left running (pages, downloads, notifications)
    viewModelScope.launch {
        delay(1200) // the web data's clearing finishes (it runs on its own threads)
        android.webkit.CookieManager.getInstance().flush() // the cookies' removal reaches the disk
        com.jlees.mibrowser.follow.PriceChecker.forgetProfile() // price checks' own browser profile (its cookies)
        withContext(Dispatchers.IO) {
            saveNow() // the empty state written over the old files, then the files themselves removed
            store.clear()
            com.jlees.mibrowser.follow.FollowWidget.update(getApplication()) // emptied before mi restarts (its own redraw waits a second)
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        val app = getApplication<android.app.Application>()
        app.getSystemService(android.app.NotificationManager::class.java)?.cancelAll()
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.component?.let { app.startActivity(android.content.Intent.makeRestartActivityTask(it)) }
        Runtime.getRuntime().exit(0)
    }
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

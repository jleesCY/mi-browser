// App updates from GitHub releases: a quiet check at launch, a manual check from Settings → About, and the prompt to
// install a new version. An update replaces the app only: Android keeps its data (tabs, history, bookmarks, favorites,
// recent searches, settings), since it's the same app signed with the same key.
package com.jlees.mibrowser.update

import com.jlees.mibrowser.data.*

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.BuildConfig
import kotlinx.coroutines.launch

class Updater(private val vm: BrowserViewModel) {
    /** At launch: ask GitHub for a newer release and offer to install it. */
    fun checkQuietly() = vm.viewModelScope.launch {
        if (BuildConfig.DEBUG) return@launch // dev builds are a separate app; releases can't update them
        val now = System.currentTimeMillis()
        if (!Updates.dueForCheck(now, vm.settings.lastUpdateCheck)) return@launch
        val result = Updates.check(BuildConfig.VERSION_NAME)
        val release = result.getOrNull()
        if (release == null) {
            if (result.isSuccess) vm.updateSettings { copy(lastUpdateCheck = now) }
            return@launch
        }
        offerUpdate(release)
    }

    /** Settings → About: check now, and say so when there's nothing new. */
    fun checkNow() = vm.viewModelScope.launch {
        val current = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) { vm.showAlert("Development build", "mi dev ($current) is a separate app for development and doesn't update from GitHub releases."); return@launch }
        Updates.check(current)
            .onSuccess { release -> if (release != null) offerUpdate(release) else vm.showAlert("Up to date", "mi browser $current is the latest version.") }
            .onFailure { vm.showAlert("Couldn't check for updates", "GitHub couldn't be reached. Check your connection and try again.") }
    }

    private fun offerUpdate(release: Release) {
        val current = BuildConfig.VERSION_NAME
        vm.showAlert(
            "Update available",
            "mi browser ${release.version} is available (you have $current). Your tabs, bookmarks, history and settings stay as they are.",
            listOf(AlertButton("Install") { installUpdate(release) }),
        )
    }

    private fun installUpdate(release: Release) = vm.viewModelScope.launch {
        val app = vm.getApplication<Application>()
        Toast.makeText(app, "Downloading update…", Toast.LENGTH_SHORT).show()
        runCatching { Updates.downloadAndInstall(app, release) }
            .onFailure { vm.showAlert("Update failed", it.message ?: "Could not download the update.") }
    }
}

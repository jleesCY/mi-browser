package com.jlees.mibrowser.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.logic.displayHost

/** Links that leave the browser for another app, and what happens when no app can take them. */
class ExternalApps(private val activity: Activity, private val vm: BrowserViewModel) {
    /**
     * Links to other apps (tel:, mailto:, market:, intent://…). Pages can only trigger this from a user tap, and the
     * user confirms before another app opens. Without a tap, an intent:// link may still fall back to its web URL.
     */
    fun open(url: String, gesture: Boolean) {
        val isIntent = url.startsWith("intent:") || url.contains("#Intent;")
        if (!gesture) {
            if (isIntent) fallbackUrl(url)?.let { vm.loadInTab(vm.tabs.activeId, it) }
            return
        }
        val target = if (isIntent) Regex("package=([^;]+)").find(url)?.groupValues?.get(1) ?: "another app"
        else Uri.parse(url).scheme?.let { "a $it: link" } ?: "another app"
        vm.showAlert(
            "Open in another app?", "This page wants to open $target.",
            listOf(
                AlertButton("Cancel", ButtonStyle.CANCEL),
                AlertButton("Open") {
                    try {
                        if (isIntent) handleIntent(url)
                        else activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e: Exception) {
                        vm.showAlert("Link Error", "Could not open this link.\n\nError: ${e.message ?: "Unknown error"}\n\nURL: $url")
                    }
                },
            ),
            onCancel = {},
        )
    }

    private fun fallbackUrl(intentUrl: String) = Regex("browser_fallback_url=([^;]+)").find(intentUrl)
        ?.let { Uri.decode(it.groupValues[1]) }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun handleIntent(intentUrl: String) {
        // Attempt 1/2: launch the intent (component/selector stripped for safety)
        try {
            val intent = Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null
            }
            activity.startActivity(intent)
            return
        } catch (_: Exception) {}
        // Attempt 3: browser_fallback_url
        Regex("browser_fallback_url=([^;]+)").find(intentUrl)?.let {
            val fallback = Uri.decode(it.groupValues[1])
            if (!fallback.startsWith("http://") && !fallback.startsWith("https://")) {
                vm.showAlert("Invalid Fallback URL", "The fallback URL provided is not a valid web address."); return
            }
            vm.loadInTab(vm.tabs.activeId, fallback)
            vm.inputUrl = displayHost(fallback)
            return
        }
        // Attempt 4: package → store
        Regex("package=([^;]+)").find(intentUrl)?.let {
            val pkg = it.groupValues[1]
            vm.showAlert("App Required", "This feature requires an external app ($pkg). Would you like to view it in the store?", listOf(
                AlertButton("Cancel", ButtonStyle.CANCEL),
                AlertButton("View Store") {
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
                },
            ))
            return
        }
        vm.showAlert("Action Failed", "Could not handle this action and no fallback was provided by the website.")
    }
}

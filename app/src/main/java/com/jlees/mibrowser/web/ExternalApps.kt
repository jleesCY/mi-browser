package com.jlees.mibrowser.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.logic.displayHost

/** How long after another app opens a tab its page may hand back to an app without a tap (a sign-in, done). */
const val APP_HANDBACK_MS = 10 * 60_000L

/**
 * Whether a page may open an app without a tap: only in a tab another app opened, within [APP_HANDBACK_MS], and only
 * that app (ExternalApps launches it by package). A sign-in started from an app (Slack's "sign in with your
 * organization") ends with the site sending you back to the app by itself, as in Chrome; a site can't use it to open
 * any other app. Anywhere else, opening an app takes a tap and a yes.
 */
fun mayOpenAppUntapped(openedByAppAt: Long, now: Long) = openedByAppAt > 0 && now - openedByAppAt in 0..APP_HANDBACK_MS

/** Links that leave the browser for another app, and what happens when no app can take them. */
class ExternalApps(private val activity: Activity, private val vm: BrowserViewModel) {
    /**
     * Links to other apps (tel:, mailto:, market:, intent://…) from tab [tabId]. From a tap, the user confirms before
     * another app opens. Without one, only a tab another app just opened may hand back to that app (mayOpenAppUntapped),
     * once, straight away; otherwise an intent:// link may still fall back to its web URL.
     */
    fun open(url: String, gesture: Boolean, tabId: String? = null) {
        val isIntent = url.startsWith("intent:") || url.contains("#Intent;")
        if (!gesture) {
            val tab = vm.tabs.list.firstOrNull { it.id == tabId }
            val app = tab?.openedByApp
            if (app != null && mayOpenAppUntapped(tab.openedByAppAt, System.currentTimeMillis()) && launch(url, isIntent, app)) {
                vm.tabs.update(tab.id) { copy(openedByAppAt = 0) } // once: a page can't keep opening it
                return
            }
            if (isIntent) fallbackUrl(url)?.let { vm.loadInTab(tabId ?: vm.tabs.activeId, it) }
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

    /** Opens [url] in app [pkg] only, without asking (a hand-back to the app that sent you here); false if it can't take it. */
    private fun launch(url: String, isIntent: Boolean, pkg: String): Boolean = runCatching {
        val intent = if (isIntent) Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply { component = null; selector = null }
        else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.`package` != null && intent.`package` != pkg) return false // a link for another app
        activity.startActivity(intent.setPackage(pkg).addCategory(Intent.CATEGORY_BROWSABLE))
    }.isSuccess

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

package com.jlees.mibrowser.web

import com.jlees.mibrowser.ButtonStyle
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel

/**
 * Pop-ups opened without a tap are blocked unless the site is allowed. Normal tabs use the saved allow-list; an
 * incognito tab ([profile] set) only what was allowed in it this session, never added to the saved list.
 */
class PopupPolicy(private val vm: BrowserViewModel) {
    /** "<profile>|<site>" allowed in incognito tabs. */
    private val incognitoSites = HashSet<String>()
    /** Last notice (site|page), so a page that keeps trying doesn't stack dialogs. */
    private var lastNotice: String? = null

    fun allowed(profile: String?, site: String) =
        if (profile != null) "$profile|$site" in incognitoSites else site in vm.settings.popupSites

    /** Tells the user a pop-up was blocked, with a way to allow the site (then they retry). */
    fun blocked(profile: String?, site: String, page: String?) {
        if (site.isEmpty() || "$site|$page" == lastNotice) return
        lastNotice = "$site|$page"
        vm.showAlert(
            "Pop-up blocked",
            "$site tried to open a new window without a tap. If you were expecting one (a sign-in or payment window), allow pop-ups for this site and try again.",
            if (profile != null) listOf(AlertButton("Keep blocking", ButtonStyle.CANCEL), AlertButton("Allow in this tab") { incognitoSites += "$profile|$site" })
            else listOf(AlertButton("Keep blocking", ButtonStyle.CANCEL), AlertButton("Always allow") { vm.allowPopups(site) }),
            onCancel = {},
        )
    }

    /** An incognito profile ended: its allowances go with it. */
    fun forget(profile: String) { incognitoSites.removeAll { it.startsWith("$profile|") }; lastNotice = null }
}

/** "Always allow" from the pop-up notice: [site] joins the saved allow-list (normal tabs only). */
fun BrowserViewModel.allowPopups(site: String) { if (site !in settings.popupSites) updateSettings { copy(popupSites = popupSites + site) } }

// Ad blocking from the tab menu: turning it off (and back on) for the current page's site.
package com.jlees.mibrowser.adblock

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.displayHost

/** The current page's site with ad blocking off ("Block ads here" in the tab menu), or null. */
fun BrowserViewModel.adsAllowedHere(): String? {
    val h = displayHost(activeUrl).takeIf { it.isNotEmpty() } ?: return null
    return settings.adAllowlist.firstOrNull { h == it || h.endsWith(".$it") }
}
/** Turns ad blocking off / back on for the current page's site (by its registrable domain) and reloads it. */
fun BrowserViewModel.toggleAdsHere() {
    val host = displayHost(activeUrl).takeIf { it.isNotEmpty() } ?: return
    val allowed = adsAllowedHere()
    updateSettings { copy(adAllowlist = if (allowed != null) adAllowlist - allowed else adAllowlist + AdBlock.registrable(host)) }
    web.tryEmit(WebCmd.Reload(tabs.activeId))
}

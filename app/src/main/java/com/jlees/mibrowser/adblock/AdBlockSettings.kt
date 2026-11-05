// Settings → Ad Blocking: what to block, allowed sites and the filter lists.
package com.jlees.mibrowser.adblock

import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

import androidx.compose.runtime.rememberCoroutineScope
import com.jlees.mibrowser.adblock.AdBlock
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.launch

/** The Ad Blocking section of Settings. */
fun adBlockSection(vm: BrowserViewModel) =
    SettingsSection("adblock", "Ad Blocking", "block", "Ads, trackers, cookie banners, allowed sites", listOf(
        SettingsRow("Block Ads", "adblock easylist banner ads malware") {
            SwitchItem("block", "Block Ads", "Ads and ad networks (EasyList and a malware and ad domain list)", vm.settings.blockAds) { vm.updateSettings { copy(blockAds = it) } }
        },
        SettingsRow("Block Trackers", "tracking analytics easyprivacy fingerprint") {
            SwitchItem("travel_explore", "Block Trackers", "Analytics and tracking scripts that follow you across sites (EasyPrivacy)", vm.settings.blockTrackers) { vm.updateSettings { copy(blockTrackers = it) } }
        },
        SettingsRow("Hide Cookie Banners", "consent gdpr popup cookie") {
            SwitchItem("cookie", "Hide Cookie Banners", "Cookie-consent pop-ups (EasyList Cookie List). Turn off if a site won't let you continue", vm.settings.hideCookieBanners) { vm.updateSettings { copy(hideCookieBanners = it) } }
        },
        SettingsRow("Remove Tracking from Links", "utm fbclid gclid parameters clean url") {
            SwitchItem("content_copy", "Remove Tracking from Links", "Strips utm_, fbclid, gclid and similar tags from links you open, copy or share", vm.settings.stripTrackingParams) { vm.updateSettings { copy(stripTrackingParams = it) } }
        },
        SettingsRow("Allowed Sites", "whitelist exception allow ads site") { AllowedSitesItem(vm) },
        SettingsRow("Update Filter Lists", "adblock update easylist") { FilterListsItem(vm) },
    ))

/** Sites where blocking is off (added from the tab menu's "Block ads here"), each with a button to block again. */
@Composable
private fun AllowedSitesItem(vm: BrowserViewModel) {
    val sites = vm.settings.adAllowlist
    Column(Modifier.fillMaxWidth()) {
        Item("public", "Allowed Sites", if (sites.isEmpty()) "None. To turn blocking off on a site, open the tab menu there and switch off Block ads here"
            else "Blocking is off on ${sites.size} site${if (sites.size == 1) "" else "s"}")
        sites.forEach { site ->
            val ui = LocalUi.current
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 72.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(site, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { vm.updateSettings { copy(adAllowlist = adAllowlist - site) } }) { Sym("delete", 20f, ui.cs.onSurfaceVariant) }
            }
        }
        if (sites.isNotEmpty()) Spacer(Modifier.height(8.dp))
    }
}

/** When the ad-blocking lists were last updated, with a button to update them now (they also update weekly on Wi-Fi). */
@Composable
private fun FilterListsItem(vm: BrowserViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    val updated = AdBlock.listsUpdated
    val ago = if (updated == 0L) "Loading…" else android.text.format.DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    Item("refresh", "Update Filter Lists", if (updating) "Updating…" else "Updated $ago. They also update weekly on Wi-Fi", onClick = {
        if (updating) return@Item
        updating = true
        scope.launch {
            val ok = AdBlock.updateIfStale(ctx.applicationContext, force = true)
            updating = false
            android.widget.Toast.makeText(ctx, if (ok) "Filter lists updated" else "Couldn't download the filter lists", android.widget.Toast.LENGTH_SHORT).show()
        }
    })
}


// Site settings: what the current tab's site may do, in one place, opened by tapping the site at the top of the tab
// menu. Everything here already exists elsewhere (pop-up and ad-blocking lists, session permissions, certificate
// exceptions); this page just gathers it for one site.
package com.jlees.mibrowser.sites

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.adblock.adsAllowedHere
import com.jlees.mibrowser.adblock.toggleAdsHere
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.SwitchItem
import com.jlees.mibrowser.web.WebViewHost
import com.jlees.mibrowser.web.connectionNotSecure

@Composable
fun SiteSettingsView(vm: BrowserViewModel, host: WebViewHost, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val tab = vm.tabs.current
    val url = vm.activeUrl
    val site = displayHost(url)
    if (tab == null || url == null || site.isEmpty()) { LaunchedEffect(Unit) { closeOverlay() }; return }
    // the host's session lists aren't Compose state: re-read them after each change
    var version by remember { mutableIntStateOf(0) }
    val profile = host.profileOf(tab.id)
    val allowed = remember(version) { host.siteAccess(tab.id, site) }
    val popups = remember(version, vm.settings.popupSites) { host.popupsAllowed(tab.id, site) }
    val certSkipped = remember(version) { vm.certExceptions.has(profile, site) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 48.dp)) {
        // the site
        Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIconTile {
                if (tab.incognito || !vm.settings.showTabLogo) Sym(if (tab.incognito) "incognito" else "public", 22f, ui.cs.onSurfaceVariant)
                else NetImage(Favicons.src(url), Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))) { Sym("public", 22f, ui.cs.onSurfaceVariant) }
            }
            Column {
                Txt(site, 18f, ui.cs.onSurface, ui.bold, maxLines = 1)
                Txt(
                    if (vm.connectionNotSecure) "Connection not secure" else "Secure connection", 13f,
                    if (vm.connectionNotSecure) ui.cs.error else ui.cs.onSurfaceVariant, ui.semibold,
                )
            }
        }

        Group {
            if (certSkipped) Item("warning", "Certificate Warning Skipped", "You chose to continue despite a certificate problem. Tap to stop trusting this site", danger = true,
                onClick = { vm.certExceptions.untrust(profile, site); version++; vm.web.tryEmit(WebCmd.Reload(tab.id)) })
            // camera, microphone and location: what was allowed this session (asked again after a switch-off)
            if (allowed.isEmpty()) Item("lock", "Camera, Microphone & Location", "Not allowed this session; the site asks when it needs them")
            else allowed.forEach { a ->
                SwitchItem(a.icon, a.label, "Allowed this session", true) { if (!it) { host.revokeSiteAccess(tab.id, site, a); version++ } }
            }
            SwitchItem("open_in_new", "Pop-ups", if (tab.incognito) "Open without a tap, in this incognito tab" else "Open without a tap", popups) {
                host.setPopupsAllowed(tab.id, site, it); version++
            }
            val blocking = vm.adsAllowedHere() == null
            SwitchItem("block", "Ad Blocking", "Ads, trackers and cookie banners on this site", blocking) { vm.toggleAdsHere() }
        }

        Group(Modifier.padding(top = 16.dp)) {
            Item("delete", "Clear Site Data", "Cookies and storage for $site; you'll be signed out of it", danger = true, onClick = {
                vm.showAlert("Clear data for $site?", "Its cookies and stored data are deleted and the page reloads. You'll be signed out of $site.", listOf(
                    AlertButton("Cancel", ButtonStyle.CANCEL),
                    AlertButton("Clear", ButtonStyle.DESTRUCTIVE) {
                        host.clearSiteData(tab.id, url)
                        Toast.makeText(ctx, "Site data cleared", Toast.LENGTH_SHORT).show()
                    },
                ))
            })
        }
        if (tab.incognito) Txt("Incognito: choices here last until this incognito tab closes.", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 16.dp, start = 4.dp))
    }
}

/** A rounded group of rows, like a Settings section. */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(ui.radius.dp)).background(ui.cs.surfaceContainerHighest), content = content)
}

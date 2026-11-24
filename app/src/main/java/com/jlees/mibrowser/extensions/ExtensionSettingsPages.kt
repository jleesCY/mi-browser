// Get Extensions, in Settings' style: searching Firefox Add-ons, or browsing the Chrome Web Store, where mi offers
// "Add to mi." on each extension's page. Opened from Library › Extensions (ExtensionsLibrary.kt); each installed
// extension's own panel is ExtensionPanel.kt.
package com.jlees.mibrowser.extensions

import com.jlees.mibrowser.ui.common.SheetPillSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.LocalSettingsNav
import com.jlees.mibrowser.ui.settings.SettingsSubPage
import com.jlees.mibrowser.ui.settings.ExpandItem
import kotlinx.coroutines.delay

/** Settings › Extensions › Get Extensions. */
fun getExtensionsPage(vm: BrowserViewModel): SettingsSubPage {
    // the sheet's search Pill searches Firefox Add-ons while this page shows
    val search = com.jlees.mibrowser.ui.settings.PageSearch("Search Firefox Add-ons")
    return SettingsSubPage("get-extensions", "Get Extensions", "extension", search) { GetExtensionsPage(vm, search) }
}

@Composable
internal fun GetExtensionsPage(vm: BrowserViewModel, search: com.jlees.mibrowser.ui.settings.PageSearch) {
    val ui = LocalUi.current
    val nav = LocalSettingsNav.current
    val query = search.query
    var results by remember { mutableStateOf<List<AmoAddon>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        results = null; failed = false
        if (query.isNotBlank()) delay(400) // typing: search once it settles
        runCatching { ExtensionInstaller.search(query) }.onSuccess { results = it }.onFailure { failed = true }
    }
    Column(Modifier.fillMaxSize()) {
        Item("open_in_new", "Chrome Web Store", "Add any extension from its page there", onClick = {
            vm.addNewTab("https://chromewebstore.google.com/category/extensions"); vm.switcherVisible = false; nav.close(); vm.openView(com.jlees.mibrowser.Sheet.NONE)
        })
        Txt(if (query.isBlank()) "Recommended" else "Results", 14f, ui.cs.primary, ui.bold, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
        // the list reads its own copy: the results change (to null) as soon as the search does, before the list is redrawn
        val list = results
        when {
            failed -> EmptyState("error", "Couldn't reach Firefox Add-ons", top = 30.dp)
            list == null -> LoadingRows()
            list.isEmpty() -> EmptyState("search", "No matches", top = 30.dp)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = SheetPillSpace)) { // ends clear of the Pill, scrolls behind it
                items(list, key = { it.slug }) { a -> AddonRow(vm, a) }
                item { Txt("Tap an extension to see how well it works in mi before you add it.", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(16.dp), lineHeight = 18f) }
            }
        }
    }
}

/** While searching: rows shaped like the results (icon, name, summary, users), gently pulsing, in place of a spinner. */
@Composable
private fun LoadingRows() {
    val ui = LocalUi.current
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "loading").animateFloat(
        0.35f, 0.7f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse",
    )
    val fill = ui.cs.onSurfaceVariant.copy(alpha = 0.12f)
    Column(Modifier.graphicsLayer { alpha = pulse }) {
        repeat(6) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(fill))
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.55f else 0.4f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(fill))
                    Box(Modifier.fillMaxWidth(0.9f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                    Box(Modifier.fillMaxWidth(0.25f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(fill))
                }
            }
        }
    }
}

@Composable
private fun AddonRow(vm: BrowserViewModel, a: AmoAddon) {
    val ui = LocalUi.current
    val installed = vm.extensions.list.any { it.source?.contains("/addon/${a.slug}/") == true }
    Row(Modifier.fillMaxWidth().touchable(enabled = !installed) { vm.fetchExtension(InstallSource.Amo(a.slug), a.name, StoreListing(a.name, a.summary, a.iconUrl)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(rowTileColor()), contentAlignment = Alignment.Center) {
            NetImage(a.iconUrl, Modifier.size(32.dp)) { Sym("extension", 24f, ui.cs.onSurfaceVariant) }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Txt(a.name, 16f, ui.cs.onSurface, ui.semibold, maxLines = 1)
            Txt(a.summary, 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 2, lineHeight = 18f)
            Txt(if (installed) "Added" else users(a.users), 12f, if (installed) ui.cs.primary else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 2.dp))
        }
    }
}

private fun users(n: Long) = when {
    n >= 1_000_000 -> "%.1fM users".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> "${n / 1_000}K users"
    else -> "$n users"
}

/** Sites where the extension is turned off: each can be turned back on; a site can be added. */
@Composable
internal fun OffSitesItem(vm: BrowserViewModel, e: InstalledExtension) {
    val ui = LocalUi.current
    var adding by remember { mutableStateOf("") }
    ExpandItem("block", "Turned Off On", if (e.offSites.isEmpty()) "Sites where it won't run" else null, if (e.offSites.isEmpty()) null else e.offSites.size.toString()) {
        e.offSites.forEach { site ->
            Row(Modifier.fillMaxWidth().padding(start = 72.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(site, 15f, ui.cs.onSurface, ui.regular, Modifier.weight(1f), maxLines = 1, pan = false)
                androidx.compose.material3.IconButton(onClick = { vm.setExtensionOffOn(e.id, site, off = false) }) { Sym("close", 20f, ui.cs.onSurfaceVariant, label = "Turn back on for $site") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 72.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ClearableField(adding, { adding = it }, modifier = Modifier.weight(1f), uri = true, placeholder = "example.com")
            androidx.compose.material3.IconButton(onClick = { vm.setExtensionOffOn(e.id, adding, off = true); adding = "" }, enabled = adding.isNotBlank()) { Sym("add", 22f, ui.cs.primary, label = "Turn off on this site") }
        }
    }
}

/** A permission in plain words. */
internal fun permissionWords(p: String) = when (p) {
    "storage", "unlimitedStorage" -> "Store its own data"
    "tabs" -> "See your tabs' addresses and titles"
    "activeTab" -> "Reach the page you're on when you use it"
    "scripting" -> "Run its scripts in pages"
    "alarms" -> "Run on a schedule"
    "notifications" -> "Show notifications"
    "contextMenus", "menus" -> "Add to the long-press menu"
    "cookies" -> "Read and change cookies"
    "webRequest", "webRequestBlocking" -> "Watch and change web requests"
    "declarativeNetRequest", "declarativeNetRequestWithHostAccess" -> "Change web requests by rules"
    "webNavigation" -> "Follow page loads"
    "downloads" -> "Manage downloads"
    "history" -> "Read your history"
    "bookmarks" -> "Read your bookmarks"
    "clipboardRead" -> "Read what you copy"
    "clipboardWrite" -> "Copy to your clipboard"
    "identity" -> "Sign you in through the browser"
    "nativeMessaging" -> "Talk to apps on a computer"
    else -> p
}

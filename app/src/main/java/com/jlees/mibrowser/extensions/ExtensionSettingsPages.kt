// Get Extensions, laid out like an app store: the extensions tested in mi, then Firefox Add-ons' extensions made for
// phones (Firefox for Android's), recommended, by category or searched, and a link to that store's own site. Opened from Library › Extensions (ExtensionsLibrary.kt); each installed
// extension's own panel is ExtensionPanel.kt.
package com.jlees.mibrowser.extensions

import com.jlees.mibrowser.ui.common.SheetPillSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.LocalSettingsNav
import com.jlees.mibrowser.ui.settings.ExpandItem
import kotlinx.coroutines.delay

/** Firefox Add-ons' categories offered as chips (slug: theirs; null: its recommended extensions). */
private enum class StoreCategory(val label: String, val slug: String?) {
    ALL("All", null), PRIVACY("Privacy", "privacy-security"), APPEARANCE("Appearance", "appearance"),
    VIDEO("Video & Music", "photos-music-videos"), SHOPPING("Shopping", "shopping"), SOCIAL("Social", "social-communication"),
    TABS("Tabs", "tabs"), SEARCH("Search", "search-tools"), LANGUAGE("Language", "language-support"),
}

/**
 * Get Extensions, laid out like an app store: the extensions tested in mi as a row of cards, then Firefox Add-ons'
 * recommended extensions (or a category's most used, or what the search Pill finds) as a list with ratings and Get, all
 * of them made for phones (ExtensionInstaller.search), and Firefox Add-ons' site for Android, to browse it there.
 */
@Composable
internal fun GetExtensionsPage(vm: BrowserViewModel, search: com.jlees.mibrowser.ui.settings.PageSearch) {
    val ui = LocalUi.current
    val nav = LocalSettingsNav.current
    val query = search.query
    var category by remember { mutableStateOf(StoreCategory.ALL) }
    var allTested by remember { mutableStateOf(false) } // the tested ones as a list (See all) rather than a row of cards
    var results by remember { mutableStateOf<List<AmoAddon>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(query, category) {
        results = null; failed = false
        if (query.isNotBlank()) delay(400) // typing: search once it settles
        runCatching { ExtensionInstaller.search(query, category.slug) }.onSuccess { results = it }.onFailure { failed = true }
    }
    val browsing = query.isBlank()
    // the list reads its own copy: the results change (to null) as soon as the search does, before the list is redrawn
    val list = results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = SheetPillSpace)) { // ends clear of the Pill, scrolls behind it
        if (browsing && VERIFIED_EXTENSIONS.isNotEmpty()) {
            item(key = "tested") {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Heading("Tested in mi")
                    TestedInfo(vm)
                    Spacer(Modifier.weight(1f))
                    // every tested one as a list, instead of swiping through the cards (and back)
                    Txt(if (allTested) "Show less" else "See all", 14f, ui.cs.primary, ui.semibold,
                        Modifier.clip(RoundedCornerShape(50)).touchable { allTested = !allTested }.padding(horizontal = 12.dp, vertical = 12.dp))
                }
            }
            fun get(v: VerifiedExtension) = vm.fetchExtension(v.source, v.name, StoreListing(v.name, v.summary, v.iconUrl))
            fun added(v: VerifiedExtension) = vm.extensions.list.any { verifiedFor(storeSource(it.source)) == v }
            if (allTested) items(VERIFIED_EXTENSIONS, key = { "tested:" + it.name }) { v ->
                StoreRow(v.iconUrl, v.name, v.summary, 0.0, null, tested = true, added = added(v)) { get(v) }
            } else item(key = "testedCards") {
                // a row of cards, swiped sideways; one already added says so
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    items(VERIFIED_EXTENSIONS, key = { it.name }) { v -> TestedCard(v, added(v)) { get(v) } }
                }
            }
            item(key = "chips") {
                LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    items(StoreCategory.entries.toList(), key = { it.name }) { c -> CategoryChip(c.label, c == category) { category = c } }
                }
            }
        }
        item(key = "heading") {
            Heading(when { !browsing -> "Results"; category == StoreCategory.ALL -> "Recommended on Firefox Add-ons"; else -> "Popular in ${category.label}" },
                Modifier.padding(top = 8.dp))
        }
        when {
            failed -> item { EmptyState("error", "Couldn't reach Firefox Add-ons", top = 30.dp) }
            list == null -> item { LoadingRows() }
            list.isEmpty() -> item { EmptyState("search", "No matches", top = 30.dp) }
            else -> items(list, key = { it.slug }) { a -> AddonRow(vm, a) }
        }
        item(key = "amo") {
            Column(Modifier.padding(top = 16.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(minOf(ui.radius, 24).dp)).background(ui.cs.surfaceContainerHighest)
                    .touchable { vm.addNewTab("https://addons.mozilla.org/android/"); vm.switcherVisible = false; nav.close(); vm.openView(com.jlees.mibrowser.Sheet.NONE) }
                    .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym("open_in_new", 22f, ui.cs.onSurfaceVariant)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Txt("Browse Firefox Add-ons for Android", 15f, ui.cs.onSurface, ui.semibold, maxLines = 1)
                        Txt("Every extension made for phones, on its own site", 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 2)
                    }
                }
                Txt("Tap an extension to see how well it works in mi before you add it.", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(horizontal = 4.dp, vertical = 16.dp), lineHeight = 18f)
            }
        }
    }
}

/** A section's heading on the store page. */
@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Txt(text, 15f, ui.cs.primary, ui.bold, modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), maxLines = 1)
}

/** A tested extension as a card: its icon, name and summary, and "Tested in mi" (or "Added", once it is). */
@Composable
private fun TestedCard(v: VerifiedExtension, added: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.width(168.dp).height(196.dp).clip(RoundedCornerShape(minOf(ui.radius, 24).dp)).background(ui.cs.surfaceContainerHighest)
        .touchable(enabled = !added, onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(rowTileColor()), contentAlignment = Alignment.Center) {
            NetImage(v.iconUrl, Modifier.size(36.dp)) { Sym("extension", 26f, ui.cs.onSurfaceVariant) }
        }
        Txt(v.name, 15f, ui.cs.onSurface, ui.bold, Modifier.padding(top = 10.dp), maxLines = 2, lineHeight = 19f)
        Txt(v.summary, 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 4.dp).weight(1f), maxLines = 3, lineHeight = 16f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!added) Sym("check", 14f, ui.cs.primary, Modifier.padding(end = 4.dp))
            Txt(if (added) "Added" else "Tested in mi", 12f, if (added) ui.cs.onSurfaceVariant else ui.cs.primary, ui.semibold, maxLines = 1)
        }
    }
}

/** A category to browse; the chosen one filled. At least 48 tall to tap. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Txt(label, 14f, if (selected) ui.cs.onSecondaryContainer else ui.cs.onSurfaceVariant, ui.semibold,
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) ui.cs.secondaryContainer else ui.cs.surfaceContainerHighest)
                .touchable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1)
    }
}

/**
 * The ⓘ beside "Tested in mi": a bubble (growing from it) saying what that means, with a link to the setting for
 * asking that an extension be made to work (Settings › About › Request Extension Support).
 */
@Composable
private fun TestedInfo(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Sym("info", 18f, ui.cs.onSurfaceVariant, label = "What Tested in mi means") }
        if (open) androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.roundToPx() }),
            onDismissRequest = { open = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            // grows from the ⓘ (its top start); closes at once
            val shown = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) { shown.animateTo(1f, androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
            val shape = RoundedCornerShape(minOf(ui.radius, 20).dp)
            Column(Modifier.widthIn(max = 300.dp).padding(8.dp)
                .graphicsLayer { alpha = shown.value; scaleX = 0.9f + 0.1f * shown.value; scaleY = scaleX; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 0f) }
                .shadow(10.dp, shape).clip(shape).background(ui.cs.surfaceContainer).padding(16.dp)) {
                Txt("Tested in mi", 15f, ui.cs.onSurface, ui.bold)
                Txt("These were checked by hand in mi, and their main features work. You can still add any other extension: mi shows how well it should work before you add it.",
                    13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 6.dp), lineHeight = 18f)
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).touchable {
                    open = false
                    vm.settingsTarget = com.jlees.mibrowser.SettingsTarget("about", "Help & Feedback", "Request Extension Support")
                    vm.openView(com.jlees.mibrowser.Sheet.SETTINGS)
                }.heightIn(min = 48.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Request Extension Support", 14f, ui.cs.primary, ui.semibold)
                    Sym("chevron_right", 18f, ui.cs.primary, Modifier.padding(start = 2.dp))
                }
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
    val installed = vm.extensions.list.any { it.source?.contains("/addon/${a.slug}/") == true }
    val tested = verifiedFor(InstallSource.Amo(a.slug)) != null
    StoreRow(a.iconUrl, a.name, a.summary, a.rating, users(a.users), tested, installed) { vm.fetchExtension(InstallSource.Amo(a.slug), a.name, StoreListing(a.name, a.summary, a.iconUrl)) }
}

/**
 * One extension in the store's list: its icon, name, a line of its summary, its rating and users (when known: a tested
 * one's aren't), whether it's tested in mi, and Get (or Added).
 */
@Composable
private fun StoreRow(iconUrl: String?, name: String, summary: String, rating: Double, users: String?, tested: Boolean, added: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).touchable(enabled = !added, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(rowTileColor()), contentAlignment = Alignment.Center) {
            NetImage(iconUrl, Modifier.size(34.dp)) { Sym("extension", 24f, ui.cs.onSurfaceVariant) }
        }
        Column(Modifier.padding(start = 14.dp, end = 10.dp).weight(1f)) {
            Txt(name, 16f, ui.cs.onSurface, ui.semibold, maxLines = 1)
            if (summary.isNotBlank()) Txt(summary, 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (rating > 0) {
                    Sym("star", 13f, ui.cs.onSurfaceVariant, Modifier.padding(end = 3.dp))
                    Txt("%.1f".format(rating) + if (users != null) " · " else "", 12f, ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1)
                }
                if (users != null) Txt(users, 12f, ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1)
                if (tested) {
                    if (rating > 0 || users != null) Txt(" · ", 12f, ui.cs.onSurfaceVariant, ui.semibold)
                    else Sym("check", 13f, ui.cs.primary, Modifier.padding(end = 3.dp))
                    Txt("Tested in mi", 12f, ui.cs.primary, ui.semibold, maxLines = 1)
                }
            }
        }
        if (added) Txt("Added", 13f, ui.cs.onSurfaceVariant, ui.semibold)
        else androidx.compose.material3.FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp)) { Txt("Get", 14f, ui.cs.onSecondaryContainer, ui.bold) }
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

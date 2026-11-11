// The tab menu (⋮ in the tab switcher): favorite, bookmark, pin; quick tiles; tools; closing tabs.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.search.addSearchShortcutFromPage
import com.jlees.mibrowser.incognito.closeIncognitoTabs
import com.jlees.mibrowser.library.toggleFavorite

import com.jlees.mibrowser.adblock.adsAllowedHere
import com.jlees.mibrowser.adblock.cleanLink
import com.jlees.mibrowser.adblock.toggleAdsHere

import com.jlees.mibrowser.launcher.LauncherShortcuts

import com.jlees.mibrowser.Sheet
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.ui.common.*

/**
 * Tab menu (⋮ in the tab switcher), acting on the centred tab. A tall card: the tab at the top, quick tiles for its
 * modes and common actions, round shortcut buttons, then plain rows, with the tab-closing rows kept at the bottom.
 * Actions that leave the switcher close it.
 */
@Composable
fun TabMenu(vm: BrowserViewModel, modifier: Modifier) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val tab = vm.tabs.current
    val url = vm.activeUrl
    val hasUrl = url != null
    val incognito = tab?.incognito == true
    val dismiss = { vm.isSubMenuVisible = false }
    val leave = { vm.isSubMenuVisible = false; vm.switcherVisible = false }
    val shape = RoundedCornerShape(minOf(ui.radius, 28).dp)

    /** A quick-settings-style tile: filled with the accent when [on]. [on] null = an action tile. */
    /** The tiles' shared look: accent-filled and rounder when [on], dimmed when disabled. Returns it and the content colour. */
    @Composable
    fun tileStyle(on: Boolean, enabled: Boolean, onPress: () -> Unit): Pair<Modifier, androidx.compose.ui.graphics.Color> {
        val radius by androidx.compose.animation.core.animateDpAsState(if (on) 28.dp else 18.dp, Motion.spatial(), label = "tile")
        val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHighest, label = "tile bg")
        return Modifier.clip(RoundedCornerShape(radius)).background(bg).alpha(if (enabled) 1f else 0.38f).touchable(enabled, onClick = onPress) to
            (if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface)
    }
    @Composable
    fun RowScope.Tile(icon: String, label: String, sub: String?, on: Boolean?, enabled: Boolean = true, onPress: () -> Unit) {
        val (style, fg) = tileStyle(on == true, enabled, onPress)
        Row(
            Modifier.weight(1f).height(68.dp).then(style).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon, 22f, fg, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Txt(label, 14f, fg, ui.semibold, maxLines = 1)
                if (sub != null) Txt(sub, 12f, fg.copy(alpha = 0.75f), ui.regular, maxLines = 1)
            }
        }
    }
    /** A round shortcut button with its label underneath. */
    @Composable
    fun RowScope.Round(icon: String, label: String, enabled: Boolean = true, onPress: () -> Unit) {
        Column(
            Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f).clip(RoundedCornerShape(16.dp)).touchable(enabled, onClick = onPress).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { Sym(icon, 22f, ui.cs.onSecondaryContainer) }
            Txt(label, 12f, ui.cs.onSurface, ui.semibold, Modifier.padding(top = 6.dp), maxLines = 1)
        }
    }
    @Composable
    fun MenuRow(icon: String, label: String, color: androidx.compose.ui.graphics.Color = ui.cs.onSurface, enabled: Boolean = true, onPress: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).alpha(if (enabled) 1f else 0.38f).touchable(enabled, onClick = onPress)
                .heightIn(min = 52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon, 22f, if (color == ui.cs.onSurface) ui.cs.onSurfaceVariant else color, Modifier.padding(end = 16.dp))
            Txt(label, 15f, color, ui.semibold)
        }
    }
    /** One of the favorite / bookmark / pin buttons: icon over label, filled with the accent when [on]. */
    @Composable
    fun RowScope.TopAction(icon: String, label: String, on: Boolean, enabled: Boolean, onPress: () -> Unit) {
        val (style, fg) = tileStyle(on, enabled, onPress)
        Column(
            Modifier.weight(1f).height(72.dp).then(style),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Sym(icon, 22f, fg)
            Txt(label, 13f, fg, ui.semibold, Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
    /** A header action (copy, share): a round icon button. */
    @Composable
    fun MenuIcon(icon: String, label: String, onPress: () -> Unit) {
        Box(Modifier.padding(start = 4.dp).size(44.dp).clip(CircleShape).touchable(onClick = onPress), contentAlignment = Alignment.Center) {
            Sym(icon, 22f, ui.cs.onSurfaceVariant, label = label)
        }
    }
    @Composable
    fun Section(title: String) = Txt(title, 13f, ui.cs.primary, ui.bold, Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp))

    Column(modifier.zIndex(10f).shadow(10.dp, shape).clip(shape).background(ui.cs.surfaceContainer).blockTouches().padding(16.dp)) {
        // the tab this menu acts on; tapping its site opens Site settings
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(22.dp))
                .then(if (hasUrl) Modifier.touchable { dismiss(); vm.openView(Sheet.SITE) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                val globe = @Composable { Sym(if (incognito) "incognito" else "public", 22f, ui.cs.onSurfaceVariant) }
                if (!incognito && url != null && vm.settings.showTabLogo) NetImage(Favicons.src(url), Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)), fallback = globe) else globe()
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Txt(if (incognito) "Incognito tab" else tab?.title?.ifEmpty { null } ?: "New Tab", 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (incognito) "Not saved to history" else displayHost(url).ifEmpty { "Home" }, 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                    if (hasUrl) Sym("chevron_right", 16f, ui.cs.onSurfaceVariant, label = "Site settings")
                }
            }
            if (hasUrl) {
                MenuIcon("content_copy", "Copy link") { copyText(ctx, vm.cleanLink(url)); android.widget.Toast.makeText(ctx, "Link copied", android.widget.Toast.LENGTH_SHORT).show(); dismiss() }
            }
        }

        // favorite · bookmark · pin: their own row, right under the tab's name
        val fav = url != null && vm.bookmarks.favorites.any { it.url == url }
        val pinned = tab?.pinned == true
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopAction("star", if (fav) "Favorited" else "Favorite", fav, hasUrl && (fav || vm.bookmarks.canAddFavorite)) {
                vm.toggleFavorite()
                android.widget.Toast.makeText(ctx, if (fav) "Removed from favorites" else "Added to favorites", android.widget.Toast.LENGTH_SHORT).show()
            }
            TopAction("bookmark_add", "Bookmark", false, hasUrl) { vm.bookmarksAutoAdd = true; vm.openView(Sheet.BOOKMARKS) }
            TopAction("keep", if (pinned) "Pinned" else "Pin", pinned, tab != null) { vm.tabs.togglePinned(vm.tabs.activeId) }
            // the system share sheet, with the page's address (tracking tags removed when that setting is on)
            TopAction("share", "Share", false, hasUrl) { dismiss(); shareText(ctx, vm.cleanLink(url!!), tab?.title?.takeIf { it.isNotBlank() }) }
        }

        // the space the middle part has and what it needs: the tools stack in rows while they fit (see below)
        var viewportH by remember { mutableIntStateOf(0) }
        var contentH by remember { mutableIntStateOf(0) }
        Column(Modifier.weight(1f).onSizeChanged { viewportH = it.height }.verticalScroll(rememberScrollState()).onSizeChanged { contentH = it.height }) {
            Section("This tab")
            val desktop = tab?.desktopMode ?: vm.settings.desktopMode
            val reader = tab?.readerMode ?: vm.settings.readerModeEnabled
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("desktop_windows", "Desktop site", if (desktop) "On" else "Off", desktop) { vm.setDesktopMode(!desktop) }
                    Tile("chrome_reader_mode", "Reader mode", if (reader) "On" else "Off", reader) { vm.setReaderMode(!reader) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("search", "Find in page", null, null, hasUrl) { leave(); vm.find.open() }
                    val blocking = vm.adsAllowedHere() == null
                    Tile("shield", "Block ads here", if (!hasUrl) null else if (blocking) "On" else "Off for this site", hasUrl && blocking, hasUrl) { vm.toggleAdsHere() }
                }
            }

            Section("Tools")
            val tools = listOfNotNull(
                TabTool("qr_code_scanner", "Scan QR", true) { leave(); vm.qrScannerVisible = true },
                TabTool("qr_code_2", "QR code", hasUrl) { leave(); vm.qrGeneratorVisible = true },
                TabTool("print", "Print", hasUrl) { leave(); vm.web.tryEmit(WebCmd.Print) },
                // a search shortcut for this site, found from the page (not from incognito: it'd save the site in Settings)
                if (!incognito) TabTool("travel_explore", "Add shortcut", hasUrl) { dismiss(); vm.addSearchShortcutFromPage() } else null,
                // not from incognito: a shortcut would write the page's address into the launcher, outside mi
                if (!incognito) TabTool("add_to_home_screen", "Home screen", hasUrl) { dismiss(); tab?.let { LauncherShortcuts.pinSite(ctx, it.title, it.url ?: return@let) } } else null,
            )
            // At most four to a row. The rows stack while the menu has room for them; only when they'd overflow do they
            // become pages swiped sideways, with dots for which page shows.
            val pages = tools.chunked(4)
            var rowH by remember { mutableIntStateOf(0) }
            var stacked by remember { mutableStateOf(true) }
            val dotsH = with(LocalDensity.current) { 12.dp.roundToPx() }
            LaunchedEffect(viewportH, contentH, rowH, pages.size) {
                if (rowH == 0 || viewportH == 0) return@LaunchedEffect
                val rows = pages.size * rowH; val paged = rowH + dotsH
                val heightStacked = if (stacked) contentH else contentH - paged + rows
                stacked = heightStacked <= viewportH
            }
            @Composable fun ToolRow(page: List<TabTool>) = Row(Modifier.fillMaxWidth().onSizeChanged { rowH = it.height }) {
                page.forEach { Round(it.icon, it.label, it.enabled, it.onPress) }
                repeat(4 - page.size) { Spacer(Modifier.weight(1f)) } // a short last row keeps the columns above
            }
            if (pages.size == 1 || stacked) pages.forEach { ToolRow(it) }
            else {
                val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth()) { i -> ToolRow(pages[i]) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    pages.indices.forEach { i ->
                        Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape).background(if (i == pager.currentPage) ui.cs.primary else ui.cs.outlineVariant))
                    }
                }
            }

        }

        // closing tabs: kept apart at the bottom
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant))
        if (vm.tabs.list.any { it.incognito }) MenuRow("incognito", "Close incognito tabs") { dismiss(); vm.closeIncognitoTabs() }
        MenuRow("delete", "Close all tabs", ui.cs.error) { dismiss(); vm.clearAllTabs() }
        // with Clear on Exit on: close mi now (Android rarely closes apps itself), clearing what was chosen
        if (vm.settings.clearDataOnExit) MenuRow("power_settings_new", "Exit and Clear", ui.cs.error) { dismiss(); (ctx as? android.app.Activity)?.finishAndRemoveTask() }
    }
}

/** One of the tab menu's tools: shown four to a row, on pages swiped sideways when there are more. */
private class TabTool(val icon: String, val label: String, val enabled: Boolean, val onPress: () -> Unit)

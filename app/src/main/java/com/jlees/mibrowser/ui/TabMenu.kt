// The tab menu (⋮ in the tab switcher): favorite, bookmark, pin, share; then three pages (the tab's tiles, extensions,
// tools) switched like the Library's; closing tabs.
package com.jlees.mibrowser.ui

import com.jlees.mibrowser.containers.nameOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import com.jlees.mibrowser.search.addSearchShortcutFromPage
import com.jlees.mibrowser.incognito.closeIncognitoTabs
import com.jlees.mibrowser.library.toggleFavorite

import com.jlees.mibrowser.adblock.adsAllowedHere
import com.jlees.mibrowser.adblock.cleanLink
import com.jlees.mibrowser.adblock.copyLink
import com.jlees.mibrowser.adblock.toggleAdsHere

import com.jlees.mibrowser.launcher.toggleAppMode

import com.jlees.mibrowser.Sheet
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.follow.followThisPage

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
        val radius by animateDpAsState(if (on) 28.dp else 18.dp, Motion.spatial(), label = "tile")
        val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHighest, label = "tile bg")
        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        return Modifier.pressScale(press).clip(RoundedCornerShape(radius)).background(bg).alpha(if (enabled) 1f else 0.38f).touchable(enabled, press, onPress) to
            (if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface)
    }
    @Composable
    fun RowScope.Tile(icon: String, label: String, sub: String?, on: Boolean?, enabled: Boolean = true, iconContent: (@Composable () -> Unit)? = null, onPress: () -> Unit) {
        val (style, fg) = tileStyle(on == true, enabled, onPress)
        Row(
            Modifier.weight(1f).height(62.dp).then(style).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pop = popScale(on == true) // turned on: the icon pops
            // an extension's tile shows its own icon
            if (iconContent != null) Box(Modifier.padding(end = 12.dp).size(22.dp), contentAlignment = Alignment.Center) { iconContent() }
            else Sym(icon, 22f, fg, Modifier.padding(end = 12.dp).graphicsLayer { scaleX = pop; scaleY = pop })
            Column(Modifier.weight(1f)) {
                Txt(label, 14f, fg, ui.semibold, maxLines = 1)
                if (sub != null) Txt(sub, 12f, fg.copy(alpha = 0.75f), ui.regular, maxLines = 1)
            }
        }
    }
    /**
     * Items in [columns] across that can be rearranged: hold one, then drag it (SortableSlots). Each slot is a row the
     * item fills, with the menu's 8dp gaps between items and none at the edges.
     */
    @Composable
    fun <T> DragSlots(items: List<T>, columns: Int, slotH: androidx.compose.ui.unit.Dp, key: (T) -> String, fixedTail: Int = 0, onReorder: (Int, Int) -> Unit, onHold: (T) -> Unit = {}, content: @Composable RowScope.(T) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (columns == 2) 8.dp else 0.dp
            val w = maxWidth + gap
            Box(Modifier.requiredWidth(w)) {
                SortableSlots(items, key, slotW = w / columns, slotH = slotH, onReorder = onReorder, columns = columns, onHold = onHold, fixedTail = fixedTail) { item ->
                    Row(Modifier.fillMaxSize().padding(horizontal = gap / 2, vertical = gap / 2)) { content(item) }
                }
            }
        }
    }
    /** A round shortcut button with its label underneath. */
    @Composable
    fun RowScope.Round(icon: String, label: String, enabled: Boolean = true, badge: String = "", iconContent: (@Composable () -> Unit)? = null, onPress: () -> Unit) {
        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Column(
            Modifier.weight(1f).pressScale(press).alpha(if (enabled) 1f else 0.38f).clip(RoundedCornerShape(16.dp)).touchable(enabled, press, onPress).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the circle snug around a big icon: compact enough for two rows of tools on a smaller phone
            Box {
                Box(Modifier.size(50.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { iconContent?.invoke() ?: Sym(icon, 24f, ui.cs.onSecondaryContainer) }
                // an extension's badge (chrome.action): a small pill on the circle's corner
                if (badge.isNotEmpty()) Txt(badge.take(4), 10f, ui.cs.onPrimary, ui.bold,
                    Modifier.align(Alignment.TopEnd).clip(CircleShape).background(ui.cs.primary).padding(horizontal = 5.dp, vertical = 1.dp), maxLines = 1)
            }
            Txt(label, 12f, ui.cs.onSurface, ui.semibold, Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
    @Composable
    fun MenuRow(icon: String, label: String, color: androidx.compose.ui.graphics.Color = ui.cs.onSurface, enabled: Boolean = true, onPress: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).alpha(if (enabled) 1f else 0.38f).touchable(enabled, onClick = onPress)
                .heightIn(min = 44.dp).padding(horizontal = 12.dp),
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
            val pop = popScale(on) // favorited, pinned: the icon pops
            Sym(icon, 22f, fg, Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
            Txt(label, 13f, fg, ui.semibold, Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
    /** A header action (copy, share): a round icon button. */
    @Composable
    fun MenuIcon(icon: String, label: String, onPress: () -> Unit) {
        Box(Modifier.padding(start = 4.dp).size(48.dp).clip(CircleShape).touchable(onClick = onPress), contentAlignment = Alignment.Center) {
            Sym(icon, 22f, ui.cs.onSurfaceVariant, label = label)
        }
    }

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
                    Txt(if (incognito) "Not saved to history" else com.jlees.mibrowser.extensions.shownHost(url).ifEmpty { "Home" }, 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1, pan = false)
                    if (hasUrl) Sym("chevron_right", 16f, ui.cs.onSurfaceVariant, label = "Site settings")
                }
            }
            if (hasUrl) {
                MenuIcon("content_copy", "Copy link") { vm.copyLink(ctx, url); dismiss() }
            }
        }

        // favorite · bookmark · pin: their own row, right under the tab's name
        val fav = url != null && vm.bookmarks.isFavorite(url)
        val pinned = tab?.pinned == true
        Row(Modifier.padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopAction("star", if (fav) "Favorited" else "Favorite", fav, hasUrl) {
                vm.toggleFavorite()
                com.jlees.mibrowser.ui.common.Notices.show(if (fav) "Removed from favorites" else "Added to favorites", done = !fav)
            }
            TopAction("bookmark_add", "Bookmark", false, hasUrl) { vm.bookmarksAutoAdd = true; vm.openView(Sheet.BOOKMARKS) }
            TopAction("keep", if (pinned) "Pinned" else "Pin", pinned, tab != null) { vm.tabs.togglePinned(vm.tabs.activeId) }
            // the system share sheet, with the page's address (tracking tags removed when that setting is on)
            TopAction("share", "Share", false, hasUrl) { dismiss(); shareText(ctx, vm.cleanLink(url!!), tab?.title?.takeIf { it.isNotBlank() }) }
        }

        // its three pages, switched like the Library's: the tab's own tiles, extensions, tools
        val page = menuPage
        ConnectedIconChoice(MenuPage.entries.map { Triple(it.name, it.label, it.icon) }, page.name, Modifier.padding(bottom = 8.dp)) { menuPage = MenuPage.valueOf(it) }
        // tiles scrolled up fade out under the page buttons, instead of being cut off at their edge
        val menuBg = ui.cs.surfaceContainer
        androidx.compose.animation.AnimatedContent(
            page, Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { androidx.compose.animation.fadeIn(Motion.enter(180)) togetherWith androidx.compose.animation.fadeOut(Motion.exit(90)) },
            label = "tab menu page",
        ) { shown -> Column(Modifier.fillMaxSize()
            .drawWithContent {
                drawContent()
                val h = 16.dp.toPx()
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(menuBg, menuBg.copy(alpha = 0f)), endY = h), size = androidx.compose.ui.geometry.Size(size.width, h))
            }
            .verticalScroll(rememberScrollState()).padding(top = 8.dp)) { when (shown) {
            MenuPage.TAB -> {
            val desktop = tab?.desktopMode ?: vm.settings.desktopMode
            val reader = tab?.readerMode ?: vm.settings.readerModeEnabled
            val blocking = vm.adsAllowedHere() == null
            // two across; hold one and drag to rearrange them (the order is kept)
            DragSlots(com.jlees.mibrowser.data.QuickTile.ordered(vm.settings.quickTiles), columns = 2, slotH = 70.dp, key = { it.name },
                onReorder = { from, to -> vm.updateSettings { copy(quickTiles = com.jlees.mibrowser.data.QuickTile.ordered(quickTiles).toMutableList().apply { add(to, removeAt(from)) }) } }) { t ->
                when (t) {
                    com.jlees.mibrowser.data.QuickTile.DESKTOP -> Tile("desktop_windows", "Desktop site", if (desktop) "On" else "Off", desktop) { vm.setDesktopMode(!desktop) }
                    com.jlees.mibrowser.data.QuickTile.READER -> Tile("chrome_reader_mode", "Reader mode", if (reader) "On" else "Off", reader) { vm.setReaderMode(!reader) }
                    com.jlees.mibrowser.data.QuickTile.FIND -> Tile("search", "Find in page", null, null, hasUrl) { leave(); vm.find.open() }
                    // its tab group (a new tab joins one from the Group tile; incognito tabs aren't grouped)
                    com.jlees.mibrowser.data.QuickTile.GROUP -> Tile("tab_group", "Group", vm.tabs.groups.firstOrNull { it.id == tab?.groupId }?.name ?: "None", false, tab?.keepsTraces == true) {
                        tab?.let { com.jlees.mibrowser.tabs.TabGroupFlow.picking = it.id }
                    }
                    // the profile it's in (its own tabs, cookies and sign-ins): tap to move it to another; greyed out while
                    // profiles are off (Settings › Tabs › Profiles)
                    com.jlees.mibrowser.data.QuickTile.CONTAINER -> {
                        val profiles = vm.settings.profilesEnabled
                        Tile("workspaces", "Profile", if (profiles) vm.nameOf(tab?.container) else "Off", false, profiles && tab != null) {
                            tab?.let { com.jlees.mibrowser.containers.ContainerFlow.picking = it.id }
                        }
                    }
                    com.jlees.mibrowser.data.QuickTile.ADS -> Tile("shield", "Block ads here", if (!hasUrl) null else if (blocking) com.jlees.mibrowser.adblock.blockingLabel(com.jlees.mibrowser.adblock.BlockedCounts.on(tab?.id)) else "Off for this site", hasUrl && blocking, hasUrl) { vm.toggleAdsHere() }
                }
            }

            }
            // extensions with a toolbar button: tapping one opens its popup (or tells it it was tapped); + gets more
            MenuPage.EXTENSIONS -> {
            val engine = com.jlees.mibrowser.extensions.ExtensionFlow.engine
            val buttons = vm.extensions.list.filter { e -> e.enabled && e.showButton && (!incognito || e.incognito) && engine?.infos?.get(e.id)?.manifest?.hasButton == true }
            // two across like the tab's own tiles, so a name has room and the badge (uBlock Origin's count) shows under it;
            // Add always last; hold one and drag to rearrange them (the order is kept)
            DragSlots(buttons.map { it.id } + "", columns = 2, slotH = 70.dp, key = { it }, fixedTail = 1,
                onReorder = { from, to -> vm.extensions.reorder(buttons.map { it.id }, from, to) },
                // held and let go without moving: the extension's own page in Settings
                onHold = { id -> if (id.isNotEmpty()) {
                    leave(); com.jlees.mibrowser.extensions.ExtensionFlow.libraryTarget = id; vm.openView(com.jlees.mibrowser.Sheet.EXTENSIONS)
                } }) { id ->
                val item = buttons.firstOrNull { it.id == id }
                if (item != null) {
                    val action = engine?.actions?.get(item.id)
                    // the name itself (an extension's button title often has its count in it too: "LocalCDN (2)"),
                    // and under it the badge it shows on this tab, else what a tap does
                    Tile("extension", item.name, engine?.badgeOf(item.id)?.takeIf { it.isNotBlank() } ?: "Open", false, action?.enabled != false,
                        iconContent = { com.jlees.mibrowser.extensions.ExtensionIcon(item.id, 22f) }) { dismiss(); engine?.pressAction(item.id) }
                } else Tile("add", "Add Extension", null, false) {
                    leave(); com.jlees.mibrowser.extensions.ExtensionFlow.libraryTarget = "get"; vm.openView(com.jlees.mibrowser.Sheet.EXTENSIONS)
                }
            }

            }
            MenuPage.TOOLS -> {
            val askNotify = rememberNotifyAsk() // app mode's way out is a notification
            val tools = listOfNotNull(
                TabTool("qr_code_scanner", "Scan QR", true) { leave(); vm.qrScannerVisible = true },
                TabTool("qr_code_2", "QR code", hasUrl) { leave(); vm.qrGeneratorVisible = true },
                TabTool("print", "Print", hasUrl) { leave(); vm.web.tryEmit(WebCmd.Print) },
                // follow this page's price (Library › Following); not from incognito, which keeps no traces
                if (!incognito) TabTool("sell", if (url != null && vm.follows.following(url) != null) "Following" else "Follow price", hasUrl) { leave(); vm.followThisPage() } else null,
                // a search shortcut for this site, found from the page (not from incognito: it'd save the site in Settings)
                if (!incognito) TabTool("travel_explore", "Add shortcut", hasUrl) { dismiss(); vm.addSearchShortcutFromPage() } else null,
                // not from incognito: a shortcut would write the page's address into the launcher, outside mi
                if (!incognito) TabTool("add_to_home_screen", "Home screen", hasUrl) { dismiss(); tab?.let { t -> t.url?.let { com.jlees.mibrowser.launcher.PinFlow.draft = com.jlees.mibrowser.launcher.PinDraft(t.title, it) } } } else null,
                // app mode: the Pill hides (launcher/AppMode); it's turned off here or from its notification
                if (!incognito) TabTool("fullscreen", if (tab?.appStart != null) "Exit immersive" else "Immersive", hasUrl) { if (tab?.appStart == null) askNotify(false); leave(); vm.toggleAppMode() } else null,
            )
            // four across; hold one and drag to rearrange them (the order is kept); the menu's middle scrolls if they don't fit
            val saved = vm.settings.toolOrder
            val ordered = tools.sortedBy { t -> saved.indexOf(t.icon).let { if (it < 0) saved.size + tools.indexOf(t) else it } }
            DragSlots(ordered, columns = 4, slotH = 84.dp, key = { it.icon },
                onReorder = { from, to -> vm.updateSettings { val moved = ordered.map { it.icon }.toMutableList().apply { add(to, removeAt(from)) }; copy(toolOrder = moved + (toolOrder - moved.toSet())) } }) { t ->
                Round(t.icon, t.label, t.enabled, onPress = t.onPress)
            }
            }
        } } }

        // closing tabs: kept apart at the bottom
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant))
        if (vm.tabs.list.any { it.incognito }) MenuRow("incognito", "Close incognito tabs") { dismiss(); vm.closeIncognitoTabs() }
        MenuRow("delete", "Close all tabs", ui.cs.error) { dismiss(); vm.clearAllTabs() }
        // with Clear on Exit on: close mi now (Android rarely closes apps itself), clearing what was chosen
        if (vm.settings.clearDataOnExit) MenuRow("power_settings_new", "Exit and Clear", ui.cs.error) { dismiss(); (ctx as? android.app.Activity)?.finishAndRemoveTask() }
    }
}

/** The tab menu's pages, switched by the buttons under its top row (as the Library's are). */
private enum class MenuPage(val label: String, val icon: String) { TAB("Tab", "tab"), EXTENSIONS("Extensions", "extension"), TOOLS("Tools", "apps") }

/** The page the tab menu shows: the one last picked, while mi runs. */
private var menuPage by mutableStateOf(MenuPage.TAB)

/** One of the tab menu's tools: shown four to a row, on pages swiped sideways when there are more. */
private class TabTool(val icon: String, val label: String, val enabled: Boolean, val onPress: () -> Unit)

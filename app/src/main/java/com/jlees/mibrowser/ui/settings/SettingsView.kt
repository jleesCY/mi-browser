@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
// Settings: one scrolling page of sections (no drill-down). A pinned search field and a row of section shortcuts sit
// on top; every row has an icon, a title and a one-line explanation, and the whole row is the touch target.
package com.jlees.mibrowser.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.BuildConfig
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.DataTransfer
import com.jlees.mibrowser.data.ImportError
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.logic.ACCENTS
import com.jlees.mibrowser.logic.DYNAMIC_ACCENT
import com.jlees.mibrowser.logic.SEARCH_ENGINES
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.history.HISTORY_RANGES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A setting row: [label] and [keywords] are what search matches; [content] draws it. */
private class SRow(val label: String, val keywords: String = "", val content: @Composable () -> Unit)
private class SGroup(val id: String, val title: String, val icon: String, val rows: List<SRow>)

private const val GUIDE_URL = "https://jleescy.github.io/mi-browser/user"
private const val SOURCE_URL = "https://github.com/jleescy/mi-browser"

@Composable
fun SettingsView(vm: BrowserViewModel, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = vm.settings
    val search = vm.settingsSearch
    val list = rememberLazyListState()
    LaunchedEffect(search) { list.scrollToItem(0) }
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    fun openPage(url: String) { vm.addNewTab(url); vm.switcherVisible = false; closeOverlay() }

    // ---- default browser (re-checked when the system picker returns)
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.getSystemService(android.app.role.RoleManager::class.java) else null
    fun roleHeld() = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
    var isDefault by remember { mutableStateOf(roleHeld()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = roleHeld() }
    fun requestDefault() {
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER))
            roleLauncher.launch(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
        else runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    // ---- export / import
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, vm.settings) }.exceptionOrNull() }
            if (err == null) vm.showAlert("Success", "Data exported successfully!") else vm.showAlert("Export Failed", err.message ?: "Unknown error occurred")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DataTransfer.import(ctx, uri) } }
            r.onSuccess { data ->
                vm.showAlert("Restore Settings?", "This will replace your current settings. Continue?", listOf(
                    AlertButton("Cancel", "cancel"),
                    AlertButton("Restore") {
                        vm.applyImport(data)
                        scope.launch { kotlinx.coroutines.delay(300); vm.showAlert("Success", "Settings restored successfully.") }
                    },
                ))
            }.onFailure { e ->
                if (e is ImportError) vm.showAlert(e.title, e.message ?: "") else vm.showAlert("Import Failed", "Could not parse file. ${e.message ?: ""}")
            }
        }
    }

    val groups = listOf(
        SGroup("appearance", "Appearance", "palette", listOf(
            SRow("Theme", "light dark system mode") {
                ChoiceItem("routine", "Theme", null, listOf("light" to "Light", "dark" to "Dark", "system" to "System"), s.themeMode) { set { copy(themeMode = it) } }
            },
            SRow("Accent Color", "colour wallpaper material you dynamic") { AccentItem(vm) },
            SRow("Font Size", "text scale app") {
                SliderItem("format_size", "Font Size", "Text size throughout mi", s.fontScale, 0.8f, 1.2f) { set { copy(fontScale = it) } }
            },
            SRow("Rounded Font", "typeface") { SwitchItem("font_download", "Rounded Font", "Softer, rounded letterforms", s.roundedFont) { set { copy(roundedFont = it) } } },
            SRow("Corners", "radius shape") {
                ChoiceItem("rounded_corner", "Corners", null, listOf("0" to "Square", "12" to "Soft", "28" to "Round"), s.cornerRadius.toString()) { set { copy(cornerRadius = it.toInt()) } }
            },
            SRow("Spacing", "density padding compact airy") {
                ChoiceItem("density_medium", "Spacing", null, listOf("compact" to "Compact", "normal" to "Normal", "airy" to "Airy"), s.uiPadding) { set { copy(uiPadding = it) } }
            },
            SRow("Status Bar", "clock battery immersive") { SwitchItem("battery_charging_full", "Status Bar", "Show the clock and battery at the top", s.showStatusBar) { set { copy(showStatusBar = it) } } },
            SRow("Darken Web Pages", "dark mode force night websites") {
                SwitchItem("contrast", "Darken Web Pages", "In dark theme, darken sites that have no dark mode", s.darkenPages) { set { copy(darkenPages = it) } }
            },
        )),
        SGroup("browsing", "Search & Browsing", "travel_explore", listOf(
            SRow("Search Engine", "google bing duckduckgo brave ecosia yahoo") { SearchEngineItem(vm) },
            SRow("Search Suggestions", "autocomplete") {
                SwitchItem("search", "Search Suggestions", "Suggestions from your search engine while the search drawer is open", s.searchSuggestions) { set { copy(searchSuggestions = it) } }
            },
            SRow("Page Text Size", "zoom font web websites") {
                SliderItem("format_size", "Page Text Size", "Text size on websites", s.textZoom / 100f, 0.5f, 2f) { set { copy(textZoom = Math.round(it * 100)) } }
            },
            SRow("Desktop Site by Default", "desktop mode user agent") {
                SwitchItem("desktop_windows", "Desktop Site by Default", "Request desktop versions of websites (each tab can still change it)", s.desktopMode) { set { copy(desktopMode = it) } }
            },
            SRow("Reader Mode by Default", "reader readability") {
                SwitchItem("chrome_reader_mode", "Reader Mode by Default", "Simplify pages for reading (each tab can still change it)", s.readerModeEnabled) { set { copy(readerModeEnabled = it) } }
            },
            SRow("On Startup", "session restore continue") {
                ChoiceItem("power_settings_new", "On Startup", null, listOf("new" to "New Tab", "last" to "Continue Session"), s.startupTabMode) { set { copy(startupTabMode = it) } }
            },
            SRow("Default Browser", "links open") {
                Item("public", "Default Browser", if (isDefault) "mi opens links from other apps" else "Tap to make mi open links from other apps",
                    onClick = if (isDefault) null else ::requestDefault,
                    trailing = { if (isDefault) Sym("check", 22f, ui.cs.primary) else Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
            },
        )),
        SGroup("pill", "Pill & Home", "toolbar", listOfNotNull(
            SRow("Loading Bar", "progress") {
                ChoiceItem("hourglass_empty", "Loading Bar", "Fill behind the address while a page loads", listOf("ltr" to "Standard", "center" to "Center Out", "none" to "Hidden"), s.progressBarMode) { set { copy(progressBarMode = it) } }
            },
            SRow("Pin Favorites", "favorites bar") { SwitchItem("star", "Pin Favorites", "Always show the favorites bar when searching", s.showFavoritesDefault) { set { copy(showFavoritesDefault = it) } } },
            SRow("Expand Searches", "recent searches drawer") {
                SwitchItem("list", "Expand Searches", "Open the search drawer fully when you tap the pill", s.recentSearchesExpanded) { set { copy(recentSearchesExpanded = it) } }
            },
            SRow("Home Logo", "fidget center") {
                ChoiceItem("home", "Home Logo", null, listOf("None", "Static", "Fidget").map { it to it }, s.homeLogoType) { set { copy(homeLogoType = it) } }
            },
            SRow("Home Clock", "time") { ChoiceItem("history", "Home Clock", null, listOf("None", "12h", "24h").map { it to it }, s.homeClockType) { set { copy(homeClockType = it) } } },
            SRow("Home Date", "calendar") { ChoiceItem("calendar_today", "Home Date", null, listOf("None", "Above", "Below").map { it to it }, s.homeDateType) { set { copy(homeDateType = it) } } },
            SRow("Home Shortcut", "button action qr scan") { ShortcutItem(vm) },
        )),
        SGroup("tabs", "Tabs & Library", "library_books", listOf(
            SRow("Site Icons on Tabs", "logo favicon switcher") { SwitchItem("tab", "Site Icons on Tabs", "Show each site's icon in the tab switcher", s.showTabLogo) { set { copy(showTabLogo = it) } } },
            SRow("Background Refresh", "preload tabs startup") {
                SwitchItem("bolt", "Background Refresh", "Load open tabs when mi starts for instant switching (uses more battery)", s.backgroundRefresh) { on ->
                    if (on) vm.confirm = ConfirmData("bgRefresh") else set { copy(backgroundRefresh = false) }
                }
            },
            SRow("Site Icons on Bookmarks", "logo favicon") { SwitchItem("bookmarks", "Site Icons on Bookmarks", "Show each site's icon in your bookmarks", s.showBookmarkIcons) { set { copy(showBookmarkIcons = it) } } },
            SRow("Group History By", "history time site") {
                ChoiceItem("layers", "Group History By", null, listOf("Time", "Site").map { it to it }, s.historyGrouping) { set { copy(historyGrouping = it) } }
            },
            SRow("History Page Size", "history load count") {
                ChoiceItem("list", "History Page Size", "Entries loaded at a time", listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, s.historyLoadCount.toString()) { set { copy(historyLoadCount = it.toInt()) } }
            },
            SRow("Clear History", "delete history") { ClearHistoryItem(vm) },
        )),
        SGroup("privacy", "Privacy & Security", "shield", listOf(
            SRow("Block Ads & Trackers", "adblock") { SwitchItem("shield", "Block Ads & Trackers", "Built-in list of ad and tracking domains", s.blockTrackers) { set { copy(blockTrackers = it) } } },
            SRow("HTTPS Only", "secure ssl tls") { SwitchItem("lock", "HTTPS Only", "Always use secure connections (local addresses excluded)", s.httpsOnly) { set { copy(httpsOnly = it) } } },
            SRow("Block Third-Party Cookies", "cross-site tracking") {
                SwitchItem("cookie", "Block Third-Party Cookies", "Stops cross-site cookies; can break payment and sign-in windows", s.blockThirdPartyCookies) { set { copy(blockThirdPartyCookies = it) } }
            },
            SRow("Allow Cookies", "cookies") { SwitchItem("cookie", "Allow Cookies", "Turning this off signs you out of most sites", !s.blockCookies) { set { copy(blockCookies = !it) } } },
            SRow("JavaScript", "js scripts") { SwitchItem("code", "JavaScript", "Most sites need it to work", s.jsEnabled) { set { copy(jsEnabled = it) } } },
            SRow("Pop-ups", "popups windows") {
                val n = s.popupSites.size
                Item("open_in_new", "Pop-ups",
                    if (n == 0) "Open when you tap; blocked otherwise" else "Also allowed without a tap on $n site${if (n == 1) "" else "s"}",
                    onClick = if (n == 0) null else { {
                        vm.showAlert("Pop-ups", "Allowed without a tap on:\n" + s.popupSites.joinToString("\n"), listOf(
                            AlertButton("Close", "cancel"),
                            AlertButton("Block all again", "destructive") { set { copy(popupSites = emptyList()) } },
                        ), onCancel = {})
                    } },
                    trailing = if (n == 0) null else { { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) } })
            },
            SRow("Clear Browsing Data", "cookies cache storage delete") {
                Item("delete", "Clear Browsing Data", "Cookies, site storage and cached files", danger = true, onClick = { vm.confirm = ConfirmData("clearData") })
            },
        )),
        SGroup("data", "Backup & Reset", "save", listOf(
            SRow("Export Settings", "backup save") {
                Item("upload", "Export Settings", "Save your settings to a file", onClick = { exporter.launch("settings_${System.currentTimeMillis()}.midata") })
            },
            SRow("Import Settings", "restore load") { Item("download", "Import Settings", "Restore from an exported file", onClick = { importer.launch(arrayOf("*/*")) }) },
            SRow("Reset All Settings", "defaults") {
                Item("restart_alt", "Reset All Settings", "Back to defaults; bookmarks, history and tabs are kept", danger = true, onClick = { vm.confirm = ConfirmData("resetSettings") })
            },
            SRow("Wipe All Data", "erase delete everything") {
                Item("delete", "Wipe All Data", "Delete bookmarks, history, tabs, favorites and settings", danger = true, onClick = { vm.confirm = ConfirmData("wipeData") })
            },
        )),
        SGroup("developer", "Developer", "code", listOf(
            SRow("Remote Debugging", "chrome inspect devtools localhost") {
                SwitchItem("code", "Remote Debugging", "Lets a computer connected with USB debugging inspect your pages (chrome://inspect). " + if (BuildConfig.DEBUG) "Always on in dev builds." else "Turns off when mi restarts.", s.remoteDebugging) { set { copy(remoteDebugging = it) } }
            },
        )),
        SGroup("about", "About", "info", listOf(
            SRow("Check for Updates", "version update") {
                Item("restart_alt", "Check for Updates", "mi browser ${BuildConfig.VERSION_NAME}", onClick = { vm.checkForUpdatesNow() })
            },
            SRow("User Guide", "help") {
                Item("help", "User Guide", "Every gesture, tool and setting", onClick = { openPage(GUIDE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SRow("Source Code", "github open source") {
                Item("code", "Source Code", "github.com/jleescy/mi-browser", onClick = { openPage(SOURCE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SRow("Credits", "licenses open source attribution") { CreditsItem() },
        )),
    )

    // ---- search: rows whose title, keywords or section match
    val q = search.trim().lowercase()
    val visible = groups.mapNotNull { g ->
        if (q.isEmpty() || g.title.lowercase().contains(q)) g
        else g.rows.filter { it.label.lowercase().contains(q) || it.keywords.contains(q) }.takeIf { it.isNotEmpty() }?.let { SGroup(g.id, g.title, g.icon, it) }
    }

    // ---- the page is a lazy list: each section's header pins to the top while its rows scroll under it, and the
    // next section's header pushes it away. Shortcut chips jump to a section; the pinned section's chip is highlighted.
    val hasBanner = q.isEmpty() && !isDefault
    val headerIndex = remember(visible, hasBanner) {
        var i = if (hasBanner) 1 else 0
        visible.associate { g -> (g.id to i).also { i += 1 + g.rows.size } }
    }
    val inView by remember(headerIndex) {
        derivedStateOf {
            if (!list.canScrollForward && list.canScrollBackward) return@derivedStateOf visible.lastOrNull()?.id ?: groups.first().id // at the bottom
            // the pinned header (or the next one once it's in the top third); the first item in view can be a row of the
            // previous section still hidden under the pinned header, so it can't decide this
            val info = list.layoutInfo
            info.visibleItemsInfo.filter { (it.key as? String)?.startsWith("h:") == true && it.offset <= info.viewportSize.height / 3 }
                .maxByOrNull { it.offset }?.let { (it.key as String).removePrefix("h:") } ?: visible.firstOrNull()?.id ?: groups.first().id
        }
    }
    // A long animated jump would build and lay out every row in between (dropped frames); instead, snap to a few rows
    // short of the section and animate just those, so it still glides in from the direction of travel.
    suspend fun jumpTo(target: Int) {
        val from = list.firstVisibleItemIndex
        if (kotlin.math.abs(target - from) > 4) list.scrollToItem(if (target > from) target - 3 else target + 3)
        list.animateScrollToItem(target)
    }
    val chips = rememberLazyListState()
    LaunchedEffect(inView) { groups.indexOfFirst { it.id == inView }.takeIf { it >= 0 }?.let { chips.animateScrollToItem(it) } } // keep the active chip in view
    val sheetBg = ui.cs.surfaceContainerLow

    Column(Modifier.fillMaxSize()) {
        SheetSearchField(search, { vm.settingsSearch = it }, "Search Settings...", Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
        if (q.isEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), state = chips, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.id }) { g ->
                    val active = g.id == inView
                    Row(
                        Modifier.height(40.dp).clip(CircleShape).background(if (active) ui.cs.secondaryContainer else ui.cs.surfaceContainerHigh)
                            .touchable { headerIndex[g.id]?.let { i -> scope.launch { jumpTo(i) } } }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(g.icon, 18f, if (active) ui.cs.onSecondaryContainer else ui.cs.onSurfaceVariant, Modifier.padding(end = 6.dp))
                        Txt(g.title, 14f, if (active) ui.cs.onSecondaryContainer else ui.cs.onSurface, ui.semibold)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp)) {
                if (hasBanner) item(key = "banner") { DefaultBrowserCard(::requestDefault) }
                if (visible.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Sym("search", 44f, ui.cs.onSurfaceVariant)
                        Txt("No settings match \"$search\"", 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
                    }
                }
                visible.forEach { g ->
                    stickyHeader(key = "h:${g.id}") { SectionHeader(g.icon, g.title, sheetBg) }
                    itemsIndexed(g.rows, key = { _, r -> "${g.id}:${r.label}" }) { i, r ->
                        Box(
                            Modifier.animateItem().fillMaxWidth().padding(bottom = if (i < g.rows.size - 1) 2.dp else 0.dp)
                                .clip(segmentShape(i, g.rows.size, ui.radius)).background(ui.cs.surfaceContainerHighest),
                        ) { r.content() }
                    }
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Txt("mi browser v${BuildConfig.VERSION_NAME}", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.graphicsLayer { alpha = 0.5f })
                    }
                }
            }
            // softens the top edge for anything scrolling under the shortcut bar before a header is pinned
            Box(Modifier.fillMaxWidth().height(16.dp).background(Brush.verticalGradient(listOf(sheetBg, sheetBg.copy(alpha = 0f)))))
        }
    }
}

/**
 * A pinned section header. Its background is the sheet's color so rows scrolling under it are hidden, and the
 * gradient tail below it makes them fade out instead of being cut off at a hard edge (when the header isn't pinned,
 * the tail is just the space above the section's first row).
 */
@Composable
private fun SectionHeader(icon: String, title: String, bg: Color) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(bg).padding(start = 8.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(icon, 18f, ui.cs.primary, Modifier.padding(end = 8.dp))
            Txt(title, 15f, ui.cs.primary, ui.bold)
        }
        Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
    }
}

// ---------------------------------------------------------------- row building blocks

/**
 * The one row layout every setting uses: icon in a tonal circle, title, optional explanation, optional trailing
 * control, and optional content underneath (choices, slider, swatches). [onClick] makes the whole row the target.
 */
@Composable
private fun Item(
    icon: String, title: String, subtitle: String? = null, danger: Boolean = false,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, below: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.touchable(onClick = onClick) else Modifier)
                .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (danger) ui.cs.errorContainer else ui.cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Sym(icon, 22f, if (danger) ui.cs.onErrorContainer else ui.cs.onSecondaryContainer) }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Txt(title, 16f, if (danger) ui.cs.error else ui.cs.onSurface, ui.semibold)
                if (subtitle != null) Txt(subtitle, 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 2.dp), lineHeight = 18f)
            }
            trailing?.invoke()
        }
        if (below != null) Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { below() }
    }
}

@Composable
private fun SwitchItem(icon: String, title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) =
    Item(icon, title, subtitle, onClick = { onChange(!value) }, trailing = { MiSwitch(value, null) })

@Composable
private fun ChoiceItem(icon: String, title: String, subtitle: String?, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) =
    Item(icon, title, subtitle, below = { ConnectedChoice(options, value, onPick = onPick) })

/** Stepped slider (0.1 steps within [min, max]) with the value shown as a percentage. */
@Composable
private fun SliderItem(icon: String, title: String, subtitle: String?, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val ui = LocalUi.current
    Item(
        icon, title, subtitle,
        trailing = { Txt("${Math.round(value * 100)}%", 14f, ui.cs.onSurfaceVariant, ui.bold, scaled = false) },
        below = {
            Slider(value = value, valueRange = min..max, steps = Math.round((max - min) / 0.1f) - 1, onValueChange = { onChange(Math.round(it * 10f) / 10f) })
        },
    )
}

/** A row that opens a short list of options under itself (search engine, shortcut action, clear history). */
@Composable
private fun ExpandItem(icon: String, title: String, subtitle: String?, value: String?, danger: Boolean = false, options: @Composable (close: () -> Unit) -> Unit) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Item(icon, title, subtitle, danger = danger, onClick = { open = !open }, trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) Txt(value, 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(end = 4.dp))
                Sym(if (open) "keyboard_arrow_up" else "keyboard_arrow_down", 22f, ui.cs.onSurfaceVariant)
            }
        })
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { options { open = false } }
    }
}

/** An option inside an [ExpandItem], indented to line up with the row's title. */
@Composable
private fun OptionRow(label: String, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().touchable(onClick = onClick).heightIn(min = 48.dp).padding(start = 72.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.padding(end = 12.dp)) { leading() }
        Txt(label, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        if (selected) Sym("check", 20f, ui.cs.primary)
    }
}

// ---------------------------------------------------------------- specific rows

@Composable
private fun AccentItem(vm: BrowserViewModel) {
    val ui = LocalUi.current
    // adaptive (wallpaper colors, Android 12+), gray, blue, green, red, orange, yellow, purple — horizontally scrollable
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val list = (if (dynamicOk) listOf(DYNAMIC_ACCENT) else emptyList()) + ACCENTS
    Item("palette", "Accent Color", if (dynamicOk) "Your wallpaper's colors, or pick one" else null, below = {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { hex ->
                val selected = vm.settings.accentColor == hex
                val scale by animateFloatAsState(if (selected) 0.86f else 1f, Motion.fastSpatial(), label = "swatch")
                val isDyn = hex == DYNAMIC_ACCENT
                Box(
                    Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(if (selected) RoundedCornerShape(30) else CircleShape)
                        .background(if (isDyn) ui.cs.tertiaryContainer else Color(hexColor(hex)))
                        .touchable { vm.updateSettings { copy(accentColor = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDyn) Sym("wallpaper", 22f, ui.cs.onTertiaryContainer)
                    if (selected && !isDyn) Sym("check", 22f, Color.White)
                }
            }
        }
    })
}

@Composable
private fun SearchEngineItem(vm: BrowserViewModel) {
    val cur = SEARCH_ENGINES.getOrElse(vm.settings.searchEngineIndex) { SEARCH_ENGINES[0] }
    ExpandItem("search", "Search Engine", "Used for searches and suggestions", cur.name) { close ->
        SEARCH_ENGINES.forEachIndexed { i, e ->
            OptionRow(e.name, vm.settings.searchEngineIndex == i, leading = { EngineIcon(i, 20f) }) { vm.updateSettings { copy(searchEngineIndex = i) }; close() }
        }
    }
}

@Composable
private fun ShortcutItem(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val s = vm.settings
    val options = listOf(Triple("New Tab", "newTab", "add"), Triple("Scan QR Code", "qr", "qr_code_scanner"), Triple("Bookmarks", "bookmarks", "bookmarks"), Triple("History", "history", "history"))
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SwitchItem("apps", "Home Shortcut", "A button on the home page", s.showHomeShortcuts) { vm.updateSettings { copy(showHomeShortcuts = it) } }
        if (s.showHomeShortcuts) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant.copy(alpha = 0.5f)))
            ExpandItem("bolt", "Shortcut Action", null, options.firstOrNull { it.second == s.homeShortcutAction }?.first) { close ->
                options.forEach { (label, v, icon) ->
                    OptionRow(label, s.homeShortcutAction == v, leading = { Sym(icon, 20f, ui.cs.onSurfaceVariant) }) { vm.updateSettings { copy(homeShortcutAction = v) }; close() }
                }
            }
        }
    }
}

@Composable
private fun ClearHistoryItem(vm: BrowserViewModel) {
    ExpandItem("delete", "Clear History", "Choose how far back to delete", null, danger = true) { close ->
        HISTORY_RANGES.forEach { (label, ms) -> OptionRow(label, false) { vm.confirm = ConfirmData("history", ms, label); close() } }
    }
}

@Composable
private fun CreditsItem() {
    val ui = LocalUi.current
    ExpandItem("info", "Credits", "Open-source projects mi is built with", null) { _ ->
        listOf(
            "Ad & tracker blocklist" to "StevenBlack/hosts, MIT License",
            "Font" to "Google Sans Flex, © Google LLC",
            "Icons" to "Material Symbols, Apache License 2.0",
            "UI" to "Jetpack Compose & Material 3, Apache License 2.0",
            "QR codes" to "ZXing, Apache License 2.0",
            "Image cropping" to "Android Image Cropper, Apache License 2.0",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
    }
}

/** Shown at the top until mi is the default browser. */
@Composable
private fun DefaultBrowserCard(onSet: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(ui.radius.coerceAtLeast(12).dp)).background(ui.cs.primaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("public", 28f, ui.cs.onPrimaryContainer, Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Txt("Make mi your default browser", 16f, ui.cs.onPrimaryContainer, ui.bold)
            Txt("Open links from other apps in mi", 13f, ui.cs.onPrimaryContainer, ui.regular, Modifier.padding(top = 2.dp))
        }
        FilledTonalButton(onClick = onSet, modifier = Modifier.padding(start = 8.dp)) { Txt("Set", 14f, ui.cs.onSecondaryContainer, ui.bold) }
    }
}

/** Expressive segmented list: large outer corners, small inner corners, 2dp gaps. */
private fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}

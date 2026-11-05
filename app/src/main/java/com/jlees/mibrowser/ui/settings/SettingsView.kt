// Settings: a main page (search field + the list of sections) where search covers every setting, and one page per
// section (back arrow, no search field). Every row has an icon, a title and a one-line explanation, and the whole row
// is the touch target.
package com.jlees.mibrowser.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import com.jlees.mibrowser.data.AdBlock
import androidx.compose.material3.IconButton
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
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.isValidBangKey
import com.jlees.mibrowser.logic.BANGS
import com.jlees.mibrowser.logic.SEARCH_ENGINE_BANG
import com.jlees.mibrowser.logic.isValidSearchTemplate
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.history.HISTORY_RANGES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A setting row: [label] and [keywords] are what search matches; [content] draws it. */
private class SRow(val label: String, val keywords: String = "", val content: @Composable () -> Unit)
/** A section: its own page from the main list ([summary] says what's in it). */
private class SGroup(val id: String, val title: String, val icon: String, val summary: String, val rows: List<SRow>)

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
        SGroup("appearance", "Appearance", "palette", "Theme, pure black, accent colour, fonts, corners", listOf(
            SRow("Theme", "light dark system mode") {
                ChoiceItem("routine", "Theme", null, listOf("light" to "Light", "dark" to "Dark", "system" to "System"), s.themeMode) { set { copy(themeMode = it) } }
            },
            SRow("Pure Black", "oled amoled black dark battery") {
                if (s.themeMode != "light") SwitchItem("contrast", "Pure Black (OLED)", "True black backgrounds in dark mode: saves battery on OLED screens", s.pureBlack) { set { copy(pureBlack = it) } }
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
        SGroup("browsing", "Search & Browsing", "travel_explore", "Search engine, suggestions, autoplay, startup", listOf(
            SRow("Search Engine", "google bing duckduckgo brave ecosia yahoo") { SearchEngineItem(vm) },
            SRow("Search Suggestions", "autocomplete") {
                SwitchItem("search", "Search Suggestions", "Suggestions from your search engine while the search drawer is open", s.searchSuggestions) { set { copy(searchSuggestions = it) } }
            },
            SRow("Pull to Refresh", "reload swipe down gesture") {
                SwitchItem("refresh", "Pull to Refresh", "Pull down at the very top of a page to reload it", s.pullToRefresh) { set { copy(pullToRefresh = it) } }
            },
            SRow("Suggestions in Incognito", "autocomplete private incognito") {
                SwitchItem("incognito", "Suggestions in Incognito", "Off: what you type in an incognito tab isn't sent to your search engine until you search", s.incognitoSuggestions) { set { copy(incognitoSuggestions = it) } }
            },
            SRow("Block Video Autoplay", "autoplay video sound media") {
                SwitchItem("block", "Block Video Autoplay", "Videos wait for a tap instead of playing on their own", s.blockAutoplay) { set { copy(blockAutoplay = it) } }
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
        SGroup("shortcuts", "Search Shortcuts", "bolt", "!w, !yt, !se and your own", listOf(
            SRow("Your Shortcuts", "bang custom add search shortcut") { CustomBangsItem(vm) },
            SRow("Built-in Shortcuts", "bang se search engine " + BANGS.values.joinToString(" ") { it.name.lowercase() }) { BuiltInBangsItem(vm) },
        )),
        SGroup("pill", "Pill & Home", "toolbar", "Loading bar, favorites, home page", listOfNotNull(
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
        SGroup("tabs", "Tabs & Library", "library_books", "Tab icons, background refresh, history", listOf(
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
        SGroup("adblock", "Ad Blocking", "block", "Ads, trackers, cookie banners, allowed sites", listOf(
            SRow("Block Ads", "adblock easylist banner ads malware") {
                SwitchItem("block", "Block Ads", "Ads and ad networks (EasyList and a malware and ad domain list)", s.blockAds) { set { copy(blockAds = it) } }
            },
            SRow("Block Trackers", "tracking analytics easyprivacy fingerprint") {
                SwitchItem("travel_explore", "Block Trackers", "Analytics and tracking scripts that follow you across sites (EasyPrivacy)", s.blockTrackers) { set { copy(blockTrackers = it) } }
            },
            SRow("Hide Cookie Banners", "consent gdpr popup cookie") {
                SwitchItem("cookie", "Hide Cookie Banners", "Cookie-consent pop-ups (EasyList Cookie List). Turn off if a site won't let you continue", s.hideCookieBanners) { set { copy(hideCookieBanners = it) } }
            },
            SRow("Remove Tracking from Links", "utm fbclid gclid parameters clean url") {
                SwitchItem("content_copy", "Remove Tracking from Links", "Strips utm_, fbclid, gclid and similar tags from links you open, copy or share", s.stripTrackingParams) { set { copy(stripTrackingParams = it) } }
            },
            SRow("Allowed Sites", "whitelist exception allow ads site") { AllowedSitesItem(vm) },
            SRow("Update Filter Lists", "adblock update easylist") { FilterListsItem(vm) },
        )),
        SGroup("privacy", "Privacy & Security", "shield", "HTTPS, cookies, JavaScript, pop-ups, clearing data", listOf(
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
                Item("delete", "Clear Browsing Data", "Cookies, site data, cache, site icons and tab snapshots", danger = true, onClick = { vm.confirm = ConfirmData("clearData") })
            },
        )),
        SGroup("data", "Backup & Reset", "save", "Export, import, reset", listOf(
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
        SGroup("about", "About", "info", "Updates, guide, source code, credits", listOf(
            SRow("Filter List Credits", "easylist license adblock") {
                Item("info", "Filter lists", "EasyList, EasyPrivacy and the EasyList Cookie List by The EasyList authors (CC BY-SA 3.0). StevenBlack hosts (MIT).")
            },
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

    // ---- the main page lists the sections (and searches every setting); a section opens as its own page, without the
    // search bar. Back (or the arrow) returns to the list.
    val q = search.trim().lowercase()
    val visible = if (q.isEmpty()) emptyList() else groups.mapNotNull { g ->
        if (g.title.lowercase().contains(q)) g
        else g.rows.filter { it.label.lowercase().contains(q) || it.keywords.contains(q) }.takeIf { it.isNotEmpty() }?.let { SGroup(g.id, g.title, g.icon, g.summary, it) }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    val sheetBg = ui.cs.surfaceContainerLow

    // keyed on the section's id: the section objects are rebuilt with every settings change, and keying on them made
    // each switch replay the page transition. A plain slide, no spring.
    AnimatedContent(
        openId, Modifier.fillMaxSize(), label = "settings page",
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(tween(220)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
        },
    ) { pageId ->
        val page = groups.firstOrNull { it.id == pageId }
        if (page == null) TopBarOver(sheetBg, bar = {
            SheetSearchField(search, { vm.settingsSearch = it }, "Search Settings...", Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
        }) { top ->
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = 48.dp)) {
                if (q.isEmpty()) {
                    if (!isDefault) item(key = "banner") { DefaultBrowserCard(::requestDefault) }
                    itemsIndexed(groups, key = { _, g -> "s:${g.id}" }) { i, g ->
                        Box(Modifier.segment(i, groups.size)) { Item(g.icon, g.title, g.summary, onClick = { openId = g.id }, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) }) }
                    }
                } else {
                    if (visible.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Sym("search", 44f, ui.cs.onSurfaceVariant)
                            Txt("No settings match \"$search\"", 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
                        }
                    }
                    // results grouped by section (headers scroll with them, under the search bar)
                    visible.forEach { g ->
                        item(key = "h:${g.id}") { SectionHeader(g.icon, g.title) }
                        itemsIndexed(g.rows, key = { _, r -> "${g.id}:${r.label}" }) { i, r ->
                            Box(Modifier.animateItem().segment(i, g.rows.size)) { r.content() }
                        }
                    }
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Txt("mi browser v${BuildConfig.VERSION_NAME}", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.graphicsLayer { alpha = 0.5f })
                    }
                }
            }
        } else TopBarOver(sheetBg, bar = {
            // a section's page: back arrow and title instead of the search bar
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openId = null }) { Sym("arrow_back", 24f, ui.cs.onSurface) }
                Sym(page.icon, 22f, ui.cs.primary, Modifier.padding(start = 4.dp, end = 10.dp))
                Txt(page.title, 22f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }) { top ->
            val pageList = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = pageList, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = 48.dp)) {
                itemsIndexed(page.rows, key = { _, r -> "${page.id}:${r.label}" }) { i, r ->
                    Box(Modifier.segment(i, page.rows.size)) { r.content() }
                }
            }
        }
    }
}

/**
 * A top bar (search field, or a section's back arrow and title) that the list scrolls under: the bar sits on the sheet's
 * colour with a soft fade below it, so rows slide behind it and fade out instead of being cut off. [content] gets the
 * top padding that keeps its first row clear of the bar at rest.
 */
@Composable
private fun TopBarOver(bg: Color, bar: @Composable () -> Unit, content: @Composable (top: androidx.compose.ui.unit.Dp) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barPx by remember { mutableIntStateOf(0) }
    val fade = 20.dp
    Box(Modifier.fillMaxSize()) {
        content(with(density) { barPx.toDp() } + fade)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().background(bg).padding(bottom = 6.dp).onSizeChanged { barPx = it.height }) { bar() }
            Box(Modifier.fillMaxWidth().height(fade).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
        }
    }
}

/** A section's title above its rows in search results. */
@Composable
private fun SectionHeader(icon: String, title: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 18f, ui.cs.primary, Modifier.padding(end = 8.dp))
        Txt(title, 15f, ui.cs.primary, ui.bold)
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
    val s = vm.settings
    var editing by remember { mutableStateOf(false) }
    ExpandItem("search", "Search Engine", "Used for searches and suggestions", vm.searchEngine.name) { close ->
        SEARCH_ENGINES.forEachIndexed { i, e ->
            OptionRow(e.name, s.searchEngineIndex == i, leading = { EngineIcon(e, 20f) }) { vm.updateSettings { copy(searchEngineIndex = i) }; close() }
        }
        // your own engine: picking it (or "Custom…") opens its editor
        val custom = isValidSearchTemplate(s.customSearchUrl)
        OptionRow(if (custom) s.customSearchName.ifBlank { "Custom" } else "Custom…", s.searchEngineIndex == SEARCH_ENGINES.size,
            leading = { EngineIcon(vm.searchEngine.takeIf { s.searchEngineIndex == SEARCH_ENGINES.size } ?: com.jlees.mibrowser.logic.SearchEngine("Custom", s.customSearchUrl), 20f) }) {
            if (custom && s.searchEngineIndex != SEARCH_ENGINES.size) { vm.updateSettings { copy(searchEngineIndex = SEARCH_ENGINES.size) }; close() } else editing = true
        }
    }
    CustomEngineDialog(vm, editing) { editing = false }
}

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

/** A search shortcut in a list: its "!key" chip, the site's name, and (for yours) a delete button. */
@Composable
private fun BangRow(key: String, name: String, onDelete: (() -> Unit)? = null) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(ui.cs.primaryContainer).padding(horizontal = 10.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Txt("!$key", 14f, ui.cs.onPrimaryContainer, ui.bold, maxLines = 1)
        }
        Txt(name, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f).padding(start = 14.dp), maxLines = 1)
        if (onDelete != null) IconButton(onClick = onDelete) { Sym("delete", 20f, ui.cs.onSurfaceVariant) }
    }
}

/** Your own shortcuts, with a button to add one. */
@Composable
private fun CustomBangsItem(vm: BrowserViewModel) {
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Item("add", "Add a shortcut", "Type it at the start of a search, like !w mars. Yours take priority over the built-in ones.", onClick = { adding = true })
        if (vm.settings.customBangs.isNotEmpty()) BangListLabel("Yours")
        vm.settings.customBangs.forEach { (key, url) ->
            BangRow(key, displayHost(url).ifEmpty { url }) { vm.updateSettings { copy(customBangs = customBangs - key) } }
        }
        if (vm.settings.customBangs.isNotEmpty()) Spacer(Modifier.height(8.dp))
    }
    AddBangDialog(vm, adding) { adding = false }
}

/** The built-in shortcuts, for reference. */
@Composable
private fun BuiltInBangsItem(vm: BrowserViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BangListLabel("Built-in")
        BangRow(SEARCH_ENGINE_BANG, "Search with ${vm.searchEngine.name}") // always searches, even for something like an address
        BANGS.forEach { (key, bang) -> BangRow(key, bang.name) }
    }
}

@Composable
private fun BangListLabel(text: String) {
    val ui = LocalUi.current
    Txt(text, 13f, ui.cs.onSurfaceVariant, ui.bold, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

/** A new shortcut: its key and the site's search address (%s = the search). */
@Composable
private fun AddBangDialog(vm: BrowserViewModel, visible: Boolean, onClose: () -> Unit) {
    val ui = LocalUi.current
    var key by remember(visible) { mutableStateOf("") }
    var url by remember(visible) { mutableStateOf("https://") }
    val k = key.trim().removePrefix("!").lowercase()
    val keyOk = isValidBangKey(k)
    val urlOk = isValidSearchTemplate(url)
    com.jlees.mibrowser.ui.ModalFrame(visible, onClose) {
        DialogCard("Add search shortcut", actions = {
            DialogAction("Cancel", onClick = onClose)
            DialogAction("Add") { if (keyOk && urlOk) { vm.updateSettings { copy(customBangs = customBangs + (k to url.trim())) }; onClose() } }
        }) {
            com.jlees.mibrowser.ui.LabeledField("Shortcut", key, { key = it }, Modifier.padding(bottom = 4.dp), placeholder = "e.g. ebay")
            Txt(
                when { k.isNotEmpty() && !keyOk -> "Use up to 12 letters or numbers"; k in BANGS -> "Replaces the built-in !$k (${BANGS[k]!!.name})"; k == SEARCH_ENGINE_BANG -> "Replaces the built-in !se (your search engine)"; k.isNotEmpty() -> "You'll type !$k"; else -> " " },
                12f, if (k.isNotEmpty() && !keyOk) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp),
            )
            com.jlees.mibrowser.ui.LabeledField("Search address", url, { url = it }, uri = true, placeholder = "https://www.ebay.com/sch/i.html?_nkw=%s")
            Txt(
                if (url.length > 8 && !urlOk) "Enter a web address starting with https://" else "Search the site for \"test\", copy the address of the results page, and put %s where \"test\" was.",
                12f, if (url.length > 8 && !urlOk) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), lineHeight = 16f,
            )
        }
    }
}

/** Name and address (%s = the search) of your own search engine. Saving selects it; it gets no suggestions. */
@Composable
private fun CustomEngineDialog(vm: BrowserViewModel, visible: Boolean, onClose: () -> Unit) {
    val ui = LocalUi.current
    var name by remember(visible) { mutableStateOf(vm.settings.customSearchName) }
    var url by remember(visible) { mutableStateOf(vm.settings.customSearchUrl.ifBlank { "https://" }) }
    val valid = isValidSearchTemplate(url)
    com.jlees.mibrowser.ui.ModalFrame(visible, onClose) {
        DialogCard("Custom search engine", actions = {
            DialogAction("Cancel", onClick = onClose)
            DialogAction("Save") {
                if (valid) { vm.updateSettings { copy(customSearchName = name.trim(), customSearchUrl = url.trim(), searchEngineIndex = SEARCH_ENGINES.size) }; onClose() }
            }
        }) {
            com.jlees.mibrowser.ui.LabeledField("Name", name, { name = it }, Modifier.padding(bottom = 15.dp), placeholder = "e.g. Startpage")
            com.jlees.mibrowser.ui.LabeledField("Search address", url, { url = it }, uri = true, placeholder = "https://example.com/search?q=%s")
            Txt(
                if (url.length > 8 && !valid) "Enter a web address starting with https://" else "Put %s where your search should go. Suggestions aren't available for custom engines.",
                12f, if (url.length > 8 && !valid) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), lineHeight = 16f,
            )
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
        Modifier.fillMaxWidth().padding(bottom = 20.dp).clip(RoundedCornerShape(ui.radius.coerceAtLeast(12).dp)).background(ui.cs.primaryContainer).padding(16.dp),
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
/** Row [i] of [count] in a segmented card: full width, a 2dp gap below, rounded outer corners. */
@Composable
private fun Modifier.segment(i: Int, count: Int): Modifier {
    val ui = LocalUi.current
    return fillMaxWidth().padding(bottom = if (i < count - 1) 2.dp else 0.dp).clip(segmentShape(i, count, ui.radius)).background(ui.cs.surfaceContainerHighest)
}

private fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}

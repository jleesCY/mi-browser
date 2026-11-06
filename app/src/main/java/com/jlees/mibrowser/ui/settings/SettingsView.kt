// Settings: a main page (search field + the list of sections) where search covers every setting, and one page per
// section (back arrow, no search field). Every row has an icon, a title and a one-line explanation, and the whole row
// is the touch target.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.search.searchSection

import com.jlees.mibrowser.home.homeSection
import com.jlees.mibrowser.library.librarySection
import com.jlees.mibrowser.tabs.tabsSection

import com.jlees.mibrowser.adblock.adBlockSection

import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction
import com.jlees.mibrowser.data.Spacing
import com.jlees.mibrowser.data.ThemeMode

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
import androidx.compose.material3.IconButton
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.ui.common.SheetSearchField
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GUIDE_URL = "https://jleescy.github.io/mi-browser/user"
private const val SOURCE_URL = "https://github.com/jleescy/mi-browser"
private const val PRIVACY_URL = "https://jleescy.github.io/mi-browser/privacy"

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
                    AlertButton("Cancel", ButtonStyle.CANCEL),
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
        SettingsSection("look", "Look & Feel", "palette", "Theme, colours, fonts, spacing, haptics", listOf(
            SettingsRow("Theme", "light dark system mode") {
                ChoiceItem("routine", "Theme", null, s.themeMode) { set { copy(themeMode = it) } }
            },
            SettingsRow("Pure Black", "oled amoled black dark battery") {
                if (s.themeMode != ThemeMode.LIGHT) SwitchItem("contrast", "Pure Black (OLED)", "True black backgrounds in dark mode: saves battery on OLED screens", s.pureBlack) { set { copy(pureBlack = it) } }
            },
            SettingsRow("Accent Color", "colour wallpaper material you dynamic") { AccentItem(vm) },
            SettingsRow("Font Size", "text scale app") {
                SliderItem("format_size", "Font Size", "Text size throughout mi", s.fontScale, 0.8f, 1.2f) { set { copy(fontScale = it) } }
            },
            SettingsRow("Rounded Font", "typeface") { SwitchItem("font_download", "Rounded Font", "Softer, rounded letterforms", s.roundedFont) { set { copy(roundedFont = it) } } },
            SettingsRow("Corners", "radius shape") {
                ChoiceItem("rounded_corner", "Corners", null, listOf("0" to "Square", "12" to "Soft", "28" to "Round"), s.cornerRadius.toString()) { set { copy(cornerRadius = it.toInt()) } }
            },
            SettingsRow("Spacing", "density padding compact airy") {
                ChoiceItem("density_medium", "Spacing", null, s.uiPadding) { set { copy(uiPadding = it) } }
            },
            SettingsRow("Status Bar", "clock battery immersive") { SwitchItem("battery_charging_full", "Status Bar", "Show the clock and battery at the top", s.showStatusBar) { set { copy(showStatusBar = it) } } },
            SettingsRow("Haptic Feedback", "vibration vibrate haptics touch feel") {
                SwitchItem("vibration", "Haptic Feedback", "A light tap when a swipe or gesture is about to act", s.hapticFeedback) { set { copy(hapticFeedback = it) } }
            },
        )),
        SettingsSection("pill", "Pill", "toolbar", "Loading bar, favorites, search drawer", listOfNotNull(
            SettingsRow("Loading Bar", "progress") {
                ChoiceItem("hourglass_empty", "Loading Bar", "Fill behind the address while a page loads", s.progressBarMode) { set { copy(progressBarMode = it) } }
            },
            SettingsRow("Pin Favorites", "favorites bar") { SwitchItem("star", "Pin Favorites", "Always show the favorites bar when searching", s.showFavoritesDefault) { set { copy(showFavoritesDefault = it) } } },
            SettingsRow("Expand Searches", "recent searches drawer") {
                SwitchItem("list", "Expand Searches", "Open the search drawer fully when you tap the pill", s.recentSearchesExpanded) { set { copy(recentSearchesExpanded = it) } }
            },
        )),
        homeSection(vm),
        searchSection(vm),
        SettingsSection("pages", "Web Pages", "public", "Text size, dark pages, autoplay, desktop and reader mode", listOf(
            SettingsRow("Page Text Size", "zoom font web websites") {
                SliderItem("format_size", "Page Text Size", "Text size on websites", s.textZoom / 100f, 0.5f, 2f) { set { copy(textZoom = Math.round(it * 100)) } }
            },
            SettingsRow("Darken Web Pages", "dark mode force night websites") {
                SwitchItem("contrast", "Darken Web Pages", "In dark theme, darken sites that have no dark mode", s.darkenPages) { set { copy(darkenPages = it) } }
            },
            SettingsRow("Block Video Autoplay", "autoplay video sound media") {
                SwitchItem("block", "Block Video Autoplay", "Videos wait for a tap instead of playing on their own", s.blockAutoplay) { set { copy(blockAutoplay = it) } }
            },
            SettingsRow("Desktop Site by Default", "desktop mode user agent") {
                SwitchItem("desktop_windows", "Desktop Site by Default", "Request desktop versions of websites (each tab can still change it)", s.desktopMode) { set { copy(desktopMode = it) } }
            },
            SettingsRow("Reader Mode by Default", "reader readability") {
                SwitchItem("chrome_reader_mode", "Reader Mode by Default", "Simplify pages for reading (each tab can still change it)", s.readerModeEnabled) { set { copy(readerModeEnabled = it) } }
            },
            SettingsRow("Pull to Refresh", "reload swipe down gesture") {
                SwitchItem("refresh", "Pull to Refresh", "Pull down at the very top of a page to reload it", s.pullToRefresh) { set { copy(pullToRefresh = it) } }
            },
        )),
        tabsSection(vm),
        librarySection(vm),
        adBlockSection(vm),
        SettingsSection("privacy", "Privacy & Security", "shield", "HTTPS, cookies, JavaScript, pop-ups, clearing data", listOf(
            SettingsRow("HTTPS Only", "secure ssl tls") { SwitchItem("lock", "HTTPS Only", "Always use secure connections (local addresses excluded)", s.httpsOnly) { set { copy(httpsOnly = it) } } },
            SettingsRow("Allow Cookies", "cookies") { SwitchItem("cookie", "Allow Cookies", "Turning this off signs you out of most sites", !s.blockCookies) { set { copy(blockCookies = !it) } } },
            SettingsRow("Block Third-Party Cookies", "cross-site tracking") {
                SwitchItem("cookie", "Block Third-Party Cookies", "Stops cross-site cookies; can break payment and sign-in windows", s.blockThirdPartyCookies) { set { copy(blockThirdPartyCookies = it) } }
            },
            SettingsRow("JavaScript", "js scripts") { SwitchItem("code", "JavaScript", "Most sites need it to work", s.jsEnabled) { set { copy(jsEnabled = it) } } },
            SettingsRow("Pop-ups", "popups windows") {
                val n = s.popupSites.size
                Item("open_in_new", "Pop-ups",
                    if (n == 0) "Open when you tap; blocked otherwise" else "Also allowed without a tap on $n site${if (n == 1) "" else "s"}",
                    onClick = if (n == 0) null else { {
                        vm.showAlert("Pop-ups", "Allowed without a tap on:\n" + s.popupSites.joinToString("\n"), listOf(
                            AlertButton("Close", ButtonStyle.CANCEL),
                            AlertButton("Block all again", ButtonStyle.DESTRUCTIVE) { set { copy(popupSites = emptyList()) } },
                        ), onCancel = {})
                    } },
                    trailing = if (n == 0) null else { { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) } })
            },
            SettingsRow("Clear Browsing Data", "cookies cache storage delete") {
                Item("delete", "Clear Browsing Data", "Cookies, site data, cache, site icons and tab snapshots", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.CLEAR_DATA) })
            },
        )),
        SettingsSection("data", "Backup & Reset", "save", "Export, import, reset", listOf(
            SettingsRow("Export Settings", "backup save") {
                Item("upload", "Export Settings", "Save your settings to a file", onClick = { exporter.launch("settings_${System.currentTimeMillis()}.midata") })
            },
            SettingsRow("Import Settings", "restore load") { Item("download", "Import Settings", "Restore from an exported file", onClick = { importer.launch(arrayOf("*/*")) }) },
            SettingsRow("Reset All Settings", "defaults") {
                Item("restart_alt", "Reset All Settings", "Back to defaults; bookmarks, history and tabs are kept", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.RESET_SETTINGS) })
            },
            SettingsRow("Wipe All Data", "erase delete everything") {
                Item("delete", "Wipe All Data", "Delete bookmarks, history, tabs, favorites and settings", danger = true, onClick = { vm.confirm = ConfirmData(ConfirmAction.WIPE_DATA) })
            },
        )),
        SettingsSection("about", "About", "info", "Default browser, updates, guide, credits", listOf(
            SettingsRow("Default Browser", "links open") {
                Item("public", "Default Browser", if (isDefault) "mi opens links from other apps" else "Tap to make mi open links from other apps",
                    onClick = if (isDefault) null else ::requestDefault,
                    trailing = { if (isDefault) Sym("check", 22f, ui.cs.primary) else Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
            },
            SettingsRow("Check for Updates", "version update") {
                Item("restart_alt", "Check for Updates", "mi browser ${BuildConfig.VERSION_NAME}", onClick = { vm.updater.checkNow() })
            },
            SettingsRow("User Guide", "help") {
                Item("help", "User Guide", "Every gesture, tool and setting", onClick = { openPage(GUIDE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SettingsRow("Privacy Policy", "privacy data collection policy") {
                Item("shield", "Privacy Policy", "mi collects no personal data", onClick = { openPage(PRIVACY_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SettingsRow("Source Code", "github open source") {
                Item("code", "Source Code", "github.com/jleescy/mi-browser", onClick = { openPage(SOURCE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SettingsRow("Credits", "licenses open source attribution") { CreditsItem() },
            SettingsRow("Filter List Credits", "easylist license adblock") {
                Item("info", "Filter lists", "EasyList, EasyPrivacy and the EasyList Cookie List by The EasyList authors (CC BY-SA 3.0). StevenBlack hosts (MIT).")
            },
        )),
    )

    // ---- the main page lists the sections (and searches every setting); a section opens as its own page, without the
    // search bar. Back (or the arrow) returns to the list.
    val q = search.trim().lowercase()
    val visible = if (q.isEmpty()) emptyList() else groups.mapNotNull { g ->
        if (g.title.lowercase().contains(q)) g
        else g.rows.filter { it.label.lowercase().contains(q) || it.keywords.contains(q) }.takeIf { it.isNotEmpty() }?.let { SettingsSection(g.id, g.title, g.icon, g.summary, it) }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) } // a full page opened from a row of the section
    // pages opened so far, by id: a page sliding out after Back is still drawn as itself (not its section)
    val subPages = remember { mutableMapOf<String, SettingsSubPage>() }
    BackHandler(enabled = openId != null || subPage != null) { if (subPage != null) subPage = null else openId = null }
    val nav = remember { SettingsNav(open = { subPages[it.id] = it; subPage = it }, close = { subPage = null }) }
    val sheetBg = ui.cs.surfaceContainerLow

    // keyed on the section's id: the section objects are rebuilt with every settings change, and keying on them made
    // each switch replay the page transition. A plain slide, no spring.
    AnimatedContent(
        openId to subPage?.id, Modifier.fillMaxSize(), label = "settings page",
        transitionSpec = {
            fun depth(p: Pair<String?, String?>) = (if (p.first != null) 1 else 0) + (if (p.second != null) 1 else 0)
            val forward = depth(targetState) > depth(initialState)
            (slideInHorizontally(tween(220)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
        },
    ) { (pageId, subId) ->
        val page = groups.firstOrNull { it.id == pageId }
        val sub = subId?.let { subPages[it] }
        if (sub != null) TopBarOver(sheetBg, bar = {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { subPage = null }) { Sym("arrow_back", 24f, ui.cs.onSurface) }
                Sym(sub.icon, 22f, ui.cs.primary, Modifier.padding(start = 4.dp, end = 10.dp))
                Txt(sub.title, 22f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }) { top ->
            CompositionLocalProvider(LocalSettingsNav provides nav) {
                Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = top, bottom = 24.dp)) { sub.content() }
            }
        } else if (page == null) TopBarOver(sheetBg, bar = {
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
                            CompositionLocalProvider(LocalSettingsNav provides nav) { Box(Modifier.animateItem().segment(i, g.rows.size)) { r.content() } }
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
                    CompositionLocalProvider(LocalSettingsNav provides nav) { Box(Modifier.segment(i, page.rows.size)) { r.content() } }
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

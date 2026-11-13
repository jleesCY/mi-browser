// Settings: a main page (search field + the list of sections) where search covers every setting, and one page per
// section (back arrow, no search field). Every row has an icon, a title and a one-line explanation, and the whole row
// is the touch target.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.data.applyImport

import com.jlees.mibrowser.downloads.downloadsSection

import com.jlees.mibrowser.search.searchSection

import com.jlees.mibrowser.home.homeSection
import com.jlees.mibrowser.library.librarySection
import com.jlees.mibrowser.tabs.tabsSection

import com.jlees.mibrowser.adblock.adBlockSection

import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ConfirmAction

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.IconButton
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jlees.mibrowser.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GUIDE_URL = "https://jleescy.github.io/mi-browser/user"
private const val SOURCE_URL = "https://github.com/jleescy/mi-browser"
private const val NEW_ISSUE_URL = "$SOURCE_URL/issues/new"
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
        lookSection(vm),
        pillSection(vm),
        gesturesSection(vm),
        homeSection(vm),
        searchSection(vm),
        pagesSection(vm),
        com.jlees.mibrowser.media.mediaSection(vm),
        tabsSection(vm),
        // the Library page holds Bookmarks and Downloads, so its settings do too (each still built by its own package)
        librarySection(vm).let { lib ->
            SettingsSection(lib.id, lib.title, lib.icon, lib.summary, listOf(SettingsHeading("Bookmarks")) + lib.rows + SettingsHeading("Downloads") + downloadsSection(vm).rows)
        },
        com.jlees.mibrowser.library.historySection(vm),
        adBlockSection(vm),
        privacySection(vm),
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
        // mi itself first (its version, being the default), then help (learning it, then reaching out), then the fine print
        SettingsSection("about", "About", "info", "Updates, help, feedback, credits", listOf(
            SettingsHeading("mi browser"),
            SettingsRow("Check for Updates", "version update") {
                Item("restart_alt", "Check for Updates", "mi browser ${BuildConfig.VERSION_NAME}", onClick = { vm.updater.checkNow() })
            },
            SettingsRow("Default Browser", "links open") {
                Item("public", "Default Browser", if (isDefault) "mi opens links from other apps" else "Make mi. open links from other apps",
                    onClick = if (isDefault) null else ::requestDefault,
                    trailing = { if (isDefault) Sym("check", 22f, ui.cs.primary) else Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
            },
            SettingsHeading("Help & Feedback"),
            SettingsRow("Gesture Walkthrough", "help gestures intro tutorial tour quick guide") {
                Item("toolbar", "Gesture Walkthrough", "Try every swipe, step by step", onClick = { set { copy(walkthroughSeen = false) } })
            },
            SettingsRow("User Guide", "help") {
                Item("help", "User Guide", "Every gesture, tool and setting", onClick = { openPage(GUIDE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SettingsRow("Report a Bug or Suggest a Feature", "feedback issue bug report feature request idea github problem") {
                Item("bug_report", "Report a Bug or Suggest a Feature", "Open a new issue on GitHub", onClick = { openPage(NEW_ISSUE_URL) }, trailing = { Sym("open_in_new", 20f, ui.cs.onSurfaceVariant) })
            },
            SettingsHeading("Legal & Credits"),
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

    // the main page's headings, each over its own block of sections (by id): a new section is added to one here
    val headings = listOf(
        // most-changed first (search engine, theme, ad blocking), housekeeping last
        "Browsing" to listOf("search", "pages", "media"),
        "Appearance" to listOf("look", "pill", "home"),
        "Protection" to listOf("adblock", "privacy"),
        "Navigation" to listOf("tabs", "gestures"),
        "Saved" to listOf("library", "history"),
        "General" to listOf("data", "about"),
    ).map { (title, ids) -> title to ids.mapNotNull { id -> groups.firstOrNull { it.id == id } } }
        .filter { it.second.isNotEmpty() }

    // ---- the main page lists the sections (and searches every setting); a section opens as its own page, without the
    // search bar. Back (or the arrow) returns to the list.
    val q = search.trim().lowercase()
    // matching settings by section, each with the heading it sits under (shown with it: "Swipe Left" alone is unclear)
    val visible = if (q.isEmpty()) emptyList() else groups.mapNotNull { g ->
        val placed = placeRows(g.rows).filter { !it.row.header }
        val hits = if (g.title.lowercase().contains(q)) placed
            else placed.filter { it.row.label.lowercase().contains(q) || it.row.keywords.contains(q) || it.heading?.lowercase()?.contains(q) == true }
        hits.takeIf { it.isNotEmpty() }?.let { g to it }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // a search result opened: its row in the section, scrolled to and briefly lit up (the search stays for Back)
    var found by remember { mutableStateOf<Pair<String?, String>?>(null) } // (heading, label)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) } // a full page opened from a row of the section
    // pages opened so far, by id: a page sliding out after Back is still drawn as itself (not its section)
    val subPages = remember { mutableMapOf<String, SettingsSubPage>() }
    BackHandler(enabled = openId != null || subPage != null) { if (subPage != null) subPage = null else openId = null }
    val nav = remember { SettingsNav(open = { subPages[it.id] = it; subPage = it }, close = { subPage = null }) }
    val sheetBg = ui.cs.surfaceContainerLow

    // The page for a (section, sub-page) state. Drawn by AnimatedContent, and during a corner swipe both the page and
    // the level above it are drawn, the page sliding off with the finger.
    @Composable
    fun Page(state: Pair<String?, String?>) {
        val (pageId, subId) = state
        val page = groups.firstOrNull { it.id == pageId }
        val sub = subId?.let { subPages[it] }
        if (sub != null) TopBarOver(sheetBg, bar = {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { subPage = null }) { Sym("arrow_back", 24f, ui.cs.onSurface, label = "Back") }
                Sym(sub.icon, 22f, ui.cs.primary, Modifier.padding(start = 4.dp, end = 10.dp))
                Txt(sub.title, 22f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }) { top ->
            CompositionLocalProvider(LocalSettingsNav provides nav) {
                Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = top, bottom = SheetPillSpace)) { sub.content() }
            }
        } else if (page == null) TopBarOver(sheetBg, bar = {}) { top -> // the search field is the Pill at the bottom
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = SheetPillSpace)) {
                if (q.isEmpty()) {
                    if (!isDefault) item(key = "banner") { DefaultBrowserCard(::requestDefault) }
                    headings.forEach { (title, sections) ->
                        item(key = "g:$title") { SectionHeader(null, title) }
                        itemsIndexed(sections, key = { _, g -> "s:${g.id}" }) { i, g ->
                            Box(Modifier.segment(i, sections.size)) { Item(g.icon, g.title, g.summary, onClick = { openId = g.id }, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) }) }
                        }
                    }
                } else {
                    if (visible.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Sym("search", 44f, ui.cs.onSurfaceVariant)
                            Txt("No settings match \"$search\"", 16f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp))
                        }
                    }
                    // results grouped by section (headers scroll with them, under the search bar). Each opens the setting on
                    // its own page, among its neighbours, rather than changing it here out of context
                    visible.forEach { (g, hits) ->
                        item(key = "h:${g.id}") { SectionHeader(g.icon, g.title) }
                        itemsIndexed(hits, key = { _, p -> "${g.id}:${p.heading}:${p.row.label}" }) { i, p ->
                            Box(Modifier.animateItem().segment(i, hits.size)) {
                                // the keyboard goes and the search Pill lets go: the setting is what you're after now
                                Item(g.icon, p.row.label, p.heading, onClick = { focusManager.clearFocus(); found = p.heading to p.row.label; openId = g.id }, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
                            }
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
                IconButton(onClick = { openId = null }) { Sym("arrow_back", 24f, ui.cs.onSurface, label = "Back") }
                Sym(page.icon, 22f, ui.cs.primary, Modifier.padding(start = 4.dp, end = 10.dp))
                Txt(page.title, 22f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }) { top ->
            val pageList = rememberLazyListState()
            // opened from a search result: scroll to the setting and light it up for a moment
            val flash = remember { androidx.compose.animation.core.Animatable(0f) }
            val placed = placeRows(page.rows)
            val target = found?.let { (heading, label) -> placed.indexOfFirst { !it.row.header && it.heading == heading && it.row.label == label } }?.takeIf { it >= 0 }
            LaunchedEffect(page.id, target) {
                if (target == null) return@LaunchedEffect
                pageList.animateScrollToItem(target)
                flash.animateTo(1f, tween(250)); flash.animateTo(0f, tween(900, delayMillis = 400))
                found = null
            }
            LazyColumn(Modifier.fillMaxSize(), state = pageList, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = SheetPillSpace)) {
                itemsIndexed(placed, key = { _, p -> "${page.id}:${p.heading}:${p.row.label}" }) { i, p ->
                    val r = p.row
                    if (r.header) SectionHeader(null, r.label) // a heading inside the section, over its own block
                    else CompositionLocalProvider(LocalSettingsNav provides nav) {
                        Box(Modifier.segment(p.index, p.count).then(
                            if (i == target) Modifier.drawWithContent { drawContent(); drawRect(ui.cs.primary.copy(alpha = 0.18f * flash.value)) } else Modifier,
                        )) { r.content() }
                    }
                }
            }
        }
    }

    // A corner swipe (bottom left, see OverlaySheet) below the top level goes up a level, following the finger: [back]
    // is how far the page has slid off (0..1). Finishing switches the state with no transition (the slide already ran).
    val back = remember { androidx.compose.animation.core.Animatable(0f) }
    var backing by remember { mutableStateOf(false) }
    var instant by remember { mutableStateOf(false) }
    val state = openId to subPage?.id
    val parent = if (subPage != null) openId to null else null to null
    val sheetBack = LocalSheetBack.current
    DisposableEffect(state) {
        sheetBack?.inner = if (openId == null && subPage == null) null else BackSwipe(
            drag = { p -> backing = true; scope.launch { back.snapTo(p) } },
            release = { up ->
                scope.launch {
                    if (up) {
                        back.animateTo(1f, tween(160))
                        instant = true
                        if (subPage != null) subPage = null else openId = null
                    } else back.animateTo(0f, tween(200))
                    backing = false; back.snapTo(0f)
                }
            },
        )
        onDispose { sheetBack?.inner = null }
    }

    Box(Modifier.fillMaxSize()) {
    if (backing) Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -size.width * 0.25f * (1f - back.value) }) { Page(parent) }
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * back.value; shadowElevation = 8.dp.toPx() }.background(sheetBg)) { Page(state) }
    } else     // keyed on the section's id: the section objects are rebuilt with every settings change, and keying on them made
    // each switch replay the page transition. A plain slide, no spring.
AnimatedContent(
        state, Modifier.fillMaxSize(), label = "settings page",
        transitionSpec = {
            if (instant) { instant = false; return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None }
            fun depth(p: Pair<String?, String?>) = (if (p.first != null) 1 else 0) + (if (p.second != null) 1 else 0)
            val forward = depth(targetState) > depth(initialState)
            (slideInHorizontally(tween(220)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
        },
    ) { Page(it) }
    // searching shows results from the top level, so it leaves any section that's open
    SheetSearchPill(search, { vm.settingsSearch = it; if (it.isNotEmpty()) { subPage = null; openId = null } })
    }
}

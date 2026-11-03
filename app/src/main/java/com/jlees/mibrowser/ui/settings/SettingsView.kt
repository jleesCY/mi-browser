// Port of legacy/src/components/Settings/SettingsView.tsx
package com.jlees.mibrowser.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.BuildConfig
import com.jlees.mibrowser.ConfirmData
import com.jlees.mibrowser.data.DEFAULT_MENU_BAR_ORDER
import com.jlees.mibrowser.data.DataTransfer
import com.jlees.mibrowser.data.ImportError
import com.jlees.mibrowser.logic.ACCENTS
import com.jlees.mibrowser.logic.DYNAMIC_ACCENT
import android.os.Build
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import com.jlees.mibrowser.logic.SEARCH_ENGINES
import com.jlees.mibrowser.logic.hexColor
import com.jlees.mibrowser.logic.moved
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.history.HISTORY_RANGES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class SRow(val label: String, val content: @Composable () -> Unit)
private class SGroup(val title: String, val id: String, val rows: List<SRow>)

@Composable
fun SettingsView(vm: BrowserViewModel, onFocusSearch: () -> Unit, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = vm.settings
    val search = vm.settingsSearch
    var category by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(category, search) { scroll.scrollTo(0) }
    fun set(f: com.jlees.mibrowser.data.Settings.() -> com.jlees.mibrowser.data.Settings) = vm.updateSettings(f)

    // ---- background image: pick → crop to screen aspect → copy into filesDir
    val config = LocalConfiguration.current
    val cropper = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val uri = result.uriContent ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(ctx.filesDir, "home_bg_${System.currentTimeMillis()}.jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { i.copyTo(it) } }
                    dest.path
                }.getOrNull()
            }
            if (path != null) {
                s.homeBackgroundImage?.takeIf { it.startsWith(ctx.filesDir.path) }?.let { File(it).delete() }
                set { copy(homeBackgroundImage = path) }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) cropper.launch(CropImageContractOptions(uri, CropImageOptions(
            fixAspectRatio = true, aspectRatioX = config.screenWidthDp, aspectRatioY = config.screenHeightDp, outputCompressQuality = 95,
        )))
    }

    // ---- export / import
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, vm.settings, vm.ignoredHosts) }.exceptionOrNull() }
            if (err == null) vm.showAlert("Success", "Data exported successfully!") else vm.showAlert("Export Failed", err.message ?: "Unknown error occurred")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DataTransfer.import(ctx, uri) } }
            r.onSuccess { data ->
                vm.showAlert("Restore Settings?", "This will replace your current settings and home page background. Continue?", listOf(
                    AlertButton("Cancel", "cancel"),
                    AlertButton("Restore") {
                        vm.applyImport(data.settings, data.ignoredHosts)
                        scope.launch { kotlinx.coroutines.delay(300); vm.showAlert("Success", "Settings restored successfully.") }
                    },
                ))
            }.onFailure { e ->
                if (e is ImportError) vm.showAlert(e.title, e.message ?: "") else vm.showAlert("Import Failed", "Could not parse file. ${e.message ?: ""}")
            }
        }
    }

    val groups = listOf(
        SGroup("Colors", "colors", listOf(
            SRow("Theme") { ChipsRow("routine", "Theme", listOf("light" to "Light", "dark" to "Dark", "system" to "System"), s.themeMode) { set { copy(themeMode = it) } } },
            SRow("Accent Color") { AccentRow(vm) },
        )),
        SGroup("Interface", "interface", listOf(
            SRow("Font Size") { StepperRow("format_size", "Font Size", s.fontScale, 0.8f, 1.2f) { set { copy(fontScale = it) } } },
            SRow("Rounded Font") { SwitchRow("font_download", "Rounded Font", s.roundedFont) { set { copy(roundedFont = it) } } },
            SRow("Corners") { ChipsRow("rounded_corner", "Corners", listOf("0" to "Square", "12" to "Soft", "28" to "Round"), s.cornerRadius.toString()) { set { copy(cornerRadius = it.toInt()) } } },
            SRow("Spacing") { ChipsRow("density_medium", "Spacing", listOf("compact" to "Compact", "normal" to "Normal", "airy" to "Airy"), s.uiPadding) { set { copy(uiPadding = it) } } },
            SRow("Status Bar") { SwitchRow("battery_charging_full", "Status Bar", s.showStatusBar) { set { copy(showStatusBar = it) } } },
            SRow("Expand Menus") { SwitchRow("open_in_full", "Expand Menus", s.expandMenus) { set { copy(expandMenus = it) } } },
        )),
        SGroup("Pill", "pill", listOf(
            SRow("Size") { ChipsRow("crop_free", "Size", listOf("60" to "Thin", "70" to "Normal", "80" to "Tall"), s.pillHeight.toString()) { set { copy(pillHeight = it.toInt()) } } },
            SRow("Loading Bar") { ChipsRow("hourglass_empty", "Loading Bar", listOf("ltr" to "Standard", "center" to "Center Out", "none" to "Hidden"), s.progressBarMode) { set { copy(progressBarMode = it) } } },
            SRow("Pin Favorites") { SwitchRow("star", "Pin Favorites", s.showFavoritesDefault) { set { copy(showFavoritesDefault = it) } } },
            SRow("Expand Searches") { SwitchRow("list", "Expand Searches", s.recentSearchesExpanded) { set { copy(recentSearchesExpanded = it) } } },
            SRow("Reorder Icons") { ReorderIconsRow(vm) },
        )),
        SGroup("Home Page", "homepage", listOfNotNull(
            SRow("Center Logo") { ChipsRow("crop_free", "Logo", listOf("None", "Static", "Fidget").map { it to it }, s.homeLogoType) { set { copy(homeLogoType = it) } } },
            SRow("Clock") { ChipsRow("history", "Clock", listOf("None", "12h", "24h").map { it to it }, s.homeClockType) { set { copy(homeClockType = it) } } },
            SRow("Date") { ChipsRow("calendar_today", "Date", listOf("None", "Above", "Below").map { it to it }, s.homeDateType) { set { copy(homeDateType = it) } } },
            SRow("Shortcut") { ShortcutRow(vm) },
            SRow("Background Image") { BackgroundRow(vm) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            if (s.homeBackgroundImage != null) SRow("Darkness") {
                StepperRow("contrast", "Darkness", s.homeBackgroundOverlayOpacity, 0f, 1f) { set { copy(homeBackgroundOverlayOpacity = it) } }
            } else null,
        )),
        SGroup("Tabs", "tabs", listOfNotNull(
            SRow("Style") { ChipsRow("grid_view", "Style", listOf("rows" to "Rows", "cards" to "Cards"), s.tabViewMode) { set { copy(tabViewMode = it) } } },
            SRow("Site Logo") { SwitchRow("info", "Site Logo", s.showTabLogo) { set { copy(showTabLogo = it) } } },
            if (s.tabViewMode == "cards") SRow("Preview Content") { SwitchRow("image", "Preview Content", s.showTabPreview) { set { copy(showTabPreview = it) } } } else null,
            SRow("Background Refresh") {
                SwitchRow("bolt", "Background Refresh", s.backgroundRefresh) { on -> if (on) vm.confirm = ConfirmData("bgRefresh") else set { copy(backgroundRefresh = false) } }
            },
        )),
        SGroup("History", "history", listOf(
            SRow("Group By") { ChipsRow("layers", "Group By", listOf("Time", "Site").map { it to it }, s.historyGrouping) { set { copy(historyGrouping = it) } } },
            SRow("Load Count") { ChipsRow("list", "Load Count", listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, s.historyLoadCount.toString()) { set { copy(historyLoadCount = it.toInt()) } } },
            SRow("Clear History") { ClearHistoryRow(vm) },
        )),
        SGroup("Bookmarks", "bookmarks", listOf(
            SRow("Site Logo") { SwitchRow("info", "Site Logo", s.showBookmarkIcons) { set { copy(showBookmarkIcons = it) } } },
        )),
        SGroup("Browsing", "browsing", listOf(
            SRow("Search Engine") { SearchEngineRow(vm) },
            SRow("On Startup") { ChipsRow("power_settings_new", "On Startup", listOf("new" to "New Tab", "last" to "Continue Session"), s.startupTabMode) { set { copy(startupTabMode = it) } } },
        )),
        SGroup("Security", "security", listOf(
            SRow("Enable JavaScript") { SwitchRow("code", "Enable JavaScript", s.jsEnabled) { set { copy(jsEnabled = it) } } },
            SRow("Enable Cookies") { SwitchRow("cookie", "Enable Cookies", !s.blockCookies) { set { copy(blockCookies = !it) } } },
            SRow("HTTPS Only") { SwitchRow("lock", "HTTPS Only", s.httpsOnly) { set { copy(httpsOnly = it) } } },
        )),
    )

    val q = search.trim().lowercase()
    fun matches(label: String) = q.isEmpty() || label.lowercase().contains(q)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 100.dp)) {
            if (search.isEmpty() && category == null) {
                Spacer(Modifier.height(16.dp))
                CategoryCard(listOf(Triple("Colors", "palette", "colors"), Triple("Interface", "smartphone", "interface"), Triple("Pill", "toolbar", "pill"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("Tabs", "tab", "tabs"), Triple("History", "history", "history"), Triple("Bookmarks", "bookmarks", "bookmarks"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("Browsing", "search", "browsing"), Triple("Security", "lock", "security"), Triple("Home Page", "home", "homepage"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("App Data", "save", "appData"))) { category = it }
            }
            if (search.isEmpty() && category != null) {
                TextButton(onClick = { category = null }, modifier = Modifier.padding(top = 8.dp)) {
                    Sym("arrow_back", 20f, ui.cs.primary, Modifier.padding(end = 8.dp))
                    Txt("Back", 16f, ui.cs.primary, ui.bold)
                }
            }
            groups.forEach { g ->
                if (search.isEmpty() && category != g.id) return@forEach
                val groupMatch = q.isNotEmpty() && g.title.lowercase().contains(q)
                val rows = g.rows.filter { groupMatch || matches(it.label) }
                if (rows.isEmpty()) return@forEach
                Txt(g.title, 14f, ui.cs.primary, ui.bold, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEachIndexed { i, r ->
                        Box(Modifier.fillMaxWidth().clip(segmentShape(i, rows.size, ui.radius)).background(ui.cs.surfaceContainerHighest)) { r.content() }
                    }
                }
            }
            if (search.isNotEmpty() || category == "appData") {
                Column(Modifier.padding(top = 20.dp)) {
                    Txt("App Data", 14f, ui.cs.primary, ui.bold, Modifier.padding(start = 16.dp, bottom = 8.dp))
                    DataButton("upload", "Export Settings", false) { exporter.launch("settings_${System.currentTimeMillis()}.midata") }
                    DataButton("download", "Import Settings", false) { importer.launch(arrayOf("*/*")) }
                    Spacer(Modifier.height(16.dp))
                    DataButton("restart_alt", "Reset All Settings", true) { vm.confirm = ConfirmData("resetSettings") }
                    DataButton("delete", "Wipe All Data", true) { vm.confirm = ConfirmData("wipeData") }
                }
            }
            // footer
            Box(Modifier.fillMaxWidth().padding(top = 30.dp).height(1.dp).background(ui.cs.onSurfaceVariant.copy(alpha = 0.19f)))
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.alpha(0.7f).touchable { vm.addNewTab("https://jleescy.github.io/mi-browser/user"); closeOverlay() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym("help", 16f * ui.fontScale, ui.cs.primary, Modifier.padding(end = 6.dp))
                    Txt("Help", 14f, ui.cs.primary, ui.semibold)
                }
                Txt("mi browser v${BuildConfig.VERSION_NAME}", 11f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 4.dp).alpha(0.4f))
            }
        }
        SheetSearchField(search, { vm.settingsSearch = it }, "Search Settings...", onFocusSearch, Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp))
    }
}

// ---------------------------------------------------------------- row widgets

@Composable
private fun CategoryCard(items: List<Triple<String, String, String>>, onPick: (String) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, (label, icon, id) ->
            Row(
                Modifier.fillMaxWidth().clip(segmentShape(i, items.size, ui.radius)).background(ui.cs.surfaceContainerHighest).touchable { onPick(id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(end = 16.dp).size(40.dp).clip(CircleShape).background(ui.cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Sym(icon, 22f, ui.cs.onPrimaryContainer)
                }
                Txt(label, 17f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
                Sym("chevron_right", 22f, ui.cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowHeader(icon: String, label: String, trailing: (@Composable () -> Unit)? = null) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 22f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
        Txt(label, 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        trailing?.invoke()
    }
}

private val rowPad = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 15.dp)

@Composable
private fun ChipsRow(icon: String, label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Column(rowPad.padding(vertical = 4.dp)) {
        RowHeader(icon, label)
        ConnectedChoice(options, value, onPick = onPick)
    }
}

@Composable
private fun SwitchRow(icon: String, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalUi.current
    Row(rowPad, verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, 22f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
        Txt(label, 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
        MiSwitch(value, onChange)
    }
}

/** Stepped M3 slider (Font Size, Darkness): 0.1 steps within [min,max]. */
@Composable
private fun StepperRow(icon: String, label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val ui = LocalUi.current
    Column(rowPad.padding(vertical = 4.dp)) {
        RowHeader(icon, label) { Txt("${Math.round(value * 100)}%", 14f, ui.cs.onSurfaceVariant, ui.bold, scaled = false) }
        Slider(
            value = value, valueRange = min..max, steps = Math.round((max - min) / 0.1f) - 1,
            onValueChange = { onChange(Math.round(it * 10f) / 10f) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 15.dp)) {
        Txt("Accent Color", 16f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 15.dp))
        // adaptive (wallpaper colors, Android 12+), gray, blue, green, red, orange, yellow, purple — horizontally scrollable
        val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val list = (if (dynamicOk) listOf(DYNAMIC_ACCENT) else emptyList()) + ACCENTS
        val size = 52.dp
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { hex ->
                    val selected = vm.settings.accentColor == hex
                    val scale by animateFloatAsState(if (selected) 0.86f else 1f, Motion.fastSpatial(), label = "swatch")
                    val isDyn = hex == DYNAMIC_ACCENT
                    Box(
                        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
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
    }
}

@Composable
private fun ReorderIconsRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val icons = mapOf("tabs" to "tab", "bookmarks" to "bookmarks", "history" to "history", "settings" to "settings", "menu" to "menu")
    val order = vm.settings.menuBarOrder.takeIf { it.size == 5 } ?: DEFAULT_MENU_BAR_ORDER
    Column(rowPad.padding(vertical = 5.dp)) {
        RowHeader("reorder", "Reorder Icons")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SortableSlots(order, { it }, 50.dp, 48.dp, horizontal = true, longPressMs = 150, dragScale = 1.1f, dragShadow = false,
                onReorder = { from, to -> vm.updateSettings { copy(menuBarOrder = order.moved(from, to)) } }) { id ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Sym(icons[id] ?: "", 24f, ui.cs.onSurface) }
            }
        }
    }
}

@Composable
private fun ShortcutRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var isShortcutMenuOpen by remember { mutableStateOf(false) }
    val s = vm.settings
    val options = listOf(Triple("New Tab", "newTab", "add"), Triple("Scan", "qr", "qr_code_scanner"), Triple("Saved", "bookmarks", "bookmarks"), Triple("Recent", "history", "history"))
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SwitchRow("apps", "Shortcut", s.showHomeShortcuts) { vm.updateSettings { copy(showHomeShortcuts = it) } }
        if (s.showHomeShortcuts) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.cs.surface))
            Row(Modifier.fillMaxWidth().touchable { isShortcutMenuOpen = !isShortcutMenuOpen }.padding(horizontal = 15.dp, vertical = 12.dp).padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Action", 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.weight(1f))
                Txt(options.first { it.second == s.homeShortcutAction }.first, 14f, ui.cs.onSurface, ui.semibold, Modifier.padding(end = 5.dp))
                Sym(if (isShortcutMenuOpen) "keyboard_arrow_up" else "keyboard_arrow_down", 16f, ui.cs.onSurfaceVariant)
            }
            if (isShortcutMenuOpen) options.forEach { (label, v, icon) ->
                Row(Modifier.fillMaxWidth().touchable { vm.updateSettings { copy(homeShortcutAction = v) }; isShortcutMenuOpen = false }.padding(vertical = 12.dp).padding(start = 47.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sym(icon, 18f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
                    Txt(label, 14f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
                    if (s.homeShortcutAction == v) Sym("check", 18f, ui.cs.primary)
                }
            }
        }
    }
}

@Composable
private fun BackgroundRow(vm: BrowserViewModel, onPick: () -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    val config = LocalConfiguration.current
    Column(rowPad.padding(vertical = 5.dp)) {
        RowHeader("image", "Background Image")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.homeBackgroundImage != null) {
                Button(
                    onClick = { vm.updateSettings { copy(homeBackgroundImage = null) } }, modifier = Modifier.padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ui.cs.errorContainer, contentColor = ui.cs.onErrorContainer),
                ) { Sym("delete", 18f, ui.cs.onErrorContainer, Modifier.padding(end = 8.dp)); Txt("Remove Image", 14f, ui.cs.onErrorContainer, ui.bold) }
            } else {
                FilledTonalButton(onClick = onPick, modifier = Modifier.padding(bottom = 16.dp)) {
                    Sym("image", 18f, ui.cs.onSecondaryContainer, Modifier.padding(end = 8.dp)); Txt("Select from Gallery", 14f, ui.cs.onSecondaryContainer, ui.bold)
                }
            }
            Box(
                Modifier.fillMaxWidth(0.4f).aspectRatio(config.screenWidthDp.toFloat() / config.screenHeightDp).shadow(5.dp, RoundedCornerShape(ui.radius.dp))
                    .clip(RoundedCornerShape(ui.radius.dp)).background(ui.cs.surface).border(1.dp, ui.cs.surface, RoundedCornerShape(ui.radius.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val img = s.homeBackgroundImage
                if (img != null) {
                    NetImage(img, Modifier.fillMaxSize(), ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.homeBackgroundOverlayOpacity)))
                } else Txt("Preview", 10f, ui.cs.onSurfaceVariant, ui.regular, Modifier.alpha(0.5f), scaled = false)
            }
        }
    }
}

@Composable
private fun ClearHistoryRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var isClearHistoryOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(rowPad.touchable { isClearHistoryOpen = !isClearHistoryOpen }, verticalAlignment = Alignment.CenterVertically) {
            Sym("delete", 22f, ui.cs.error, Modifier.padding(end = 10.dp))
            Txt("Clear History", 16f, ui.cs.error, ui.semibold, Modifier.weight(1f))
            Sym(if (isClearHistoryOpen) "keyboard_arrow_up" else "keyboard_arrow_down", 16f, ui.cs.onSurfaceVariant)
        }
        if (isClearHistoryOpen) HISTORY_RANGES.forEach { (label, ms) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.cs.surface))
            Box(Modifier.fillMaxWidth().touchable { vm.confirm = ConfirmData("history", ms, label) }.padding(vertical = 12.dp).padding(start = 40.dp)) {
                Txt(label, 14f, ui.cs.onSurface, ui.semibold)
            }
        }
    }
}

@Composable
private fun SearchEngineRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    var isSearchEngineOpen by remember { mutableStateOf(false) }
    val cur = SEARCH_ENGINES.getOrElse(vm.settings.searchEngineIndex) { SEARCH_ENGINES[0] }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(rowPad.touchable { isSearchEngineOpen = !isSearchEngineOpen }, verticalAlignment = Alignment.CenterVertically) {
            Sym("search", 22f, ui.cs.onSurface, Modifier.padding(end = 10.dp))
            Txt("Search Engine", 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
            EngineIcon(vm.settings.searchEngineIndex, 18f, modifier = Modifier.padding(end = 6.dp))
            Txt(cur.name, 14f, ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(end = 5.dp))
            Sym(if (isSearchEngineOpen) "keyboard_arrow_up" else "keyboard_arrow_down", 16f, ui.cs.onSurfaceVariant)
        }
        if (isSearchEngineOpen) Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.cs.surface))
            SEARCH_ENGINES.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().touchable { vm.updateSettings { copy(searchEngineIndex = i) }; isSearchEngineOpen = false }.padding(vertical = 12.dp).padding(start = 40.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    EngineIcon(i, 20f, modifier = Modifier.padding(end = 12.dp))
                    Txt(e.name, 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
                    if (vm.settings.searchEngineIndex == i) Sym("check", 18f, ui.cs.primary)
                }
            }
        }
    }
}

@Composable
private fun DataButton(icon: String, label: String, destructive: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val content = if (destructive) ui.cs.onErrorContainer else ui.cs.onSecondaryContainer
    val inner: @Composable RowScope.() -> Unit = { Sym(icon, 20f, content, Modifier.padding(end = 10.dp)); Txt(label, 16f, content, ui.bold) }
    val m = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(56.dp)
    if (destructive) Button(onClick, m, colors = ButtonDefaults.buttonColors(containerColor = ui.cs.errorContainer, contentColor = content), content = inner)
    else FilledTonalButton(onClick, m, content = inner)
}

/** Expressive segmented list: large outer corners, small inner corners, 2dp gaps. */
fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}

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
            SRow("Theme") { ChipsRow("eye-outline", "Theme", listOf("light" to "Light", "dark" to "Dark", "adaptive" to "Adaptive"), s.themeMode) { set { copy(themeMode = it) } } },
            SRow("Accent Color") { AccentRow(vm) },
        )),
        SGroup("Interface", "interface", listOf(
            SRow("Font Size") { StepperRow("text-outline", "Font Size", s.fontScale, 0.8f, 1.2f, (s.fontScale - 0.8f) / 0.4f) { set { copy(fontScale = it) } } },
            SRow("Font Weight") { ChipsRow("barbell-outline", "Font Weight", listOf("light" to "Light", "normal" to "Normal", "bold" to "Bold"), s.fontWeight, spaceAround = true) { set { copy(fontWeight = it) } } },
            SRow("Corners") { ChipsRow("shapes-outline", "Corners", listOf("0" to "Square", "10" to "Semi", "22" to "Round"), s.cornerRadius.toString(), spaceAround = true) { set { copy(cornerRadius = it.toInt()) } } },
            SRow("Spacing") { ChipsRow("resize-outline", "Spacing", listOf("compact" to "Compact", "normal" to "Normal", "airy" to "Airy"), s.uiPadding) { set { copy(uiPadding = it) } } },
            SRow("Status Bar") { SwitchRow("battery-charging-outline", "Status Bar", s.showStatusBar) { set { copy(showStatusBar = it) } } },
            SRow("Expand Menus") { SwitchRow("expand-outline", "Expand Menus", s.expandMenus) { set { copy(expandMenus = it) } } },
        )),
        SGroup("Pill", "pill", listOf(
            SRow("Size") { ChipsRow("scan-outline", "Size", listOf("60" to "Thin", "70" to "Normal", "80" to "Tall"), s.pillHeight.toString(), spaceAround = true) { set { copy(pillHeight = it.toInt()) } } },
            SRow("Loading Bar") { ChipsRow("hourglass-outline", "Loading Bar", listOf("ltr" to "Standard", "center" to "Center Out", "none" to "Hidden"), s.progressBarMode) { set { copy(progressBarMode = it) } } },
            SRow("Pin Favorites") { SwitchRow("star-outline", "Pin Favorites", s.showFavoritesDefault) { set { copy(showFavoritesDefault = it) } } },
            SRow("Expand Searches") { SwitchRow("list-outline", "Expand Searches", s.recentSearchesExpanded) { set { copy(recentSearchesExpanded = it) } } },
            SRow("Reorder Icons") { ReorderIconsRow(vm) },
        )),
        SGroup("Home Page", "homepage", listOfNotNull(
            SRow("Center Logo") { ChipsRow("scan-outline", "Logo", listOf("None", "Static", "Fidget").map { it to it }, s.homeLogoType) { set { copy(homeLogoType = it) } } },
            SRow("Clock") { ChipsRow("time-outline", "Clock", listOf("None", "12h", "24h").map { it to it }, s.homeClockType) { set { copy(homeClockType = it) } } },
            SRow("Date") { ChipsRow("calendar-outline", "Date", listOf("None", "Above", "Below").map { it to it }, s.homeDateType) { set { copy(homeDateType = it) } } },
            SRow("Shortcut") { ShortcutRow(vm) },
            SRow("Background Image") { BackgroundRow(vm) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            if (s.homeBackgroundImage != null) SRow("Darkness") {
                StepperRow("contrast-outline", "Darkness", s.homeBackgroundOverlayOpacity, 0f, 1f, s.homeBackgroundOverlayOpacity) { set { copy(homeBackgroundOverlayOpacity = it) } }
            } else null,
        )),
        SGroup("Tabs", "tabs", listOfNotNull(
            SRow("Style") { ChipsRow("grid-outline", "Style", listOf("rows" to "Rows", "cards" to "Cards"), s.tabViewMode) { set { copy(tabViewMode = it) } } },
            SRow("Site Logo") { SwitchRow("information-circle-outline", "Site Logo", s.showTabLogo) { set { copy(showTabLogo = it) } } },
            if (s.tabViewMode == "cards") SRow("Preview Content") { SwitchRow("image-outline", "Preview Content", s.showTabPreview) { set { copy(showTabPreview = it) } } } else null,
            SRow("Background Refresh") {
                SwitchRow("flash-outline", "Background Refresh", s.backgroundRefresh) { on -> if (on) vm.confirm = ConfirmData("bgRefresh") else set { copy(backgroundRefresh = false) } }
            },
        )),
        SGroup("History", "history", listOf(
            SRow("Group By") { ChipsRow("layers-outline", "Group By", listOf("Time", "Site").map { it to it }, s.historyGrouping) { set { copy(historyGrouping = it) } } },
            SRow("Load Count") { ChipsRow("list-outline", "Load Count", listOf(10, 25, 50, 100).map { it.toString() to it.toString() }, s.historyLoadCount.toString()) { set { copy(historyLoadCount = it.toInt()) } } },
            SRow("Clear History") { ClearHistoryRow(vm) },
        )),
        SGroup("Bookmarks", "bookmarks", listOf(
            SRow("Site Logo") { SwitchRow("information-circle-outline", "Site Logo", s.showBookmarkIcons) { set { copy(showBookmarkIcons = it) } } },
        )),
        SGroup("Browsing", "browsing", listOf(
            SRow("Search Engine") { SearchEngineRow(vm) },
            SRow("On Startup") { ChipsRow("power-outline", "On Startup", listOf("new" to "New Tab", "last" to "Continue Session"), s.startupTabMode) { set { copy(startupTabMode = it) } } },
        )),
        SGroup("Security", "security", listOf(
            SRow("Enable JavaScript") { SwitchRow("code-slash-outline", "Enable JavaScript", s.jsEnabled) { set { copy(jsEnabled = it) } } },
            SRow("Enable Cookies") { SwitchRow("cafe-outline", "Enable Cookies", !s.blockCookies) { set { copy(blockCookies = !it) } } },
            SRow("HTTPS Only") { SwitchRow("lock-closed-outline", "HTTPS Only", s.httpsOnly) { set { copy(httpsOnly = it) } } },
        )),
    )

    val q = search.trim().lowercase()
    fun matches(label: String) = q.isEmpty() || label.lowercase().contains(q)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 100.dp)) {
            if (search.isEmpty() && category == null) {
                Spacer(Modifier.height(16.dp))
                CategoryCard(listOf(Triple("Colors", "color-palette-outline", "colors"), Triple("Interface", "phone-portrait-outline", "interface"), Triple("Pill", "unlink-outline", "pill"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("Tabs", "copy-outline", "tabs"), Triple("History", "time-outline", "history"), Triple("Bookmarks", "bookmarks-outline", "bookmarks"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("Browsing", "search-outline", "browsing"), Triple("Security", "lock-closed-outline", "security"), Triple("Home Page", "home-outline", "homepage"))) { category = it }
                Spacer(Modifier.height(20.dp))
                CategoryCard(listOf(Triple("App Data", "save-outline", "appData"))) { category = it }
            }
            if (search.isEmpty() && category != null) {
                Row(Modifier.padding(top = 12.dp, bottom = 16.dp).touchable { category = null }, verticalAlignment = Alignment.CenterVertically) {
                    Ion("chevron-back", 24f, ui.accent)
                    Txt("Back", 16f, ui.accent, ui.bold, Modifier.padding(start = 5.dp))
                }
            }
            groups.forEach { g ->
                if (search.isEmpty() && category != g.id) return@forEach
                val groupMatch = q.isNotEmpty() && g.title.lowercase().contains(q)
                val rows = g.rows.filter { groupMatch || matches(it.label) }
                if (rows.isEmpty()) return@forEach
                Txt(g.title, 14f, ui.textSec, ui.bold, Modifier.padding(top = 20.dp, bottom = 10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(ui.radius.dp)).background(ui.card).animateContentSize()) {
                    rows.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
                        r.content()
                    }
                }
            }
            if (search.isNotEmpty() || category == "appData") {
                Column(Modifier.padding(top = 20.dp)) {
                    Txt("App Data", 14f, ui.textSec, ui.bold, Modifier.padding(bottom = 10.dp))
                    DataButton("cloud-upload-outline", "Export Settings", ui.card, ui.text) { exporter.launch("settings_${System.currentTimeMillis()}.midata") }
                    DataButton("cloud-download-outline", "Import Settings", ui.card, ui.text) { importer.launch(arrayOf("*/*")) }
                    Spacer(Modifier.height(20.dp))
                    DataButton("refresh-outline", "Reset All Settings", Red, Color.White) { vm.confirm = ConfirmData("resetSettings") }
                    DataButton("trash-outline", "Wipe All Data", Red, Color.White) { vm.confirm = ConfirmData("wipeData") }
                }
            }
            // footer
            Box(Modifier.fillMaxWidth().padding(top = 30.dp).height(1.dp).background(ui.textSec.copy(alpha = 0.19f)))
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.alpha(0.7f).touchable { vm.addNewTab("https://jleescy.github.io/mi-browser/user"); closeOverlay() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ion("help-circle-outline", 16f * ui.fontScale, ui.accent, Modifier.padding(end = 6.dp))
                    Txt("Help", 14f, ui.accent, ui.semibold)
                }
                Txt("mi browser v${BuildConfig.VERSION_NAME}", 11f, ui.textSec, ui.regular, Modifier.padding(top = 4.dp).alpha(0.4f))
            }
        }
        SheetSearchField(search, { vm.settingsSearch = it }, "Search Settings...", onFocusSearch, Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp))
    }
}

// ---------------------------------------------------------------- row widgets

@Composable
private fun CategoryCard(items: List<Triple<String, String, String>>, onPick: (String) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(ui.radius.dp)).background(ui.card)) {
        items.forEachIndexed { i, (label, icon, id) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
            Row(Modifier.fillMaxWidth().touchable { onPick(id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ion(icon, 20f, ui.text, Modifier.padding(end = 16.dp))
                Txt(label, 16f, ui.text, ui.bold)
            }
        }
    }
}

@Composable
private fun RowHeader(icon: String, label: String, trailing: (@Composable () -> Unit)? = null) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Ion(icon, 22f, ui.text, Modifier.padding(end = 10.dp))
        Txt(label, 16f, ui.text, ui.semibold, Modifier.weight(1f))
        trailing?.invoke()
    }
}

private val rowPad = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 15.dp)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier.clip(RoundedCornerShape(ui.radius.dp)).background(if (selected) ui.accent else Color.Transparent).touchable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 20.dp),
    ) { Txt(label, 12f, if (selected) Color.White else ui.text, ui.bold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsRow(icon: String, label: String, options: List<Pair<String, String>>, value: String, spaceAround: Boolean = false, onPick: (String) -> Unit) {
    Column(rowPad.padding(vertical = 5.dp)) {
        RowHeader(icon, label)
        if (spaceAround) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { options.forEach { (v, l) -> Chip(l, v == value) { onPick(v) } } }
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { options.forEach { (v, l) -> Chip(l, v == value) { onPick(v) } } }
    }
}

@Composable
private fun SwitchRow(icon: String, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalUi.current
    Row(rowPad, verticalAlignment = Alignment.CenterVertically) {
        Ion(icon, 22f, ui.text, Modifier.padding(end = 10.dp))
        Txt(label, 16f, ui.text, ui.semibold, Modifier.weight(1f))
        MiSwitch(value, onChange)
    }
}

/** −/+ stepper with a progress bar (Font Size, Darkness): step 0.1 within [min,max]. */
@Composable
private fun StepperRow(icon: String, label: String, value: Float, min: Float, max: Float, fraction: Float, onChange: (Float) -> Unit) {
    val ui = LocalUi.current
    fun round(v: Float) = Math.round(v * 10f) / 10f
    Column(rowPad.padding(vertical = 5.dp)) {
        RowHeader(icon, label) { Txt("${Math.round(value * 100)}%", 14f, ui.textSec, ui.bold, scaled = false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.touchable { onChange(round(maxOf(min, value - 0.1f))) }) { Ion("remove-circle-outline", 28f, ui.textSec) }
            Box(Modifier.weight(1f).padding(horizontal = 15.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ui.bg)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(ui.accent, RoundedCornerShape(2.dp)))
            }
            Box(Modifier.touchable { onChange(round(minOf(max, value + 0.1f))) }) { Ion("add-circle-outline", 28f, ui.textSec) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 15.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Accent Color", 16f, ui.text, ui.semibold, Modifier.weight(1f))
            Box(Modifier.touchable { vm.isAccentExpanded = !vm.isAccentExpanded }.padding(5.dp)) {
                Txt(if (vm.isAccentExpanded) "Show Less" else "Show More", 12f, ui.accent, ui.bold)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize()) {
            val size = maxWidth * 0.14f
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (vm.isAccentExpanded) ACCENTS else ACCENTS.take(6)).forEach { hex ->
                    val selected = vm.settings.accentColor == hex
                    Box(
                        Modifier.size(size).graphicsLayer { val sc = if (selected) 0.9f else 1f; scaleX = sc; scaleY = sc }
                            .clip(RoundedCornerShape(10.dp)).background(c(hexColor(hex)))
                            .then(if (selected) Modifier.border(3.dp, ui.text, RoundedCornerShape(10.dp)) else Modifier)
                            .touchable { vm.updateSettings { copy(accentColor = hex) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderIconsRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val icons = mapOf("tabs" to "copy-outline", "bookmarks" to "bookmarks-outline", "history" to "time-outline", "settings" to "settings-outline", "menu" to "menu-outline")
    val order = vm.settings.menuBarOrder.takeIf { it.size == 5 } ?: DEFAULT_MENU_BAR_ORDER
    Column(rowPad.padding(vertical = 5.dp)) {
        RowHeader("reorder-four-outline", "Reorder Icons")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SortableSlots(order, { it }, 50.dp, 48.dp, horizontal = true, longPressMs = 150, dragScale = 1.1f, dragShadow = false,
                onReorder = { from, to -> vm.updateSettings { copy(menuBarOrder = order.moved(from, to)) } }) { id ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Ion(icons[id] ?: "", 24f, ui.text) }
            }
        }
    }
}

@Composable
private fun ShortcutRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val s = vm.settings
    val options = listOf(Triple("New Tab", "newTab", "add"), Triple("Scan", "qr", "qr-code-outline"), Triple("Saved", "bookmarks", "bookmarks-outline"), Triple("Recent", "history", "time-outline"))
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SwitchRow("apps-outline", "Shortcut", s.showHomeShortcuts) { vm.updateSettings { copy(showHomeShortcuts = it) } }
        if (s.showHomeShortcuts) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
            Row(Modifier.fillMaxWidth().touchable { vm.isShortcutMenuOpen = !vm.isShortcutMenuOpen }.padding(horizontal = 15.dp, vertical = 12.dp).padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Action", 14f, ui.textSec, ui.semibold, Modifier.weight(1f))
                Txt(options.first { it.second == s.homeShortcutAction }.first, 14f, ui.text, ui.semibold, Modifier.padding(end = 5.dp))
                Ion(if (vm.isShortcutMenuOpen) "chevron-up" else "chevron-down", 16f, ui.textSec)
            }
            if (vm.isShortcutMenuOpen) options.forEach { (label, v, icon) ->
                Row(Modifier.fillMaxWidth().touchable { vm.updateSettings { copy(homeShortcutAction = v) }; vm.isShortcutMenuOpen = false }.padding(vertical = 12.dp).padding(start = 47.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ion(icon, 18f, ui.text, Modifier.padding(end = 10.dp))
                    Txt(label, 14f, ui.text, ui.semibold, Modifier.weight(1f))
                    if (s.homeShortcutAction == v) Ion("checkmark", 18f, ui.accent)
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
        RowHeader("image-outline", "Background Image")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.homeBackgroundImage != null) {
                Box(Modifier.padding(bottom = 15.dp).background(Red, RoundedCornerShape(ui.radius.dp)).touchable { vm.updateSettings { copy(homeBackgroundImage = null) } }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Txt("Remove Image", 12f, Color.White, ui.bold)
                }
            } else {
                Row(Modifier.padding(bottom = 15.dp).background(ui.card, RoundedCornerShape(ui.radius.dp)).border(1.dp, ui.bg, RoundedCornerShape(ui.radius.dp)).touchable(onClick = onPick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ion("image-outline", 18f, ui.text, Modifier.padding(end = 8.dp))
                    Txt("Select from Gallery", 12f, ui.text, ui.bold)
                }
            }
            Box(
                Modifier.fillMaxWidth(0.4f).aspectRatio(config.screenWidthDp.toFloat() / config.screenHeightDp).shadow(5.dp, RoundedCornerShape(ui.radius.dp))
                    .clip(RoundedCornerShape(ui.radius.dp)).background(ui.bg).border(1.dp, ui.bg, RoundedCornerShape(ui.radius.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val img = s.homeBackgroundImage
                if (img != null) {
                    NetImage(img, Modifier.fillMaxSize(), ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.homeBackgroundOverlayOpacity)))
                } else Txt("Preview", 10f, ui.textSec, ui.regular, Modifier.alpha(0.5f), scaled = false)
            }
        }
    }
}

@Composable
private fun ClearHistoryRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(rowPad.touchable { vm.isClearHistoryOpen = !vm.isClearHistoryOpen }, verticalAlignment = Alignment.CenterVertically) {
            Ion("trash-outline", 22f, Red, Modifier.padding(end = 10.dp))
            Txt("Clear History", 16f, Red, ui.semibold, Modifier.weight(1f))
            Ion(if (vm.isClearHistoryOpen) "chevron-up" else "chevron-down", 16f, ui.textSec)
        }
        if (vm.isClearHistoryOpen) HISTORY_RANGES.forEach { (label, ms) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
            Box(Modifier.fillMaxWidth().touchable { vm.confirm = ConfirmData("history", ms, label) }.padding(vertical = 12.dp).padding(start = 40.dp)) {
                Txt(label, 14f, ui.text, ui.semibold)
            }
        }
    }
}

@Composable
private fun SearchEngineRow(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val cur = SEARCH_ENGINES.getOrElse(vm.settings.searchEngineIndex) { SEARCH_ENGINES[0] }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(rowPad.touchable { vm.isSearchEngineOpen = !vm.isSearchEngineOpen }, verticalAlignment = Alignment.CenterVertically) {
            Ion("search-outline", 22f, ui.text, Modifier.padding(end = 10.dp))
            Txt("Search Engine", 16f, ui.text, ui.semibold, Modifier.weight(1f))
            Ion(cur.icon, 18f, ui.text, Modifier.padding(end = 5.dp))
            Txt(cur.name, 14f, ui.textSec, ui.semibold, Modifier.padding(end = 5.dp))
            Ion(if (vm.isSearchEngineOpen) "chevron-up" else "chevron-down", 16f, ui.textSec)
        }
        if (vm.isSearchEngineOpen) Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ui.bg))
            SEARCH_ENGINES.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().touchable { vm.updateSettings { copy(searchEngineIndex = i) }; vm.isSearchEngineOpen = false }.padding(vertical = 12.dp).padding(start = 40.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ion(e.icon, 20f, ui.text, Modifier.padding(end = 10.dp))
                    Txt(e.name, 16f, ui.text, ui.semibold, Modifier.weight(1f))
                    if (vm.settings.searchEngineIndex == i) Ion("checkmark", 18f, ui.accent)
                }
            }
        }
    }
}

@Composable
private fun DataButton(icon: String, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(ui.radius.dp)).background(bg).touchable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Ion(icon, 20f, fg)
        Txt(label, 16f, fg, ui.bold)
    }
}

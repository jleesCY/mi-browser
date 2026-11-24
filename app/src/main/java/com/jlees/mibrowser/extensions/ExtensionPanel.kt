// An installed extension's panel, sliding up from the bottom like a followed product's (ui/common/ExpandingPanel): at
// first what it is (icon, name, version, how well it works in mi, what it does); scrolling up grows it to the whole
// screen with why it got its rating, its switches, what it can reach, and managing it. Options, Remove and Done stay
// pinned at the bottom. Opened from Library › Extensions, or by holding its button in the tab menu.
package com.jlees.mibrowser.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.SwitchItem
import com.jlees.mibrowser.ui.settings.SegmentedGroup

@Composable
fun BoxScope.ExtensionPanel(vm: BrowserViewModel) {
    SlidingPanel(ExtensionFlow.opened?.let { vm.extensions[it] }) { e, hidden ->
        val close = { ExtensionFlow.opened = null }
        ExpandingPanel(e.id, hidden, close, top = { Top(e) }, rest = { Rest(vm, e) }) { m -> Bar(vm, e, close, m) }
    }
}

/** What it is, and how well it works in mi. */
@Composable
private fun Top(e: InstalledExtension) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { ExtensionIcon(e.id, 38f) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Txt(e.name, 19f, ui.cs.onSurface, ui.bold, maxLines = 2)
            // how much room it takes: its files, added up off the main thread
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val size by produceState<String?>(null, e.id, e.version) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ExtensionFlow.engine?.dirOf(e.id)?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() }
                }?.let { android.text.format.Formatter.formatShortFileSize(ctx, it) }
            }
            Txt(listOfNotNull("Version ${e.version}", size, if (e.enabled) null else "Off").joinToString(" · "), 13f, ui.cs.onSurfaceVariant, ui.regular)
        }
    }
    Box(Modifier.padding(top = 14.dp)) { if (e.verified) VerifiedBadge() else FitBadge(e.fit) }
    WebViewWarning(verifiedFor(storeSource(e.source)))
    if (e.description.isNotBlank()) Txt(e.description, 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 12.dp), lineHeight = 20f, maxLines = 4)
    Spacer(Modifier.height(16.dp))
}

/** Reached by scrolling up: why it got its rating, its switches, what it can reach, keeping it up to date and its data. */
@Composable
private fun Rest(vm: BrowserViewModel, e: InstalledExtension) {
    val ui = LocalUi.current
    val id = e.id
    val m = ExtensionFlow.engine?.infos?.get(id)?.manifest
    if (e.reasons.isNotEmpty()) SegmentedGroup("How It Works in mi", listOf { Column(Modifier.fillMaxWidth().padding(16.dp)) { FitReasons(e.fit, e.reasons, e.serious) } })
    SegmentedGroup("Settings", listOfNotNull(
        { SwitchItem("power_settings_new", "On", "Run this extension", e.enabled) { vm.setExtensionEnabled(id, it) } },
        { SwitchItem("incognito", "Allow in Incognito", "Run it in incognito tabs too", e.incognito) { vm.setExtensionIncognito(id, it) } },
        if (m?.hasButton == true) ({ SwitchItem("more_vert", "Show in Tab Menu", "Its button appears in the tab menu", e.showButton) { vm.setExtensionButton(id, it) } }) else null,
        if (m?.contentScripts?.isNotEmpty() == true) ({ OffSitesItem(vm, e) }) else null,
    ))
    if (m != null) SegmentedGroup("Access", listOfNotNull(
        reachOf(m)?.let { reach -> { Item("shield", "Sites", reach) } },
        if (m.permissions.isNotEmpty()) ({
            ExpandItem("key", "Permissions", null, m.permissions.size.toString()) {
                m.permissions.forEach { p -> Txt(permissionWords(p), 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) }
            }
        }) else null,
    ))
    SegmentedGroup("Manage", listOfNotNull(
        if (e.source != null) ({ Item("refresh", "Check for Update", "Get the newest version from the store", onClick = { vm.checkExtensionUpdate(id) }) }) else null,
        e.source?.let { src -> { Item("open_in_new", "View in Store", null, onClick = {
            ExtensionFlow.opened = null; vm.addNewTab(src); vm.switcherVisible = false; vm.openView(com.jlees.mibrowser.Sheet.NONE)
        }) } },
        { Item("delete_sweep", "Clear Data", "Forget everything it has stored", onClick = {
            vm.showAlert("Clear ${e.name}'s Data?", "Its settings and everything it has saved will be forgotten, as if it were just added.", listOf(
                AlertButton("Cancel", ButtonStyle.CANCEL),
                AlertButton("Clear Data", ButtonStyle.DESTRUCTIVE) { vm.clearExtensionData(id) },
            ))
        }) },
    ))
}

/** Pinned at the bottom: its Options across the whole width (if it has any), Remove and Done side by side under it. */
@Composable
private fun Bar(vm: BrowserViewModel, e: InstalledExtension, close: () -> Unit, modifier: Modifier) {
    val ui = LocalUi.current
    val options = ExtensionFlow.engine?.infos?.get(e.id)?.manifest?.optionsPage
    Column(modifier.fillMaxWidth().background(ui.cs.surfaceContainerLow).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (options != null) Button({ close(); ExtensionFlow.page = OpenPage(e.id, options, ExtensionEngine.Kind.OPTIONS) }, Modifier.fillMaxWidth().height(52.dp)) {
            Sym("settings", 18f, ui.cs.onPrimary, Modifier.padding(end = 8.dp)); Txt("Options", 16f, ui.cs.onPrimary, ui.bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                { close(); vm.removeExtension(e.id) },
                Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = ui.cs.errorContainer, contentColor = ui.cs.onErrorContainer),
            ) { Txt("Remove", 15f, ui.cs.onErrorContainer, ui.bold) }
            FilledTonalButton(close, Modifier.weight(1f).height(48.dp)) { Txt("Done", 15f, ui.cs.onSecondaryContainer, ui.bold) }
        }
    }
}

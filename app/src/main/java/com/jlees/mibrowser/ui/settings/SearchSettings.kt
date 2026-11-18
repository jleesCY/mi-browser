// Settings › Search: the search engine (or your own), and search shortcuts (yours, and the ones mi comes with).
package com.jlees.mibrowser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.search.AddBangDialog
import com.jlees.mibrowser.search.BANGS
import com.jlees.mibrowser.search.SEARCH_ENGINES
import com.jlees.mibrowser.search.SEARCH_ENGINE_BANG
import com.jlees.mibrowser.search.SearchEngine
import com.jlees.mibrowser.search.isValidSearchTemplate
import com.jlees.mibrowser.search.searchEngine
import com.jlees.mibrowser.ui.common.*

/** Settings › Search. (What shows above the Pill as you type is in Settings › Pill.) */
fun searchSection(vm: BrowserViewModel) =
    SettingsSection("search", "Search", "search", "Search engine and shortcuts", listOf(
        SettingsRow("Search Engine", "google bing duckduckgo brave ecosia yahoo") { SearchEngineSetting(vm) },
        SettingsRow("Your Shortcuts", "bang custom add search shortcut help what") { CustomBangsItem(vm) },
        SettingsRow("Built-in Shortcuts", "bang se search engine " + BANGS.values.joinToString(" ") { it.name.lowercase() }) { BuiltInBangsItem(vm) },
    ))

/** The search engine row in Settings → Search, with the custom engine dialog. */
@Composable
private fun SearchEngineSetting(vm: BrowserViewModel) {
    val s = vm.settings
    var editing by remember { mutableStateOf(false) }
    ExpandItem("search", "Search Engine", "The engine used for searches and suggestions", vm.searchEngine.name) { close ->
        SEARCH_ENGINES.forEachIndexed { i, e ->
            OptionRow(e.name, s.searchEngineIndex == i, leading = { EngineIcon(e, 20f) }) { vm.updateSettings { copy(searchEngineIndex = i) }; close() }
        }
        // your own engine: picking it (or "Custom…") opens its editor
        val custom = isValidSearchTemplate(s.customSearchUrl)
        OptionRow(if (custom) s.customSearchName.ifBlank { "Custom" } else "Custom…", s.searchEngineIndex == SEARCH_ENGINES.size,
            leading = { EngineIcon(vm.searchEngine.takeIf { s.searchEngineIndex == SEARCH_ENGINES.size } ?: SearchEngine("Custom", s.customSearchUrl), 20f) }) {
            if (custom && s.searchEngineIndex != SEARCH_ENGINES.size) { vm.updateSettings { copy(searchEngineIndex = SEARCH_ENGINES.size) }; close() } else editing = true
        }
    }
    CustomEngineDialog(vm, editing) { editing = false }
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
        if (onDelete != null) IconButton(onClick = onDelete) { Sym("delete", 20f, ui.cs.onSurfaceVariant, label = "Delete shortcut") }
    }
}

/** Your own shortcuts, with a button to add one. */
@Composable
private fun CustomBangsItem(vm: BrowserViewModel) {
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // the "i" explains shortcuts, for anyone who hasn't met "!w" before
        Item("add", "Create a new shortcut", onClick = { adding = true }, trailing = {
            IconButton(onClick = { explainShortcuts(vm) }) { Sym("info", 22f, LocalUi.current.cs.onSurfaceVariant, label = "What are shortcuts?") }
        })
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
            LabeledField("Name", name, { name = it }, Modifier.padding(bottom = 15.dp), placeholder = "e.g. Startpage")
            LabeledField("Search address", url, { url = it }, uri = true, placeholder = "https://example.com/search?q=%s")
            Txt(
                if (url.length > 8 && !valid) "Enter a web address starting with https://" else "Put %s where your search should go. Suggestions aren't available for custom engines.",
                12f, if (url.length > 8 && !valid) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), lineHeight = 16f,
            )
        }
    }
}

/** What search shortcuts are, with an example (the "i" beside Add a shortcut). */
private fun explainShortcuts(vm: BrowserViewModel) = vm.showAlert(
    "Search shortcuts",
    "Use shortcuts to send searches directly to the shortcut's site, rather than your selected search engine.\n\n" +
        "To add your own, search a site as usual, then open the tab menu and select \"Add shortcut\".\n\n" +
        "Or manually add one by tapping \"Create a new shortcut\" here and paste the address of a site's search results page.",
)


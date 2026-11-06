// Settings → Search: the search engine (or your own), suggestions, and search shortcuts.
package com.jlees.mibrowser.search

import com.jlees.mibrowser.ui.settings.SwitchItem

import com.jlees.mibrowser.ui.settings.ExpandItem
import com.jlees.mibrowser.ui.settings.Item
import com.jlees.mibrowser.ui.settings.OptionRow
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection

import com.jlees.mibrowser.search.SearchEngine
import com.jlees.mibrowser.search.searchEngine
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.search.SEARCH_ENGINES
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.search.isValidBangKey
import com.jlees.mibrowser.search.BANGS
import com.jlees.mibrowser.search.SEARCH_ENGINE_BANG
import com.jlees.mibrowser.search.isValidSearchTemplate
import com.jlees.mibrowser.ui.common.*

/** Settings → Search: the engine, suggestions, and search shortcuts (yours and the built-ins). */
fun searchSection(vm: BrowserViewModel) =
    SettingsSection("search", "Search", "search", "Search engine, suggestions, !shortcuts", listOf(
        SettingsRow("Search Engine", "google bing duckduckgo brave ecosia yahoo") { SearchEngineSetting(vm) },
        SettingsRow("Search Suggestions", "autocomplete") {
                SwitchItem("search", "Search Suggestions", "Suggestions from your search engine while the search drawer is open", vm.settings.searchSuggestions) { vm.updateSettings { copy(searchSuggestions = it) } }
            },
        SettingsRow("Suggestions in Incognito", "autocomplete private incognito") {
                SwitchItem("incognito", "Suggestions in Incognito", "Off: what you type in an incognito tab isn't sent to your search engine until you search", vm.settings.incognitoSuggestions) { vm.updateSettings { copy(incognitoSuggestions = it) } }
            },
        SettingsRow("Your Shortcuts", "bang custom add search shortcut") { CustomBangsItem(vm) },
        SettingsRow("Built-in Shortcuts", "bang se search engine " + BANGS.values.joinToString(" ") { it.name.lowercase() }) { BuiltInBangsItem(vm) },
    ))

/** The search engine row in Settings → Search, with the custom engine dialog. */
@Composable
fun SearchEngineSetting(vm: BrowserViewModel) {
    val s = vm.settings
    var editing by remember { mutableStateOf(false) }
    ExpandItem("search", "Search Engine", "Used for searches and suggestions", vm.searchEngine.name) { close ->
        SEARCH_ENGINES.forEachIndexed { i, e ->
            OptionRow(e.name, s.searchEngineIndex == i, leading = { EngineIcon(e, 20f) }) { vm.updateSettings { copy(searchEngineIndex = i) }; close() }
        }
        // your own engine: picking it (or "Custom…") opens its editor
        val custom = isValidSearchTemplate(s.customSearchUrl)
        OptionRow(if (custom) s.customSearchName.ifBlank { "Custom" } else "Custom…", s.searchEngineIndex == SEARCH_ENGINES.size,
            leading = { EngineIcon(vm.searchEngine.takeIf { s.searchEngineIndex == SEARCH_ENGINES.size } ?: com.jlees.mibrowser.search.SearchEngine("Custom", s.customSearchUrl), 20f) }) {
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
            LabeledField("Shortcut", key, { key = it }, Modifier.padding(bottom = 4.dp), placeholder = "e.g. ebay")
            Txt(
                when { k.isNotEmpty() && !keyOk -> "Use up to 12 letters or numbers"; k in BANGS -> "Replaces the built-in !$k (${BANGS[k]!!.name})"; k == SEARCH_ENGINE_BANG -> "Replaces the built-in !se (your search engine)"; k.isNotEmpty() -> "You'll type !$k"; else -> " " },
                12f, if (k.isNotEmpty() && !keyOk) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp),
            )
            LabeledField("Search address", url, { url = it }, uri = true, placeholder = "https://www.ebay.com/sch/i.html?_nkw=%s")
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
            LabeledField("Name", name, { name = it }, Modifier.padding(bottom = 15.dp), placeholder = "e.g. Startpage")
            LabeledField("Search address", url, { url = it }, uri = true, placeholder = "https://example.com/search?q=%s")
            Txt(
                if (url.length > 8 && !valid) "Enter a web address starting with https://" else "Put %s where your search should go. Suggestions aren't available for custom engines.",
                12f, if (url.length > 8 && !valid) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), lineHeight = 16f,
            )
        }
    }
}


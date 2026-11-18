// The dialog for adding a search shortcut: from Settings › Search (Create a new shortcut), and filled in from a page
// (tab menu › Add shortcut).
package com.jlees.mibrowser.search

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.*

/** A new shortcut: its key and the site's search address (%s = the search). */
@Composable
fun AddBangDialog(vm: BrowserViewModel, visible: Boolean, initialKey: String = "", initialUrl: String = "https://", onClose: () -> Unit) {
    val ui = LocalUi.current
    var key by remember(visible) { mutableStateOf(initialKey) }
    var url by remember(visible) { mutableStateOf(initialUrl) }
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
            // a pasted results page is turned into the search address: its search becomes %s
            LabeledField("Search address", url, { v -> url = if (v.length - url.length > 1 && "%s" !in v) searchTemplateFrom(v) ?: v else v }, uri = true, placeholder = "https://www.ebay.com/sch/i.html?_nkw=%s")
            Txt(
                if (url.length > 8 && !urlOk) "Enter a web address starting with https://" else "Easiest: search the site, then tap ⋮ › Add shortcut. Or paste the address of one of its results pages here.",
                12f, if (url.length > 8 && !urlOk) ui.cs.error else ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), lineHeight = 16f,
            )
        }
    }
}

/** The Add dialog for a shortcut found on a page (tab menu › Add shortcut), filled in and ready to save. */
@Composable
fun ShortcutDraftDialog(vm: BrowserViewModel) {
    val draft = vm.shortcutDraft
    AddBangDialog(vm, draft != null, draft?.first.orEmpty(), draft?.second ?: "https://") { vm.shortcutDraft = null }
}

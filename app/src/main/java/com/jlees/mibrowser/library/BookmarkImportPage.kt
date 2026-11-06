package com.jlees.mibrowser.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.common.Txt
import com.jlees.mibrowser.ui.settings.LocalSettingsNav
import com.jlees.mibrowser.ui.settings.SettingsSubPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Library → Import Bookmarks: its own page. */
fun bookmarkImportPage(vm: BrowserViewModel) = SettingsSubPage("import-bookmarks", "Import Bookmarks", "download") { BookmarkImportPage(vm) }

/**
 * A bookmarks file from another browser (or mi): choose it (it fills the box) or paste its contents into the box, then
 * Add. The box is checked as it changes, so the button says how many bookmarks it adds.
 */
@Composable
private fun BookmarkImportPage(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val nav = LocalSettingsNav.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<BookmarkEntry>?>(null) } // null: not checked yet
    LaunchedEffect(text) {
        found = null
        if (text.isBlank()) return@LaunchedEffect
        delay(300) // typing or pasting: check once it settles
        found = withContext(Dispatchers.Default) { parseBookmarkFile(text) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } } }
            read.onSuccess { text = withoutEmbeddedIcons(it) }.onFailure { vm.showAlert("Couldn't Open", "That file couldn't be read.") }
        }
    }
    val count = found?.let(::countLinks) ?: 0

    fun add() {
        val entries = found ?: parseBookmarkFile(text)
        if (countLinks(entries) == 0) return
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date())
        val fromMi = isMiExport(text)
        val (added, skipped) = vm.bookmarks.import(entries, fromMi, "Imported $date")
        focus.clearFocus()
        nav.close()
        vm.showAlert(
            "Bookmarks Imported",
            (if (added == 1) "1 bookmark added" else "$added bookmarks added") +
                (if (fromMi) "." else " to \"Imported $date\".") +
                if (skipped > 0) " $skipped already saved were skipped." else "",
        )
    }

    // the sheet already sits above the keyboard (OverlaySheet pads by its height): no ime padding here
    Column(Modifier.fillMaxSize()) {
        FilledTonalButton(onClick = { picker.launch(arrayOf("text/html", "application/xhtml+xml", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth()) {
            Sym("folder", 20f, ui.cs.onSecondaryContainer, Modifier.padding(end = 8.dp))
            Txt("Choose File", 15f, ui.cs.onSecondaryContainer, ui.semibold)
        }
        val shape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f)
                .background(ui.cs.surfaceContainerHighest, shape).border(1.dp, ui.cs.outlineVariant, shape).padding(14.dp),
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Txt("Or paste a bookmarks file here", 14f, ui.cs.onSurfaceVariant, ui.regular); inner() }
            },
        )
        if (text.isNotBlank()) {
            val f = found
            Txt(
                when { f == null -> "Checking…"; count == 0 -> "No bookmarks found"; count == 1 -> "1 bookmark found"; else -> "$count bookmarks found" },
                13f, if (f != null && count == 0) ui.cs.error else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { text = ""; focus.clearFocus() }, Modifier.weight(1f)) { Txt("Cancel", 15f, ui.cs.primary, ui.semibold) }
                Button(onClick = ::add, Modifier.weight(1f), enabled = count > 0) {
                    Txt(if (count > 0) "Add $count" else "Add Bookmarks", 15f, if (count > 0) ui.cs.onPrimary else ui.cs.onSurface.copy(alpha = 0.38f), ui.semibold)
                }
            }
        }
    }
}

/** Links in [entries], including inside folders. */
fun countLinks(entries: List<BookmarkEntry>): Int = entries.sumOf { if (it is BookmarkEntry.Folder) countLinks(it.children) else 1 }

/**
 * Chrome embeds each site's icon (ICON="data:…") in its export, which can make the file megabytes long; mi doesn't use
 * them, so they're dropped before the text goes in the box.
 */
fun withoutEmbeddedIcons(html: String) = html.replace(Regex("""\s+ICON(_URI)?="[^"]*"""", RegexOption.IGNORE_CASE), "")

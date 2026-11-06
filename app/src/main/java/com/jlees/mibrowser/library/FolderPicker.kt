package com.jlees.mibrowser.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Sym
import com.jlees.mibrowser.ui.common.Txt
import com.jlees.mibrowser.ui.common.touchable

/**
 * Choosing where a bookmark goes: browse into folders (tap one to open it), go back up with the arrow or by tapping a
 * folder in the path, make a new folder where you are, and Choose This Folder.
 *
 * @param nodes all bookmarks; [excluded] folders can't be chosen or entered (a folder can't go inside itself)
 * @param onNewFolder creates a folder named so inside the given folder and returns its id
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderPicker(
    nodes: List<BookmarkNode>, selected: String?, excluded: Set<String>,
    onNewFolder: (name: String, parent: String?) -> String, onChoose: (String?) -> Unit, onBack: () -> Unit,
) {
    val ui = LocalUi.current
    var at by remember { mutableStateOf(selected?.takeIf { it !in excluded }) }
    val trail = folderTrail(nodes, at)
    val folders = childFolders(nodes, at, excluded)
    var naming by remember { mutableStateOf<String?>(null) } // the new folder's name while typing it

    Column {
        // back (up one level; from the top, back to the bookmark) and where you are
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).touchable { if (at == null) onBack() else at = trail.dropLast(1).lastOrNull()?.id }, contentAlignment = Alignment.Center) {
                Sym("arrow_back", 24f, ui.cs.onSurface)
            }
            Txt(trail.lastOrNull()?.title ?: "Bookmarks", 20f, ui.cs.onSurface, ui.bold, Modifier.padding(start = 6.dp).weight(1f), maxLines = 1)
        }
        // the path: each step jumps back to that folder
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val steps = listOf<Pair<String?, String>>(null to "Bookmarks") + trail.map { it.id to it.title }
            steps.forEachIndexed { i, (id, title) ->
                if (i > 0) Sym("chevron_right", 16f, ui.cs.onSurfaceVariant, Modifier.padding(horizontal = 2.dp))
                val current = i == steps.lastIndex
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (current) ui.cs.secondaryContainer else ui.cs.surfaceContainerHighest)
                        .touchable { at = id }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Txt(title, 13f, if (current) ui.cs.onSecondaryContainer else ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1) }
            }
        }
        // a fixed height: the browser keeps its size as you move between folders with more or fewer inside
        LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (folders.isEmpty() && naming == null) item {
                Txt("No folders here", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(vertical = 16.dp, horizontal = 4.dp))
            }
            items(folders, key = { it.id }) { f ->
                val (subfolders, bookmarks) = folderContents(nodes, f.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainerHighest).touchable { at = f.id }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(ui.cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Sym("folder_fill", 20f, ui.cs.onTertiaryContainer)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Txt(f.title, 15f, ui.cs.onSurface, ui.bold, maxLines = 1)
                        Txt(contentsLabel(subfolders, bookmarks), 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                    }
                    if (f.id == selected) Sym("check", 20f, ui.cs.primary, Modifier.padding(end = 6.dp))
                    Sym("chevron_right", 20f, ui.cs.onSurfaceVariant)
                }
            }
            item(key = "new") {
                val name = naming
                if (name == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).touchable { naming = "" }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Sym("create_new_folder", 22f, ui.cs.primary) }
                    Txt("New Folder", 15f, ui.cs.primary, ui.semibold, Modifier.padding(start = 12.dp))
                } else {
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                    fun create() { if (name.isNotBlank()) at = onNewFolder(name.trim(), at); naming = null }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainerHighest).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym("create_new_folder", 22f, ui.cs.onSurfaceVariant)
                        BasicTextField(
                            name, { naming = it }, Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(fr), singleLine = true,
                            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 15.sp),
                            cursorBrush = SolidColor(ui.cs.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { create() }),
                            decorationBox = { inner -> Box { if (name.isEmpty()) Txt("Folder name", 15f, ui.cs.onSurfaceVariant, ui.semibold); inner() } },
                        )
                        Box(Modifier.size(40.dp).clip(CircleShape).touchable { naming = null }, contentAlignment = Alignment.Center) { Sym("close", 20f, ui.cs.onSurfaceVariant) }
                        Box(Modifier.size(40.dp).clip(CircleShape).touchable { create() }, contentAlignment = Alignment.Center) { Sym("check", 22f, ui.cs.primary) }
                    }
                }
            }
        }
        Button(onClick = { onChoose(at) }, Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Txt("Choose This Folder", 15f, ui.cs.onPrimary, ui.semibold)
        }
    }
}

private fun contentsLabel(folders: Int, bookmarks: Int) = listOfNotNull(
    folders.takeIf { it > 0 }?.let { if (it == 1) "1 folder" else "$it folders" },
    bookmarks.takeIf { it > 0 }?.let { if (it == 1) "1 bookmark" else "$it bookmarks" },
).joinToString(" · ").ifEmpty { "Empty" }

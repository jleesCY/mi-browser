// Port of legacy/src/components/Bookmarks/{BookmarksView,BookmarkRow}.tsx
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.ui.bookmarks

import com.jlees.mibrowser.data.Favicons
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.jlees.mibrowser.ui.common.DialogCard
import com.jlees.mibrowser.ui.common.DialogAction
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.logic.*
import com.jlees.mibrowser.ui.LabeledField
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.common.ScrollTopAndFab
import com.jlees.mibrowser.ui.common.SwipeActions
import com.jlees.mibrowser.ui.common.deleteAnim
import com.jlees.mibrowser.ui.common.rememberDeleteAnim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BookmarksView(vm: BrowserViewModel, kbVisible: Boolean, closeOverlay: () -> Unit) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    var folderStack by remember { mutableStateOf(listOf<BookmarkNode>()) }
    val currentFolder = folderStack.lastOrNull()?.id
    var search by remember { mutableStateOf("") }

    // modal state
    var modalMode by remember { mutableStateOf<String?>(null) } // add_bookmark | add_folder | edit
    var editing by remember { mutableStateOf<BookmarkNode?>(null) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var modalFolder by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }

    LaunchedEffect(vm.bookmarks.size) { focusManager.clearFocus() }

    fun openAdd() { modalMode = "add_bookmark"; title = vm.currentTab?.title ?: ""; url = vm.activeUrl ?: ""; modalFolder = currentFolder; picker = false }
    fun openFolder() { modalMode = "add_folder"; title = "New Folder"; url = ""; modalFolder = currentFolder; picker = false }
    fun openEdit(b: BookmarkNode) { modalMode = "edit"; editing = b; title = b.title; url = b.url; modalFolder = b.parentId; picker = false }
    fun save() {
        when (modalMode) {
            "add_bookmark" -> vm.bookmarks = addBookmark(vm.bookmarks, title, url, modalFolder)
            "add_folder" -> vm.bookmarks = addFolder(vm.bookmarks, title, modalFolder)
            "edit" -> editing?.let { e ->
                var list = vm.bookmarks
                if (modalFolder != e.parentId) list = moveBookmark(list, e.id, modalFolder)
                vm.bookmarks = updateBookmark(list, e.id, title, if (e.isFolder) null else url)
            }
        }
        modalMode = null
    }

    LaunchedEffect(vm.bookmarksAutoAdd) { if (vm.bookmarksAutoAdd) { openAdd(); vm.bookmarksAutoAdd = false } }

    // BookmarksView BackHandler (runs before the app-level one)
    BackHandler(enabled = modalMode != null || search.isNotEmpty() || folderStack.isNotEmpty()) {
        when {
            modalMode != null && picker -> picker = false
            modalMode != null -> modalMode = null
            search.isNotEmpty() -> search = ""
            else -> folderStack = folderStack.dropLast(1)
        }
    }

    val visible = if (search.isNotBlank()) vm.bookmarks.filter { it.title.lowercase().contains(search.lowercase()) || (!it.isFolder && it.url.lowercase().contains(search.lowercase())) }
    else vm.bookmarks.filter { it.parentId == currentFolder }.sortedBy { it.order }
    val showPath = search.isNotEmpty() || folderStack.isNotEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slotW = maxWidth.value - 40
        val rowH = 60f * ui.fontScale
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = if (showPath) 130.dp else 90.dp, bottom = 100.dp)) {
            SortableSlots(
                items = visible, key = { it.id }, slotW = slotW.dp, slotH = (rowH + 10).dp,
                onReorder = { from, to -> if (search.isEmpty()) vm.bookmarks = reorderBookmarks(vm.bookmarks, currentFolder, from, to) },
            ) { b ->
                BookmarkRow(b, rowH, vm.settings.showBookmarkIcons,
                    onPress = { if (b.isFolder) folderStack = folderStack + b else { vm.navigateTab(b.url, b.title); closeOverlay() } },
                    onDelete = { vm.bookmarks = deleteBookmark(vm.bookmarks, b.id) },
                    onRename = { openEdit(b) })
            }
        }

        // header
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
            if (showPath) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                if (folderStack.isNotEmpty()) Box(Modifier.padding(end = 10.dp).touchable { folderStack = folderStack.dropLast(1) }) { Sym("arrow_back", 24f, ui.cs.onSurface) }
                Txt(if (search.isNotEmpty()) "Search Results" else "/" + folderStack.joinToString("/") { it.title }, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
            SheetSearchField(search, { search = it }, "Search Bookmarks...")
        }

        // derived: recomposes only when crossing the threshold, not on every scroll frame
        val scrolledDown by remember { derivedStateOf { scroll.value / density > 100 } }
        if (search.isEmpty()) ScrollTopAndFab(kbVisible, scrolledDown, { scope.launch { scroll.animateScrollTo(0) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallFloatingActionButton(onClick = { openFolder() }, modifier = Modifier.padding(end = 16.dp), containerColor = ui.cs.secondaryContainer) {
                    Sym("create_new_folder", 24f, ui.cs.onSecondaryContainer)
                }
                FloatingActionButton(onClick = { openAdd() }, containerColor = ui.cs.primaryContainer) { Sym("add", 28f, ui.cs.onPrimaryContainer) }
            }
        }
    }

    // ---- add / edit modal
    val titleFr = remember { FocusRequester() }
    LaunchedEffect(modalMode, picker) { if (modalMode != null && !picker) { delay(150); runCatching { titleFr.requestFocus() } } }
    ModalFrame(modalMode != null, { if (picker) picker = false else modalMode = null }) {
        DialogCard(
            if (picker) null else when (modalMode) { "add_bookmark" -> "Add Bookmark"; "add_folder" -> "New Folder"; else -> "Edit Item" },
            actions = if (picker) null else { {
                DialogAction("Cancel") { modalMode = null }
                DialogAction("Save") { save() }
            } },
        ) {
            if (!picker) {
                LabeledField("Title", title, { title = it }, Modifier.padding(bottom = 15.dp), titleFr)
                if (modalMode == "add_bookmark" || (modalMode == "edit" && editing?.isFolder == false)) {
                    LabeledField("URL", url, { url = it }, Modifier.padding(bottom = 15.dp), uri = true)
                }
                Txt("Location", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 5.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainerHighest).touchable { picker = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(modalFolder?.let { id -> vm.bookmarks.firstOrNull { it.id == id }?.title ?: "Unknown" } ?: "Bookmarks", 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f))
                    Sym("chevron_right", 20f, ui.cs.onSurfaceVariant)
                }
            } else {
                Row(Modifier.padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(end = 10.dp).touchable { picker = false }) { Sym("arrow_back", 24f, ui.cs.onSurface) }
                    Txt("Select Location", 18f, ui.cs.onSurface, ui.bold)
                }
                val e = editing
                val excluded = if (modalMode == "edit" && e != null && e.isFolder) descendantsAndSelf(vm.bookmarks, e.id) else emptySet()
                val options = listOf<Pair<String?, String>>(null to "Bookmarks (Root)") +
                    vm.bookmarks.filter { it.isFolder && it.id !in excluded }.map { it.id to folderPath(vm.bookmarks, it.id) }.sortedBy { it.second }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options, key = { it.first ?: "root" }) { (id, path) ->
                        Row(
                            Modifier.fillMaxWidth().touchable { modalFolder = id; picker = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Sym(if (id == null) "home" else "folder", 20f, ui.cs.onSurfaceVariant, Modifier.padding(end = 10.dp))
                            Txt(path, 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f).padding(end = 10.dp), maxLines = 3)
                            if (modalFolder == id) Sym("check", 20f, ui.cs.primary)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ui.cs.surface))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(b: BookmarkNode, height: Float, showIcon: Boolean, onPress: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val ui = LocalUi.current
    val (anim, del) = rememberDeleteAnim()
    val shape = RoundedCornerShape(ui.radius.dp)
    SwipeRevealRow(160.dp, { SwipeActions(height.dp, onRename) { del(onDelete) } }, Modifier.height(height.dp).deleteAnim { anim.value }) {
        Row(
            Modifier.fillMaxWidth().height(height.dp).clip(shape).background(ui.cs.surfaceContainerHighest).touchable(onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.padding(end = 15.dp).size(44.dp).clip(if (b.isFolder) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                    .background(if (b.isFolder) ui.cs.tertiaryContainer else ui.cs.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    b.isFolder -> Sym("folder_fill", 24f, ui.cs.onTertiaryContainer)
                    showIcon -> NetImage(Favicons.src(b.url), Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))) { Sym("public", 26f, ui.cs.onSurfaceVariant) }
                    else -> Txt(b.title.ifEmpty { "?" }.take(1).uppercase(), 20f, ui.cs.onSurface, FontWeight.W700)
                }
            }
            Column(Modifier.weight(1f)) {
                Txt(b.title, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                if (!b.isFolder) Txt(b.url, 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
            }
            if (b.isFolder) Sym("chevron_right", 20f, ui.cs.onSurfaceVariant)
        }
    }
}


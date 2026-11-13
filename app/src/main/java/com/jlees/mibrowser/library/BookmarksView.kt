// The Bookmarks screen: folders, search, reorder by dragging, add/edit dialog.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.jlees.mibrowser.library

import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.ButtonStyle

import com.jlees.mibrowser.data.Favicons
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.jlees.mibrowser.ui.common.DialogCard
import com.jlees.mibrowser.ui.common.rowTileColor
import com.jlees.mibrowser.ui.common.DialogAction
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.logic.*
import com.jlees.mibrowser.ui.common.LabeledField
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.*
import com.jlees.mibrowser.ui.common.SheetPillSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the bookmark dialog is doing. */
private enum class BookmarkDialog { ADD_BOOKMARK, ADD_FOLDER, EDIT }

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
    var modalMode by remember { mutableStateOf<BookmarkDialog?>(null) }
    var editing by remember { mutableStateOf<BookmarkNode?>(null) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var modalFolder by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }

    LaunchedEffect(vm.bookmarks.nodes.size) { focusManager.clearFocus() }

    fun openAdd() { modalMode = BookmarkDialog.ADD_BOOKMARK; title = vm.tabs.current?.title ?: ""; url = vm.activeUrl ?: ""; modalFolder = currentFolder; picker = false }
    fun openFolder() { modalMode = BookmarkDialog.ADD_FOLDER; title = "New Folder"; url = ""; modalFolder = currentFolder; picker = false }
    fun openEdit(b: BookmarkNode) { modalMode = BookmarkDialog.EDIT; editing = b; title = b.title; url = b.url; modalFolder = b.parentId; picker = false }
    fun save() {
        when (modalMode) {
            BookmarkDialog.ADD_BOOKMARK -> vm.bookmarks.add(title, url, modalFolder)
            BookmarkDialog.ADD_FOLDER -> vm.bookmarks.addFolder(title, modalFolder)
            BookmarkDialog.EDIT -> editing?.let { e -> vm.bookmarks.edit(e.id, title, if (e.isFolder) null else url, modalFolder) }
            null -> {}
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

    // inside a folder, a swipe right on the search Pill goes up a folder (the list slides away with the finger)
    val folderSlide = remember { androidx.compose.animation.core.Animatable(0f) }
    val sheetBack = LocalSheetBack.current
    DisposableEffect(folderStack.size, search.isEmpty()) {
        sheetBack?.inner = if (folderStack.isEmpty() || search.isNotEmpty()) null else BackSwipe(
            drag = { p -> scope.launch { folderSlide.snapTo(p) } },
            release = { up ->
                scope.launch {
                    if (up) { folderSlide.animateTo(1f, androidx.compose.animation.core.tween(160)); folderStack = folderStack.dropLast(1) }
                    else folderSlide.animateTo(0f, androidx.compose.animation.core.tween(200))
                    folderSlide.snapTo(0f)
                }
            },
        )
        onDispose { sheetBack?.inner = null }
    }

    val visible = if (search.isNotBlank()) vm.bookmarks.nodes.filter { it.title.lowercase().contains(search.lowercase()) || (!it.isFolder && it.url.lowercase().contains(search.lowercase())) }
    else vm.bookmarks.nodes.filter { it.parentId == currentFolder }.sortedBy { it.order }
    val showPath = search.isNotEmpty() || folderStack.isNotEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slotW = maxWidth.value - 40
        val rowH = rowHeight(ui.padding, ui.fontScale) // the same rows as History and Downloads
        Column(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * folderSlide.value; alpha = 1f - folderSlide.value }
            .verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = if (showPath) 64.dp else 16.dp, bottom = SheetPillSpace)) {
            // History and Downloads' lists start at 16dp: under the folder path this one starts lower, so the message comes up by the difference
            if (visible.isEmpty()) EmptyState("bookmarks", top = (50 - ((if (showPath) 64 else 16) - 16)).coerceAtLeast(0).dp, text = when {
                search.isNotBlank() -> "No matches"
                currentFolder != null -> "This Folder Is Empty"
                else -> "No Bookmarks Yet"
            })
            SortableSlots(
                items = visible, key = { it.id }, slotW = slotW.dp, slotH = (rowH + rowMargin(ui.padding)).dp,
                onReorder = { from, to -> if (search.isEmpty()) vm.bookmarks.reorder(currentFolder, from, to) },
            ) { b ->
                BookmarkRow(b, rowH, vm.settings.showBookmarkIcons,
                    onPress = { if (b.isFolder) folderStack = folderStack + b else { vm.navigateTab(b.url, b.title); closeOverlay() } },
                    onDelete = { confirmDelete(vm, b) },
                    onRename = { openEdit(b) })
            }
        }

        // the folder path (the search field is the Pill at the bottom)
        if (showPath) Column(Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                if (folderStack.isNotEmpty()) Box(Modifier.padding(end = 10.dp).touchable { folderStack = folderStack.dropLast(1) }) { Sym("arrow_back", 24f, ui.cs.onSurface, label = "Back") }
                Txt(if (search.isNotEmpty()) "Search Results" else "/" + folderStack.joinToString("/") { it.title }, 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }
        SheetSearchPill(search, { search = it })

        // derived: recomposes only when crossing the threshold, not on every scroll frame
        val scrolledDown by remember { derivedStateOf { scroll.value / density > 100 } }
        // above the search Pill: scroll to top (once scrolled down), then new folder and add; none while typing
        if (search.isEmpty() && !kbVisible) Column(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = SheetPillSpace), horizontalAlignment = Alignment.End) {
            if (scrolledDown) SmallFloatingActionButton(onClick = { scope.launch { scroll.animateScrollTo(0) } }, modifier = Modifier.padding(bottom = 16.dp), containerColor = ui.cs.secondaryContainer) {
                Sym("arrow_upward", 24f, ui.cs.onSecondaryContainer, label = "Scroll to top")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallFloatingActionButton(onClick = { openFolder() }, modifier = Modifier.padding(end = 16.dp), containerColor = ui.cs.secondaryContainer) {
                    Sym("create_new_folder", 24f, ui.cs.onSecondaryContainer, label = "New folder")
                }
                FloatingActionButton(onClick = { openAdd() }, containerColor = ui.cs.primaryContainer) { Sym("add", 28f, ui.cs.onPrimaryContainer, label = "Add bookmark") }
            }
        }
    }

    // ---- add / edit modal
    val titleFr = remember { FocusRequester() }
    LaunchedEffect(modalMode, picker) { if (modalMode != null && !picker) { delay(150); runCatching { titleFr.requestFocus() } } }
    ModalFrame(modalMode != null, { if (picker) picker = false else modalMode = null }) {
        DialogCard(
            if (picker) null else when (modalMode) { BookmarkDialog.ADD_BOOKMARK -> "Add Bookmark"; BookmarkDialog.ADD_FOLDER -> "New Folder"; else -> "Edit Item" },
            actions = if (picker) null else { {
                DialogAction("Cancel") { modalMode = null }
                DialogAction("Save") { save() }
            } },
        ) {
            if (!picker) {
                LabeledField("Title", title, { title = it }, Modifier.padding(bottom = 15.dp), titleFr)
                if (modalMode == BookmarkDialog.ADD_BOOKMARK || (modalMode == BookmarkDialog.EDIT && editing?.isFolder == false)) {
                    LabeledField("URL", url, { url = it }, Modifier.padding(bottom = 15.dp), uri = true)
                }
                Txt("Location", 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 5.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainerHighest).touchable { picker = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt((listOf("Bookmarks") + folderTrail(vm.bookmarks.nodes, modalFolder).map { it.title }).joinToString(" › "), 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 2)
                    Sym("chevron_right", 20f, ui.cs.onSurfaceVariant)
                }
            } else {
                val e = editing
                FolderPicker(
                    vm.bookmarks.nodes, modalFolder,
                    excluded = if (modalMode == BookmarkDialog.EDIT && e != null && e.isFolder) descendantsAndSelf(vm.bookmarks.nodes, e.id) else emptySet(),
                    onNewFolder = { name, parent -> vm.bookmarks.addFolder(name, parent) },
                    onChoose = { modalFolder = it; picker = false },
                    onBack = { picker = false },
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(b: BookmarkNode, height: Float, showIcon: Boolean, onPress: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val ui = LocalUi.current
    // swipe left to delete, right to edit (see SwipeActionRow); the sortable list positions rows itself, so no slot height
    SwipeActionRow(height.dp, onDelete, onEdit = onRename, confirmDelete = true) {
        Row(
            Modifier.fillMaxWidth().height(height.dp).clip(libraryRowShape()).background(ui.cs.surfaceContainerHighest).touchable(onClick = onPress).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIconTile(
                shape = if (b.isFolder) MaterialShapes.Cookie6Sided.toShape() else CircleShape,
                color = if (b.isFolder) ui.cs.tertiaryContainer else rowTileColor(),
            ) {
                when {
                    b.id == FAVORITES_FOLDER -> Sym("star", 24f, ui.cs.onTertiaryContainer) // your favorites (the home page's)
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

/** Deleting a bookmark (or a folder, with everything in it) asks first: it can't be undone. */
private fun confirmDelete(vm: BrowserViewModel, b: BookmarkNode) {
    val inside = descendantsAndSelf(vm.bookmarks.nodes, b.id) - b.id
    val bookmarks = vm.bookmarks.nodes.count { it.id in inside && !it.isFolder }
    val folders = inside.size - bookmarks
    val contents = listOfNotNull(
        folders.takeIf { it > 0 }?.let { if (it == 1) "1 folder" else "$it folders" },
        bookmarks.takeIf { it > 0 }?.let { if (it == 1) "1 bookmark" else "$it bookmarks" },
    ).joinToString(" and ")
    // the Favorites folder is your favorites: deleting it empties the home page's grid, so it says so
    if (b.id == FAVORITES_FOLDER) {
        val n = vm.bookmarks.favorites.size
        return vm.showAlert(
            "Delete All Favorites?",
            if (n == 0) "The Favorites folder will be deleted." else "The Favorites folder holds your favorites. Deleting it removes ${if (n == 1) "your favorite" else "all $n favorites"} from your home page and bookmarks.",
            listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Delete All", ButtonStyle.DESTRUCTIVE) { vm.bookmarks.delete(b.id) }),
        )
    }
    vm.showAlert(
        if (b.isFolder) "Delete Folder?" else "Delete Bookmark?",
        when {
            !b.isFolder -> "\"${b.title}\" will be removed from your bookmarks."
            contents.isEmpty() -> "The folder \"${b.title}\" will be deleted."
            else -> "The folder \"${b.title}\" and the $contents in it will be deleted."
        },
        listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Delete", ButtonStyle.DESTRUCTIVE) { vm.bookmarks.delete(b.id) }),
    )
}

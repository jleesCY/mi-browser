// The home page's favorites: a grid at the bottom, above the Pill, four across and two down per page, swiped sideways
// for more (Settings › Home Page › Favorites). Favorites are the bookmarks in the Favorites folder (library/).
package com.jlees.mibrowser.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Color
import com.jlees.mibrowser.logic.displayHost
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.Favicons
import com.jlees.mibrowser.data.BookmarkNode
import com.jlees.mibrowser.library.FavoriteDialog
import com.jlees.mibrowser.ui.common.*

private const val COLUMNS = 4
private const val ROWS = 2
private val TILE_HEIGHT = 74.dp

/** The favorites the home page shows (none when Settings › Home Page › Favorites is off). */
fun BrowserViewModel.homeFavorites(): List<BookmarkNode> = if (settings.homeFavorites) bookmarks.favorites else emptyList()

/** Tap a favorite to open it; hold it to rename, change or delete it. Nothing shows without favorites. */
@Composable
fun BoxScope.HomeFavorites(vm: BrowserViewModel) {
    var editing by remember { mutableStateOf<BookmarkNode?>(null) }
    FavoriteDialog(vm.bookmarks, editing) { editing = null }
    // while typing in the Pill they step aside (fading out): the page shrinks above the keyboard, and the grid would
    // ride up with it into the logo
    AnimatedVisibility(!vm.isInputFocused, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            FavoritesGrid(
                vm.homeFavorites(), onOpen = { vm.navigateTab(it.url, it.title) }, onHold = { editing = it },
                onReorder = { from, to -> vm.bookmarks.reorderFavorites(from, to) },
            )
        }
    }
}

/**
 * The grid itself, also drawn (still) on a blank tab's card in the tab switcher. Hold a favorite and drag to move it
 * within its page ([onReorder], places among all favorites); hold and let go to edit it ([onHold]).
 */
@Composable
fun BoxScope.FavoritesGrid(
    favorites: List<BookmarkNode>, onOpen: (BookmarkNode) -> Unit = {}, onHold: (BookmarkNode) -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
) {
    val ui = LocalUi.current
    if (favorites.isEmpty()) return
    val pages = favorites.chunked(COLUMNS * ROWS)
    val pager = rememberPagerState { pages.size }
    // every page is as tall as the fullest one, so swiping between them never shifts the grid
    val rows = minOf(ROWS, (favorites.size + COLUMNS - 1) / COLUMNS)
    // clear of the Pill by the same gap everywhere: the navigation bar's height is read as it is, not as what's left of
    // it (inside the tab switcher's card it has already been taken up, which dropped the grid lower there)
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = navBar + 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { p ->
            BoxWithConstraints(Modifier.fillMaxWidth().height(TILE_HEIGHT * rows).padding(horizontal = 32.dp)) {
                val first = p * COLUMNS * ROWS
                SortableSlots(
                    pages[p], key = { it.id }, slotW = maxWidth / COLUMNS, slotH = TILE_HEIGHT, columns = COLUMNS,
                    onReorder = { from, to -> onReorder(first + from, first + to) }, onHold = onHold,
                ) { fav -> Tile(fav, Modifier.fillMaxSize(), onOpen = { onOpen(fav) }) }
            }
        }
        // which page: a dot each, the current one solid
        if (pages.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pages.size) { i ->
                Box(Modifier.size(6.dp).background(ui.cs.onSurface.copy(alpha = if (i == pager.currentPage) 0.8f else 0.25f), CircleShape))
            }
        }
    }
}

@Composable
private fun Tile(fav: BookmarkNode, modifier: Modifier, onOpen: () -> Unit) {
    val ui = LocalUi.current
    Column(
        modifier.height(TILE_HEIGHT).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            // a hold is the grid's (edit, or drag to move; it gives the buzz): with a long-click of its own, letting go after
    // one isn't a tap
            .combinedClickable(interactionSource = null, indication = ripple(), hapticFeedbackEnabled = false, onLongClick = {}, onClick = onOpen)
            .padding(top = 6.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(ui.cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            NetImage(Favicons.src(fav.url), Modifier.size(26.dp).clip(CircleShape)) { Monogram(fav.url) }
        }
        Txt(fav.title, 12f, ui.cs.onSurface, ui.semibold, Modifier.padding(top = 5.dp), maxLines = 1, align = TextAlign.Center)
    }
}

/** A site without an icon (yet): its first letter on a colour of its own, the same for the site every time. */
@Composable
private fun Monogram(url: String) {
    val host = displayHost(url).removePrefix("www.")
    val hue = (host.hashCode() and 0x7fffffff) % 360
    Box(Modifier.size(46.dp).background(Color.hsl(hue.toFloat(), 0.45f, 0.42f), CircleShape), contentAlignment = Alignment.Center) {
        Txt(host.take(1).uppercase(), 20f, Color.White, LocalUi.current.extrabold)
    }
}

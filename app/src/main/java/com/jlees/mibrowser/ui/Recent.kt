// Port of legacy/src/components/BrowserSession/RecentSearchesView.tsx (+ favorites handlers in index.tsx 1352-1367, 2582-2649)
package com.jlees.mibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconUrl
import com.jlees.mibrowser.ui.common.*

@Composable
fun RecentSearches(vm: BrowserViewModel, pill: PillState, screenH: Float) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current.density
    val filter = vm.inputUrl
    val activeUrl = vm.activeUrl
    val items = vm.recents.filter { item ->
        if (filter.isEmpty()) true
        else if (activeUrl != null && (filter == activeUrl || filter == displayHost(activeUrl))) true
        else item.title.lowercase().contains(filter.lowercase())
    }
    Box(Modifier.fillMaxSize().background(ui.cs.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize()) {
            // drawer opened (or closed) while typing: fetch (or drop) suggestions for what's already typed
            val expanded by remember { derivedStateOf { pill.recentExpanded } }
            LaunchedEffect(expanded) { if (vm.isInputFocused) vm.onTyping(vm.inputUrl, expanded) }
            // past searches that match come first; engine suggestions repeating one of them are dropped
            val searched = items.map { it.title.lowercase() }.toSet()
            val suggestions = vm.suggestions.filter { it.lowercase() !in searched }
            if (items.isEmpty() && suggestions.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.TopCenter) {
                    Txt(if (vm.recents.isEmpty()) "No recent history" else "No matches found", 16f, ui.cs.onSurfaceVariant, ui.semibold)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)) {
                    items(items, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().touchable { vm.inputUrl = item.title; vm.ui.tryEmit(com.jlees.mibrowser.UiEvent.FocusInput) }
                                .padding(vertical = 7.dp, horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(item.title.ifEmpty { "Untitled" }, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
                            Box(Modifier.padding(start = 8.dp).touchable { vm.deleteRecentSearch(item.id) }.padding(6.dp)) { Sym("close", 16f, ui.cs.onSurfaceVariant) }
                        }
                    }
                    if (suggestions.isNotEmpty() && items.isNotEmpty()) item { Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant)) }
                    // search-engine suggestions: tap to search
                    items(suggestions, key = { "s_$it" }) { s ->
                        Row(
                            Modifier.fillMaxWidth().touchable { focusManager.clearFocus(); vm.submitInput(s) }.padding(vertical = 10.dp, horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Sym("search", 20f, ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
                            Txt(s, 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
                        }
                    }
                }
            }
            // favorites bar
            Row(
                Modifier.fillMaxWidth().background(ui.cs.surfaceContainerHigh).border(1.dp, ui.cs.surface).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                SortableSlots(
                    items = vm.favorites, key = { it.id }, slotW = 60.dp, slotH = 44.dp, horizontal = true,
                    longPressMs = 150, dragScale = 1.1f, dragShadow = false,
                    onReorder = { from, to -> vm.reorderFavorites(from, to) },
                    onDragEnd = { fav, pos ->
                        // dragged up and away from the bottom bar → delete (handleFavoriteDragEnd)
                        if (pos.y / density < screenH - 150) vm.removeFavorite(fav.id)
                    },
                ) { fav ->
                    Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(ui.cs.onSurface.copy(alpha = if (ui.isDark) 0.1f else 0.05f))
                                .touchable {
                                    vm.inputUrl = displayHost(fav.url)
                                    focusManager.clearFocus()
                                    vm.navigateTab(fav.url, fav.title)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            NetImage(fav.icon ?: faviconUrl(fav.url), Modifier.size(36.dp).clip(CircleShape)) { Sym("public", 32f, ui.cs.onSurface) }
                        }
                    }
                }
                if (vm.favorites.size < 5) {
                    Box(
                        Modifier.padding(horizontal = 8.dp).size(44.dp).clip(CircleShape).background(ui.cs.surfaceContainerHighest).border(1.dp, ui.cs.surface, CircleShape)
                            .touchable {
                                val url = vm.activeUrl
                                if (url != null && url != "about:blank") vm.addFavorite(vm.currentTab?.title ?: displayHost(url), url)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Sym("add", 28f, ui.cs.onSurface) }
                }
            }
        }
        // floating close button
        Box(
            Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp, end = 20.dp).size(40.dp).shadow(4.dp, CircleShape)
                .background(ui.cs.surfaceContainerHighest, CircleShape).border(1.dp, ui.cs.surface, CircleShape).touchable { pill.animateRecent(pill.recentMin) },
            contentAlignment = Alignment.Center,
        ) { Sym("keyboard_arrow_down", 24f, ui.cs.onSurface) }
    }
}


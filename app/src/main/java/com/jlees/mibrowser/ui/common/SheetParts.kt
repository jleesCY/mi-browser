// Pieces shared by the Library (bookmarks/history) sheets and the tab switcher.
package com.jlees.mibrowser.ui.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.data.TabItem
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.faviconUrl
import com.jlees.mibrowser.ui.SheetSearchField
import com.jlees.mibrowser.ui.blockTouches
import com.jlees.mibrowser.ui.interp
import com.jlees.mibrowser.ui.timing
import kotlinx.coroutines.launch

/** Search header with ⋮ overflow menu (SearchHeader in TabsView/HistoryView). */
@Composable
fun SearchHeaderWithMenu(
    value: String, onChange: (String) -> Unit, placeholder: String, onFocus: () -> Unit,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().zIndex(1000f).padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
        SheetSearchField(value, onChange, placeholder, onFocus, trailing = {
            IconButton(onClick = { open = !open }) { Sym("more_vert", 24f, ui.cs.onSurfaceVariant) }
        })
        if (open) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 60.dp).width(IntrinsicSize.Max).widthIn(min = 200.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)).background(ui.cs.surfaceContainer, RoundedCornerShape(16.dp)).padding(vertical = 8.dp, horizontal = 4.dp),
            ) { menu { open = false } }
        }
    }
}

@Composable
fun ScrollTopAndFab(sheetH: Float, snapDefault: Float, kbVisible: Boolean, showScrollTop: Boolean, onScrollTop: () -> Unit, fabs: @Composable () -> Unit) {
    val ui = LocalUi.current
    val bottom = interp(sheetH, listOf(0f, snapDefault), listOf(-100f, 20f))
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).offset(y = (-bottom).dp).alpha(if (kbVisible) 0f else 1f),
            horizontalAlignment = Alignment.End,
        ) {
            if (showScrollTop && !kbVisible) {
                SmallFloatingActionButton(onClick = onScrollTop, modifier = Modifier.padding(bottom = 16.dp), containerColor = ui.cs.secondaryContainer) {
                    Sym("arrow_upward", 24f, ui.cs.onSecondaryContainer)
                }
            }
            if (!kbVisible) fabs()
        }
    }
}

/** Shared delete animation: scale 1→0, opacity [0,0.3,1]→[0,1,1] over 150ms, then delete. */
@Composable
fun rememberDeleteAnim(): Pair<Animatable<Float, *>, (() -> Unit) -> Unit> {
    val anim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return anim to { done -> scope.launch { anim.animateTo(0f, timing(150)); done() } }
}

fun Modifier.deleteAnim(v: Float) = graphicsLayer { scaleX = v; scaleY = v; alpha = interp(v, listOf(0f, 0.3f, 1f), listOf(0f, 1f, 1f)) }

/** Favicon in an expressive shape: a cookie for the active tab, a circle otherwise. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwipeActions(height: Dp, onRename: () -> Unit, onDelete: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.radius.dp)
    Box(Modifier.padding(start = 8.dp).size(72.dp, height).clip(shape).background(ui.cs.secondaryContainer).touchable(onClick = onRename), contentAlignment = Alignment.Center) { Sym("edit", 24f, ui.cs.onSecondaryContainer) }
    Box(Modifier.padding(start = 8.dp).size(72.dp, height).clip(shape).background(ui.cs.errorContainer).touchable(onClick = onDelete), contentAlignment = Alignment.Center) { Sym("delete", 24f, ui.cs.onErrorContainer) }
}

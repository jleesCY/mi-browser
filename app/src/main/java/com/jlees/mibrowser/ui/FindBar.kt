// Find in page: the bar above the keyboard with match navigation.
package com.jlees.mibrowser.ui

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jlees.mibrowser.ui.common.*
import kotlin.math.roundToInt

// ---------------------------------------------------------------- find in page
@Composable
fun FindBar(imeDp: () -> Float, onQuery: (String) -> Unit, onNext: (forward: Boolean) -> Unit, onClose: () -> Unit) {
    val ui = LocalUi.current
    val slide = remember { Animatable(100f) }
    val fr = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        slide.animateTo(0f, Motion.spatial())
        fr.requestFocus()
    }
    Box(Modifier.fillMaxSize().zIndex(100f)) {
        Row(
            Modifier.align(Alignment.BottomCenter).paddingDp(bottom = imeDp).offset { IntOffset(0, (slide.value * density).roundToInt()) }
                .fillMaxWidth().height(60.dp)
                .shadow(3.dp, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp))
                .background(ui.cs.surfaceContainer, RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp)).blockTouches()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).padding(end = 6.dp).background(ui.cs.surfaceContainerHighest, RoundedCornerShape(22.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("search", 20f, ui.cs.onSurfaceVariant, Modifier.padding(start = 8.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; onQuery(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(fr),
                    singleLine = true,
                    textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = (16 * ui.fontScale).sp),
                    cursorBrush = SolidColor(ui.cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotEmpty()) onNext(true) }),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Txt("Find in page...", 16f, ui.cs.onSurfaceVariant, ui.semibold); inner() } },
                )
            }
            IconButton(onClick = { if (text.isNotEmpty()) onNext(false) }) { Sym("keyboard_arrow_up", 24f, ui.cs.onSurface) }
            IconButton(onClick = { if (text.isNotEmpty()) onNext(true) }) { Sym("keyboard_arrow_down", 24f, ui.cs.onSurface) }
            FilledTonalIconButton(onClick = onClose) { Sym("close", 22f, ui.cs.onSecondaryContainer) }
        }
    }
}

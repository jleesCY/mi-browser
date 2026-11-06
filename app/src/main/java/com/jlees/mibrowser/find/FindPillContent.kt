package com.jlees.mibrowser.find

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.ui.PILL_HEIGHT
import com.jlees.mibrowser.ui.common.*

/**
 * What the Pill shows while finding in page: a search field with the match count ("2/7"), previous/next and close.
 * The Pill cross-fades into this (see BottomArea), so the Pill itself becomes the find bar.
 */
@Composable
fun FindPillContent(find: FindInPage, cornerRadius: Int) {
    val ui = LocalUi.current
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun close() { focus.clearFocus(); find.close() }
    BackHandler { close() }

    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val fieldShape = RoundedCornerShape((cornerRadius * 1.5f).dp)
        Row(
            Modifier.weight(1f).height((PILL_HEIGHT * 0.7f).dp).clip(fieldShape)
                .background(if (ui.isDark) ui.cs.surfaceContainerHighest else lerp(ui.cs.surfaceDim, ui.cs.primaryContainer, 0.4f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("search", 20f, ui.cs.onSurfaceVariant, Modifier.padding(start = 14.dp))
            BasicTextField(
                value = find.query,
                onValueChange = { find.search(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(fr),
                singleLine = true,
                textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = (16 * ui.fontScale).sp),
                cursorBrush = SolidColor(ui.cs.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { find.next(true) }),
                decorationBox = { inner -> Box { if (find.query.isEmpty()) Txt("Find in page", 16f, ui.cs.onSurfaceVariant, ui.semibold); inner() } },
            )
            // "2/7" while there are matches, "0/0" in the error colour when the text isn't on the page
            if (find.query.isNotEmpty()) Txt(
                "${find.current}/${find.total}", 14f,
                if (find.total == 0) ui.cs.error else ui.cs.onSurfaceVariant, ui.bold,
                Modifier.padding(end = 14.dp), maxLines = 1,
            )
        }
        val enabled = find.total > 0 || find.query.isNotEmpty()
        IconButton(onClick = { find.next(false) }, enabled = enabled) { Sym("keyboard_arrow_up", 26f, ui.cs.onSurface.copy(alpha = if (enabled) 1f else 0.38f), label = "Previous match") }
        IconButton(onClick = { find.next(true) }, enabled = enabled) { Sym("keyboard_arrow_down", 26f, ui.cs.onSurface.copy(alpha = if (enabled) 1f else 0.38f), label = "Next match") }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(ui.cs.secondaryContainer).clickable { close() },
            contentAlignment = Alignment.Center,
        ) { Sym("close", 22f, ui.cs.onSecondaryContainer, label = "Close find") }
    }
}

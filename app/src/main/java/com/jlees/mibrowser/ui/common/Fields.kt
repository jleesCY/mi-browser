// Text fields shared by dialogs and sheets.
package com.jlees.mibrowser.ui.common

import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.ui.common.*

/** A field with its label above it (dialogs: bookmarks, search engine, search shortcuts). */
@Composable
fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    fr: FocusRequester = remember { FocusRequester() }, uri: Boolean = false, placeholder: String? = null,
) {
    val ui = LocalUi.current
    Column(modifier) {
        Txt(label, 14f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 5.dp))
        ClearableField(value, onChange, fr, uri = uri, placeholder = placeholder)
    }
}

/** A text field with a clear (×) button. */
@Composable
fun ClearableField(
    value: String, onChange: (String) -> Unit, fr: FocusRequester = remember { FocusRequester() }, modifier: Modifier = Modifier,
    uri: Boolean = false, placeholder: String? = null, password: Boolean = false,
) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth().height(56.dp).background(ui.cs.surfaceContainerHighest, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr), singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = FontWeight.W600, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            keyboardOptions = when {
                password -> KeyboardOptions(autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                uri -> KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                else -> KeyboardOptions.Default
            },
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) Txt(placeholder, 16f, ui.cs.onSurfaceVariant, ui.regular)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) Box(Modifier.touchable { onChange(""); fr.requestFocus() }) { Sym("cancel", 20f, ui.cs.onSurfaceVariant, label = "Clear") }
    }
}

/** The search field inside a sheet view's search Pill (see SheetSearchPill): just "Search", so it never overflows. */
@Composable
fun SheetSearchField(
    value: String, onChange: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null, onFocus: (Boolean) -> Unit = {},
) {
    val ui = LocalUi.current
    val fr = remember { FocusRequester() }
    Row(
        // the main Pill's field: a lighter tone inside the Pill's bar, the same height and corners
        Modifier.fillMaxWidth().height(49.dp)
            .background(ui.pillField, RoundedCornerShape((ui.radius * 1.5f).dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr).onFocusChanged { onFocus(it.isFocused) }, singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt("Search", 16f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false); inner() } },
        )
        if (value.isNotEmpty()) IconButton(onClick = { onChange(""); fr.requestFocus() }) { Sym("close", 22f, ui.cs.onSurfaceVariant, label = "Clear") }
        else trailing?.invoke()
    }
}

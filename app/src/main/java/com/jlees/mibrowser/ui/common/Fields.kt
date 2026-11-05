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
        if (value.isNotEmpty()) Box(Modifier.touchable { onChange(""); fr.requestFocus() }) { Sym("cancel", 20f, ui.cs.onSurfaceVariant) }
    }
}

/** Search field used at the top of the sheet views ("Search Tabs..." etc.). */
@Composable
fun SheetSearchField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    val fr = remember { FocusRequester() }
    Row(
        modifier.fillMaxWidth().height(56.dp).background(ui.cs.surfaceContainerHigh, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr), singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt(placeholder, 16f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false); inner() } },
        )
        if (value.isNotEmpty()) IconButton(onClick = { onChange(""); fr.requestFocus() }) { Sym("close", 22f, ui.cs.onSurfaceVariant) }
        else trailing?.invoke()
    }
}

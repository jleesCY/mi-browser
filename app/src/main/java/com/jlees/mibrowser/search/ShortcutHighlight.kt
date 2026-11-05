package com.jlees.mibrowser.search

import com.jlees.mibrowser.search.typedBangLength
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.ui.common.*

/**
 * Shows a search shortcut typed at the start ("!w mars") as a mini pill: a thin space before and an en space after "!w" pad the
 * pill (drawn behind by the Pill's text field) and the shortcut is bold in the accent colour. Edits map straight through.
 */
class BangPillTransformation(private val bangs: Map<String, String>, private val style: SpanStyle) : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val n = typedBangLength(text.text, bangs)
        if (n == 0) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(THIN); append(text.subSequence(0, n)); append(GAP); append(text.subSequence(n, text.length))
            addStyle(style, 0, n + 2)
        }
        return TransformedText(out, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when { offset == 0 -> 0; offset <= n -> offset + 1; else -> offset + 2 }
            override fun transformedToOriginal(offset: Int) = when { offset <= 1 -> 0; offset <= n + 1 -> offset - 1; else -> (offset - 2).coerceAtMost(text.length) }
        })
    }
    private companion object { const val THIN = "\u2009"; const val GAP = "\u2002" } // en space: room between the pill and the search
}

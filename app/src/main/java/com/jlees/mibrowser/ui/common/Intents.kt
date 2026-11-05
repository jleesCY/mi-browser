// Handing text and links to other apps: the share sheet and the clipboard.
package com.jlees.mibrowser.ui.common

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.jlees.mibrowser.ui.common.*

fun shareText(ctx: android.content.Context, text: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (title != null) send.putExtra(Intent.EXTRA_TITLE, title)
    ctx.startActivity(Intent.createChooser(send, title))
}

fun copyText(ctx: android.content.Context, text: String) =
    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(android.content.ClipData.newPlainText("link", text))

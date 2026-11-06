// "Paste and go": the search drawer offers a link you just copied, as its first row.
package com.jlees.mibrowser.search

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Patterns
import android.view.textclassifier.TextClassifier

/**
 * Whether the clipboard holds a recently copied link. On Android 12+ reading the clipboard shows a system "pasted"
 * notice, so mi only looks at the clip's description there (Android's own link classification and when it was
 * copied) and reads the link when the user taps the row. Older Android shows no notice, so the link itself is read
 * and shown.
 */
object CopiedLink {
    /** Copied in the last 10 minutes: an old link on the clipboard isn't offered again and again. */
    private const val FRESH_MS = 10 * 60_000L

    /** A link to offer: [text] is the link, or null when it can't be known without reading the clipboard. */
    class Offer(val text: String?)

    fun peek(ctx: Context): Offer? {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return null
        val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return null
        if (!desc.hasMimeType("text/*") || !fresh(desc.timestamp)) return null
        if (Build.VERSION.SDK_INT >= 31) {
            val classified = desc.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
            return if (classified && desc.getConfidenceScore(TextClassifier.TYPE_URL) >= 0.5f) Offer(null) else null
        }
        return read(ctx)?.takeIf { Patterns.WEB_URL.matcher(it).matches() }?.let(::Offer)
    }

    /** The copied text (on Android 12+, this is what shows the "pasted" notice: only on the user's tap). */
    fun read(ctx: Context): String? = runCatching {
        ctx.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    // the description's timestamp is documented on elapsed time; wall-clock is accepted too, to be safe across versions
    private fun fresh(ts: Long) = ts == 0L ||
        (SystemClock.elapsedRealtime() - ts) in 0..FRESH_MS || (System.currentTimeMillis() - ts) in 0..FRESH_MS
}

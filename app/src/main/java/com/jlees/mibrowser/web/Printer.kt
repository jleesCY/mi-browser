package com.jlees.mibrowser.web

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.print.PrintManager
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter

/**
 * Prints a tab's page. The WebView must outlive window.close() until the print job is done: a tab closed while
 * printing is kept ([closeWhenDone]) and [onClosed] destroys it once the job finishes.
 */
class Printer(private val activity: Activity, private val onClosed: (id: String) -> Unit) {
    private val active = HashSet<String>()
    private val closeAfter = HashSet<String>()

    fun isPrinting(id: String) = id in active
    fun closeWhenDone(id: String) { closeAfter += id }

    fun print(id: String, wv: WebView) {
        if (id in active) return
        val title = wv.title?.takeIf { it.isNotBlank() } ?: "mi"
        active += id
        val adapter = KeepAlivePrintAdapter(wv.createPrintDocumentAdapter(title)) {
            active -= id
            if (closeAfter.remove(id)) onClosed(id) // the page closed its window while active
        }
        runCatching { (activity.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, adapter, null) }
            .onFailure { active -= id }
    }

    /** Delegates to WebView's adapter and reports when the print job no longer needs the WebView (onFinish). */
    private class KeepAlivePrintAdapter(private val inner: PrintDocumentAdapter, private val done: () -> Unit) : PrintDocumentAdapter() {
        override fun onStart() = inner.onStart()
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) =
            inner.onLayout(old, new, signal, cb, extras)
        override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, signal: CancellationSignal?, cb: WriteResultCallback) =
            inner.onWrite(pages, dest, signal, cb)
        override fun onFinish() { inner.onFinish(); done() }
    }
}

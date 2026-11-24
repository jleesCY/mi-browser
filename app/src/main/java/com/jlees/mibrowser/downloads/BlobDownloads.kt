// Files a page makes itself (blob: and data: addresses: file converters, editors, exports). mi's downloader can't fetch
// them from outside the page, so when a page starts downloading one, mi asks the page for it (assets/js/blob-download.js)
// and writes the pieces it sends into Downloads (or the chosen folder). Used by the browser window and app windows.
package com.jlees.mibrowser.downloads

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jlees.mibrowser.logic.uniqueId
import org.json.JSONObject
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * [folder]: where downloads go (null: Downloads). [onSaved] gets the finished file, [onFailed] the name of one that
 * couldn't be saved; both on the main thread.
 */
class BlobDownloads(
    private val ctx: Context,
    private val folder: () -> String?,
    private val onSaved: (DownloadItem) -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private class Incoming(val item: DownloadItem, val out: OutputStream)

    /** Files asked for (token → whether it's from an incognito tab) and those arriving; touched on [io] only, after asking. */
    private val asked = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val incoming = HashMap<String, Incoming>()
    /** One thread, in order: the pieces are written as they come, off the main thread. */
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Lets [wv]'s pages hand files over ([script]: blob-download.js). Needs WebView's message and start-script features. */
    fun attach(wv: WebView, script: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addWebMessageListener(wv, "miBlob", setOf("*")) { _, message, _, _, _ -> message.data?.let { data -> io.execute { handle(data) } } }
        WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
    }

    /** The page started downloading [url], a blob: or data: address: ask it for the file. */
    fun fetch(wv: WebView, url: String, incognito: Boolean) {
        val token = uniqueId()
        asked[token] = incognito
        wv.evaluateJavascript("window.__miBlob && window.__miBlob.save(${JSONObject.quote(url)}, ${JSONObject.quote(token)})", null)
    }

    internal fun handle(json: String) {
        val m = runCatching { JSONObject(json) }.getOrNull() ?: return
        val token = m.optString("token")
        when (m.optString("t")) {
            "start" -> {
                val incognito = asked.remove(token) ?: return // not one mi asked for: a page can't save files by itself
                val mime = m.optString("type").ifBlank { "application/octet-stream" }
                val name = m.optString("name").ifBlank { "download" + (MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: "") }
                runCatching {
                    val item = createDownloadFile(ctx, DownloadItem(
                        id = uniqueId(), url = "", fileName = name, mime = mime, total = m.optLong("size", -1),
                        startedAt = System.currentTimeMillis(), folder = folder(), incognito = incognito,
                    ))
                    incoming[token] = Incoming(item, ctx.contentResolver.openOutputStream(Uri.parse(item.uri))!!)
                }.onFailure { main.post { onFailed(name) } }
            }
            "chunk" -> {
                val f = incoming[token] ?: return
                runCatching { f.out.write(Base64.decode(m.optString("data"), Base64.NO_WRAP)) }.onFailure { fail(token) }
            }
            "end" -> {
                val f = incoming.remove(token) ?: return
                runCatching { f.out.close() }
                showDownloadFile(ctx, f.item)
                val done = f.item.copy(state = DownloadState.DONE, done = f.item.total.coerceAtLeast(0))
                main.post { onSaved(done) }
            }
            "fail" -> { asked.remove(token); fail(token, m.optString("name")) }
        }
    }

    /** A file that didn't arrive whole: its partial file goes. */
    private fun fail(token: String, name: String = "") {
        val f = incoming.remove(token)
        if (f != null) { runCatching { f.out.close() }; runCatching { ctx.contentResolver.delete(Uri.parse(f.item.uri), null, null) } }
        main.post { onFailed(f?.item?.fileName ?: name.ifBlank { "the file" }) }
    }
}

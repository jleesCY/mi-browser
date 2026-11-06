// mi's own downloader: fetches a file with the tab's cookies and identity, saves it into the public Downloads folder,
// and can pause, resume (HTTP Range), cancel and retry. Progress goes to DownloadsStore; DownloadService keeps
// transfers alive (and shows them) while mi is in the background.
package com.jlees.mibrowser.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.jlees.mibrowser.logic.uniqueId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class Downloader(context: Context, private val store: DownloadsStore) {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val jobs = HashMap<String, Job>()
    /** Session only, never saved: what the server needs to hand the file over again (sign-in cookies, identity). */
    private val headers = HashMap<String, Map<String, String>>()

    init { current = this }

    /** Starts downloading [url], asked from a page with [cookies] and [userAgent]. Returns the new download's id. */
    fun enqueue(url: String, cookies: String?, userAgent: String, contentDisposition: String?, mime: String?, length: Long, incognito: Boolean): String {
        val type = mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
            ?: MimeTypeMap.getFileExtensionFromUrl(url)?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: "application/octet-stream"
        val item = DownloadItem(
            id = uniqueId(), url = url, mime = type, total = if (length > 0) length else -1, startedAt = System.currentTimeMillis(),
            fileName = downloadFileName(url, contentDisposition, type) { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) },
            incognito = incognito,
        )
        headers[item.id] = buildMap { put("User-Agent", userAgent); cookies?.let { put("Cookie", it) } }
        store.add(item)
        resume(item.id)
        return item.id
    }

    fun pause(id: String) { jobs.remove(id)?.cancel(); store.update(id) { if (active) copy(state = DownloadState.PAUSED) else this } }


    /** Stops [id] (if running) and deletes its file and its entry. */
    fun cancel(id: String) { jobs.remove(id)?.cancel(); store[id]?.uri?.let(::deleteFile); store.remove(id) }


    /** A content:// address other apps can open the file through (granted with the intent), or null. */
    fun shareableUri(item: DownloadItem): Uri? {
        val u = Uri.parse(item.uri ?: return null)
        return if (u.scheme == "file") runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(u.path!!)) }.getOrNull() else u
    }


    /** Starts [id], or continues a paused or failed one where it stopped (from the start if the server can't resume). */
    fun resume(id: String) {
        if (jobs[id]?.isActive == true) return
        store.update(id) { copy(state = DownloadState.QUEUED, error = null) }
        jobs[id] = scope.launch { transfer(id) }
        DownloadService.ensureRunning(ctx)
    }

    private suspend fun transfer(id: String) {
        var item = store[id] ?: return
        try {
            var resumeFrom = if (item.uri != null) item.done else 0L
            val conn = open(item.url, headers[id].orEmpty(), resumeFrom)
            val code = conn.responseCode
            when {
                code == 416 && item.total > 0 && resumeFrom >= item.total -> { finish(item); return } // already complete
                code >= 400 -> throw IOException("Server error $code")
                code != 206 -> resumeFrom = 0 // the server sent the whole file: start over
            }
            val length = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
            val total = if (length < 0) -1 else resumeFrom + length
            if (item.uri == null) item = createFile(item)
            post(id) { copy(state = DownloadState.RUNNING, done = resumeFrom, total = if (total > 0) total else this.total, uri = item.uri, fileName = item.fileName) }

            var done = resumeFrom
            var lastPost = 0L
            conn.inputStream.use { input ->
                ctx.contentResolver.openOutputStream(Uri.parse(item.uri), if (resumeFrom > 0) "wa" else "wt")!!.use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive() // pause / cancel
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastPost > 250) { lastPost = now; val d = done; post(id) { copy(done = d) } }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("Connection lost")
            item = store[id] ?: return
            finish(item.copy(done = done, total = if (total > 0) total else done))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            post(id) { copy(state = DownloadState.FAILED, error = (e as? IOException)?.message?.takeIf { it.length < 60 } ?: "Connection problem") }
            DownloadService.notifyFinished(ctx, store[id] ?: item, this)
        } finally {
            main.post { jobs.remove(id); DownloadService.ensureRunning(ctx) }
        }
    }

    private fun finish(item: DownloadItem) {
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            ctx.contentResolver.update(Uri.parse(item.uri), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        post(item.id) { copy(state = DownloadState.DONE, done = item.done, total = item.total) }
        DownloadService.notifyFinished(ctx, item.copy(state = DownloadState.DONE), this)
    }

    /** GET [url] with [headers] (and a Range from [from]), following redirects, including http ⇄ https. */
    private fun open(url: String, headers: Map<String, String>, from: Long): HttpURLConnection {
        var target = URL(url)
        repeat(8) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000; readTimeout = 30_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (from > 0) setRequestProperty("Range", "bytes=$from-")
            }
            val code = c.responseCode
            if (code !in 300..399 || code == 304) return c
            val location = c.getHeaderField("Location") ?: return c
            c.disconnect()
            target = URL(target, location)
        }
        throw IOException("Too many redirects")
    }

    /** Creates the file in Downloads (hidden from other apps until finished, on Android 10+). */
    private fun createFile(item: DownloadItem): DownloadItem {
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }) ?: throw IOException("Couldn't create the file")
            // MediaStore renames a duplicate ("name (1).pdf"): show the name it got
            val name = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            return item.copy(uri = uri.toString(), fileName = name ?: item.fileName)
        }
        @Suppress("DEPRECATION") val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val name = uniqueFileName(item.fileName) { File(dir, it).exists() }
        val file = File(dir, name).apply { createNewFile() }
        return item.copy(uri = Uri.fromFile(file).toString(), fileName = name)
    }

    private fun deleteFile(uri: String) {
        val u = Uri.parse(uri)
        runCatching { if (u.scheme == "file") File(u.path!!).delete() else ctx.contentResolver.delete(u, null, null) }
    }

    /** Store updates happen on the main thread (it's Compose state the UI reads). */
    private fun post(id: String, f: DownloadItem.() -> DownloadItem) = main.post { store.update(id, f) }

    companion object {
        /** The app's downloader, for DownloadService (one per process; set when the ViewModel creates it). */
        @Volatile var current: Downloader? = null
            private set
        /** The store's items, for the service's notification. */
        fun items() = current?.store?.items.orEmpty()
    }
}

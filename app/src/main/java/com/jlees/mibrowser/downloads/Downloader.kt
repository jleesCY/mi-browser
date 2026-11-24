// mi's own downloader: fetches a file with the tab's cookies and identity, saves it into the public Downloads folder,
// and can pause, resume (HTTP Range), cancel and retry. A large file from a server that sends parts is fetched over
// several connections at once. Progress goes to DownloadsStore; DownloadService keeps
// transfers alive (and shows them) while mi is in the background.
package com.jlees.mibrowser.downloads

import android.content.Intent
import android.provider.DocumentsContract
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
import android.net.ConnectivityManager
import android.net.Network
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
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

/** Bytes read at a time: large reads mean fewer, cheaper writes on a fast connection. */
private const val BUFFER = 256 * 1024
/** Retries in a row a dropped download gets by itself before it waits for the user's Retry. */
private const val MAX_RETRIES = 5

class Downloader(context: Context, private val store: DownloadsStore) {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val jobs = HashMap<String, Job>()
    /** Session only, never saved: what the server needs to hand the file over again (sign-in cookies, identity). */
    private val headers = HashMap<String, Map<String, String>>()
    private val speeds = HashMap<String, Speedometer>()
    /** Downloads that failed on the connection, retried by themselves (with a growing wait) once there's a network. */
    private val retries = HashMap<String, Int>()
    private val network = ctx.getSystemService(ConnectivityManager::class.java)
    private var watching: ConnectivityManager.NetworkCallback? = null

    init { current = this }

    /**
     * Starts downloading [url], asked from a page with [cookies] and [userAgent]. It's saved in [folder] (a document
     * tree the user picked; null: Downloads), or exactly to [target] (a file chosen in the save dialog). Returns its id.
     */
    fun enqueue(
        url: String, cookies: String?, userAgent: String, contentDisposition: String?, mime: String?, length: Long, incognito: Boolean,
        folder: String? = null, target: Uri? = null, targetName: String? = null,
    ): String {
        val type = mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
            ?: MimeTypeMap.getFileExtensionFromUrl(url)?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: "application/octet-stream"
        val item = DownloadItem(
            id = uniqueId(), url = url, mime = type, total = if (length > 0) length else -1, startedAt = System.currentTimeMillis(),
            fileName = downloadFileName(url, contentDisposition, type) { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) },
            incognito = incognito, folder = folder,
        ).let { if (target != null) it.copy(uri = target.toString(), fileName = targetName ?: it.fileName) else it }
        headers[item.id] = buildMap { put("User-Agent", userAgent); cookies?.let { put("Cookie", it) } }
        store.add(item)
        resume(item.id)
        return item.id
    }

    /** Pauses every running download (Android 15+'s daily limit for background transfers ran out). */
    fun pauseAll() = store.items.filter { it.active }.forEach { pause(it.id) }

    fun pause(id: String) { stopRetrying(id); jobs.remove(id)?.cancel(); store.update(id) { if (active) copy(state = DownloadState.PAUSED) else this } }


    /** Stops [id] (if running) and deletes its file and its entry. */
    fun cancel(id: String) { stopRetrying(id); jobs.remove(id)?.cancel(); store[id]?.uri?.let(::deleteFile); store.remove(id) }


    /** Carries on the downloads that were running when mi was last closed (run once the list has loaded). */
    fun resumeInterrupted() = store.interrupted.forEach { id -> if (store[id]?.state == DownloadState.PAUSED) resume(id) }

    private fun speedOf(id: String, now: Long, done: Long) = synchronized(speeds) { speeds.getOrPut(id) { Speedometer() } }.reading(now, done)

    /**
     * Retries [id] by itself once there's a network: at once if there is one, after a growing wait (2, 4, 8… s),
     * at most [MAX_RETRIES] times in a row. False when it has used them up (it stays failed, for Retry).
     */
    private fun retryWhenOnline(id: String): Boolean {
        val n = (retries[id] ?: 0) + 1
        if (n > MAX_RETRIES) { stopRetrying(id); return false }
        retries[id] = n
        if (watching == null) watching = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(net: Network) { main.post { retryWaiting() } }
        }.also { runCatching { network.registerDefaultNetworkCallback(it) } }
        if (network.activeNetwork != null) retryWaiting() // online already (the connection, not the network, dropped)
        return true
    }

    /** A network is there: each waiting download goes again after its wait (resume ignores one already going). */
    private fun retryWaiting() {
        for ((id, n) in retries.toMap()) {
            main.postDelayed({ if (store[id]?.state == DownloadState.FAILED && id in retries) resume(id) }, 1000L shl n)
        }
    }

    /** [id] finished, or was paused or cancelled: no more retries; with none left waiting, stop listening for networks. */
    private fun stopRetrying(id: String) {
        retries.remove(id)
        if (retries.isEmpty()) watching?.let { runCatching { network.unregisterNetworkCallback(it) }; watching = null }
    }

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
            // fetched in parts before: every part goes on where it stopped
            if (item.parts != null && item.uri != null) {
                val file = openRw(item) ?: throw IOException("Can't open the file")
                started(id, item, item.parts.sumOf { it.done })
                transferParts(id, item, file, null); return
            }
            // resume from what's really in the file: the saved progress trails the last write by up to a second, and
            // appending from there would write some bytes twice
            var resumeFrom = item.uri?.let { fileSize(Uri.parse(it)) } ?: 0L
            val conn = open(item.url, headers[id].orEmpty(), resumeFrom)
            val code = conn.responseCode
            when {
                code == 416 && item.total > 0 && resumeFrom >= item.total -> { finish(item.copy(done = item.total)); return } // already complete
                code >= 400 -> throw IOException("Server error $code")
                code != 206 -> resumeFrom = 0 // the server sent the whole file: start over
            }
            val length = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
            val total = if (length < 0) -1 else resumeFrom + length
            if (item.uri == null) {
                if (total > 0 && item.folder == null && total > freeSpace()) throw IOException("ENOSPC")
                item = createDownloadFile(ctx, item)
            }
            // a large file from a server that hands out parts (it answered the Range from byte 0): several connections
            if (code == 206 && resumeFrom == 0L && total >= PARTS_FROM) openRw(item)?.let { file ->
                item = item.copy(parts = planParts(total), total = total)
                started(id, item, 0)
                transferParts(id, item, file, conn); return
            }
            started(id, item.copy(total = if (total > 0) total else item.total), resumeFrom)

            var done = resumeFrom
            var lastPost = 0L
            conn.inputStream.use { input ->
                ctx.contentResolver.openOutputStream(Uri.parse(item.uri), if (resumeFrom > 0) "wa" else "wt")!!.use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive() // pause / cancel
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastPost > 250) { lastPost = now; val d = done; val v = speedOf(id, now, d); post(id) { copy(done = d, speed = v) } }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException(CONNECTION_LOST)
            item = store[id] ?: return
            finish(item.copy(done = done, total = if (total > 0) total else done))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = downloadError(e)
            main.post {
                store.update(id) { copy(state = DownloadState.FAILED, error = error) }
                // a dropped connection: tried again by itself; anything else (or out of tries) waits for Retry
                if (retriesItself(error) && retryWhenOnline(id)) store.update(id) { copy(error = RETRYING) }
                else DownloadService.notifyFinished(ctx, store[id] ?: item, this)
            }
        } finally {
            main.post { jobs.remove(id); synchronized(speeds) { speeds.remove(id) }; DownloadService.ensureRunning(ctx) }
        }
    }

    /** The server answered: running (unless paused or cancelled before this arrived), with the file's details. */
    private fun started(id: String, item: DownloadItem, done: Long) = post(id) {
        val running = jobs[id]?.isActive == true
        copy(state = if (running) DownloadState.RUNNING else state, done = done, total = item.total, uri = item.uri, fileName = item.fileName, parts = item.parts)
    }

    /**
     * Fetches [item]'s unfinished parts over [DOWNLOAD_CONNECTIONS] connections, each written at its own place in
     * [file]; [first] (already open from byte 0) serves the first part. A server that refuses the extra connections
     * (too many at once) leaves the parts to the ones it accepted.
     */
    private suspend fun transferParts(id: String, item: DownloadItem, file: ParcelFileDescriptor, first: HttpURLConnection?) {
        val parts = item.parts!!
        val done = AtomicLongArray(parts.size).apply { parts.forEachIndexed { i, p -> set(i, p.done) } }
        fun report() {
            val now = parts.mapIndexed { i, p -> p.copy(done = done[i]) }
            val sum = now.sumOf { it.done }
            val v = speedOf(id, System.currentTimeMillis(), sum)
            post(id) { copy(parts = now, done = sum, speed = v) }
        }
        val queue = ConcurrentLinkedQueue(parts.indices.filter { !parts[it].finished && !(it == 0 && first != null) })
        val workers = AtomicInteger(DOWNLOAD_CONNECTIONS)
        file.use {
            val out = FileOutputStream(file.fileDescriptor).channel
            coroutineScope {
                val reporter = launch { while (true) { delay(250); report() } }
                try {
                    List(DOWNLOAD_CONNECTIONS) { w ->
                        launch(Dispatchers.IO) {
                            val buf = ByteArray(BUFFER)
                            try {
                                if (w == 0 && first != null) fetchPart(id, item.url, parts[0], 0, done, out, buf, first)
                                while (true) {
                                    val i = queue.poll() ?: break
                                    try { fetchPart(id, item.url, parts[i], i, done, out, buf, null) }
                                    catch (e: PartRefused) { queue.add(i); if (workers.get() > 1) break else throw IOException("Server error ${e.code}") }
                                }
                            } finally { workers.decrementAndGet() }
                        }
                    }.joinAll()
                } finally { reporter.cancel(); report() }
            }
        }
        if (parts.indices.any { parts[it].start + done[it] < parts[it].end }) throw IOException(CONNECTION_LOST)
        finish(item.copy(done = item.total))
    }

    private class PartRefused(val code: Int) : IOException()

    /**
     * Fetches the rest of [part] (number [i]; its progress in [done]) into [out] at its place, over [opened] or a new
     * connection asking for just those bytes. A dropped connection is retried, three times, waiting a little longer each time.
     */
    private suspend fun fetchPart(
        id: String, url: String, part: DownloadPart, i: Int, done: AtomicLongArray, out: FileChannel, buf: ByteArray, opened: HttpURLConnection?,
    ) {
        var conn = opened
        var retries = 0
        while (part.start + done[i] < part.end) {
            var c: HttpURLConnection? = null
            try {
                c = conn ?: open(url, headers[id].orEmpty(), part.start + done[i], part.end - 1)
                conn = null
                if (c.responseCode != 206) throw PartRefused(c.responseCode)
                c.inputStream.use { input ->
                    var pos = part.start + done[i]
                    while (pos < part.end) {
                        coroutineContext.ensureActive() // pause / cancel
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), part.end - pos).toInt())
                        if (n < 0) throw IOException(CONNECTION_LOST)
                        val b = ByteBuffer.wrap(buf, 0, n)
                        while (b.hasRemaining()) out.write(b, pos + b.position())
                        pos += n
                        done[i] = pos - part.start
                    }
                }
            } catch (e: PartRefused) { throw e
            } catch (e: IOException) {
                if (e.message?.contains("ENOSPC") == true || ++retries > 3) throw e
                delay(1000L shl retries)
            } finally { c?.disconnect() }
        }
    }

    private fun finish(item: DownloadItem) {
        main.post { stopRetrying(item.id) }
        showDownloadFile(ctx, item)
        post(item.id) { copy(state = DownloadState.DONE, done = item.done, total = item.total, parts = null) }
        DownloadService.notifyFinished(ctx, item.copy(state = DownloadState.DONE), this)
    }

    /**
     * GET [url] with [headers] for bytes [from] to [to] (to the end when -1), following redirects, including
     * http ⇄ https. Asking for a range even from byte 0 shows whether the server can send parts (it answers 206).
     */
    private fun open(url: String, headers: Map<String, String>, from: Long, to: Long = -1): HttpURLConnection {
        var target = URL(url)
        repeat(8) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000; readTimeout = 30_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                setRequestProperty("Range", "bytes=$from-" + if (to >= 0) "$to" else "")
            }
            val code = c.responseCode
            if (code !in 300..399 || code == 304) return c
            val location = c.getHeaderField("Location") ?: return c
            c.disconnect()
            target = URL(target, location)
        }
        throw IOException("Too many redirects")
    }

    /**
     * Finished downloads whose file was deleted outside mi (Files app, gallery) leave the list; run when the list
     * loads and when the Downloads page opens.
     */
    fun pruneMissing() = scope.launch {
        val gone = store.items.filter { it.state == DownloadState.DONE && it.uri != null && !exists(Uri.parse(it.uri)) }.map { it.id }.toSet()
        if (gone.isNotEmpty()) main.post { gone.forEach(store::remove) }
    }

    /** The file opened to be written anywhere in (parts land at their own places), or null if its provider can't. */
    private fun openRw(item: DownloadItem): ParcelFileDescriptor? = runCatching { ctx.contentResolver.openFileDescriptor(Uri.parse(item.uri), "rw") }.getOrNull()

    /** Room left in Downloads (a full disk fails the download up front, not 9 GB in). */
    @Suppress("DEPRECATION")
    private fun freeSpace(): Long = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).usableSpace.takeIf { it > 0 } ?: Long.MAX_VALUE

    /** The bytes already in the file (0 if it can't be read: the download then starts over). */
    private fun fileSize(u: Uri): Long = runCatching {
        if (u.scheme == "file") File(u.path!!).length() else ctx.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize }
    }.getOrNull()?.takeIf { it >= 0 } ?: 0L

    /** Whether the file is still there. Unknown (no permission any more, say) counts as there: never prune on a guess. */
    private fun exists(u: Uri): Boolean = when {
        u.scheme == "file" -> File(u.path ?: return false).exists()
        else -> try { ctx.contentResolver.query(u, arrayOf(BaseColumns._ID), null, null, null)?.use { it.count > 0 } ?: true }
        catch (_: java.io.FileNotFoundException) { false } catch (_: IllegalArgumentException) { false } catch (_: Exception) { true }
    }

    private fun deleteFile(uri: String) {
        val u = Uri.parse(uri)
        runCatching {
            when {
                u.scheme == "file" -> File(u.path!!).delete()
                DocumentsContract.isDocumentUri(ctx, u) -> {
                    DocumentsContract.deleteDocument(ctx.contentResolver, u) // a picked folder / save dialog
                    // a file chosen in the save dialog held its own lasting access: give it back (a no-op otherwise)
                    runCatching { ctx.contentResolver.releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                }
                else -> ctx.contentResolver.delete(u, null, null)
            }
        }
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

/** Creates [item]'s file in Downloads, or the folder it names (hidden from other apps until finished, on Android 10+). */
internal fun createDownloadFile(ctx: Context, item: DownloadItem): DownloadItem {
    if (item.folder != null) { // a folder the user picked (its provider renames a duplicate itself)
        val tree = Uri.parse(item.folder)
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = runCatching { DocumentsContract.createDocument(ctx.contentResolver, dir, item.mime, item.fileName) }.getOrNull()
            ?: throw IOException("Can't save to the chosen folder")
        val name = runCatching {
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return item.copy(uri = doc.toString(), fileName = name ?: item.fileName)
    }
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

/** A finished download's file: shown to other apps (gallery, Files) now that it's whole. */
internal fun showDownloadFile(ctx: Context, item: DownloadItem) {
    if (Build.VERSION.SDK_INT >= 29 && Uri.parse(item.uri).authority == MediaStore.AUTHORITY) runCatching {
        ctx.contentResolver.update(Uri.parse(item.uri), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
}

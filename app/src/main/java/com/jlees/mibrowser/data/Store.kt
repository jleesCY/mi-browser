// JSON-file persistence (one file per key in app-private storage).
package com.jlees.mibrowser.data

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.encodeToStream

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }

/** One JSON file per key in app-private storage, written atomically. */
class JsonStore(private val dir: File) {
    init { dir.mkdirs() }

    /**
     * The value saved under [key], or null when there's none. A file that's there but can't be read (damaged, or written
     * by something this version can't make sense of) is moved aside rather than left for the next save to overwrite: the
     * app starts without it, and the file stays in the data folder for a later version, or a person, to recover.
     */
    fun <T> read(key: String, s: KSerializer<T>): T? {
        val f = File(dir, "$key.json")
        if (!f.exists()) return null
        return try { json.decodeFromString(s, f.readText()) } catch (_: Exception) {
            f.renameTo(File(dir, "$key.json.broken-${System.currentTimeMillis()}"))
            null
        }
    }

    /**
     * A failed write (storage full) keeps the last good file; the next change tries again. Written as a stream, never as
     * one string, so big values (an extension's megabytes of saved data) don't need twice their size in memory. A [key]
     * may have folders ("extension_storage/<id>").
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Synchronized
    fun <T> write(key: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$key.json.tmp")
        try {
            tmp.parentFile?.mkdirs()
            tmp.writeSynced { json.encodeToStream(s, value, it) }
            tmp.renameTo(File(dir, "$key.json"))
        } catch (_: IOException) { tmp.delete() }
    }

    /**
     * Whether a file was kept aside (see [read]) since this was last asked, by any copy of the store: a background job
     * (price checks, a home-screen app's window) reads settings with its own, so the app says so at its next launch.
     */
    fun newlyKeptAside(): Boolean {
        val seen = File(dir, "kept_aside_seen")
        val last = runCatching { seen.readText().toLong() }.getOrDefault(0L)
        val newest = dir.listFiles()?.mapNotNull { it.name.substringAfter(".json.broken-", "").toLongOrNull() }?.maxOrNull() ?: return false
        if (newest <= last) return false
        runCatching { seen.writeText(newest.toString()) }
        return true
    }

    fun delete(key: String) { File(dir, "$key.json").delete() }
    /** A folder of its own inside the store (one that keeps its own files: extension_storage). */
    fun folder(name: String) = File(dir, name).apply { mkdirs() }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

/**
 * One stored file, owned by the store whose state it saves: written [delayMs] after that state changes (off the main
 * thread) once [launchIn] runs, or right away by [writeNow] (the app is closing). The wait isn't restarted by further
 * changes: state that keeps changing (a download's progress) is still written every [delayMs], not only once it stops.
 */
class Saver<T>(private val delayMs: Long, private val read: () -> T, private val write: (T) -> Unit) {
    /** The value on disk: the one loaded, then the last one written. */
    @Volatile private var written: Any? = null

    /**
     * Writes the state now if it differs from what's on disk (leaving the app does this every time). Compared by value:
     * some stores build a new list on each read (tabs, downloads), and an equal one needs no write.
     */
    fun writeNow() { val v = read(); if (v != written) { write(v); written = v } }

    fun launchIn(scope: CoroutineScope) = scope.launch {
        written = read()
        snapshotFlow(read).drop(1).conflate().collect { delay(delayMs); val v = read(); withContext(Dispatchers.IO) { write(v) }; written = v }
    }
}

/**
 * Writes this file and waits until its bytes are on disk before returning: a phone switched off (or an app killed)
 * right after leaves the whole file or none of it, never an empty one that a rename then puts in place of good data.
 */
fun File.writeSynced(write: (java.io.OutputStream) -> Unit) = java.io.FileOutputStream(this).use { fos ->
    val out = fos.buffered()
    write(out)
    out.flush()
    fos.fd.sync()
}

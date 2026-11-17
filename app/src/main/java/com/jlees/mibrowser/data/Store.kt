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

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }

/** One JSON file per key in app-private storage, written atomically. */
class JsonStore(private val dir: File) {
    init { dir.mkdirs() }

    fun <T> read(key: String, s: KSerializer<T>): T? = try {
        File(dir, "$key.json").takeIf { it.exists() }?.readText()?.let { json.decodeFromString(s, it) }
    } catch (_: Exception) { null }

    /** A failed write (storage full) keeps the last good file; the next change tries again. */
    @Synchronized
    fun <T> write(key: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$key.json.tmp")
        try {
            tmp.writeText(json.encodeToString(s, value))
            tmp.renameTo(File(dir, "$key.json"))
        } catch (_: IOException) { tmp.delete() }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

/**
 * One stored file, owned by the store whose state it saves: written [delayMs] after that state changes (off the main
 * thread) once [launchIn] runs, or right away by [writeNow] (the app is closing). The wait isn't restarted by further
 * changes: state that keeps changing (a download's progress) is still written every [delayMs], not only once it stops.
 */
class Saver<T>(private val delayMs: Long, private val read: () -> T, private val write: (T) -> Unit) {
    fun writeNow() = write(read())
    fun launchIn(scope: CoroutineScope) = scope.launch {
        snapshotFlow(read).drop(1).conflate().collect { delay(delayMs); val v = read(); withContext(Dispatchers.IO) { write(v) } }
    }
}

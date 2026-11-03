// JSON-file persistence (one file per key in app-private storage).
package com.jlees.mibrowser.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }

/** One JSON file per AsyncStorage key, written atomically. */
class JsonStore(private val dir: File) {
    init { dir.mkdirs() }

    fun <T> read(key: String, s: KSerializer<T>): T? = try {
        File(dir, "$key.json").takeIf { it.exists() }?.readText()?.let { json.decodeFromString(s, it) }
    } catch (_: Exception) { null }

    @Synchronized
    fun <T> write(key: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(json.encodeToString(s, value))
        tmp.renameTo(File(dir, "$key.json"))
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

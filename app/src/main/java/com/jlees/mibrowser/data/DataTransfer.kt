// Settings backup: .midata = zip { data.json: {settings, and other parts by name (follows: followed prices)} }. Older backups may also hold a background image; it is ignored.
package com.jlees.mibrowser.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportError(val title: String, message: String) : Exception(message)

/** A backup read back: its settings, and the whole of data.json for the other parts (each feature reads its own). */
class Backup(val settings: Settings, val data: JsonObject)


object DataTransfer {
    /** Saves [settings] and the [parts] given (name → data) to [uri]. */
    fun exportTo(ctx: Context, uri: Uri, settings: Settings, parts: Map<String, JsonElement> = emptyMap()) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val data = JsonObject(mapOf("settings" to json.encodeToJsonElement(settings)) + parts)
                zip.putNextEntry(ZipEntry("data.json")); zip.write(data.toString().toByteArray()); zip.closeEntry()
            }
        } ?: error("Could not open file")
    }

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: ""

    /** Validates and parses a .midata file. */
    fun import(ctx: Context, uri: Uri): Backup {
        val name = displayName(ctx, uri)
        if (!name.endsWith(".midata") && !name.endsWith(".zip")) throw ImportError("Invalid File", "Please select a valid .midata file.")
        var raw: ByteArray? = null
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (raw == null) { val e = zip.nextEntry ?: break; if (e.name == "data.json") raw = zip.readBytes() }
            }
        }
        raw ?: throw ImportError("Invalid Data", "The selected file does not contain valid app data.")
        val obj = json.parseToJsonElement(String(raw)).jsonObject
        val settingsEl = obj["settings"]?.takeIf { it is JsonObject } ?: throw ImportError("Invalid Data", "Structure mismatch.")
        return Backup(json.decodeFromJsonElement<Settings>(settingsEl), obj)
    }
}

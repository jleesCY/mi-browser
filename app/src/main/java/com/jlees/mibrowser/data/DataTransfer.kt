// Settings backup: .midata = zip { data.json: {settings, and other parts by name (follows: followed prices)} }. Older backups may also hold a background image; it is ignored.
package com.jlees.mibrowser.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportError(val title: String, message: String) : Exception(message)

/** A backup read back: its settings, and the whole of data.json for the other parts (each feature reads its own). */
class Backup(val settings: Settings, val data: JsonObject)


object DataTransfer {
    /** Saves [settings] and the [parts] given (name → data) to [uri]. */
    fun exportTo(ctx: Context, uri: Uri, settings: Settings, parts: Map<String, JsonElement>) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val data = JsonObject(mapOf("settings" to json.encodeToJsonElement(settings)) + parts)
                zip.putNextEntry(ZipEntry("data.json")); zip.write(data.toString().toByteArray()); zip.closeEntry()
            }
        } ?: error("Could not open file")
    }

    /** Validates and parses a .midata file. Its name isn't checked (pickers and cloud apps drop or change the extension): anything without a data.json isn't one. */
    fun import(ctx: Context, uri: Uri): Backup {
        var raw: ByteArray? = null
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (raw == null) { val e = zip.nextEntry ?: break; if (e.name == "data.json") raw = zip.readBytes() }
            }
        }
        raw ?: throw ImportError("Invalid File", "Please select a .midata file exported from mi.")
        val obj = runCatching { json.parseToJsonElement(String(raw!!)).jsonObject }.getOrNull() ?: throw ImportError("Invalid Data", "The selected file does not contain valid app data.")
        val settingsEl = obj["settings"]?.takeIf { it is JsonObject } ?: throw ImportError("Invalid Data", "Structure mismatch.")
        return Backup(json.decodeFromJsonElement<Settings>(settingsEl), obj)
    }
}

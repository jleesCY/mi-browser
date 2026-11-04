// Settings backup: .midata = zip { data.json: {settings} }. Older backups may also hold a background image; it is ignored.
package com.jlees.mibrowser.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportError(val title: String, message: String) : Exception(message)


object DataTransfer {
    fun exportTo(ctx: Context, uri: Uri, settings: Settings) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val data = JsonObject(mapOf("settings" to json.encodeToJsonElement(settings)))
                zip.putNextEntry(ZipEntry("data.json")); zip.write(data.toString().toByteArray()); zip.closeEntry()
            }
        } ?: error("Could not open file")
    }

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: ""

    /** Validates and parses a .midata file. */
    fun import(ctx: Context, uri: Uri): Settings {
        val name = displayName(ctx, uri)
        if (!name.endsWith(".midata") && !name.endsWith(".zip")) throw ImportError("Invalid File", "Please select a valid .midata file.")
        val files = HashMap<String, ByteArray>()
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && !e.name.contains("..")) files[e.name] = zip.readBytes()
                }
            }
        }
        val raw = files["data.json"] ?: throw ImportError("Invalid Data", "The selected file does not contain valid app data.")
        val obj = json.parseToJsonElement(String(raw)).jsonObject
        val settingsEl = obj["settings"]?.takeIf { it is JsonObject } ?: throw ImportError("Invalid Data", "Structure mismatch.")
        return json.decodeFromJsonElement<Settings>(settingsEl)
    }
}

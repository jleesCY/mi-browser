// Port of legacy/src/utils/dataTransfer.ts — .midata = zip { data.json: {settings, ignoredHosts, backgroundImageName}, background.<ext> }
package com.jlees.mibrowser.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportError(val title: String, message: String) : Exception(message)

data class ImportedData(val settings: Settings, val ignoredHosts: List<String>?)

object DataTransfer {
    fun exportTo(ctx: Context, uri: Uri, settings: Settings, ignoredHosts: List<String>) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                var bgName: String? = null
                settings.homeBackgroundImage?.let { File(it.removePrefix("file://")) }?.takeIf { it.exists() }?.let { f ->
                    bgName = "background.${f.extension.ifEmpty { "jpg" }}"
                    zip.putNextEntry(ZipEntry(bgName)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                val data = JsonObject(mapOf(
                    "settings" to json.encodeToJsonElement(settings),
                    "ignoredHosts" to JsonArray(ignoredHosts.map { JsonPrimitive(it) }),
                    "backgroundImageName" to (bgName?.let { JsonPrimitive(it) } ?: JsonNull),
                ))
                zip.putNextEntry(ZipEntry("data.json")); zip.write(data.toString().toByteArray()); zip.closeEntry()
            }
        } ?: error("Could not open file")
    }

    private fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: ""

    /** Validates and parses a .midata file; the background image is copied into filesDir. */
    fun import(ctx: Context, uri: Uri): ImportedData {
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
        var settings = json.decodeFromJsonElement<Settings>(settingsEl)
        val hosts = (obj["ignoredHosts"] as? JsonArray)?.jsonArray?.map { it.jsonPrimitive.content }
        val bgName = (obj["backgroundImageName"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        settings = if (bgName != null && files[bgName] != null) {
            val dest = File(ctx.filesDir, "bg_${System.currentTimeMillis()}_${File(bgName).name}")
            dest.writeBytes(files.getValue(bgName))
            settings.copy(homeBackgroundImage = dest.path)
        } else settings.copy(homeBackgroundImage = null)
        return ImportedData(settings, hosts)
    }
}

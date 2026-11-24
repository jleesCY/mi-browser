// An extension's files: a Firefox .xpi (a zip), unpacked into
// mi's storage, and read: its manifest (with its own words in the phone's language), its messages for chrome.i18n, and
// what its code uses, for the rating shown before installing.
package com.jlees.mibrowser.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

/** An extension read from its folder: what it is, its words, and how well it works in mi. */
class ExtensionInfo(val id: String, val dir: File, val manifest: Manifest, val messages: Map<String, String>, val compatibility: Compatibility) {
    val name get() = localize(manifest.name, messages)
    val description get() = localize(manifest.description, messages)
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object ExtensionPackage {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true; allowComments = true }

    /** Unpacks [bytes] (an .xpi, a zip) into [dir], replacing what was there. Paths that would leave [dir] are refused. */
    fun unpack(bytes: ByteArray, dir: File) {
        dir.deleteRecursively(); dir.mkdirs()
        val root = dir.canonicalFile
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(dir, e.name).canonicalFile
                require(out.path.startsWith(root.path + File.separator)) { "unsafe path in package: ${e.name}" }
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zip.copyTo(it) } }
            }
        }
        // some packages keep everything in one folder at the top: use that folder as the extension's root
        if (!File(dir, "manifest.json").exists()) {
            val inner = dir.listFiles()?.singleOrNull { it.isDirectory && File(it, "manifest.json").exists() } ?: error("no manifest.json")
            val tmp = File(dir.parentFile, dir.name + ".tmp"); inner.renameTo(tmp); dir.deleteRecursively(); tmp.renameTo(dir)
        }
    }

    /** The extension's messages (chrome.i18n) in the phone's language, else its default; keys lowercased as Chrome compares them. */
    fun messages(dir: File, defaultLocale: String?, locale: Locale = Locale.getDefault()): Map<String, String> {
        val candidates = listOfNotNull("${locale.language}_${locale.country}", locale.language, defaultLocale, "en", "en_US")
        val out = HashMap<String, String>()
        // the default fills in what the phone's language doesn't have
        for (loc in candidates.reversed()) {
            val f = File(dir, "_locales/$loc/messages.json").takeIf { it.exists() } ?: continue
            val o = runCatching { lenient.parseToJsonElement(f.readText().removePrefix("﻿")).jsonObject }.getOrNull() ?: continue
            for ((k, v) in o) {
                val entry = v as? JsonObject ?: continue
                var msg = entry["message"].str() ?: continue
                // named placeholders ($name$) become their content ($1 or text)
                (entry["placeholders"] as? JsonObject)?.forEach { (pk, pv) ->
                    val content = pv.objOrNull()?.get("content").str() ?: return@forEach
                    msg = msg.replace("$$pk$", content, ignoreCase = true)
                }
                out[k.lowercase()] = msg
            }
        }
        return out
    }

    /** Reads an unpacked extension in [dir]: its manifest, words and rating. */
    fun inspect(dir: File, id: String? = null): ExtensionInfo {
        val raw = lenient.parseToJsonElement(File(dir, "manifest.json").readText().removePrefix("﻿")).jsonObject
        val manifest = parseManifest(raw)
        val messages = messages(dir, manifest.defaultLocale)
        val (apis, flags) = scanned(dir, manifest)
        val name = localize(manifest.name, messages); val description = localize(manifest.description, messages)
        val compat = compatibility(manifest, apis, flags, looksLikePasswordManager(name, description))
        return ExtensionInfo(id ?: extensionId(manifest.geckoId, name), dir, manifest, messages, compat)
    }

    /** Where an extension's scan is kept, in its own folder (gone with it; a new version is a new folder's files). */
    private const val SCAN_FILE = "_mi_scan.json"

    @kotlinx.serialization.Serializable
    private class Scan(val version: String, val apis: Set<String>, val dynamicApi: Boolean, val contentEval: Boolean)

    /**
     * The APIs [dir]'s code uses and how it reaches them: read from its kept scan when it's for this version, else
     * scanned now (one file at a time: a big extension's tens of megabytes never sit in memory at once) and kept, so
     * mi's start doesn't read every extension's code again. The rating is still worked out from it each time.
     */
    private fun scanned(dir: File, manifest: Manifest): Pair<Set<String>, CodeFlags> {
        val file = File(dir, SCAN_FILE)
        runCatching { lenient.decodeFromString(Scan.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.version == manifest.versionName }?.let { return it.apis to CodeFlags(it.dynamicApi, it.contentEval) }
        val all = dir.walkTopDown().filter { it.isFile && (it.extension == "js" || it.extension == "mjs") }.toList()
        val contentFiles = manifest.contentScripts.flatMap { it.js }.map { File(dir, it.trimStart('/')).canonicalFile }.toSet()
        val apis = HashSet<String>(); var flags = CodeFlags()
        for (f in all) {
            val code = runCatching { f.readText() }.getOrNull() ?: continue
            apis += scanApis(code)
            val ff = scanFlags(code, if (f.canonicalFile in contentFiles) code else "")
            flags = CodeFlags(flags.dynamicApi || ff.dynamicApi, flags.contentEval || ff.contentEval)
        }
        runCatching { file.writeText(lenient.encodeToString(Scan.serializer(), Scan(manifest.versionName, apis, flags.dynamicApi, flags.contentEval))) }
        return apis to flags
    }
}

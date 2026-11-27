package com.jlees.mibrowser

import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.extensions.ExtensionPackage
import com.jlees.mibrowser.extensions.ExtensionsStore
import com.jlees.mibrowser.extensions.Fit
import com.jlees.mibrowser.extensions.InstalledExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun tabMenuOrder() {
        val s = ExtensionsStore(JsonStore(tmp.newFolder())).apply { load() }
        listOf("a", "b", "c", "d").forEach { s.add(InstalledExtension(it, it, "1")) }
        // only a, c and d have buttons: dragging d to the front reorders those three, b keeps its place
        s.reorder(listOf("a", "c", "d"), from = 2, to = 0)
        assertEquals(listOf("d", "b", "a", "c"), s.list.map { it.id })
        // quick tiles: the saved order, then any tile it doesn't name
        assertEquals(listOf(com.jlees.mibrowser.data.QuickTile.FIND, com.jlees.mibrowser.data.QuickTile.DESKTOP, com.jlees.mibrowser.data.QuickTile.READER, com.jlees.mibrowser.data.QuickTile.ADS, com.jlees.mibrowser.data.QuickTile.GROUP, com.jlees.mibrowser.data.QuickTile.CONTAINER), // tiles added since come after the saved order
            com.jlees.mibrowser.data.QuickTile.ordered(listOf(com.jlees.mibrowser.data.QuickTile.FIND, com.jlees.mibrowser.data.QuickTile.DESKTOP)))
    }

    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun packagesUnpackAndRate() {
        val files = mapOf(
            "manifest.json" to """{"manifest_version":3,"name":"__MSG_name__","version":"1.2","default_locale":"en","permissions":["storage"],
                "content_scripts":[{"matches":["<all_urls>"],"js":["c.js"]}]}""",
            "_locales/en/messages.json" to """{"name":{"message":"Hello Ext"}}""",
            "c.js" to "chrome.storage.local.get('a'); chrome.runtime.sendMessage({});",
        )
        val dir = File(tmp.root, "ext")
        ExtensionPackage.unpack(zip(files), dir)
        val info = ExtensionPackage.inspect(dir, id = "hello-ext")
        assertEquals("Hello Ext", info.name)
        assertEquals("hello-ext", info.id)
        assertEquals(Fit.WORKS, info.compatibility.fit)
        // its code is scanned once and kept: reading it again (mi starting) uses the kept scan for this version
        assertTrue(File(dir, "_mi_scan.json").exists())
        File(dir, "c.js").writeText("chrome.runtime.connectNative('x')") // (a change the kept scan doesn't see)
        assertEquals(Fit.WORKS, ExtensionPackage.inspect(dir, id = "hello-ext").compatibility.fit)
        // another version is scanned again
        File(dir, "manifest.json").writeText(files.getValue("manifest.json").replace("\"1.2\"", "\"1.3\""))
        assertEquals(Fit.MAYBE, ExtensionPackage.inspect(dir, id = "hello-ext").compatibility.fit)
        // a package whose files are inside one folder: that folder becomes the extension
        val nested = File(tmp.root, "nested")
        ExtensionPackage.unpack(zip(files.mapKeys { "inner/${it.key}" }), nested)
        assertTrue(File(nested, "manifest.json").exists())
        // a path that would leave the folder is refused
        val evil = runCatching { ExtensionPackage.unpack(zip(mapOf("../evil.txt" to "x", "manifest.json" to "{}")), File(tmp.root, "evil")) }
        assertTrue(evil.isFailure); assertFalse(File(tmp.root, "evil.txt").exists())
    }
}

package com.jlees.mibrowser

import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.extensions.ExtensionPackage
import com.jlees.mibrowser.extensions.ExtensionsStore
import com.jlees.mibrowser.extensions.Fit
import com.jlees.mibrowser.extensions.InstalledExtension
import com.jlees.mibrowser.extensions.StorageArea
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun storageAndChanges() {
        val dir = tmp.newFolder()
        val s = ExtensionsStore(JsonStore(dir)).apply { load() }
        s.add(InstalledExtension("e1", "One", "1.0", fit = Fit.WORKS))
        // a change reports old and new values (chrome.storage.onChanged)
        val c1 = s.write("e1", StorageArea.LOCAL, null, mapOf("a" to JsonPrimitive(1)))
        assertEquals(mapOf("a" to (null to JsonPrimitive(1))), c1)
        assertEquals(JsonPrimitive(1), s.read("e1", StorageArea.LOCAL)["a"])
        // setting the same value again changes nothing (no event)
        assertTrue(s.write("e1", StorageArea.LOCAL, null, mapOf("a" to JsonPrimitive(1))).isEmpty())
        // incognito: changes are seen there, kept in memory, and the saved data is untouched
        s.write("e1", StorageArea.LOCAL, "incognito-1", mapOf("a" to JsonPrimitive(2)))
        assertEquals(JsonPrimitive(2), s.read("e1", StorageArea.LOCAL, "incognito-1")["a"])
        assertEquals(JsonPrimitive(1), s.read("e1", StorageArea.LOCAL)["a"])
        s.forgetScope("incognito-1")
        assertEquals(JsonPrimitive(1), s.read("e1", StorageArea.LOCAL, "incognito-1")["a"]) // gone with its profile
        // session storage is never written
        s.write("e1", StorageArea.SESSION, null, mapOf("s" to JsonPrimitive("x")))
        s.savers.forEach { it.writeNow() }
        val again = ExtensionsStore(JsonStore(dir)).apply { load() }
        assertEquals(JsonPrimitive(1), again.read("e1", StorageArea.LOCAL)["a"])
        assertTrue(again.read("e1", StorageArea.SESSION).isEmpty())
        assertEquals("One", again["e1"]?.name)
        // removing an extension takes its data with it; Undo puts both back
        val kept = again["e1"]!!; val data = again.savedData("e1")
        again.remove("e1"); assertTrue(again.read("e1", StorageArea.LOCAL).isEmpty()); assertEquals(null, again["e1"])
        again.restore(kept, data); assertEquals(JsonPrimitive(1), again.read("e1", StorageArea.LOCAL)["a"]); assertEquals("One", again["e1"]?.name)
        // remove and clear
        s.write("e1", StorageArea.SYNC, null, mapOf("x" to JsonPrimitive(1), "y" to JsonPrimitive(2)))
        s.write("e1", StorageArea.SYNC, null, emptyMap(), removed = listOf("x"))
        assertEquals(setOf("y"), s.read("e1", StorageArea.SYNC).keys)
        s.write("e1", StorageArea.SYNC, null, emptyMap(), clear = true)
        assertTrue(s.read("e1", StorageArea.SYNC).isEmpty())
    }

    @Test fun dataFilePerExtension() {
        val dir = tmp.newFolder()
        // the old single file is split into one per extension on the first start
        java.io.File(dir, "extension_storage.json").writeText("""{"e1":{"LOCAL":{"a":1}},"e2":{"LOCAL":{"b":2}}}""")
        val s = ExtensionsStore(JsonStore(dir)).apply { load() }
        assertEquals(JsonPrimitive(1), s.read("e1", StorageArea.LOCAL)["a"])
        assertFalse(java.io.File(dir, "extension_storage.json").exists())
        val f1 = java.io.File(dir, "extension_storage/e1.json"); val f2 = java.io.File(dir, "extension_storage/e2.json")
        assertTrue(f1.exists() && f2.exists())
        // only the extension that changed is written again; a removed one's file goes
        f2.setLastModified(0)
        s.write("e1", StorageArea.LOCAL, null, mapOf("a" to JsonPrimitive(3)))
        s.savers.forEach { it.writeNow() }
        assertEquals(0L, f2.lastModified())
        assertEquals(JsonPrimitive(3), ExtensionsStore(JsonStore(dir)).apply { load() }.read("e1", StorageArea.LOCAL)["a"])
        s.remove("e2"); s.savers.forEach { it.writeNow() }
        assertFalse(f2.exists())
    }

    @Test fun tabMenuOrder() {
        val s = ExtensionsStore(JsonStore(tmp.newFolder())).apply { load() }
        listOf("a", "b", "c", "d").forEach { s.add(InstalledExtension(it, it, "1")) }
        // only a, c and d have buttons: dragging d to the front reorders those three, b keeps its place
        s.reorder(listOf("a", "c", "d"), from = 2, to = 0)
        assertEquals(listOf("d", "b", "a", "c"), s.list.map { it.id })
        // quick tiles: the saved order, then any tile it doesn't name
        assertEquals(listOf(com.jlees.mibrowser.data.QuickTile.FIND, com.jlees.mibrowser.data.QuickTile.DESKTOP, com.jlees.mibrowser.data.QuickTile.READER, com.jlees.mibrowser.data.QuickTile.ADS),
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
        // a .crx (version 3): the zip behind a header
        val z = zip(files)
        val header = ByteArray(8)
        val crx = ByteBuffer.allocate(12 + header.size + z.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("Cr24".toByteArray()).putInt(3).putInt(header.size).put(header).put(z).array()
        val dir = File(tmp.root, "ext")
        ExtensionPackage.unpack(crx, dir)
        val info = ExtensionPackage.inspect(dir, chromeId = "abcdefghijklmnopabcdefghijklmnop")
        assertEquals("Hello Ext", info.name)
        assertEquals("abcdefghijklmnopabcdefghijklmnop", info.id)
        assertEquals(Fit.WORKS, info.compatibility.fit)
        // its code is scanned once and kept: reading it again (mi starting) uses the kept scan for this version
        assertTrue(File(dir, "_mi_scan.json").exists())
        File(dir, "c.js").writeText("chrome.runtime.connectNative('x')") // (a change the kept scan doesn't see)
        assertEquals(Fit.WORKS, ExtensionPackage.inspect(dir, chromeId = "abcdefghijklmnopabcdefghijklmnop").compatibility.fit)
        // another version is scanned again
        File(dir, "manifest.json").writeText(files.getValue("manifest.json").replace("\"1.2\"", "\"1.3\""))
        assertEquals(Fit.MAYBE, ExtensionPackage.inspect(dir, chromeId = "abcdefghijklmnopabcdefghijklmnop").compatibility.fit)
        // a package whose files are inside one folder: that folder becomes the extension
        val nested = File(tmp.root, "nested")
        ExtensionPackage.unpack(zip(files.mapKeys { "inner/${it.key}" }), nested)
        assertTrue(File(nested, "manifest.json").exists())
        // a path that would leave the folder is refused
        val evil = runCatching { ExtensionPackage.unpack(zip(mapOf("../evil.txt" to "x", "manifest.json" to "{}")), File(tmp.root, "evil")) }
        assertTrue(evil.isFailure); assertFalse(File(tmp.root, "evil.txt").exists())
    }
}

package com.jlees.mibrowser

import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.extensions.ExtensionsStore
import com.jlees.mibrowser.extensions.InstalledExtension
import com.jlees.mibrowser.extensions.StorageArea
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** chrome.storage for extensions: one value per file, read and written only as asked (ExtensionStorage). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtensionStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun storageAndChanges() {
        val dir = tmp.newFolder()
        val s = ExtensionsStore(JsonStore(dir)).apply { load() }
        s.add(InstalledExtension("e1", "One", "1.0"))
        // a change reports old and new values (chrome.storage.onChanged), as JSON text
        assertEquals(mapOf("a" to (null to "1")), s.set("e1", StorageArea.LOCAL, null, mapOf("a" to "1")))
        assertEquals(mapOf("a" to "1"), s.get("e1", StorageArea.LOCAL))
        // setting the same value again changes nothing (no event)
        assertTrue(s.set("e1", StorageArea.LOCAL, null, mapOf("a" to "1")).isEmpty())
        // only the keys asked for are read
        s.set("e1", StorageArea.LOCAL, null, mapOf("big" to "\"" + "x".repeat(100_000) + "\""))
        assertEquals(setOf("a"), s.get("e1", StorageArea.LOCAL, keys = listOf("a", "missing")).keys)
        assertEquals(setOf("a", "big"), s.keys("e1", StorageArea.LOCAL, null).toSet())
        // incognito: changes are seen there, kept in memory, and the saved data is untouched
        s.set("e1", StorageArea.LOCAL, "incognito-1", mapOf("a" to "2"))
        assertEquals("2", s.get("e1", StorageArea.LOCAL, "incognito-1", listOf("a"))["a"])
        assertEquals("1", s.get("e1", StorageArea.LOCAL, keys = listOf("a"))["a"])
        s.forgetScope("incognito-1")
        assertEquals("1", s.get("e1", StorageArea.LOCAL, "incognito-1", listOf("a"))["a"]) // gone with its profile
        // session storage is never written
        s.set("e1", StorageArea.SESSION, null, mapOf("s" to "\"x\""))
        s.savers.forEach { it.writeNow() }
        val again = ExtensionsStore(JsonStore(dir)).apply { load() }
        assertEquals("1", again.get("e1", StorageArea.LOCAL, keys = listOf("a"))["a"])
        assertTrue(again.get("e1", StorageArea.SESSION).isEmpty())
        assertEquals("One", again["e1"]?.name)
        // removing an extension takes its data with it; Undo puts both back
        val kept = again["e1"]!!
        again.remove("e1"); assertTrue(again.get("e1", StorageArea.LOCAL).isEmpty()); assertEquals(null, again["e1"])
        again.restore(kept); assertEquals("1", again.get("e1", StorageArea.LOCAL, keys = listOf("a"))["a"]); assertEquals("One", again["e1"]?.name)
        // remove and clear
        s.set("e1", StorageArea.SYNC, null, mapOf("x" to "1", "y" to "2"))
        s.set("e1", StorageArea.SYNC, null, emptyMap(), removed = listOf("x"))
        assertEquals(setOf("y"), s.get("e1", StorageArea.SYNC).keys)
        assertEquals(mapOf("y" to ("2" to null)), s.set("e1", StorageArea.SYNC, null, emptyMap(), clear = true))
        assertTrue(s.get("e1", StorageArea.SYNC).isEmpty())
        // the engine's own values (DNR rules…) are kept per extension too
        s.setDnrDynamic("e1", JsonPrimitive(5))
        assertEquals(JsonPrimitive(5), ExtensionsStore(JsonStore(dir)).apply { load() }.dnrDynamic("e1"))
    }

    @Test fun storedTextGoesToPagesAsItIs() {
        // passed through unparsed; JSON null (which can't be) as null
        assertEquals("""{"a":{"x":[1,2]},"b":null,"c":"s"}""",
            kotlinx.serialization.json.JsonObject(mapOf("a" to "{\"x\":[1,2]}", "b" to "null", "c" to "\"s\"").mapValues { com.jlees.mibrowser.extensions.rawJson(it.value) }).toString())
    }
}

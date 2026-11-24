package com.jlees.mibrowser

import com.jlees.mibrowser.extensions.quirks.Quirks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Rules for particular extensions (extensions/quirks). */
class QuirksTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test fun darkReadersThemesTurnItsPageProxiesOff() {
        val id = "addon-darkreader-org-b2512f"
        // known by its Firefox add-on id
        val m = com.jlees.mibrowser.extensions.parseManifest(json("""{"manifest_version":2,"name":"Dark Reader","version":"4","browser_specific_settings":{"gecko":{"id":"addon@darkreader.org"}}}""").jsonObject)
        fun fixesOf(e: kotlinx.serialization.json.JsonElement) = e.jsonObject["data"]!!.jsonObject["fixes"]!!
        // as it sends them: a list, the first for every site; that one gets both proxies off, the rest stay
        val theme = json("""{"type":"bg-cs-add-dynamic-theme","scriptId":"x","data":{"theme":{"mode":1},"fixes":[{"url":["*"],"invert":["img"]},{"url":["example.com"],"css":"a{}"}]}}""")
        val list = fixesOf(Quirks.tabMessage(m, id, theme)).jsonArray
        assertEquals("true", list[0].jsonObject["disableStyleSheetsProxy"]!!.jsonPrimitive.content)
        assertEquals("true", list[0].jsonObject["disableCustomElementRegistryProxy"]!!.jsonPrimitive.content)
        assertEquals("[\"img\"]", list[0].jsonObject["invert"].toString())
        assertEquals("""{"url":["example.com"],"css":"a{}"}""", list[1].toString())
        // one object (older versions), or none at all
        val single = json("""{"type":"t","data":{"theme":{},"fixes":{"invert":[]}}}""")
        assertEquals("true", fixesOf(Quirks.tabMessage(m, id, single)).jsonObject["disableStyleSheetsProxy"]!!.jsonPrimitive.content)
        val none = json("""{"type":"t","data":{"theme":{},"fixes":null}}""")
        val made = fixesOf(Quirks.tabMessage(m, id, none)).jsonArray[0].jsonObject
        assertEquals("[\"*\"]", made["url"].toString()); assertEquals("true", made["disableStyleSheetsProxy"]!!.jsonPrimitive.content)
        // other messages, and other extensions, are left alone
        val cleanUp = json("""{"type":"bg-cs-clean-up","scriptId":"x"}""")
        assertSame(cleanUp, Quirks.tabMessage(m, id, cleanUp))
        assertSame(theme, Quirks.tabMessage(null, "someone-else", theme))
    }
}

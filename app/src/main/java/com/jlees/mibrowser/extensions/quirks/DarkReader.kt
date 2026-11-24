// Dark Reader (Firefox Add-ons: addon@darkreader.org).
//
// Its theme injects "proxies" into the page that hide Dark Reader's own style sheets from the page's scripts
// (document.styleSheets, customElements). In a browser those reach only the page: content scripts have their own copy of
// the DOM classes. In mi, content scripts share the page's, so Dark Reader then can't find its own sheets either: its
// check for a site's own dark theme sees Dark Reader's colours, decides the site is already dark, and takes the theme
// away. Dark Reader has a per-site switch for both proxies (its "fixes"); mi turns them off in every theme it sends.
package com.jlees.mibrowser.extensions.quirks

import com.jlees.mibrowser.extensions.Manifest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object DarkReader {
    private const val GECKO_ID = "addon@darkreader.org"

    fun isDarkReader(m: Manifest?, @Suppress("UNUSED_PARAMETER") id: String) = m?.geckoId == GECKO_ID

    private val proxiesOff = mapOf("disableStyleSheetsProxy" to JsonPrimitive(true), "disableCustomElementRegistryProxy" to JsonPrimitive(true))

    /**
     * A theme message ({type, data: {theme, fixes…}}) with both proxies off; anything else as it is. Its fixes come as a
     * list (the first, for every site: url ["*"]; Dark Reader turns a proxy off if any says so), or one object in older
     * versions, or none.
     */
    fun tabMessage(m: Manifest?, id: String, message: JsonElement): JsonElement {
        if (!isDarkReader(m, id)) return message
        val o = message as? JsonObject ?: return message
        val data = o["data"] as? JsonObject ?: return message
        if (data["theme"] == null) return message
        val fixes: JsonElement = when (val f = data["fixes"]) {
            is JsonArray -> if (f.isEmpty()) everySite() else JsonArray(f.mapIndexed { i, e -> if (i == 0 && e is JsonObject) JsonObject(e + proxiesOff) else e })
            is JsonObject -> JsonObject(f + proxiesOff)
            else -> everySite()
        }
        return JsonObject(o + ("data" to JsonObject(data + ("fixes" to fixes))))
    }

    /** No fixes sent: one, for every site, with the proxies off. */
    private fun everySite() = JsonArray(listOf(JsonObject(mapOf("url" to JsonArray(listOf(JsonPrimitive("*")))) + proxiesOff)))
}

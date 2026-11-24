// Rules for particular extensions: where one needs something the general engine shouldn't do for all. The engine calls
// these hooks; each extension's rules live in their own file in this folder (DarkReader.kt…) and act only on it.
package com.jlees.mibrowser.extensions.quirks

import com.jlees.mibrowser.extensions.Manifest
import kotlinx.serialization.json.JsonElement

object Quirks {
    /** A message extension [id] (with manifest [m]) sends to a tab's content scripts (tabs.sendMessage), as delivered. */
    fun tabMessage(m: Manifest?, id: String, message: JsonElement): JsonElement = DarkReader.tabMessage(m, id, message)
}

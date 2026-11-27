// HTML text helpers shared by features (bookmark files, product pages).
package com.jlees.mibrowser.logic

private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|#39|nbsp);")

/** [s] with its HTML entities (named, decimal and hex) turned back into characters; unknown ones are left as they are. */
fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    when (val e = m.groupValues[1]) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos", "#39" -> "'"; "nbsp" -> " "
        else -> runCatching {
            val code = if (e.startsWith("#x", true)) e.substring(2).toInt(16) else e.substring(1).toInt()
            String(Character.toChars(code))
        }.getOrDefault(m.value)
    }
}

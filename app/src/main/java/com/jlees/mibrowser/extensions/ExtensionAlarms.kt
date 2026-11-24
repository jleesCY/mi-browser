// chrome.alarms: an extension's alarms, kept while mi runs (as browsers keep them while they run), each firing
// alarms.onAlarm in the extension's pages at its time and again every period. Main thread only.
package com.jlees.mibrowser.extensions

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ExtensionAlarms(private val e: ExtensionEngine) {
    private class Alarm(val ext: String, val name: String, var at: Long, val period: Double?) { lateinit var run: Runnable }
    private val alarms = HashMap<String, Alarm>()

    /** Sets extension [ext]'s alarm [name] for [at] (ms), repeating every [periodMinutes] if given; replaces one of the same name. */
    fun set(ext: String, name: String, at: Long, periodMinutes: Double?) {
        clear(ext, name)
        val alarm = Alarm(ext, name, at, periodMinutes)
        alarm.run = Runnable {
            e.emitToPages(ext, "alarms.onAlarm", json(alarm))
            val period = alarm.period
            if (period != null) { alarm.at = System.currentTimeMillis() + (period * 60_000).toLong(); e.main.postDelayed(alarm.run, (period * 60_000).toLong()) }
            else alarms.remove("$ext/$name")
        }
        alarms["$ext/$name"] = alarm
        e.main.postDelayed(alarm.run, (at - System.currentTimeMillis()).coerceAtLeast(0))
    }

    fun get(ext: String, name: String): JsonObject? = alarms["$ext/$name"]?.let(::json)
    fun all(ext: String) = JsonArray(alarms.values.filter { it.ext == ext }.map(::json))

    /** Whether there was one to clear. */
    fun clear(ext: String, name: String): Boolean = alarms.remove("$ext/$name")?.also { e.main.removeCallbacks(it.run) } != null

    /** mi is closing: no alarm fires after it. */
    fun clearEverything() { alarms.values.forEach { e.main.removeCallbacks(it.run) }; alarms.clear() }

    /** Every alarm of [ext] (it was turned off or removed); whether it had any. */
    fun clearAll(ext: String): Boolean = alarms.values.filter { it.ext == ext }.map { clear(ext, it.name) }.isNotEmpty()

    private fun json(a: Alarm) = buildJsonObject { put("name", a.name); put("scheduledTime", a.at); a.period?.let { put("periodInMinutes", it) } }
}

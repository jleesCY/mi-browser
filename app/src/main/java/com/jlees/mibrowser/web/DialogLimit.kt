package com.jlees.mibrowser.web

import android.os.SystemClock

/** Pages that open 3 JS dialogs (alert, confirm, prompt) within 10 s: further ones are dismissed until it navigates. */
class DialogLimit {
    private val times = HashMap<String, ArrayDeque<Long>>()
    private val blocked = HashSet<String>()

    /** Whether tab [id] may show another dialog now (counts it if so). */
    fun allow(id: String): Boolean {
        if (id in blocked) return false
        val now = SystemClock.uptimeMillis()
        val t = times.getOrPut(id) { ArrayDeque() }
        while (t.isNotEmpty() && now - t.first() > 10_000) t.removeFirst()
        if (t.size >= 3) { blocked += id; return false }
        t.addLast(now)
        return true
    }

    /** Tab [id] navigated or closed: starts counting afresh. */
    fun reset(id: String) { times.remove(id); blocked.remove(id) }
}

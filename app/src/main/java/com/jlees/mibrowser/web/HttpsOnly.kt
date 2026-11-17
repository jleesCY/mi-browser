// HTTPS Only's memory of insecure sites the user chose to continue to over plain http.
package com.jlees.mibrowser.web

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost

/**
 * With Warn Every Time on, a site continued to stays allowed only while its tab stays on it (tab → host here; leaving
 * the site or closing the tab forgets it, so the next visit asks again). Off, it's let through for the session in that
 * profile (vm.httpAllowed). Nothing is kept anywhere else.
 */
class HttpsOnly(private val vm: BrowserViewModel, private val profileOf: (String) -> String?) {
    private val continued = HashMap<String, String>()

    /** Whether [host] may load over plain http in tab [id]. */
    fun letsThrough(id: String, host: String) =
        continued[id] == host || (!vm.settings.httpsWarnEveryTime && vm.httpAllowed.has(profileOf(id), host))

    /** The user continued to [host] in tab [id] (this visit, or the session; see Warn Every Time). */
    fun continueTo(id: String, host: String) {
        if (vm.settings.httpsWarnEveryTime) continued[id] = host else vm.httpAllowed.trust(profileOf(id), host)
    }

    /** Tab [id] started loading [url]: a visit's allowance ends when it leaves the site. */
    fun pageStarted(id: String, url: String?) { if (continued[id].let { it != null && it != displayHost(url) }) continued.remove(id) }

    fun forget(id: String) { continued.remove(id) }
}

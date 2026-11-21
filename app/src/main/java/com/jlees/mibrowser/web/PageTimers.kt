// WebView's page timers (JavaScript timers and animation) are process-wide: pausing them in one WebView pauses every
// page in mi. They run while any mi window needs them: the main window while it shows or plays media (WebViewHost),
// or an app window while it shows (launcher/AppWindow). Main thread only.
package com.jlees.mibrowser.web

import android.webkit.WebView

object PageTimers {
    /** The main window shows, or a tab plays media in the background (false until it says: an app window may be first). */
    var mainNeeds = false
    /** App windows on screen. */
    var appWindows = 0
    private var paused = false

    /** Pauses or resumes every page's timers, through [any] WebView, to match what the windows need. */
    fun apply(any: WebView) {
        val run = mainNeeds || appWindows > 0
        if (run == paused) { if (run) any.resumeTimers() else any.pauseTimers(); paused = !run }
    }
}

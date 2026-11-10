package com.jlees.mibrowser.web

import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * With the status bar hidden, pages fill the screen behind the camera cutout but start below it
 * (assets/js/top-inset.js adds that space inside the page). Applied when a page loads and whenever the status bar
 * setting changes; nothing is added while the status bar shows (the page then starts below it anyway).
 */
class TopInset(private val script: String, private val statusBarShown: () -> Boolean) {
    private val applied = HashMap<String, Int>() // tab id → the space (dp) its current page has

    /** The page in [id] has the right space for now (a no-op when it already does). */
    fun apply(id: String, wv: WebView) {
        val dp = wanted(wv)
        if (applied[id] == dp) return
        applied[id] = dp
        wv.evaluateJavascript(script.replace("__DP__", dp.toString()), null)
    }

    /** A new page in [id] (it has no space yet), or the tab is gone. */
    fun reset(id: String) { applied.remove(id) }

    private fun wanted(wv: WebView): Int {
        if (statusBarShown()) return 0
        val px = ViewCompat.getRootWindowInsets(wv)?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top ?: 0
        return (px / wv.resources.displayMetrics.density).toInt()
    }
}

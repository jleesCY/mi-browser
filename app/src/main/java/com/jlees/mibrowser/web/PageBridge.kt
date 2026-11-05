package com.jlees.mibrowser.web

import android.app.Activity
import android.webkit.*
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.ui.common.shareText
import android.os.SystemClock
import androidx.webkit.WebViewCompat
import org.json.JSONObject

/**
 * Messages from bridge.js: window.print() and navigator.share, which WebView doesn't provide. Only the main frame may
 * use them, at most once every 1.5 s, and only right after a real touch in mi (the page-side gesture check can be
 * bypassed by calling miBridge directly).
 */
class PageBridge(private val activity: Activity, private val print: (id: String, wv: WebView) -> Unit) {
    private var lastAction = 0L
    private var lastUserTouch = 0L

    /** The user touched mi (any screen). */
    fun noteUserTouch() { lastUserTouch = SystemClock.uptimeMillis() }

    /** Messages from bridge.js. Only the main frame may print or share, at most once every 1.5 s. */
    fun listener(id: String) = WebViewCompat.WebMessageListener { view, message, _, isMainFrame, _ ->
        if (!isMainFrame) return@WebMessageListener
        val now = SystemClock.uptimeMillis()
        if (now - lastAction < 1500) return@WebMessageListener
        // the page-side gesture check can be bypassed by calling miBridge directly; require a real touch in mi
        if (now - lastUserTouch > 5000) return@WebMessageListener
        val msg = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return@WebMessageListener
        lastAction = now
        when (msg.optString("type")) {
            "print" -> print(id, view)
            "passkeyBlocked" -> Toast.makeText(activity, "Passkeys can't be created in incognito", Toast.LENGTH_LONG).show()
            "share" -> {
                val text = listOf(msg.optString("text"), msg.optString("url")).filter { it.isNotBlank() }.joinToString("\n")
                if (text.isNotEmpty()) shareText(activity, text, msg.optString("title").ifBlank { null })
            }
        }
    }
}

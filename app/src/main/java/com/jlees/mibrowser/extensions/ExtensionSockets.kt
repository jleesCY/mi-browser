// Extensions' WebSockets, opened by mi. In a browser an extension's socket says it comes from the extension (Origin:
// moz-extension://… or chrome-extension://…) and carries the site's cookies, and services like Grammarly's check that;
// a WebView socket can only say it comes from mi's address for the extension, or the page's site. So the WebSocket an
// extension's code sees (ext-api.js) is a stand-in that asks mi, and mi makes the connection itself: only to sites the
// extension may reach, with its native origin, the site's cookies (as the tab would send them) and mi's identity.
// Sockets close when the page or frame that opened them goes.
package com.jlees.mibrowser.extensions

import android.util.Base64
import android.webkit.CookieManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The http(s) address a ws(s) address is reached at (for host permissions and cookies). */
fun httpOfSocket(url: String): String? = when {
    url.startsWith("wss://", true) -> "https://" + url.substring(6)
    url.startsWith("ws://", true) -> "http://" + url.substring(5)
    else -> null
}

/** A close code mi may send for a page: 1000, or one of the 3000–4999 an application chooses (else 1000). */
fun socketCloseCode(code: Int?): Int = code?.takeIf { it == 1000 || it in 3000..4999 } ?: 1000

internal class ExtensionSockets(private val e: ExtensionEngine) {
    private val client by lazy { OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build() }
    private class Open(val ctx: ExtensionEngine.Ctx, val socket: WebSocket)
    /** By extension and the socket's id (made by the page). */
    private val open = ConcurrentHashMap<String, Open>()

    private fun str(a: JsonArray, i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.contentOrNull

    fun handle(c: ExtensionEngine.Ctx, name: String, a: JsonArray, done: Done) {
        val id = str(a, 0) ?: return done(false, null, "No socket")
        val key = c.ext + "/" + id
        when (name) {
            "ws.open" -> {
                val url = str(a, 1) ?: return done(false, null, "No address")
                val http = httpOfSocket(url) ?: return done(false, null, "The URL's scheme must be either 'ws' or 'wss'.")
                // only where the extension may reach, as its requests
                if (e.infos[c.ext]?.manifest?.hostPermissions?.none { matchesPattern(it, http) } != false) return done(false, null, "No permission for $url")
                val protocols = (a.getOrNull(2) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                val cookies = c.tabId?.let { e.requests.cookieStoreOf(it) } ?: CookieManager.getInstance()
                val req = Request.Builder().url(url)
                    .header("Origin", nativeBase(c.ext).trimEnd('/'))
                    .header("User-Agent", e.userAgents.mobile)
                    .apply {
                        if (!e.vm.settings.blockCookies) cookies.getCookie(http)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                        if (protocols.isNotEmpty()) header("Sec-WebSocket-Protocol", protocols.joinToString(", "))
                    }.build()
                val socket = runCatching { client.newWebSocket(req, Listener(c, id, key, http, cookies)) }.getOrElse { return done(false, null, it.message ?: "Can't connect") }
                open[key] = Open(c, socket)
                done(true, null, null)
            }
            "ws.send" -> {
                val s = open[key]?.socket ?: return done(false, null, "Not open")
                val sent = str(a, 1)?.let { s.send(it) } ?: str(a, 2)?.let { s.send(Base64.decode(it, Base64.DEFAULT).toByteString()) } ?: false
                done(true, JsonPrimitive(sent), null)
            }
            "ws.close" -> {
                open[key]?.socket?.close(socketCloseCode((a.getOrNull(1) as? JsonPrimitive)?.intOrNull), str(a, 2)?.take(120))
                done(true, null, null)
            }
            else -> done(false, null, "$name is not supported in mi.")
        }
    }

    private inner class Listener(val c: ExtensionEngine.Ctx, val id: String, val key: String, val http: String, val cookies: CookieManager) : WebSocketListener() {
        private fun tell(d: JsonObject) { e.main.post { e.emit(listOf(c), "ws.event", JsonObject(d + ("id" to JsonPrimitive(id)))) } }
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!e.vm.settings.blockCookies) response.headers("Set-Cookie").forEach { cookies.setCookie(http, it) }
            tell(buildJsonObject { put("type", "open"); put("protocol", response.header("Sec-WebSocket-Protocol").orEmpty()) })
        }
        override fun onMessage(webSocket: WebSocket, text: String) { tell(buildJsonObject { put("type", "message"); put("text", text) }) }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            tell(buildJsonObject { put("type", "message"); put("b64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)) })
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(socketCloseCode(code), null) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            open.remove(key)
            tell(buildJsonObject { put("type", "close"); put("code", code); put("reason", reason); put("clean", true) })
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            open.remove(key)
            tell(buildJsonObject { put("type", "error") })
            tell(buildJsonObject { put("type", "close"); put("code", 1006); put("reason", ""); put("clean", false) })
        }
    }

    /** [c] (a page or frame) is gone: its sockets close. */
    fun dropped(c: ExtensionEngine.Ctx) {
        open.entries.filter { it.value.ctx === c }.forEach { (k, o) -> o.socket.cancel(); open.remove(k) }
    }
}

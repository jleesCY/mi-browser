// Background playback: follows the audio or video a page plays (assets/js/media.js), keeps that tab running while mi
// is in the background, and drives the media notification (MediaService) whose buttons play and pause it.
package com.jlees.mibrowser.media

import android.content.Context
import android.graphics.Bitmap
import com.jlees.mibrowser.data.Favicons
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import org.json.JSONObject

class BackgroundMedia(
    private val ctx: Context,
    private val enabled: () -> Boolean,
    private val isIncognito: (String) -> Boolean,
    /** Tab id → the page it shows, for the notification's site icon. */
    private val urlOf: (String) -> String?,
    /** The playing tab changed: the host re-applies which pages run (see keepsRunning). */
    private val changed: () -> Unit,
) {
    /** The tab the notification controls (playing or paused), or null. */
    var tabId: String? = null; private set
    private var playing = false
    private var token = ""
    private var frame: JavaScriptReplyProxy? = null
    private var last: PageMedia? = null
    /** The notification's picture: the playing site's icon (from mi's stored site icons), and which one it is. */
    private var artUrl = ""
    private var art: Bitmap? = null
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** The size of the last video that played, for the picture-in-picture window's shape (observable: the window
     *  reshapes when the size arrives, often just after the video goes fullscreen). */
    var videoSize by mutableStateOf(0 to 0); private set

    /** The tab playing right now (kept running in the background), or null. */
    val playingTab get() = tabId.takeIf { playing && enabled() }

    init {
        MediaService.commands = { cmd -> when (cmd) { MediaCommand.PLAY -> play(); MediaCommand.PAUSE -> pause(); MediaCommand.STOP -> stop()
            MediaCommand.BACK -> frame?.postMessage("skip:-$SKIP_SECONDS"); MediaCommand.FORWARD -> frame?.postMessage("skip:$SKIP_SECONDS") } }
        MediaService.seek = { ms -> frame?.postMessage("seek:${ms / 1000.0}") }
    }

    /** Reports from media.js in tab [id] (any frame: players are often embedded). */
    fun listener(id: String) = WebViewCompat.WebMessageListener { _, message, _, _, reply ->
        val m = parse(message.data) ?: return@WebMessageListener
        if (m.playing && m.width > 0 && videoSize != (m.width to m.height)) videoSize = m.width to m.height
        if (!enabled()) { if (tabId != null) end(); return@WebMessageListener }
        val ours = id == tabId && m.token == token
        if (!ours) {
            if (!m.playing || !isSessionMedia(m)) return@WebMessageListener
            frame?.run { postMessage("pause"); postMessage("release") } // a new player takes over: one at a time
            tabId = id; token = m.token
            reply.postMessage("keep") // the page believes it's on screen, so it keeps playing in the background
        }
        frame = reply
        playing = m.playing
        last = m
        if (!isIncognito(id)) loadArt(urlOf(id))
        publish()
        changed()
    }

    /** The notification shows the latest report (incognito: nothing about the page). */
    private fun publish() {
        val m = last ?: return
        val id = tabId ?: return
        val incognito = isIncognito(id)
        MediaService.state = MediaInfo(
            title = if (incognito) "Incognito tab" else m.title.ifBlank { "Playing in mi" },
            artist = if (incognito) "" else m.artist,
            playing = playing, tabId = id,
            positionMs = (m.position * 1000).toLong(), durationMs = (m.duration * 1000).toLong(), rate = m.rate.toFloat(),
            at = SystemClock.elapsedRealtime(), art = if (incognito) null else art,
        )
        MediaService.refresh(ctx)
    }

    /** Loads the site icon for [pageUrl] (stored, or asked of the site itself) and shows it when it arrives. */
    private fun loadArt(pageUrl: String?) {
        val src = Favicons.src(pageUrl) ?: return
        if (src == artUrl) return
        artUrl = src; art = null
        io.launch {
            val bmp = Favicons.load(src) ?: return@launch
            withContext(Dispatchers.Main) { if (src == artUrl) { art = bmp; publish() } }
        }
    }

    fun play() {
        if (tabId == null) return
        playing = true; changed() // the page runs again before it's asked to play (it pauses in the background)
        frame?.postMessage("play")
    }

    fun pause() { frame?.postMessage("pause") }

    /** Notification dismissed: the media stops and the notification goes. */
    fun stop() { pause(); end() }

    /** Tab [id]'s page is gone (closed, or another page loaded in it). */
    fun forget(id: String) { if (id == tabId) end() }

    fun end() {
        if (tabId == null) return
        frame?.postMessage("release")
        tabId = null; playing = false; frame = null; token = ""; last = null; artUrl = ""; art = null
        MediaService.state = null
        MediaService.refresh(ctx)
        changed()
    }

    /** The WebViews are going (Activity teardown): the session ends and the service lets go of this object. */
    fun release() { end(); io.coroutineContext.cancelChildren(); MediaService.commands = null; MediaService.seek = null }

    private fun parse(data: String?): PageMedia? = runCatching {
        val o = JSONObject(data ?: return null)
        PageMedia(o.getString("token"), o.getBoolean("playing"), o.getBoolean("muted"), o.getDouble("duration"),
            o.optInt("width"), o.optInt("height"), o.optString("title").take(200), o.optString("artist").take(200),
            o.optDouble("position", 0.0), o.optDouble("rate", 1.0))
    }.getOrNull()
}

package com.jlees.mibrowser

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import com.jlees.mibrowser.media.BackgroundMedia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Background playback's session (BackgroundMedia): which player it follows, and when the session ends. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundMediaTest {
    /** A page frame's end of the connection: records what mi tells it (play, pause, keep, release…). */
    private class FakeFrame : JavaScriptReplyProxy() {
        val sent = mutableListOf<String>()
        override fun postMessage(message: String) { sent += message }
        override fun postMessage(arrayBuffer: ByteArray) {}
    }

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val wv = WebView(ctx)
    private var enabled = true
    private val media = BackgroundMedia(ctx, { enabled }, { false }, { null }) {}

    /** What media.js reports from [frame] in [tab]. */
    private fun report(tab: String, frame: FakeFrame, token: String, playing: Boolean = true, muted: Boolean = false, duration: Double = 300.0) =
        media.listener(tab).onPostMessage(
            wv, WebMessageCompat("""{"token":"$token","playing":$playing,"muted":$muted,"duration":$duration}"""),
            Uri.parse("https://site.test"), true, frame,
        )

    @After fun tearDown() = media.release()

    @Test fun audibleMediaStartsASessionMutedClipsAndShortSoundsDont() {
        val f = FakeFrame()
        report("t1", f, "x", muted = true)
        assertNull(media.tabId) // a muted autoplaying video
        report("t1", f, "x", duration = 2.0)
        assertNull(media.tabId) // a short sound
        report("t1", f, "x")
        assertEquals("t1", media.tabId)
        assertEquals("t1", media.playingTab) // kept running in the background
        assertEquals(listOf("keep"), f.sent) // the page is told it's on screen, so it keeps playing
    }

    @Test fun aNewPlayerTakesOverAndTheOldOnePauses() {
        val a = FakeFrame()
        val b = FakeFrame()
        report("t1", a, "x")
        report("t2", b, "y")
        assertEquals("t2", media.tabId)
        assertTrue(a.sent.containsAll(listOf("pause", "release"))) // one player at a time
        assertEquals(listOf("keep"), b.sent)
    }

    @Test fun pausingKeepsTheSessionButLetsTheTabSleep() {
        val a = FakeFrame()
        report("t1", a, "x")
        report("t1", a, "x", playing = false)
        assertEquals("t1", media.tabId) // the notification stays, to play again
        assertNull(media.playingTab) // but the paused page isn't kept running
    }

    @Test fun closingTheTabOrTurningTheSettingOffEndsIt() {
        val a = FakeFrame()
        report("t1", a, "x")
        media.forget("t1") // the tab closed, or another page loaded in it
        assertNull(media.tabId)
        assertTrue("release" in a.sent)

        report("t1", a, "x")
        enabled = false // Settings › Media › Background Playback off
        report("t1", a, "x")
        assertNull(media.tabId)
    }
}

package com.jlees.mibrowser

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.jlees.mibrowser.web.TopInset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The space pages keep for the camera cutout (TopInset): set once per page, again after a new page or a setting change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TopInsetTest {
    private val script = "inset(__DP__)"
    private var statusBarShown = true
    private val inset = TopInset(script) { statusBarShown }
    private val wv = WebView(ApplicationProvider.getApplicationContext())
    private fun lastScript() = shadowOf(wv).lastEvaluatedJavascript

    @Test fun appliedOncePerPageAndAgainAfterANewOne() {
        inset.apply("t", wv)
        assertEquals("inset(0)", lastScript()) // status bar shown: no space
        wv.evaluateJavascript("marker", null)
        inset.apply("t", wv)
        assertEquals("marker", lastScript()) // nothing changed: the page isn't touched again
        inset.reset("t") // a new page started: it has no space yet
        inset.apply("t", wv)
        assertEquals("inset(0)", lastScript())
    }

    @Test fun noSpaceWithoutACutout() {
        statusBarShown = false // hidden, but this screen has no camera cutout (the test window has none)
        inset.apply("t", wv)
        assertEquals("inset(0)", lastScript())
    }
}

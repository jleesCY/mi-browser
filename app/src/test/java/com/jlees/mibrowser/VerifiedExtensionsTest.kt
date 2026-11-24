package com.jlees.mibrowser

import com.jlees.mibrowser.extensions.InstallSource
import com.jlees.mibrowser.extensions.VerifiedExtension
import com.jlees.mibrowser.extensions.storeSource
import com.jlees.mibrowser.extensions.verifiedFor
import com.jlees.mibrowser.extensions.WebViewFit
import com.jlees.mibrowser.extensions.webViewFit
import com.jlees.mibrowser.extensions.webViewWarning
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verified extensions are matched by their store id only: a file, another store or another add-on isn't one. */
class VerifiedExtensionsTest {
    private val list = listOf(
        VerifiedExtension("Dark Thing", amoSlug = "dark-thing", testedWebView = 133, worksFrom = 111, problemsBelow = 120),
    )

    @Test fun theRealListIsSound() {
        for (v in com.jlees.mibrowser.extensions.VERIFIED_EXTENSIONS) {
            // a store id to match by, and a range in order: won't work < may have problems < the one it was tested on
            assertTrue(v.name, v.amoSlug.isNotBlank())
            assertTrue(v.name, v.worksFrom <= v.problemsBelow && v.problemsBelow <= v.testedWebView)
        }
        assertEquals("Dark Reader", verifiedFor(InstallSource.Amo("darkreader"))?.name)
    }

    @Test fun matchedByStoreId() {
        assertEquals("Dark Thing", verifiedFor(storeSource("https://addons.mozilla.org/en-US/firefox/addon/dark-thing/"), list)?.name)
        assertNull(verifiedFor(storeSource("https://addons.mozilla.org/firefox/addon/dark-thing-copy/"), list))
        assertNull(verifiedFor(InstallSource.File("https://example.com/dark-thing.xpi"), list)) // a file isn't verified
        assertNull(verifiedFor(null, list))
    }

    @Test fun warnsForOlderWebViews() {
        val v = list[0]
        assertEquals(WebViewFit.WONT_WORK, webViewFit(v, 110))
        assertEquals(WebViewFit.MAY_HAVE_PROBLEMS, webViewFit(v, 115))
        assertEquals(WebViewFit.OK, webViewFit(v, 120))
        assertEquals(WebViewFit.UNKNOWN, webViewFit(v, null))
        assertTrue(webViewWarning(v, 110)!!.contains("needs version 111 or newer"))
        assertNull(webViewWarning(v, 133))
    }
}

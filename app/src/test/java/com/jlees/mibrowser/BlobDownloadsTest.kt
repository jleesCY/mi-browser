package com.jlees.mibrowser

import androidx.test.core.app.ApplicationProvider
import com.jlees.mibrowser.downloads.BlobDownloads
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlobDownloadsTest {
    @Test fun aPageCantSaveAFileMiDidntAskFor() {
        var saved = 0; var failed = 0
        val blobs = BlobDownloads(ApplicationProvider.getApplicationContext(), { null }, { saved++ }, { failed++ })
        // a page posting a file of its own accord (no token from a real download): ignored, nothing written
        blobs.handle("""{"t":"start","token":"made-up","name":"evil.apk","type":"application/vnd.android.package-archive","size":3}""")
        blobs.handle("""{"t":"chunk","token":"made-up","data":"AAAA"}""")
        blobs.handle("""{"t":"end","token":"made-up"}""")
        ShadowLooper.idleMainLooper()
        assertEquals(0, saved); assertEquals(0, failed)
    }
}

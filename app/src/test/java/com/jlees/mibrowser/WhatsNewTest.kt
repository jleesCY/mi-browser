package com.jlees.mibrowser

import com.jlees.mibrowser.update.NoteBlock
import com.jlees.mibrowser.update.WhatsNewStep
import com.jlees.mibrowser.update.parseReleaseNotes
import com.jlees.mibrowser.update.whatsNewStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewTest {
    @Test fun whenItShows() {
        assertEquals(WhatsNewStep.SHOW, whatsNewStep(lastSeen = 191, current = 200, walkthroughSeen = true)) // an update
        assertEquals(WhatsNewStep.NONE, whatsNewStep(200, 200, true)) // already shown
        assertEquals(WhatsNewStep.RECORD, whatsNewStep(0, 200, walkthroughSeen = false)) // a new install: nothing is new
        assertEquals(WhatsNewStep.SHOW, whatsNewStep(0, 200, walkthroughSeen = true)) // updating from before What's New
        assertEquals(WhatsNewStep.SHOW, whatsNewStep(191, 200, walkthroughSeen = false)) // after the first time, every update
    }

    @Test fun releaseNotesBecomeBlocks() {
        val md = """
            # mi. 2.0.0: a new look, extensions and more
            Your browser now has a **new icon** and
            a new [home page](https://example.com).

            ## Looks
            - The new logo
            * Sky blue, everywhere

            [All changes since v1.9.0](https://github.com/mi-app-hub/mi-browser/compare/v1.9.0...v2.0.0)
            APK: app/build/outputs/apk/release/mi-browser-2.0.0-release.apk
        """.trimIndent()
        val n = parseReleaseNotes("2.0.0", md)
        assertEquals("a new look, extensions and more", n.tagline)
        assertEquals(listOf(
            NoteBlock.Paragraph("Your browser now has a **new icon** and a new home page."), // lines joined, link text kept
            NoteBlock.Heading("Looks"),
            NoteBlock.Bullet("The new logo"),
            NoteBlock.Bullet("Sky blue, everywhere"),
        ), n.blocks) // the compare link and the APK path are for developers: gone
        assertNull(parseReleaseNotes("1.0.0", "Just a line").tagline) // no title: no tagline
    }
}

package com.jlees.mibrowser

import com.jlees.mibrowser.downloads.DownloadItem
import com.jlees.mibrowser.downloads.DownloadState
import com.jlees.mibrowser.downloads.afterRestart
import com.jlees.mibrowser.downloads.downloadFileName
import com.jlees.mibrowser.downloads.downloadStatus
import com.jlees.mibrowser.downloads.formatBytes
import com.jlees.mibrowser.downloads.uniqueFileName
import org.junit.Assert.assertEquals
import org.junit.Test

/** File names, sizes and status lines for downloads. */
class DownloadRulesTest {
    @Test fun fileNames() {
        // the server's name wins, including the RFC 5987 (UTF-8) form
        assertEquals("report.pdf", downloadFileName("https://a.com/dl?id=5", "attachment; filename=\"report.pdf\"", "application/pdf"))
        assertEquals("résumé 2026.pdf", downloadFileName("https://a.com/x", "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9%202026.pdf", null))
        // else the address, decoded and without its query
        assertEquals("my file.zip", downloadFileName("https://a.com/files/my%20file.zip?token=abc", null, null))
        // no name at all: "download", with an extension from the type
        assertEquals("download.png", downloadFileName("https://a.com/", null, "image/png") { if (it == "image/png") "png" else null })
        // path separators and reserved characters can't escape the Downloads folder
        assertEquals("_.._etc_passwd", downloadFileName("https://a.com/x", "attachment; filename=\"/../etc/passwd\"", null))
    }

    @Test fun duplicateNamesGetANumber() {
        val taken = setOf("a.pdf", "a (1).pdf", "notes")
        assertEquals("b.pdf", uniqueFileName("b.pdf", taken::contains))
        assertEquals("a (2).pdf", uniqueFileName("a.pdf", taken::contains))
        assertEquals("notes (1)", uniqueFileName("notes", taken::contains))
    }

    @Test fun sizesAndStatus() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("10.0 MB", formatBytes(10L * 1024 * 1024))
        val d = DownloadItem("1", "https://a.com/f.zip", "f.zip", total = 10L * 1024 * 1024, done = 5L * 1024 * 1024, state = DownloadState.RUNNING)
        assertEquals("5.0 MB of 10.0 MB · 50%", downloadStatus(d))
        assertEquals("Paused · 5.0 MB of 10.0 MB", downloadStatus(d.copy(state = DownloadState.PAUSED)))
        assertEquals("Failed · Server error 404", downloadStatus(d.copy(state = DownloadState.FAILED, error = "Server error 404")))
        assertEquals("10.0 MB", downloadStatus(d.copy(state = DownloadState.DONE)))
    }

    @Test fun interruptedDownloadsComeBackPaused() {
        val items = listOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.DONE, DownloadState.FAILED).mapIndexed { i, s -> DownloadItem("$i", "u", "f", state = s) }
        assertEquals(listOf(DownloadState.PAUSED, DownloadState.PAUSED, DownloadState.DONE, DownloadState.FAILED), afterRestart(items).map { it.state })
    }
}

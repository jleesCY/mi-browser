package com.jlees.mibrowser

import com.jlees.mibrowser.downloads.DownloadItem
import com.jlees.mibrowser.downloads.DownloadState
import com.jlees.mibrowser.downloads.afterRestart
import com.jlees.mibrowser.downloads.downloadFileName
import com.jlees.mibrowser.downloads.downloadStatus
import com.jlees.mibrowser.downloads.formatBytes
import com.jlees.mibrowser.downloads.uniqueFileName
import com.jlees.mibrowser.downloads.planParts
import com.jlees.mibrowser.downloads.Speedometer
import com.jlees.mibrowser.downloads.timeLeft
import com.jlees.mibrowser.downloads.retriesItself
import com.jlees.mibrowser.downloads.RETRYING
import com.jlees.mibrowser.downloads.downloadError
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

    @Test fun aFileSplitsIntoPartsCoveringEveryByteOnce() {
        val total = 10L * 1024 * 1024 * 1024 + 7 // 10 GB and change
        val parts = planParts(total, 4)
        assertEquals(16, parts.size) // four per connection
        assertEquals(0L, parts.first().start)
        assertEquals(total, parts.last().end)
        parts.zipWithNext { a, b -> assertEquals(a.end, b.start) } // no gaps, no overlap
        assertEquals(5, planParts(20L * 1024 * 1024, 4).size) // small files: parts of at least 4 MB
    }

    @Test fun aFullDiskSaysSo() {
        assertEquals("Not enough storage", downloadError(java.io.IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals("Server error 404", downloadError(java.io.IOException("Server error 404")))
        assertEquals("Connection problem", downloadError(IllegalStateException()))
    }

    @Test fun speedAndTimeLeft() {
        assertEquals("40s left", timeLeft(40L * 1024 * 1024, 1024 * 1024))
        assertEquals("3 min left", timeLeft(150L * 1024 * 1024, 1024 * 1024)) // 2.5 min, rounded up
        assertEquals("1h 5m left", timeLeft(3900L * 1024 * 1024, 1024 * 1024))
        assertEquals("900 KB of 1.2 MB", com.jlees.mibrowser.downloads.bytesOf(900L * 1024, 1229L * 1024))
        val meter = Speedometer()
        meter.reading(0, 0)
        assertEquals(4_000_000L, meter.reading(1000, 4_000_000)) // first reading: as measured
        val next = meter.reading(2000, 4_000_000) // a stalled second: eased toward 0, not dropped to it
        assertEquals(true, next in 3_000_000..3_300_000)
        val d = DownloadItem("1", "u", "f", total = 100L * 1024 * 1024, done = 40L * 1024 * 1024, state = DownloadState.RUNNING, speed = 1024 * 1024)
        assertEquals("40.0 of 100 MB · 1.0 MB/s · 1 min left", downloadStatus(d))
    }

    @Test fun aDroppedConnectionRetriesItselfButNotARefusal() {
        val err = { e: Exception -> com.jlees.mibrowser.downloads.downloadError(e) }
        listOf(java.net.SocketTimeoutException("read timed out"), java.net.UnknownHostException("a.com"), java.net.ConnectException("refused"),
            java.net.ProtocolException("unexpected end of stream"), java.io.IOException("Connection lost"))
            .forEach { assertEquals(it.toString(), true, retriesItself(err(it))) }
        listOf(java.io.IOException("Server error 403"), java.io.IOException("write failed: ENOSPC"), java.io.IOException("Can't open the file"),
            java.io.IOException("Too many redirects"), IllegalArgumentException("bad url"))
            .forEach { assertEquals(it.toString(), false, retriesItself(err(it))) }
        assertEquals(RETRYING, downloadStatus(DownloadItem("1", "u", "f", state = DownloadState.FAILED, error = RETRYING)))
    }

    @Test fun deleteAllTakesOnlyFinishedDownloads() {
        val items = DownloadState.values().mapIndexed { i, s -> DownloadItem("$i", "u", "f$i", state = s) }
        // a download still going (or paused, or failed) keeps its place: Cancel is for those
        assertEquals(listOf(DownloadState.DONE), com.jlees.mibrowser.downloads.finishedDownloads(items).map { it.state })
    }
}

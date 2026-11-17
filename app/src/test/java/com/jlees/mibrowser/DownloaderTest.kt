package com.jlees.mibrowser

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.downloads.DownloadState
import com.jlees.mibrowser.downloads.Downloader
import com.jlees.mibrowser.downloads.DownloadsStore
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.random.Random

/**
 * mi's downloader against a real (local) web server: a download arrives byte for byte, and pausing and resuming
 * (HTTP Range) never writes bytes twice or loses any. Runs Android code through Robolectric; files are written to a
 * plain file (the save target), since the test has no media store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloaderTest {
    @Volatile private var body = Random(7).nextBytes(400 * 1024)
    /** Requests being answered right now, and the most at once. */
    private val open = java.util.concurrent.atomic.AtomicInteger()
    private val mostAtOnce = java.util.concurrent.atomic.AtomicInteger()
    private lateinit var server: HttpServer
    /** Bytes per write and pause between writes: slow enough for a test to pause mid-download. */
    @Volatile private var chunk = 64 * 1024
    @Volatile private var delayMs = 0L
    /** Requests to cut off partway (a dropped connection), counting down. */
    private val dropNext = java.util.concurrent.atomic.AtomicInteger()
    /** Requests the server has answered. */
    private val answered = java.util.concurrent.atomic.AtomicInteger()
    private lateinit var downloads: DownloadsStore
    private lateinit var downloader: Downloader

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.bin") { ex ->
            // "Range: bytes=N-" or "bytes=N-M" → 206 with those bytes; otherwise 200 with everything
            val range = ex.requestHeaders.getFirst("Range")?.removePrefix("bytes=")
            val from = range?.substringBefore('-')?.toIntOrNull() ?: 0
            val to = range?.substringAfter('-')?.toIntOrNull()?.plus(1) ?: body.size
            val part = body.copyOfRange(from, to)
            if (range != null) ex.responseHeaders.add("Content-Range", "bytes $from-${to - 1}/${body.size}")
            ex.sendResponseHeaders(if (range != null) 206 else 200, part.size.toLong())
            answered.incrementAndGet()
            mostAtOnce.accumulateAndGet(open.incrementAndGet(), ::maxOf)
            val cutAt = if (dropNext.getAndUpdate { maxOf(0, it - 1) } > 0) part.size / 2 else part.size
            if (cutAt < part.size) { // half the bytes, then the connection goes (the server ends it on an exception)
                ex.responseBody.write(part, 0, cutAt); ex.responseBody.flush(); open.decrementAndGet()
                throw java.io.IOException("dropped")
            }
            runCatching {
                ex.responseBody.use { out ->
                    var i = 0
                    while (i < cutAt) {
                        val n = minOf(chunk, cutAt - i)
                        out.write(part, i, n); out.flush(); i += n
                        if (delayMs > 0) Thread.sleep(delayMs)
                    }
                }
            }.also { open.decrementAndGet() }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool() // several downloads at once
        server.start()
        downloads = DownloadsStore(JsonStore(Files.createTempDirectory("store").toFile()))
        downloader = Downloader(ApplicationProvider.getApplicationContext(), downloads)
    }

    @After fun tearDown() = server.stop(0)

    private val url get() = "http://127.0.0.1:${server.address.port}/file.bin"
    private fun target() = File.createTempFile("download", ".bin")
    private fun start(file: File) = downloader.enqueue(url, null, "test", null, "application/octet-stream", body.size.toLong(), false,
        target = Uri.fromFile(file), targetName = file.name)

    /** Runs the main thread's queued store updates until [done] holds (or fails after [ms]). */
    private fun waitFor(ms: Long = 15_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out")
    }

    @Test fun downloadArrivesWhole() {
        val file = target()
        val id = start(file)
        waitFor { downloads[id]?.state == DownloadState.DONE }
        assertArrayEquals(body, file.readBytes())
        assertEquals(body.size.toLong(), downloads[id]!!.done)
    }

    @Test fun pauseAndResumeNeverDuplicatesBytes() {
        chunk = 8 * 1024; delayMs = 15 // ~50 writes: plenty of time to pause in the middle
        val file = target()
        val id = start(file)
        waitFor { file.length() > 64 * 1024 }
        downloader.pause(id)
        waitFor { downloads[id]?.state == DownloadState.PAUSED }
        Thread.sleep(200) // let the cancelled transfer stop writing
        shadowOf(Looper.getMainLooper()).idle()
        delayMs = 0
        downloader.resume(id)
        waitFor { downloads[id]?.state == DownloadState.DONE }
        assertArrayEquals(body, file.readBytes()) // every byte once, in order
    }

    @Test fun pauseRightAtTheStartStaysPaused() {
        chunk = 8 * 1024; delayMs = 15
        val file = target()
        val id = start(file)
        // the server has answered, so the transfer has queued its "now running" update for the main thread, which hasn't
        // run it yet: a pause now lands first, the race the update must not undo
        val end = System.currentTimeMillis() + 10_000
        while (answered.get() == 0 && System.currentTimeMillis() < end) Thread.sleep(10)
        Thread.sleep(300)
        downloader.pause(id)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DownloadState.PAUSED, downloads[id]!!.state)
    }

    @Test fun pauseAllPausesEveryRunningDownload() { // Android 15's daily limit for background transfers ran out
        chunk = 8 * 1024; delayMs = 15
        val ids = listOf(start(target()), start(target()))
        waitFor { ids.all { downloads[it]?.state == DownloadState.RUNNING } }
        downloader.pauseAll()
        shadowOf(Looper.getMainLooper()).idle()
        ids.forEach { assertEquals(DownloadState.PAUSED, downloads[it]!!.state) }
    }

    @Test fun aLargeFileComesOverSeveralConnections() {
        body = Random(9).nextBytes(20 * 1024 * 1024) // past the size worth splitting
        val file = target()
        val id = start(file)
        waitFor(30_000) { downloads[id]?.state == DownloadState.DONE }
        assertArrayEquals(body, file.readBytes()) // every part at its place
        assertEquals(true, mostAtOnce.get() > 1)
        assertEquals(null, downloads[id]!!.parts) // finished: nothing left to resume
    }

    @Test fun aDownloadInPartsPausesAndResumesEveryPart() {
        body = Random(11).nextBytes(20 * 1024 * 1024)
        chunk = 64 * 1024; delayMs = 10
        val file = target()
        val id = start(file)
        waitFor(30_000) { (downloads[id]?.done ?: 0) > 2 * 1024 * 1024 }
        downloader.pause(id)
        waitFor { downloads[id]?.state == DownloadState.PAUSED }
        Thread.sleep(300) // let the cancelled transfers stop writing
        shadowOf(Looper.getMainLooper()).idle()
        val parts = downloads[id]!!.parts!!
        assertEquals(true, parts.any { it.done > 0 } && parts.any { !it.finished }) // stopped partway, progress kept per part
        downloader.resume(id)
        waitFor { downloads[id]?.state == DownloadState.RUNNING } // shown as running again, not left "Waiting…"
        delayMs = 0
        waitFor(30_000) { downloads[id]?.state == DownloadState.DONE }
        assertArrayEquals(body, file.readBytes())
    }

    @Test fun aDroppedConnectionCarriesOnByItself() {
        dropNext.set(1) // the first answer stops halfway
        val file = target()
        val id = start(file)
        waitFor { downloads[id]?.state == DownloadState.FAILED }
        assertEquals(com.jlees.mibrowser.downloads.RETRYING, downloads[id]!!.error)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3)) // past the first retry's wait
        waitFor { downloads[id]?.state == DownloadState.DONE } // no Retry tapped
        assertArrayEquals(body, file.readBytes())
    }

    @Test fun downloadsRunningWhenMiClosedCarryOn() {
        val dir = Files.createTempDirectory("store").toFile()
        val file = target()
        val saved = DownloadsStore(JsonStore(dir))
        saved.add(com.jlees.mibrowser.downloads.DownloadItem("x", url, file.name, total = body.size.toLong(), state = DownloadState.RUNNING, uri = Uri.fromFile(file).toString()))
        saved.savers.forEach { it.writeNow() }
        val reopened = DownloadsStore(JsonStore(dir)).apply { load() }
        assertEquals(DownloadState.PAUSED, reopened["x"]!!.state)
        Downloader(ApplicationProvider.getApplicationContext(), reopened).resumeInterrupted()
        waitFor { reopened["x"]?.state == DownloadState.DONE }
        assertArrayEquals(body, file.readBytes())
    }
}

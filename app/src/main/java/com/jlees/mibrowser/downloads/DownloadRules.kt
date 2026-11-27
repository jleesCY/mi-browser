// Download rules: the saved record, file names, sizes and the status line. Plain functions, unit-tested.
package com.jlees.mibrowser.downloads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.net.URLDecoder

@Serializable
enum class DownloadState {
    @SerialName("queued") QUEUED, @SerialName("running") RUNNING, @SerialName("paused") PAUSED,
    @SerialName("done") DONE, @SerialName("failed") FAILED,
}

/**
 * One download. [uri] is where the file is saved (a MediaStore item, or a file:// path before Android 10). [total] is
 * -1 while the size is unknown. Incognito downloads are never saved to the list (the file itself stays in Downloads).
 */
@Serializable
data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mime: String = "application/octet-stream",
    val total: Long = -1,
    val done: Long = 0,
    val state: DownloadState = DownloadState.QUEUED,
    val uri: String? = null,
    val startedAt: Long = 0,
    val error: String? = null,
    /** The folder (a document tree the user picked) the file is created in; null: the Downloads folder. */
    val folder: String? = null,
    /** Fetched over several connections at once: each part's progress, so a paused download resumes every part. */
    val parts: List<DownloadPart>? = null,
    @Transient val incognito: Boolean = false,
    /** Bytes a second while running (smoothed; 0 before it's known). */
    @Transient val speed: Long = 0,
) {
    val active get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING
    /** 0..1, or null while the size is unknown. */
    val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

/** Bytes [start] until [end] (exclusive) of a file fetched in parts; [done] of them are written. */
@Serializable
data class DownloadPart(val start: Long, val end: Long, val done: Long = 0) {
    val finished get() = start + done >= end
}

/** Connections a large download is fetched over at once (each one's speed is often capped by the server or the route). */
const val DOWNLOAD_CONNECTIONS = 4
/** Smaller files aren't worth the extra connections. */
const val PARTS_FROM = 16L * 1024 * 1024
private const val MIN_PART = 4L * 1024 * 1024

/**
 * A [total]-byte file split into parts the connections take turns at: four per connection (at least 4 MB each), so
 * one that finishes early picks up another part instead of leaving the last one to a single slow connection.
 * ponytail: fixed parts, no splitting a part in flight; split the largest remaining part if tails still drag.
 */
fun planParts(total: Long, connections: Int = DOWNLOAD_CONNECTIONS): List<DownloadPart> {
    val size = maxOf(MIN_PART, (total + connections * 4 - 1) / (connections * 4))
    return (0 until total step size).map { DownloadPart(it, minOf(it + size, total)) }
}

/** What a failure shows under the download's name. */
fun downloadError(e: Exception): String = when {
    e.message?.contains("ENOSPC") == true -> "Not enough storage"
    e is java.io.IOException && e.message in OWN_ERRORS -> e.message!!
    e is java.net.SocketException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException || e is java.io.EOFException ||
        e is java.net.ProtocolException || e.message == CONNECTION_LOST -> CONNECTION_LOST
    e is java.io.IOException -> e.message?.takeIf { it.length < 60 } ?: "Connection problem"
    else -> "Connection problem"
}

const val CONNECTION_LOST = "Connection lost"
/** Failures of mi's own (the file, the folder, the server's answer): trying again by itself won't help. */
private val OWN_ERRORS = setOf("Can't open the file", "Can't save to the chosen folder", "Couldn't create the file", "Too many redirects")

/**
 * The file name for a download: the server's (Content-Disposition `filename*=` or `filename=`), else the last part
 * of the address, else "download"; with an extension from [extensionForMime] when it has none. Made safe for a file
 * system (no path separators or reserved characters).
 */
fun downloadFileName(url: String, contentDisposition: String?, mime: String?, extensionForMime: (String) -> String? = { null }): String {
    val fromHeader = contentDisposition?.let { cd ->
        Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
            runCatching { URLDecoder.decode(m.groupValues[2].trim().trim('"'), m.groupValues[1].ifBlank { "UTF-8" }) }.getOrNull()
        } ?: Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.trim()
    }
    val fromUrl = url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        .takeIf { it.isNotBlank() && !url.substringBefore('?').trimEnd('/').endsWith("//" + it) } // not just the host
    var name = (fromHeader ?: fromUrl ?: "download").replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.')
    if (name.isEmpty()) name = "download"
    if ('.' !in name) mime?.substringBefore(';')?.trim()?.let(extensionForMime)?.let { name = "$name.$it" }
    return name.take(120)
}

/** [name], or "name (1).ext", "name (2).ext"… if a file by that name is already [taken]. */
fun uniqueFileName(name: String, taken: (String) -> Boolean): String {
    if (!taken(name)) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot); val ext = name.substring(dot)
    return generateSequence(1) { it + 1 }.map { "$base ($it)$ext" }.first { !taken(it) }
}

/** 1,536 → "1.5 KB" (1024-based, like Files apps). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0; var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return (if (v >= 100) "%.0f %s" else "%.1f %s").format(java.util.Locale.US, v, units[i])
}

/**
 * The line under a download's name: progress while it runs, otherwise its state and size. [withTimeLeft]: the time
 * left ends the line (the notification); the Downloads page shows it beside the progress bar instead (see [timeLeftOf]).
 */
fun downloadStatus(d: DownloadItem, withTimeLeft: Boolean = true): String = when (d.state) {
    DownloadState.QUEUED -> "Waiting…"
    DownloadState.RUNNING -> when {
        d.speed > 0 -> listOfNotNull(
            if (d.total > 0) bytesOf(d.done, d.total) else formatBytes(d.done),
            "${formatBytes(d.speed)}/s",
            if (withTimeLeft) timeLeftOf(d) else null,
        ).joinToString(" · ")
        d.total > 0 -> "${formatBytes(d.done)} of ${formatBytes(d.total)} · ${(d.fraction!! * 100).toInt()}%"
        else -> formatBytes(d.done)
    }
    DownloadState.PAUSED -> "Paused · " + if (d.total > 0) "${formatBytes(d.done)} of ${formatBytes(d.total)}" else formatBytes(d.done)
    DownloadState.FAILED -> if (d.error == RETRYING) RETRYING else "Failed" + (d.error?.let { " · $it" } ?: "")
    DownloadState.DONE -> formatBytes(if (d.total > 0) d.total else d.done)
}

/** "40s left", "3 min left", "1h 5m left" (short: it shares a line with the progress and speed): [remaining] bytes at [speed] bytes a second (rounded up). */
fun timeLeft(remaining: Long, speed: Long): String {
    val s = (remaining + speed - 1) / speed
    return when {
        s < 60 -> "${s}s left"
        s < 3600 -> "${(s + 59) / 60} min left"
        else -> "${s / 3600}h ${(s % 3600 + 59) / 60}m left"
    }
}

/** A running download's time left, when its size and speed are known. */
fun timeLeftOf(d: DownloadItem): String? = if (d.state == DownloadState.RUNNING && d.total > 0 && d.speed > 0) timeLeft(d.total - d.done, d.speed) else null

/** "4.4 of 57.2 MB" (one unit when both share it), else "900 KB of 1.2 MB". */
fun bytesOf(done: Long, total: Long): String {
    val a = formatBytes(done); val b = formatBytes(total)
    return if (a.substringAfter(' ') == b.substringAfter(' ')) "${a.substringBefore(' ')} of $b" else "$a of $b"
}

/** A download's speed from its progress readings, smoothed over a few seconds so it doesn't jump with every one. */
class Speedometer {
    private var lastAt = -1L
    private var lastDone = 0L
    private var speed = 0.0

    /** [done] bytes at [nowMs]: the speed so far, in bytes a second. */
    fun reading(nowMs: Long, done: Long): Long {
        if (lastAt >= 0 && nowMs > lastAt) {
            val now = (done - lastDone) * 1000.0 / (nowMs - lastAt)
            speed = if (speed == 0.0) now else speed * 0.8 + now * 0.2
        }
        lastAt = nowMs; lastDone = done
        return speed.toLong()
    }
}

/** A download whose connection dropped, waiting to try again by itself. */
const val RETRYING = "Retrying when connected"

/** A download that stopped with [error] is retried by itself: only when the connection dropped (the network may come back). */
fun retriesItself(error: String?) = error == CONNECTION_LOST || error == RETRYING

/**
 * After the app was closed mid-download nothing is transferring any more: those downloads load as paused, and carry on
 * by themselves (DownloadsStore.interrupted), where they stopped if the server allows it.
 */
fun afterRestart(items: List<DownloadItem>) = items.map { if (it.active) it.copy(state = DownloadState.PAUSED) else it }

/** The downloads Delete All Downloads deletes: the finished ones (each one's file, which only mi saved); not one still going. */
fun finishedDownloads(items: List<DownloadItem>) = items.filter { it.state == DownloadState.DONE }

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
    @Transient val incognito: Boolean = false,
) {
    val active get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING
    /** 0..1, or null while the size is unknown. */
    val fraction get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

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

/** The line under a download's name: progress while it runs, otherwise its state and size. */
fun downloadStatus(d: DownloadItem): String = when (d.state) {
    DownloadState.QUEUED -> "Waiting…"
    DownloadState.RUNNING -> if (d.total > 0) "${formatBytes(d.done)} of ${formatBytes(d.total)} · ${(d.fraction!! * 100).toInt()}%" else formatBytes(d.done)
    DownloadState.PAUSED -> "Paused · " + if (d.total > 0) "${formatBytes(d.done)} of ${formatBytes(d.total)}" else formatBytes(d.done)
    DownloadState.FAILED -> "Failed" + (d.error?.let { " · $it" } ?: "")
    DownloadState.DONE -> formatBytes(if (d.total > 0) d.total else d.done)
}

/**
 * After the app was closed mid-download nothing is transferring any more: those downloads load as paused (they can
 * resume where they stopped, if the server allows it).
 */
fun afterRestart(items: List<DownloadItem>) = items.map { if (it.active) it.copy(state = DownloadState.PAUSED) else it }

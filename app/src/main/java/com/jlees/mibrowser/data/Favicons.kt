// Site icons kept on the device (filesDir/favicons/<host>.png). Filled as you browse, from the page itself: its touch
// icon (high resolution) or the icon WebView reports while loading. Lists showing a site with no stored icon ask that
// site for its own icon. No third-party icon service is ever contacted.
package com.jlees.mibrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import com.jlees.mibrowser.logic.faviconHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object Favicons {
    private const val MAX_PX = 128
    /** Stored icons kept at most (the least recently used beyond it are deleted at startup). */
    private const val MAX_ICONS = 500
    private const val MAX_BYTES = 512 * 1024
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dir: File? = null

    /** host → version of its stored icon. Snapshot state: lists switch to a newly saved icon right away. */
    private val stored = mutableStateMapOf<String, Int>()
    /** host → stored icon width, so a smaller icon never replaces a larger one. */
    private val widths = ConcurrentHashMap<String, Int>()
    /** Sites whose own icon couldn't be fetched this session (not retried until restart). */
    private val unavailable = ConcurrentHashMap.newKeySet<String>()
    /** Sites already asked for their standard touch icon this session. */
    private val upgradeTried = ConcurrentHashMap.newKeySet<String>()

    fun init(ctx: Context) {
        val d = File(ctx.filesDir, "favicons").apply { mkdirs() }
        dir = d
        io.launch {
            // at most MAX_ICONS kept: past that, the least recently used go (a file's date is when it was last shown)
            val all = d.listFiles().orEmpty()
            val drop = leastRecentlyUsed(all.map { it.name to it.lastModified() }, MAX_ICONS).toSet()
            all.filter { it.name in drop }.forEach { it.delete() }
            val found = all.filter { it.name !in drop }.mapNotNull { f ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                if (bounds.outWidth > 0) f.nameWithoutExtension to bounds.outWidth else { f.delete(); null }
            }
            found.forEach { (host, w) -> widths[host] = w }
            withContext(Dispatchers.Main) { found.forEach { (host, _) -> stored[host] = 0 } }
        }
    }

    /**
     * What an image view should load for [url]'s site: "favicon-file:<host>:<version>" for a stored icon, otherwise
     * "favicon:<host>" (fetched from the site itself). Both are handled by [load].
     */
    fun src(url: String?): String? {
        val host = faviconHost(url) ?: return null
        val v = stored[host]
        return if (v != null) "favicon-file:$host:$v" else "favicon:$host"
    }

    /** Loads a [src] value (on a background thread). */
    fun load(src: String): Bitmap? {
        val d = dir ?: return null
        if (src.startsWith("favicon-file:")) {
            val host = src.removePrefix("favicon-file:").substringBeforeLast(':')
            val f = File(d, "$host.png")
            f.setLastModified(System.currentTimeMillis()) // used: kept over icons not seen for a while (see init)
            return BitmapFactory.decodeFile(f.path)
        }
        val host = src.removePrefix("favicon:")
        if (host in unavailable) return null
        // the site's own icons only: the high-resolution touch icon, then favicon.ico. No cookies are sent.
        val bmp = listOf("https://$host/apple-touch-icon.png", "https://$host/favicon.ico").firstNotNullOfOrNull { fetch(it) }
        if (bmp == null) { unavailable += host; return null }
        save(host, bmp)
        return bmp
    }

    /** Fetches an icon declared by a page (its touch icon) and stores it for [host]. */
    fun fetchFor(host: String, iconUrl: String) {
        if (!iconUrl.startsWith("https://") && !iconUrl.startsWith("http://")) return
        io.launch { fetch(iconUrl)?.let { save(host, it) } }
    }

    /** The page declared no large icon and only a small one is stored: ask the site itself for its standard
     *  /apple-touch-icon.png, once per session. */
    fun upgrade(host: String) {
        if ((widths[host] ?: 0) >= 64 || !upgradeTried.add(host)) return
        fetchFor(host, "https://$host/apple-touch-icon.png")
    }

    /** Stores [icon] for [host] unless a larger icon is already stored. */
    fun save(host: String, icon: Bitmap) {
        val d = dir ?: return
        if (icon.width <= (widths[host] ?: 0)) return
        val scaled = if (icon.width > MAX_PX || icon.height > MAX_PX) Bitmap.createScaledBitmap(icon, MAX_PX, MAX_PX * icon.height / icon.width.coerceAtLeast(1), true) else icon
        widths[host] = icon.width
        io.launch {
            runCatching { File(d, "$host.png").outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) } }.onSuccess {
                withContext(Dispatchers.Main) { stored[host] = (stored[host] ?: 0) + 1 }
            }
        }
    }

    /** A stored icon for [url]'s site, decoded now (small file); null if none. For home-screen shortcuts. */
    fun bitmapFor(url: String?): Bitmap? = src(url)?.takeIf { it.startsWith("favicon-file:") }?.let(::load)

    /** Clear Browsing Data: stored icons reveal which sites were visited. */
    fun clear() {
        stored.clear(); widths.clear(); unavailable.clear(); upgradeTried.clear()
        val d = dir ?: return
        io.launch { d.listFiles()?.forEach { it.delete() } }
    }

    private fun fetch(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000; instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) return null
        val (bytes, more) = conn.inputStream.use { com.jlees.mibrowser.logic.readAtMost(it, MAX_BYTES) }
        if (more) return null // bigger than any icon: not one
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 16 && it.height >= 16 }
    }.getOrNull()
}

/** The names (of [items]: name and last-used time) beyond the [keep] most recently used: the ones to delete. */
fun leastRecentlyUsed(items: List<Pair<String, Long>>, keep: Int): List<String> =
    items.sortedByDescending { it.second }.drop(keep).map { it.first }

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
            val found = d.listFiles()?.mapNotNull { f ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                if (bounds.outWidth > 0) f.nameWithoutExtension to bounds.outWidth else { f.delete(); null }
            }.orEmpty()
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
            return BitmapFactory.decodeFile(File(d, "$host.png").path)
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
        val bytes = conn.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) return null // not an icon
            }
            out.toByteArray()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 16 && it.height >= 16 }
    }.getOrNull()
}

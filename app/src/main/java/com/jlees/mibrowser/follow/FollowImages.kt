// Followed products' pictures, kept on the phone: downloaded once (the shop isn't asked again each time the list shows).
package com.jlees.mibrowser.follow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object FollowImages {
    private const val MAX_PX = 480
    /** A picture bigger than this isn't a product thumbnail worth the memory. */
    private const val MAX_BYTES = 15L shl 20

    private fun dir(ctx: Context) = File(ctx.filesDir, "follow_images")

    /**
     * Downloads [image] (from the product data; relative to [pageUrl]) as follow [id]'s picture: shrunk to at most
     * [MAX_PX] a side, saved as a JPEG. Returns its file path, or null if it couldn't be fetched or read. Off the main thread.
     */
    fun save(ctx: Context, id: String, image: String, pageUrl: String): String? = runCatching {
        val url = URL(URL(pageUrl), image)
        if (url.protocol != "https" && url.protocol != "http") return null
        val bytes = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(ctx).replace("; wv", ""))
            try { if (responseCode >= 400 || contentLengthLong > MAX_BYTES) null else inputStream.use { it.readBytes() } } finally { disconnect() }
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_PX && bounds.outHeight / (sample * 2) >= MAX_PX) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val file = File(dir(ctx).apply { mkdirs() }, "$id.jpg")
        file.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    }.getOrNull()

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.jpg").delete() }

    /** Wipe All Data. */
    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }
}

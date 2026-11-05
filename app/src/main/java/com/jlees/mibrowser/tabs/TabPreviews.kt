package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.ui.common.cacheImage

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.compose.ui.graphics.asImageBitmap
import com.jlees.mibrowser.BrowserViewModel
import java.io.File

/**
 * The page snapshot on each tab's switcher card: drawn at half size, shown from memory at once and saved to the cache
 * (for after a restart) off the main thread. Tabs that keep no traces are never photographed.
 */
class TabPreviews(private val activity: Activity, private val vm: BrowserViewModel) {
    fun capture(id: String, wv: WebView?) {
        val tab = vm.tabs.list.firstOrNull { it.id == id }
        if (tab == null || !tab.keepsTraces) return // incognito tabs are never photographed: no snapshot in memory or on disk
        if (wv != null && wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow) {
            try {
                val bmp = Bitmap.createBitmap(wv.width / 2, wv.height / 2, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply { scale(0.5f, 0.5f); translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat()) }.also { wv.draw(it) }
                // shown from memory right away; the file (for after a restart) is written off the main thread
                val file = File(activity.cacheDir, "preview_${id}_${System.currentTimeMillis()}.jpg")
                cacheImage(file.path, bmp.asImageBitmap())
                val old = tab.previewImage
                vm.tabs.update(id) { copy(previewImage = file.path) }
                Thread {
                    runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                    old?.let { File(it).delete() }
                }.start()
            } catch (_: Exception) {}
        }
        if (vm.tabs.list.none { it.url != null }) {
            activity.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
        }
    }
}

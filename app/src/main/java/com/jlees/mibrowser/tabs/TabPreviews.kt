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
 * The page snapshot on each tab's switcher card: drawn at full size and kept at half, shown from memory at once and saved to the cache
 * (for after a restart) off the main thread. Tabs that keep no traces are never photographed.
 */
class TabPreviews(private val activity: Activity, private val vm: BrowserViewModel) {
    /** What each tab's page was like at its last snapshot: unchanged, there's no need to take another. */
    private val lastShot = HashMap<String, String>()
    /** Saves snapshots one at a time, off the main thread. */
    private val saver = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun capture(id: String, wv: WebView?) {
        val tab = vm.tabs.list.firstOrNull { it.id == id }
        if (tab == null || !tab.keepsTraces) return // incognito tabs are never photographed: no snapshot in memory or on disk
        // the same page, scrolled to the same place, at the same size and fully drawn: the last snapshot still stands
        val shot = wv?.let { "${it.url}|${it.scrollX},${it.scrollY}|${it.width}x${it.height}|${it.contentHeight}|${it.progress}" }
        if (shot != null && shot == lastShot[id] && tab.previewImage != null) return
        // with the keyboard up the page is shorter than usual: such a snapshot wouldn't match the page (keep the last one)
        if (wv != null && androidx.core.view.ViewCompat.getRootWindowInsets(wv)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true) return
        if (wv != null && wv.width > 0 && wv.height > 0 && wv.isAttachedToWindow) {
            try {
                // drawn at full size, exactly as on screen, then shrunk: asked to draw into a scaled-down canvas, WebView gets
                // some pages wrong (CSS-sized images squashed, text at another size; e.g. espn.com's stats tables)
                val full = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                Canvas(full).apply { translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat()) }.also { wv.draw(it) }
                val bmp = Bitmap.createScaledBitmap(full, wv.width / 2, wv.height / 2, true)
                if (bmp !== full) full.recycle()
                // shown from memory right away; the file (for after a restart) is written off the main thread
                val file = File(activity.cacheDir, "preview_${id}_${System.currentTimeMillis()}.jpg")
                cacheImage(file.path, bmp.asImageBitmap())
                val old = tab.previewImage
                vm.tabs.update(id) { copy(previewImage = file.path) }
                if (shot != null) lastShot[id] = shot
                saver.execute {
                    runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                    old?.let { File(it).delete() }
                }
            } catch (_: Exception) {}
        }
        if (vm.tabs.list.none { it.url != null } && vm.tabs.recentlyClosed.isEmpty()) {
            activity.cacheDir.listFiles { f -> f.name.startsWith("preview_") }?.forEach { it.delete() }
        }
    }
}

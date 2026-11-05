// Saving images to the gallery: from a page (long-press menu) and from the QR generator.
package com.jlees.mibrowser.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.ContextCompat
import com.jlees.mibrowser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val mediaScope: CoroutineScope = MainScope()

/** Writes bytes into Pictures/ via MediaStore (API 29+) or the public dir (≤28). */
fun saveToGallery(ctx: Context, bytes: ByteArray, name: String, mime: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create file")
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).apply { mkdirs() }
        val f = File(dir, name).apply { writeBytes(bytes) }
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.path), arrayOf(mime), null)
    }
}

fun needsLegacyWritePermission(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, name: String) {
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    saveToGallery(ctx, out.toByteArray(), name, "image/png")
}

fun saveImageFromUrl(activity: Activity, vm: BrowserViewModel, url: String) {
    // SECURITY: only http, https and data URLs
    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("data:")) return
    if (needsLegacyWritePermission(activity)) {
        vm.showAlert("Permission Required", "This app needs access to your Photos.")
        androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        return
    }
    val ext = when {
        url.contains(".png") -> "png"
        url.contains(".gif") -> "gif"
        url.contains(".webp") -> "webp"
        url.startsWith("data:image/png") -> "png"
        else -> "jpg"
    }
    val mime = if (ext == "jpg") "image/jpeg" else "image/$ext"
    mediaScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val bytes = if (url.startsWith("data:")) Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
                else (URL(url).openConnection() as HttpURLConnection).run {
                    if (responseCode != 200) error("Download failed")
                    inputStream.use { it.readBytes() }
                }
                saveToGallery(activity, bytes, "download_${System.currentTimeMillis()}.$ext", mime)
            }
            vm.showAlert("Success", "Image saved to gallery!")
            vm.contextMenu = null
        } catch (e: Exception) {
            vm.showAlert("Save Error", e.message ?: "Unknown error")
        }
    }
}

package com.jlees.mibrowser.downloads

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ButtonStyle
import com.jlees.mibrowser.logic.displayHost
import java.net.URL

/** A page started a download: confirm with the user, then hand it to mi's [Downloader] with the tab's cookies. */
class DownloadPrompt(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
) {
    fun start(cookies: CookieManager, incognito: Boolean, url: String, userAgent: String, contentDisposition: String?, mimetype: String?, length: Long) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            vm.showAlert("Can't download", "This file is created by the page itself (a blob or data address), which mi can't save yet.")
            return
        }
        val fileName = downloadFileName(url, contentDisposition, mimetype) { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val cookie = runCatching { URL(url).let { cookies.getCookie("${it.protocol}://${it.host}") } }.getOrNull()
        val go = {
            vm.downloader.enqueue(url, cookie, userAgent, contentDisposition, mimetype, length, incognito)
            Toast.makeText(activity, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        }
        val start = {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) go()
            else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if (it.values.all { g -> g }) go() }
        }
        val size = if (length > 0) " (${formatBytes(length)})" else ""
        vm.showAlert(
            "Download file?", "$fileName$size\nfrom ${displayHost(url)}" +
                if (incognito) "\n\nThe file is saved to your Downloads folder and stays after you close this incognito tab." else "",
            listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Download") { start() }),
            onCancel = {},
        )
    }
}

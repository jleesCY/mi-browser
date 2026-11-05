package com.jlees.mibrowser.web

import com.jlees.mibrowser.ButtonStyle
import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import java.net.URL
import android.webkit.CookieManager

/** Page downloads: confirmed by the user, then saved to Downloads by the system DownloadManager with the tab's cookies. */
class Downloads(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
) {
    fun start(cookies: CookieManager, incognito: Boolean, url: String, userAgent: String, contentDisposition: String?, mimetype: String?, length: Long) {
        val req = try { DownloadManager.Request(Uri.parse(url)) } catch (_: IllegalArgumentException) { return }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype).replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        try {
            val u = URL(url)
            cookies.getCookie("${u.protocol}://${u.host}")?.let { req.addRequestHeader("Cookie", it) }
        } catch (_: Exception) {}
        req.addRequestHeader("User-Agent", userAgent)
        req.setTitle(fileName)
        req.setDescription("Downloading $fileName")
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val go = {
            try {
                (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(activity, "Downloading $fileName", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
        val start = {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) go()
            else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if (it.values.all { g -> g }) go() }
        }
        val size = if (length > 0) " (" + android.text.format.Formatter.formatShortFileSize(activity, length) + ")" else ""
        vm.showAlert(
            "Download file?", "$fileName$size\nfrom ${displayHost(url)}" +
                if (incognito) "\n\nThe file is saved to your Downloads folder and stays after you close this incognito tab." else "",
            listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Download") { start() }),
            onCancel = {},
        )
    }
}

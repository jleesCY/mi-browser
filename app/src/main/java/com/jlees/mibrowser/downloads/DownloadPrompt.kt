package com.jlees.mibrowser.downloads

import com.jlees.mibrowser.data.DownloadHandler


import androidx.activity.result.ActivityResult

import android.provider.DocumentsContract

import android.os.Environment

import android.net.Uri

import android.content.Intent


import android.app.DownloadManager

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

/**
 * A page started a download: confirm with the user (Settings → Downloads → Confirm Downloads), then hand it to the
 * chosen downloader: mi's own (into Downloads, a picked folder, or a file chosen in the save dialog) or Android's
 * download manager. Incognito tabs always use mi's: Android's would keep a record of the download.
 */
class DownloadPrompt(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit,
) {
    fun start(cookies: CookieManager, incognito: Boolean, url: String, userAgent: String, contentDisposition: String?, mimetype: String?, length: Long) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            vm.showAlert("Can't download", "This file is created by the page itself (a blob or data address), which mi can't save yet.")
            return
        }
        val s = vm.settings
        val handler = if (incognito) DownloadHandler.MI else s.downloadHandler
        val fileName = downloadFileName(url, contentDisposition, mimetype) { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val cookie = runCatching { URL(url).let { cookies.getCookie("${it.protocol}://${it.host}") } }.getOrNull()

        fun withMi(target: Uri? = null, targetName: String? = null) {
            vm.downloader.enqueue(url, cookie, userAgent, contentDisposition, mimetype, length, incognito,
                folder = s.downloadFolder.takeIf { target == null }, target = target, targetName = targetName)
            Toast.makeText(activity, "Downloading ${targetName ?: fileName}", Toast.LENGTH_SHORT).show()
        }
        // mi's downloader: the progress and "Download complete" notifications need permission on Android 13+, asked at
        // the first download (it starts whatever the answer; Android stops asking after two refusals)
        val mi = {
            // not in incognito: Android's save dialog can remember where it saved in its own recents
            val go = { if (s.askWhereToSave && !incognito) chooseFile(fileName, mimetype) { uri, name -> withMi(uri, name) } else withMi() }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { go() }
            else go()
        }
        val handOff: () -> Unit = when (handler) {
            DownloadHandler.MI -> mi
            DownloadHandler.ANDROID -> { { withAndroid(url, cookie, userAgent, fileName) } }
        }
        // writing to the shared Downloads folder needs permission before Android 10
        val start = {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) handOff()
            else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if (it.values.all { g -> g }) handOff() }
        }
        if (!s.confirmDownloads) { start(); return }
        val size = if (length > 0) " (${formatBytes(length)})" else ""
        vm.showAlert(
            "Download file?", "$fileName$size\nfrom ${displayHost(url)}" +
                if (incognito) "\n\nThe file is saved to your phone and stays after you close this incognito tab." else "",
            listOf(AlertButton("Cancel", ButtonStyle.CANCEL), AlertButton("Download") { start() }),
            onCancel = {},
        )
    }

    /** Ask Where to Save: Android's save dialog (opening in the chosen folder); [then] gets the file and its name. */
    private fun chooseFile(name: String, mime: String?, then: (Uri, String) -> Unit) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, name)
        vm.settings.downloadFolder?.let { f ->
            val tree = Uri.parse(f)
            runCatching { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))) }
        }
        runCatching {
            launchForResult(intent) { res ->
                val uri = res.data?.data ?: return@launchForResult // cancelled
                // the save dialog's access lasts only until mi's process ends: keep it, so the download can resume after a
                // restart and its file can still be deleted from the list (given back when it's deleted)
                runCatching {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                val chosen = runCatching {
                    activity.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull()
                then(uri, chosen ?: name)
            }
        }.onFailure { vm.showAlert("Can't choose a location", "This phone has no save dialog; the file is saved to Downloads instead.") }
    }

    /** Android's download manager: it saves to Downloads and shows its own notifications (not mi's list). */
    private fun withAndroid(url: String, cookie: String?, userAgent: String, fileName: String) {
        val req = runCatching { DownloadManager.Request(Uri.parse(url)) }.getOrNull() ?: return
        cookie?.let { req.addRequestHeader("Cookie", it) }
        req.addRequestHeader("User-Agent", userAgent)
        req.setTitle(fileName)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        runCatching { activity.getSystemService(DownloadManager::class.java).enqueue(req) }
            .onSuccess { Toast.makeText(activity, "Downloading $fileName with Android", Toast.LENGTH_SHORT).show() }
            .onFailure { vm.showAlert("Couldn't download", "Android's download manager didn't take it. Choose mi in Settings → Downloads.") }
    }
}

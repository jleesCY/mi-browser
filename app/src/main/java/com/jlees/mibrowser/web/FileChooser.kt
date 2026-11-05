package com.jlees.mibrowser.web

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** `<input type="file">`: the system picker, plus the camera for image inputs when mi may use it. */
class FileChooser(private val activity: Activity, private val launchForResult: (Intent, (ActivityResult) -> Unit) -> Unit) {
        fun show(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            val content = params.createIntent().apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            val accept = params.acceptTypes.joinToString(",")
            val wantsImage = accept.isBlank() || accept.contains("image")
            var captured: Uri? = null
            val extras = mutableListOf<Intent>()
            if (wantsImage && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val file = File(activity.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                captured = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                extras += Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, captured)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(content, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
            try {
                launchForResult(chooser) { res ->
                    val uris = FileChooserParams.parseResult(res.resultCode, res.data)
                        ?: res.data?.clipData?.let { cd -> Array(cd.itemCount) { cd.getItemAt(it).uri } }
                        ?: if (res.resultCode == Activity.RESULT_OK && captured != null) arrayOf(captured) else null
                    callback.onReceiveValue(uris)
                }
            } catch (_: ActivityNotFoundException) {
                callback.onReceiveValue(null)
                return false
            }
            return true
        }
}

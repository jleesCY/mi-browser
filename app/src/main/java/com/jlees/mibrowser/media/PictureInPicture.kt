// Picture-in-picture: a fullscreen video shrinks into a floating window when you leave mi (Home, recent apps). Android
// 12+ enters it on its own with the gesture (smoother); older versions are asked when mi is left (onUserLeaveHint).
package com.jlees.mibrowser.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

object PictureInPicture {
    fun supported(a: Activity) = a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The window's shape follows the video ([size]); [auto]: Android 12+ enters picture-in-picture by itself. */
    fun params(size: Pair<Int, Int>, auto: Boolean): PictureInPictureParams {
        val (w, h) = pipAspect(size.first, size.second)
        return PictureInPictureParams.Builder().setAspectRatio(Rational(w, h))
            .apply { if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(auto) }.build()
    }
}

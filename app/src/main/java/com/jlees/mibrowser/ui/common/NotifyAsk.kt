// Asking for Android's permission to notify, for the features that notify (Following, app mode).
package com.jlees.mibrowser.ui.common

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat

/**
 * Asks for Android's permission to notify when mi lacks it (Android 13+; before that it's only ever turned off in
 * Android's settings). Given true: if it's refused, or was for good (Android stops asking after twice), Android's
 * notification settings for mi open instead; that's for when you've just turned Notifications on yourself.
 */
@Composable
fun rememberNotifyAsk(): (thenSettings: Boolean) -> Unit {
    val ctx = LocalContext.current
    var thenSettings by remember { mutableStateOf(false) }
    val openSettings = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted && thenSettings) openSettings() }
    return { orSettings ->
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                thenSettings = orSettings; launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else if (orSettings) openSettings()
        }
    }
}

// The silent notification shown while incognito tabs are open; tapping it closes them all.
package com.jlees.mibrowser.incognito

import android.content.Intent
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R

/**
 * While incognito tabs are open: a silent notification that closes them all. Hidden on the lock screen, never
 * bridged to other devices. Cancelled at launch too, in case the process ended while it was showing.
 */
object IncognitoNotice {
    fun update(ctx: android.content.Context, count: Int) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (count == 0 || !nm.areNotificationsEnabled()) { nm.cancel(ID); return }
        nm.createNotificationChannel(android.app.NotificationChannel("incognito", "Incognito tabs", android.app.NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while incognito tabs are open"; setShowBadge(false)
        })
        val close = android.app.PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_CLOSE_INCOGNITO),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(ID, android.app.Notification.Builder(ctx, "incognito")
            .setSmallIcon(R.drawable.sym_incognito)
            .setContentTitle(if (count == 1) "1 incognito tab open" else "$count incognito tabs open")
            .setContentText("Tap to close incognito tabs")
            .setContentIntent(close)
            .setVisibility(android.app.Notification.VISIBILITY_SECRET)
            .setLocalOnly(true).setOngoing(true).setShowWhen(false).setOnlyAlertOnce(true)
            .build())
    }

    private const val ID = 0x1c0
}

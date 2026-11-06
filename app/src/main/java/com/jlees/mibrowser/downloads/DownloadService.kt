package com.jlees.mibrowser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R

/**
 * Keeps downloads going while mi is in the background (a foreground service, so Android doesn't stop the transfer)
 * and shows their progress in a notification. Stops itself when nothing is downloading.
 */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val active = Downloader.items().filter { it.active }
            if (active.isEmpty()) { stopSelf(); return }
            nm.notify(PROGRESS_ID, progress(this@DownloadService, active))
            handler.postDelayed(this, 1000)
        }
    }
    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        val n = progress(this, Downloader.items().filter { it.active })
        if (Build.VERSION.SDK_INT >= 29) startForeground(PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(PROGRESS_ID, n)
        handler.removeCallbacks(tick); handler.post(tick)
        return START_NOT_STICKY // after the process dies, downloads come back paused (see afterRestart)
    }

    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "downloads"
        private const val PROGRESS_ID = 0x2d0

        /** Starts the service while something is downloading (it stops itself after). */
        fun ensureRunning(ctx: Context) {
            if (Downloader.items().none { it.active }) return
            runCatching { ctx.startForegroundService(Intent(ctx, DownloadService::class.java)) }
        }

        /** "Download complete" (tap opens the file) or "Download failed" (tap opens mi's downloads). */
        fun notifyFinished(ctx: Context, item: DownloadItem, downloader: Downloader) {
            channel(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            val done = item.state == DownloadState.DONE
            val open = downloader.shareableUri(item)?.takeIf { done }?.let { uri ->
                PendingIntent.getActivity(ctx, item.id.hashCode(),
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } ?: openDownloads(ctx)
            nm.notify(item.id.hashCode(), Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(if (done) R.drawable.sym_download else R.drawable.sym_error)
                .setContentTitle(if (done) "Download complete" else "Download failed")
                .setContentText(if (item.incognito) "Incognito download" else item.fileName)
                .setContentIntent(open).setAutoCancel(true)
                .setVisibility(if (item.incognito) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
                .build())
        }

        private fun progress(ctx: Context, active: List<DownloadItem>): Notification {
            val one = active.singleOrNull()
            val total = active.sumOf { it.total.coerceAtLeast(0) }
            val done = active.sumOf { it.done }
            val known = active.isNotEmpty() && active.all { it.total > 0 }
            return Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.sym_download)
                .setContentTitle(when { one == null -> "Downloading ${active.size} files"; one.incognito -> "Incognito download"; else -> one.fileName })
                .setContentText(one?.let(::downloadStatus) ?: "${formatBytes(done)} of ${formatBytes(total)}")
                .setProgress(100, if (known && total > 0) (done * 100 / total).toInt() else 0, !known)
                .setContentIntent(openDownloads(ctx))
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setVisibility(if (active.any { it.incognito }) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
                .build()
        }

        private fun openDownloads(ctx: Context) = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun channel(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Download progress and finished downloads"; setShowBadge(false)
            })
    }
}

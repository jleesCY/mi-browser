// The media notification (and lock-screen / headset controls) for what a page is playing. A foreground service while
// it plays and for 10 minutes after a pause, so Android keeps mi alive in the background and headset or lock-screen
// buttons can resume it; after that it's an ordinary notification you can swipe away. A paused page itself is suspended
// (see keepsRunning), so the grace period costs next to nothing.
package com.jlees.mibrowser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R

enum class MediaCommand { PLAY, PAUSE, STOP, BACK, FORWARD }

/** How far the notification's skip buttons jump. */
const val SKIP_SECONDS = 10

/**
 * What the notification shows. [title] and [artist] are generic for incognito tabs, which have no [art]. Position is
 * [positionMs] at [at] (elapsedRealtime), moving at [rate] while playing; [durationMs] is negative for live streams.
 */
data class MediaInfo(
    val title: String, val artist: String, val playing: Boolean, val tabId: String,
    val positionMs: Long = 0, val durationMs: Long = -1, val rate: Float = 1f, val at: Long = 0, val art: android.graphics.Bitmap? = null,
)

class MediaService : Service() {
    private lateinit var session: MediaSession
    private val nm get() = getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    /** Paused for a while: let go of the foreground (the notification stays, now dismissible). */
    private val letGo = Runnable { foreground = false; stopForeground(STOP_FOREGROUND_DETACH) }
    /** Headphones unplugged or Bluetooth audio gone: pause instead of carrying on through the phone's speaker
     *  (WebView doesn't). Calls and other apps are handled by WebView itself (it holds audio focus). */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (state?.playing == true) commands?.invoke(MediaCommand.PAUSE) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Media playback", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Controls for audio and video playing in mi"; setShowBadge(false)
        })
        session = MediaSession(this, "mi").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { commands?.invoke(MediaCommand.PLAY) }
                override fun onPause() { commands?.invoke(MediaCommand.PAUSE) }
                override fun onStop() { commands?.invoke(MediaCommand.STOP) }
                override fun onSeekTo(pos: Long) { seek?.invoke(pos) }
                override fun onRewind() { commands?.invoke(MediaCommand.BACK) }
                override fun onFastForward() { commands?.invoke(MediaCommand.FORWARD) }
                override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                    MediaCommand.entries.firstOrNull { it.name == action }?.let { commands?.invoke(it) }
                }
            })
            isActive = true
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(noisy, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // the notification's buttons
        intent?.action?.let { a -> MediaCommand.entries.firstOrNull { it.name == a }?.let { commands?.invoke(it) } }
        update()
        return START_NOT_STICKY // after the process dies, the pages playing are gone too
    }

    private fun update() {
        val s = state ?: run { stopSelf(); return }
        val seekable = s.durationMs > 0 // live streams can't be scrubbed
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.title).putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
            .apply { if (seekable) putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs) }
            .apply { s.art?.let { putBitmap(MediaMetadata.METADATA_KEY_ART, it) } }
            .build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                (if (seekable) PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD else 0L))
            // the skip buttons on Android 13+ media controls (built from the session, not the notification's buttons)
            .apply { if (seekable) skipButtons.forEach { (c, icon, label) -> addCustomAction(c.name, label, icon) } }
            // the system moves the seek bar on from this position at this speed until the next report
            .setState(if (s.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                if (seekable) s.positionMs else PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (s.playing) s.rate else 0f, s.at)
            .build())
        val n = notification(s)
        if (s.playing) {
            handler.removeCallbacks(letGo)
            // ponytail: Android 12+ may refuse to make a service foreground while mi is in the background (resumed after
            // the grace period); the notification still shows, mi is just easier for Android to end until reopened
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
                foreground = true
            }.onFailure { nm.notify(ID, n) }
        } else {
            nm.notify(ID, n)
            if (foreground) { handler.removeCallbacks(letGo); handler.postDelayed(letGo, PAUSED_GRACE_MS) }
        }
    }

    private fun notification(s: MediaInfo): Notification {
        val toggle = if (s.playing) MediaCommand.PAUSE else MediaCommand.PLAY
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_TAB).putExtra(MainActivity.EXTRA_TAB, s.tabId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(c: MediaCommand, icon: Int, label: String) = Notification.Action.Builder(Icon.createWithResource(this, icon), label, command(c)).build()
        val playPause = action(toggle, if (s.playing) R.drawable.sym_pause else R.drawable.sym_play_arrow, if (s.playing) "Pause" else "Play")
        // older Android shows these buttons; skipping needs a known length (not a live stream)
        val actions = if (s.durationMs > 0) listOf(action(skipButtons[0].first, skipButtons[0].second, skipButtons[0].third), playPause,
            action(skipButtons[1].first, skipButtons[1].second, skipButtons[1].third)) else listOf(playPause)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.sym_play_arrow)
            .setContentTitle(s.title).setContentText(s.artist).setLargeIcon(s.art)
            .apply { actions.forEach { addAction(it) } }
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*actions.indices.toList().toIntArray()))
            .setContentIntent(open).setDeleteIntent(command(MediaCommand.STOP))
            .setOngoing(s.playing).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // controls on the lock screen; incognito titles are generic
            .build()
    }

    private fun command(c: MediaCommand) = PendingIntent.getService(this, c.ordinal, Intent(this, MediaService::class.java).setAction(c.name), PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() {
        handler.removeCallbacks(letGo)
        unregisterReceiver(noisy)
        session.release()
        nm.cancel(ID) // detached while paused, so it isn't removed with the service
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "media"
        private const val ID = 0x3e0
        private const val PAUSED_GRACE_MS = 10 * 60_000L
        private val skipButtons = listOf(
            Triple(MediaCommand.BACK, R.drawable.sym_fast_rewind, "Back $SKIP_SECONDS seconds"),
            Triple(MediaCommand.FORWARD, R.drawable.sym_fast_forward, "Forward $SKIP_SECONDS seconds"),
        )
        private var instance: MediaService? = null
        /** What's playing (null: nothing; the service stops). Set by BackgroundMedia. */
        var state: MediaInfo? = null
        /** Where the buttons go (BackgroundMedia). */
        var commands: ((MediaCommand) -> Unit)? = null
        /** Where the seek bar goes: a position in ms (BackgroundMedia). */
        var seek: ((Long) -> Unit)? = null

        /** Shows [state]: starts the service when something plays, updates it, or stops it when [state] is null. */
        fun refresh(ctx: Context) {
            val running = instance
            if (running != null) running.update()
            else if (state?.playing == true) runCatching { ctx.startForegroundService(Intent(ctx, MediaService::class.java)) }
        }
    }
}

// Following's notifications: price changes (right away, or in a daily summary, as each product is set) and the ones
// asking you to look (a price that couldn't be found, or looks wrong), which always come.
package com.jlees.mibrowser.follow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R

object FollowNotifier {
    private const val PRICES = "follow_prices"
    private const val ATTENTION = "follow_attention"
    private const val DIGEST_ID = 0x70112
    private const val SUMMARY_ID = 0x70113
    /** Each product's notifications are bundled under one ([SUMMARY_ID]) when there are several. */
    private const val GROUP = "follow"
    /** A daily summary goes out once its oldest change has waited this long. */
    private const val DIGEST_AFTER = 20 * 3_600_000L
    private const val STALE = 2 * 24 * 3_600_000L

    /** Notifies the [updates] you asked for, of products set to notify right away (those asking you to look, always). */
    fun notify(ctx: Context, store: FollowStore, updates: List<FollowUpdate>) {
        val nm = manager(ctx) ?: return
        val sent = mutableSetOf<String>()
        for (u in updates) {
            val f = store[u.followId] ?: continue
            val asks = u.kind == UpdateKind.NEEDS_ATTENTION || u.kind == UpdateKind.CONFIRM
            if (!asks && (!u.alert || f.notify != NotifyMode.NOW)) continue
            nm.notify(u.followId.hashCode(), Notification.Builder(ctx, if (asks) ATTENTION else PRICES)
                .setSmallIcon(R.drawable.sym_sell)
                .setContentTitle(f.title.ifBlank { "Followed product" })
                .setContentText(u.text)
                .setContentIntent(open(ctx, f.id))
                .setGroup(GROUP)
                .setAutoCancel(true)
                .build())
            sent += u.id
        }
        if (sent.isEmpty()) return
        store.markNotified(sent)
        // two or more showing: Android bundles them under this one (it shows only when there are several), which stays quiet
        val showing = nm.activeNotifications.filter { it.notification.group == GROUP && it.id != SUMMARY_ID }.map { it.id }.toSet() +
            updates.filter { it.id in sent }.map { it.followId.hashCode() }
        if (showing.size < 2) return
        nm.notify(SUMMARY_ID, Notification.Builder(ctx, PRICES)
            .setSmallIcon(R.drawable.sym_sell)
            .setContentTitle("${showing.size} followed products")
            .setContentIntent(open(ctx, null))
            .setGroup(GROUP).setGroupSummary(true).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .build())
    }

    /** The daily summary: the changes you asked for of products set to Daily, once the oldest has waited [DIGEST_AFTER]. */
    fun digest(ctx: Context, store: FollowStore) {
        val now = System.currentTimeMillis()
        // only recent changes: ones held back while notifications were off (or the product was set to Never) stay quiet
        val waiting = store.updates.filter { u -> !u.notified && u.alert && now - u.at < STALE && store[u.followId]?.notify == NotifyMode.DIGEST }
        if (waiting.isEmpty() || now - waiting.minOf { it.at } < DIGEST_AFTER) return
        val nm = manager(ctx) ?: return
        val lines = waiting.take(6).map { "${store[it.followId]?.title.orEmpty().take(30)}: ${it.text}" }
        nm.notify(DIGEST_ID, Notification.Builder(ctx, PRICES)
            .setSmallIcon(R.drawable.sym_sell)
            .setContentTitle("${waiting.size} price ${if (waiting.size == 1) "change" else "changes"}")
            .setContentText(lines.first())
            .setStyle(Notification.InboxStyle().also { s -> lines.forEach(s::addLine) })
            .setContentIntent(open(ctx, null))
            .setAutoCancel(true)
            .build())
        store.markNotified(waiting.map { it.id }.toSet())
    }

    private fun manager(ctx: Context): NotificationManager? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return null
        nm.createNotificationChannel(NotificationChannel(PRICES, "Price changes", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Price changes and back in stock, for products you follow" })
        nm.createNotificationChannel(NotificationChannel(ATTENTION, "Needs attention", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A followed price that couldn't be found or looks wrong" })
        return nm
    }

    /** Tapping opens Library › Following (on the product, when there's one). The widget's rows and heading open the same. */
    fun open(ctx: Context, followId: String?) = PendingIntent.getActivity(ctx, followId?.hashCode() ?: 0,
        Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_FOLLOWING).putExtra(MainActivity.EXTRA_FOLLOW, followId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

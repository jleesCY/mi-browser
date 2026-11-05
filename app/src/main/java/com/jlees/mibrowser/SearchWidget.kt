// Home-screen search widget: a pill-shaped search bar (opens mi on a new tab, ready to type) with incognito and QR buttons.
package com.jlees.mibrowser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        fun open(action: String, code: Int) = PendingIntent.getActivity(
            ctx, code, Intent(ctx, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(ctx.packageName, R.layout.widget_search).apply {
            setOnClickPendingIntent(R.id.widget_search, open(MainActivity.ACTION_SEARCH, 1))
            setOnClickPendingIntent(R.id.widget_incognito, open(MainActivity.ACTION_NEW_INCOGNITO, 2))
            setOnClickPendingIntent(R.id.widget_qr, open(MainActivity.ACTION_SCAN_QR, 3))
        }
        manager.updateAppWidget(ids, views)
    }
}

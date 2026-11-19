// Home-screen Following widget: all your followed products in a scrolling list, those that changed since you last
// looked first (with a dot). Tapping one opens its panel in Library › Following; the heading opens the list, and its refresh button checks every price now. It redraws
// whenever the followed products change (FollowStore.get), in the app or in a background check.
package com.jlees.mibrowser.follow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jlees.mibrowser.MainActivity
import com.jlees.mibrowser.R
import com.jlees.mibrowser.logic.displayHost
import kotlin.concurrent.thread
import kotlin.math.abs

class FollowWidget : AppWidgetProvider() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) FollowJob.checkAllNow(ctx) else super.onReceive(ctx, intent)
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync() // reading the saved products is file work: off the main thread, keeping the receiver alive till it's done
        thread { try { update(ctx) } finally { pending.finish() } }
    }

    companion object {
        private const val ACTION_REFRESH = "REFRESH"

        /** The widget manager and the Following widgets on the home screen; null if there are none. */
        private fun placed(ctx: Context) = AppWidgetManager.getInstance(ctx).let { m ->
            m.getAppWidgetIds(ComponentName(ctx, FollowWidget::class.java)).takeIf { it.isNotEmpty() }?.let { m to it }
        }

        /** While checks you asked for run: "Checking 3 of 12" and a spinner, in place of "Following" and the refresh button. */
        private fun header(views: RemoteViews, anyFollowed: Boolean) = with(views) {
            val p = FollowJob.progress
            setTextViewText(R.id.widget_following_title, heading(p))
            setViewVisibility(R.id.widget_following_refresh, if (p != null || !anyFollowed) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_following_progress, if (p != null) View.VISIBLE else View.GONE)
        }

        /** A check's progress moved on: only the heading is redrawn (the rows redraw as the prices they found are saved). Off the main thread. */
        @Synchronized fun showProgress(ctx: Context) {
            val (manager, ids) = placed(ctx) ?: return
            manager.partiallyUpdateAppWidget(ids, RemoteViews(ctx.packageName, R.layout.widget_following).also { header(it, FollowStore.get(ctx).follows.isNotEmpty()) })
        }

        /** Redraws every Following widget on the home screen. Off the main thread. One at a time: the last to run shows the latest (a check's end isn't overtaken by its start). */
        @Suppress("DEPRECATION") // setRemoteAdapter(Intent): its replacement (a list of rows given at once) is Android 12+ only, and can't scroll through many pictures
        @Synchronized fun update(ctx: Context) {
            val (manager, ids) = placed(ctx) ?: return
            val store = FollowStore.get(ctx).also { it.load() }
            val views = RemoteViews(ctx.packageName, R.layout.widget_following).apply {
                setOnClickPendingIntent(R.id.widget_following_header, FollowNotifier.open(ctx, null))
                // refresh: checks every price now
                setOnClickPendingIntent(R.id.widget_following_refresh, PendingIntent.getBroadcast(ctx, 0,
                    Intent(ctx, FollowWidget::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE))
                header(this, store.follows.isNotEmpty())
                // the rows come from FollowWidgetService; tapping one opens its product (the row adds which: FollowWidgetService)
                setRemoteAdapter(R.id.widget_following_list, Intent(ctx, FollowWidgetService::class.java))
                setEmptyView(R.id.widget_following_list, R.id.widget_following_empty)
                setPendingIntentTemplate(R.id.widget_following_list, PendingIntent.getActivity(ctx, 1,
                    Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_FOLLOWING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) // mutable: each row fills in its product
            }
            manager.updateAppWidget(ids, views)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_following_list) // the rows read again
        }
    }
}

/** The widget's rows, as its list scrolls to them: every followed product, those that changed since you last looked first. */
class FollowWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Rows(applicationContext)

    private class Rows(private val ctx: Context) : RemoteViewsFactory {
        private var rows = listOf<Pair<Follow, ProductLine>>()

        // asked again whenever the widget redraws (FollowWidget.update); on a background thread, so the files can be read here
        override fun onDataSetChanged() {
            val store = FollowStore.get(ctx).also { it.load() }
            rows = pinnedFirst(store.follows, store.updates).map { it to productLine(it, store.updates) }
        }

        override fun getViewAt(position: Int): RemoteViews {
            val (f, line) = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_following_row)
            return RemoteViews(ctx.packageName, R.layout.widget_following_row).apply {
                // its picture, saved at up to 480px a side: a quarter of that is plenty here
                val picture = f.image?.let { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                if (picture != null) setImageViewBitmap(R.id.widget_row_image, picture)
                else { setImageViewResource(R.id.widget_row_image, R.drawable.sym_sell); setInt(R.id.widget_row_image, "setColorFilter", ctx.getColor(R.color.widget_subtext)) }
                setViewVisibility(R.id.widget_row_dot, if (line.changed) View.VISIBLE else View.GONE)
                setTextViewText(R.id.widget_row_title, line.title)
                setTextViewText(R.id.widget_row_detail, line.detail)
                setTextColor(R.id.widget_row_detail, ctx.getColor(when (line.tone) { Tone.PLAIN -> R.color.widget_subtext; Tone.GOOD -> R.color.widget_accent; Tone.BAD -> R.color.widget_error }))
                setTextViewText(R.id.widget_row_price, line.price)
                // one line here: how much off it is, else how it moved since you followed it
                (line.off?.let { it to Tone.GOOD } ?: line.since)?.let { (text, tone) ->
                    setViewVisibility(R.id.widget_row_move, View.VISIBLE)
                    setTextViewText(R.id.widget_row_move, text)
                    setTextColor(R.id.widget_row_move, ctx.getColor(if (tone == Tone.BAD) R.color.widget_error else R.color.widget_accent))
                }
                setOnClickFillInIntent(R.id.widget_row, Intent().putExtra(MainActivity.EXTRA_FOLLOW, f.id))
            }
        }

        override fun getCount() = rows.size
        override fun getItemId(position: Int) = rows.getOrNull(position)?.first?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun onCreate() {}
        override fun onDestroy() {}
    }
}

/** The widget's heading: "Following", or how the checks you asked for are going. */
fun heading(p: Progress?) = when {
    p == null -> "Following"
    p.total == 0 -> "Checking…"
    else -> "Checking ${minOf(p.done + 1, p.total)} of ${p.total}"
}

/** How a line of the widget reads: plain, good news (a drop, in stock: the accent colour) or bad (out of stock, needs you: red). */
enum class Tone { PLAIN, GOOD, BAD }

/**
 * A followed product as Library › Following and the widget show it: its name; its latest unseen change ([changed], with
 * a dot), else what needs you, else its stock and site; its price; how much [off] it is (a sale), and how it moved
 * [since] you followed it.
 */
data class ProductLine(val title: String, val changed: Boolean, val detail: String, val tone: Tone, val price: String, val off: String?, val since: Pair<String, Tone>?)

fun productLine(f: Follow, updates: List<FollowUpdate>): ProductLine {
    val change = updates.filter { !it.read && it.followId == f.id }.maxByOrNull { it.at }
    val site = displayHost(f.url)
    val (detail, tone) = when {
        change != null -> change.text to if (change.kind in setOf(UpdateKind.OUT_OF_STOCK, UpdateKind.NEEDS_ATTENTION, UpdateKind.CONFIRM)) Tone.BAD else Tone.GOOD
        f.status == FollowStatus.NEEDS_ATTENTION -> "Needs attention: pick the price again" to Tone.BAD
        f.status == FollowStatus.CONFIRM -> "Check this price" to Tone.BAD
        f.inStock == false -> "Out of stock · $site" to Tone.BAD
        f.paused -> "Paused · $site" to Tone.PLAIN
        f.inStock == true -> "In stock · $site" to Tone.PLAIN
        else -> site to Tone.PLAIN
    }
    val price = f.current?.let { (if (f.startingPrice) "From " else "") + formatPrice(it, f.currency) } ?: "—"
    val moved = f.first?.let { first -> f.current?.let { (it - first) / first * 100 } }?.takeIf { abs(it) >= 0.5 }
    val since = moved?.let { (if (it < 0) "↓ " else "↑ ") + "${abs(it).toInt()}%" to if (it < 0) Tone.GOOD else Tone.BAD }
    return ProductLine(f.title.ifBlank { site }, change != null, detail, tone, price, percentOff(f.was, f.current)?.let { "$it% off" }, since)
}

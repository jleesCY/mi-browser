// Checking followed prices in the background: an Android job about once an hour (only with a network and the battery
// not low) checks whichever products are due; also run by Check Now.
package com.jlees.mibrowser.follow

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Settings
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Settings › Following › Notifications, read from the saved settings (the check may run with mi closed, so without its ViewModel). */
private fun notificationsOn(ctx: Context) =
    (JsonStore(File(ctx.filesDir, "store")).read("settings", Settings.serializer()) ?: Settings()).followNotifications

/** [done] of [total] checks you asked for; total 0: waiting to start. */
data class Progress(val done: Int, val total: Int)

class FollowJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            // the widget's refresh: every product (but paused ones) not checked since you tapped it (so if Android stops it
            // early, its next run carries on where it left off); the hourly job: whichever are due
            val since = params.extras.getLong(SINCE)
            val all = if (params.jobId == NOW_ID) FollowStore.get(this@FollowJob).also { withContext(Dispatchers.IO) { it.load() } }.follows.filter { !it.paused && it.lastChecked < since }.map { it.id }.toSet() else null
            runChecks(this@FollowJob, all)
            jobFinished(params, false)
        }
        return true
    }

    /** Android ended the job early (it ran too long, or conditions changed): it runs again later. */
    // only this run is stopped: Android may hand the same service its next job
    override fun onStopJob(params: JobParameters): Boolean { scope.coroutineContext.cancelChildren(); return true }

    companion object {
        private const val JOB_ID = 0x70110
        private const val NOW_ID = 0x70111
        private const val SINCE = "since"
        private val running = Mutex()

        /**
         * The checks you asked for (the widget's refresh, Check Now): how many are done of how many ([Progress.total] 0:
         * asked for, waiting for a network). Null when none are running: the hourly background check doesn't show. In
         * memory: a new process starts with none, as no check runs in it yet.
         */
        @Volatile var progress: Progress? = null
            private set(p) { field = p; appContext?.let { thread { FollowWidget.showProgress(it) } } } // redrawing reads files: off the main thread
        private var appContext: Context? = null

        /** The widget's refresh: checks everything now (as soon as there's a network, Wi-Fi Only or not, as Check All Now in the app). */
        fun checkAllNow(ctx: Context) {
            appContext = ctx.applicationContext
            if (progress == null) progress = Progress(0, 0)
            ctx.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(NOW_ID, ComponentName(ctx, FollowJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(PersistableBundle().apply { putLong(SINCE, System.currentTimeMillis()) })
                // started at once (Android 12+; before, an ordinary job, which Android may hold back a few minutes)
                .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(true) }
                .build())
        }

        /** Background checks are scheduled while anything is followed (and Settings › Following allows them), on any network or [wifiOnly]. */
        fun schedule(ctx: Context, on: Boolean, wifiOnly: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!on) { js.cancel(JOB_ID); return }
            val network = if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            @Suppress("DEPRECATION") if (js.getPendingJob(JOB_ID)?.networkType == network) return // already as asked (scheduling again would restart its hour)
            js.schedule(JobInfo.Builder(JOB_ID, ComponentName(ctx, FollowJob::class.java))
                .setPeriodic(60 * 60_000L, 20 * 60_000L) // about hourly; each product waits for its own interval (isDue)
                .setRequiredNetworkType(network)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true) // survives a restart
                .build())
        }

        /**
         * Checks the due products ([only]: just those, due or not: Check Now), one after another, spacing out checks to
         * the same shop. Each result updates the store, and its updates notify (FollowNotifier).
         */
        suspend fun runChecks(ctx: Context, only: Set<String>? = null) = running.withLock {
            appContext = ctx.applicationContext
            val shown = only != null
            try { checkAll(ctx, only, shown) } finally { if (shown) progress = null }
        }

        private suspend fun checkAll(ctx: Context, only: Set<String>?, shown: Boolean) {
            val store = FollowStore.get(ctx)
            withContext(Dispatchers.IO) { store.load() }
            val notify = withContext(Dispatchers.IO) { notificationsOn(ctx) }
            val checker = PriceChecker(ctx)
            val now = System.currentTimeMillis()
            val due = store.follows.filter { if (only != null) it.id in only else isDue(it, now) }
            if (shown) progress = Progress(0, due.size)
            try {
                // shops side by side, each shop's products one after another, a few seconds apart (politely); hidden page
                // loads still take turns (PriceChecker.render), but quick reads of other shops go on meanwhile
                coroutineScope { for (shop in due.groupBy { Uri.parse(it.url).host }.values) launch { shop.forEachIndexed { i, f ->
                    if (i > 0) delay(3000)
                    val c = checker.check(f)
                    if (shown) progress = progress?.let { it.copy(done = it.done + 1) }
                    if (store[f.id] == null) return@forEachIndexed // unfollowed while it was being checked
                    val at = System.currentTimeMillis()
                    store.update(f.id) { copy(needsRender = needsRender || (c.rendered && c.reading != null && extraction.from == ReadFrom.PRODUCT_DATA), extraction = c.extraction) }
                    // a check you asked for (Check Now) tells you at once; the background one tries a few times first
                    val updates = if (c.reading != null) store.record(f.id, c.reading, at, guessed = c.guessed) else store.failed(f.id, at, c.problem, atOnce = only != null)
                    // a product without a picture yet (followed by picking the price) gets one when a check finds it
                    val image = c.reading?.image
                    if (f.image == null && image != null) withContext(Dispatchers.IO) { FollowImages.save(ctx, f.id, image, f.url) }?.let { p -> store.update(f.id) { copy(image = p) } }
                    if (notify) FollowNotifier.notify(ctx, store, updates)
                } } }
                if (notify) FollowNotifier.digest(ctx, store)
            } finally {
                // saved now: with mi closed there's no screen whose frames would set off the store's own saving
                withContext(Dispatchers.IO + NonCancellable) { store.saveNow() }
            }
        }
    }
}

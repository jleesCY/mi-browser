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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Settings › Following › Notifications, read from the saved settings (the check may run with mi closed, so without its ViewModel). */
private fun notificationsOn(ctx: Context) =
    (com.jlees.mibrowser.data.JsonStore(java.io.File(ctx.filesDir, "store")).read("settings", com.jlees.mibrowser.data.Settings.serializer()) ?: com.jlees.mibrowser.data.Settings()).followNotifications

class FollowJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch { runChecks(this@FollowJob); jobFinished(params, false) }
        return true
    }

    /** Android ended the job early (it ran too long, or conditions changed): it runs again later. */
    // only this run is stopped: Android may hand the same service its next job
    override fun onStopJob(params: JobParameters): Boolean { scope.coroutineContext.cancelChildren(); return true }

    companion object {
        private const val JOB_ID = 0x70110
        private val running = Mutex()

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
            val store = FollowStore.get(ctx)
            withContext(Dispatchers.IO) { store.load() }
            val notify = withContext(Dispatchers.IO) { notificationsOn(ctx) }
            val checker = PriceChecker(ctx)
            val now = System.currentTimeMillis()
            val due = store.follows.filter { if (only != null) it.id in only else isDue(it, now) }
            var lastHost: String? = null
            try {
                for (f in due) {
                    val host = Uri.parse(f.url).host
                    if (host == lastHost) delay(3000) // politely, a few seconds apart on the same site
                    lastHost = host
                    val c = checker.check(f)
                    if (store[f.id] == null) continue // unfollowed while it was being checked
                    val at = System.currentTimeMillis()
                    store.update(f.id) { copy(needsRender = needsRender || (c.rendered && c.reading != null && extraction.from == ReadFrom.PRODUCT_DATA), extraction = c.extraction) }
                    // a check you asked for (Check Now) tells you at once; the background one tries a few times first
                    val updates = if (c.reading != null) store.record(f.id, c.reading, at, guessed = c.guessed) else store.failed(f.id, at, c.problem, atOnce = only != null)
                    // a product without a picture yet (followed by picking the price) gets one when a check finds it
                    val image = c.reading?.image
                    if (f.image == null && image != null) withContext(Dispatchers.IO) { FollowImages.save(ctx, f.id, image, f.url) }?.let { p -> store.update(f.id) { copy(image = p) } }
                    if (notify) FollowNotifier.notify(ctx, store, updates)
                }
                if (notify) FollowNotifier.digest(ctx, store)
            } finally {
                // saved now: with mi closed there's no screen whose frames would set off the store's own saving
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { store.saveNow() }
            }
        }
    }
}

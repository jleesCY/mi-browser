// The products you follow, their price history, and the Updates list, saved between launches.
package com.jlees.mibrowser.follow

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.mibrowser.data.JsonStore
import com.jlees.mibrowser.data.Saver
import com.jlees.mibrowser.logic.uniqueId
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.MainScope
import kotlinx.serialization.builtins.ListSerializer

/**
 * One per process ([get]): the background check (FollowJob) runs with or without mi open, so the store is created by
 * whichever comes first and both use the same one. It saves itself (its own [savers] run from creation), and its state
 * changes on the main thread only (it's Compose state the Library reads).
 */
class FollowStore(private val store: JsonStore) {
    /** Newest first. */
    var follows by mutableStateOf(listOf<Follow>()); private set
    /** Newest first; capped at [MAX_UPDATES]. */
    var updates by mutableStateOf(listOf<FollowUpdate>()); private set
    private var loaded = false

    private val followsSer = ListSerializer(Follow.serializer())
    private val updatesSer = ListSerializer(FollowUpdate.serializer())

    /** Reads the saved lists, once (later calls do nothing). Off the main thread. */
    @Synchronized fun load() {
        if (loaded) return
        follows = store.read("follows", followsSer) ?: emptyList()
        updates = store.read("follow_updates", updatesSer) ?: emptyList()
        loaded = true
    }

    val savers = listOf(
        Saver(1000, { follows }) { store.write("follows", followsSer, it) },
        Saver(1000, { updates }) { store.write("follow_updates", updatesSer, it) },
    )
    fun saveNow() = savers.forEach { it.writeNow() }

    operator fun get(id: String) = follows.firstOrNull { it.id == id }
    fun following(url: String) = follows.firstOrNull { it.url == url }

    fun add(f: Follow) { follows = listOf(f) + follows.filterNot { it.url == f.url } }
    fun update(id: String, change: Follow.() -> Follow) { follows = follows.map { if (it.id == id) it.change() else it } }
    /** Library › Following rearranged by dragging: the products in the order of [ids] (any not in it after them). */
    fun reorder(ids: List<String>) { follows = ids.mapNotNull { get(it) } + follows.filterNot { it.id in ids } }
    fun remove(id: String) { follows = follows.filterNot { it.id == id }; updates = updates.filterNot { it.followId == id } }

    /**
     * A check of [id] found [r] at [now]: its history and status move on, and the updates it brings are added (and
     * returned, for notifications). A reading that looks wrong is held for you to confirm instead, unless it's [trusted]
     * (you just picked it).
     */
    fun record(id: String, r: Reading, now: Long, trusted: Boolean = false, guessed: Boolean = false): List<FollowUpdate> {
        val f = get(id) ?: return emptyList()
        // a price found only by guessing (nothing left where it was): any change waits for you to confirm it
        val guessedChange = guessed && f.current != null && r.amount != null && abs(r.amount - f.current!!) >= 0.005
        if (!trusted && (isSuspicious(f, r) || guessedChange)) {
            val p = PricePoint(now, r.amount, r.inStock)
            // already waiting for you on this same price: not asked again on every check
            if (f.status == FollowStatus.CONFIRM && f.pending?.amount == r.amount) { update(id) { copy(lastChecked = now) }; return emptyList() }
            val text = "Check this price: ${r.amount?.let { formatPrice(it, r.currency ?: f.currency) } ?: "?"}"
            update(id) { copy(lastChecked = now, status = FollowStatus.CONFIRM, pending = p) }
            return addUpdates(listOf(FollowUpdate(uniqueId(), id, now, UpdateKind.CONFIRM, text, alert = true)))
        }
        val (point, changes) = changesFor(f, r, now, ::uniqueId)
        update(id) { copy(lastChecked = now, status = FollowStatus.OK, problem = null, failures = 0, pending = null, was = wasFor(f, r), history = if (point != null) withPoint(history, point) else history, title = title.ifBlank { r.title.orEmpty() }) }
        return addUpdates(changes)
    }

    /**
     * A check of [id] couldn't read the price, for [problem]. You're told once it has failed [FAILS_BEFORE_ASKING] times
     * in a row ([now]: at once, for a check you asked for), and then once, not each check, unless the reason changes.
     */
    fun failed(id: String, now: Long, problem: Problem, atOnce: Boolean = false): List<FollowUpdate> {
        val f = get(id) ?: return emptyList()
        val failures = f.failures + 1
        if (!atOnce && failures < FAILS_BEFORE_ASKING && f.status != FollowStatus.NEEDS_ATTENTION) {
            update(id) { copy(lastChecked = now, failures = failures) } // it'll try again at the next check
            return emptyList()
        }
        update(id) { copy(lastChecked = now, failures = failures, status = FollowStatus.NEEDS_ATTENTION, problem = problem) }
        if (f.status == FollowStatus.NEEDS_ATTENTION && f.problem == problem) return emptyList()
        return addUpdates(listOf(FollowUpdate(uniqueId(), id, now, UpdateKind.NEEDS_ATTENTION, problem.text, alert = true)))
    }

    /** A backup's follows ([restored]) join yours: those you don't already follow (by address), pictures fetched again on their next check. Returns how many joined. */
    fun restore(restored: List<Follow>): Int {
        val new = restored.filter { r -> follows.none { it.url == r.url } }.map { it.copy(image = null, lastChecked = 0) } // its picture is fetched again, at its first check (due now)
        follows = follows + new
        return new.size
    }

    /** You confirmed the price that looked wrong: it counts (and the history moves on from it). */
    fun confirmPending(id: String) {
        val f = get(id) ?: return
        val p = f.pending ?: return
        update(id) { copy(status = FollowStatus.OK, pending = null, history = withPoint(history, p)) }
        markRead(id)
    }

    fun markRead(followId: String? = null) { updates = updates.map { if (followId == null || it.followId == followId) it.copy(read = true) else it } }
    fun markNotified(ids: Set<String>) { updates = updates.map { if (it.id in ids) it.copy(notified = true) else it } }

    private fun addUpdates(new: List<FollowUpdate>): List<FollowUpdate> {
        if (new.isNotEmpty()) updates = (new + updates).take(MAX_UPDATES)
        return new
    }

    /** Wipe All Data. */
    fun clear() { follows = emptyList(); updates = emptyList() }

    companion object {
        const val MAX_UPDATES = 300
        @Volatile private var instance: FollowStore? = null

        /** The process's store (not loaded yet: call [load] off the main thread). */
        fun get(ctx: Context): FollowStore = instance ?: synchronized(this) {
            instance ?: FollowStore(JsonStore(File(ctx.applicationContext.filesDir, "store"))).also { s ->
                instance = s
                val scope = MainScope()
                s.savers.forEach { it.launchIn(scope) }
                // the home-screen widget redraws with every change (a check, a follow, opening it to read): debounced as the saves are
                Saver(1000, { s.follows to s.updates }) { FollowWidget.update(ctx.applicationContext) }.launchIn(scope)
            }
        }
    }
}

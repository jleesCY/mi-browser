package com.jlees.mibrowser

import com.jlees.mibrowser.adblock.BlockedCounts
import com.jlees.mibrowser.adblock.blockingLabel
import com.jlees.mibrowser.tabs.REST_AFTER_MS
import com.jlees.mibrowser.tabs.tabsToRest
import com.jlees.mibrowser.tabs.warmInBackground
import org.junit.Assert.assertEquals
import org.junit.Test

/** Resting unused tabs, and counting what mi blocked on a page. */
class RestAndBlockedTest {
    @Test fun unusedTabsRest() {
        val now = 100 * 60_000L
        val seen = mapOf("a" to now - REST_AFTER_MS - 1, "b" to now - 60_000L, "c" to now - REST_AFTER_MS * 3, "media" to 0L)
        // a and c haven't been looked at in 10 minutes; b has; the media tab is kept whatever its age
        assertEquals(listOf("a", "c"), tabsToRest(listOf("a", "b", "c", "media"), seen, now, keep = setOf("media")))
        // a live tab with no time yet (just made) counts as seen now
        assertEquals(emptyList<String>(), tabsToRest(listOf("new"), emptyMap(), now, emptySet()))
    }

    @Test fun restedTabsAreNotLoadedAgainBehindYourBack() {
        // a rested tab used to be warmed again at once (resting dropped the live count under the cap)
        assertEquals(false, warmInBackground(live = false, hasLoadedOnce = true, resting = true, liveCount = 1, maxLive = 4))
        assertEquals(true, warmInBackground(live = false, hasLoadedOnce = true, resting = false, liveCount = 1, maxLive = 4))
        assertEquals(false, warmInBackground(live = false, hasLoadedOnce = true, resting = false, liveCount = 4, maxLive = 4)) // cap full
        assertEquals(true, warmInBackground(live = true, hasLoadedOnce = true, resting = false, liveCount = 4, maxLive = 4))  // live: kept in sync
    }

    @Test fun blockedPerPage() {
        BlockedCounts.pageStarted("t")
        repeat(3) { BlockedCounts.blocked("t") }
        assertEquals(3, BlockedCounts.on("t"))
        assertEquals("On · 3 blocked", blockingLabel(BlockedCounts.on("t")))
        BlockedCounts.pageStarted("t") // a new page counts again from zero
        assertEquals("On", blockingLabel(BlockedCounts.on("t")))
        BlockedCounts.forget("t")
        assertEquals(0, BlockedCounts.on("t"))
        assertEquals(0, BlockedCounts.on(null))
    }
}

// Which page sits where in the tab switcher: its left and right buttons and the Pill's left button. Each place has a
// swipe on the Pill (the left button's page comes in with a swipe right, from the left; the right button's with a swipe
// left; the Pill button's with a swipe up, from the bottom), so a page's button, swipe and the side it slides in from
// always agree. Chosen in Settings › Tabs; every page has exactly one place.
package com.jlees.mibrowser.tabs

import com.jlees.mibrowser.Sheet
import com.jlees.mibrowser.data.Settings
import com.jlees.mibrowser.data.SwitcherPanel

data class SwitcherLayout(val left: SwitcherPanel, val right: SwitcherPanel, val up: SwitcherPanel) {
    /** The page a swipe on the switcher's Pill opens. */
    fun at(swipe: PillSwipe) = when (swipe) { PillSwipe.RIGHT -> left; PillSwipe.LEFT -> right; PillSwipe.UP -> up }

    /** The swipe (and so the button and side) of [panel]. */
    fun swipeFor(panel: SwitcherPanel) = PillSwipe.entries.first { at(it) == panel }

    /** [panel] moves to [swipe]'s place, and the page that was there takes [panel]'s old place. */
    fun with(swipe: PillSwipe, panel: SwitcherPanel): SwitcherLayout {
        val from = swipeFor(panel)
        val displaced = at(swipe)
        fun pick(s: PillSwipe) = when (s) { swipe -> panel; from -> displaced; else -> at(s) }
        return SwitcherLayout(pick(PillSwipe.RIGHT), pick(PillSwipe.LEFT), pick(PillSwipe.UP))
    }

    companion object {
        val DEFAULT = SwitcherLayout(SwitcherPanel.LIBRARY, SwitcherPanel.SETTINGS, SwitcherPanel.HISTORY)
    }
}

/** The layout in [Settings] (the default if the saved one doesn't have each page once). */
val Settings.switcherLayout get() =
    SwitcherLayout(switcherLeft, switcherRight, switcherUp).takeIf { setOf(it.left, it.right, it.up).size == 3 } ?: SwitcherLayout.DEFAULT

fun Settings.withSwitcherLayout(l: SwitcherLayout) = copy(switcherLeft = l.left, switcherRight = l.right, switcherUp = l.up)

/** The overlay page a panel opens. */
val SwitcherPanel.sheet get() = when (this) { SwitcherPanel.LIBRARY -> Sheet.BOOKMARKS; SwitcherPanel.SETTINGS -> Sheet.SETTINGS; SwitcherPanel.HISTORY -> Sheet.HISTORY }

/** The panel an overlay page belongs to (null: not one of them, e.g. Site settings). */
fun panelOf(view: Sheet): SwitcherPanel? = when (view) {
    Sheet.BOOKMARKS, Sheet.DOWNLOADS -> SwitcherPanel.LIBRARY
    Sheet.HISTORY, Sheet.RECENTLY_CLOSED -> SwitcherPanel.HISTORY
    Sheet.SETTINGS -> SwitcherPanel.SETTINGS
    else -> null
}

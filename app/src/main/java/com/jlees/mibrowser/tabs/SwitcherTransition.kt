// The zoom between a page and its card in the tab switcher: where both ends are on screen and how far along it is.
package com.jlees.mibrowser.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

class SwitcherTransition {
    /** 0 = browsing, 1 = in the switcher. Driven by the pill drag and by the switcher's open/close animations. */
    val anim = Animatable(0f)
    private var shown by mutableStateOf(false)
    /**
     * The switcher is up. Hiding it, by any path (opening a bookmark from a sheet over it, a tab menu tool, the
     * walkthrough…), also ends [settled] and the flying tab: left on, the Pill's swipes stay off (off while settled).
     */
    var visible: Boolean
        get() = shown
        set(v) { shown = v; if (!v) { settled = false; flyingTabId = null } }
    /** True once fully in the switcher: the switcher sits above the page. While false, the live page is drawn
     *  on top, scaled between full size and its card, like a system app switcher. */
    var settled by mutableStateOf(false)
    /** Closing: this tab's snapshot expands over the page (its WebView may not have drawn yet). */
    var flyingTabId by mutableStateOf<String?>(null)
    /** Page area in the window (content below the status bar) and the active tab's card: the zoom's two ends. */
    var pageBounds by mutableStateOf<Rect?>(null)
    var target by mutableStateOf<Rect?>(null)
    /** Where the switcher's centre pill sits in the window: the search pill morphs into it while the switcher opens. */
    var barPill by mutableStateOf<Rect?>(null)
}

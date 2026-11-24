package com.jlees.mibrowser.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * mi's WebView. Incognito: page text fields ask the keyboard app not to learn from what's typed (like Chrome's).
 *
 * Pull to refresh, only from the very top of the page:
 *  - the touch must start with the page scrolled to the top ([getScrollY] == 0), so reaching the top mid-gesture
 *    never counts;
 *  - Chromium must report the pull as over-scroll at the top ([onOverScrolled] clampedY at 0), i.e. nothing on the
 *    page (including an inner scrolling panel) can scroll further up;
 *  - and it must do so within the first [ARM_WINDOW_DP] of downward travel: a panel scrolled partway down scrolls
 *    first, so its over-scroll arrives too late and the pull never arms.
 * Released past [PULL_DP] of travel after arming (half-speed, with resistance) reloads.
 */
@SuppressLint("ViewConstructor")
class MiWebView(
    ctx: Context, private val incognito: Boolean, private val pullEnabled: () -> Boolean,
    private val onPull: (Float) -> Unit, private val onRefresh: () -> Unit,
    /** A tick when the pull passes the point where releasing reloads (and back), if haptics are on. */
    private val onThreshold: () -> Unit = {},
) : WebView(ctx) {
    private val density = ctx.resources.displayMetrics.density
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var startedAtTop = false
    private var armedAt = Float.NaN
    private var progress = 0f

    /** Extra items for the selected-text menu (extensions' contextMenus): titles and what tapping each does. */
    var selectionItems: () -> List<Pair<String, () -> Unit>> = { emptyList() }

    // the selected-text menu WebView opens, with the extra items after its own
    override fun startActionMode(callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
        val extra = selectionItems()
        if (extra.isEmpty()) return super.startActionMode(callback, type)
        val base = EXTRA_ITEM_BASE
        val wrapped = object : android.view.ActionMode.Callback2() {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                val ok = callback.onCreateActionMode(mode, menu)
                extra.forEachIndexed { i, (title, _) -> menu.add(android.view.Menu.NONE, base + i, 1000 + i, title) }
                return ok
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = callback.onPrepareActionMode(mode, menu)
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                val i = item.itemId - base
                if (i in extra.indices) { extra[i].second(); mode.finish(); return true }
                return callback.onActionItemClicked(mode, item)
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode) = callback.onDestroyActionMode(mode)
            override fun onGetContentRect(mode: android.view.ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
                if (callback is android.view.ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
            }
        }
        return super.startActionMode(wrapped, type)
    }

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? =
        super.onCreateInputConnection(outAttrs).also { if (incognito) outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; armedAt = Float.NaN
                startedAtTop = scrollY == 0 && pullEnabled()
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> disarm() // pinch zoom
            android.view.MotionEvent.ACTION_MOVE -> {
                lastX = e.x; lastY = e.y
                if (!armedAt.isNaN()) {
                    val was = progress >= 1f
                    progress = ((e.y - armedAt) * 0.5f / (PULL_DP * density)).coerceIn(0f, 1.4f)
                    if ((progress >= 1f) != was) onThreshold()
                    onPull(progress)
                }
            }
            android.view.MotionEvent.ACTION_UP -> { val go = !armedAt.isNaN() && progress >= 1f; disarm(); if (go) onRefresh() }
            android.view.MotionEvent.ACTION_CANCEL -> disarm()
        }
        return super.onTouchEvent(e)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (!clampedY || scrollY != 0 || !startedAtTop || !armedAt.isNaN()) return
        val dy = lastY - downY
        val dx = kotlin.math.abs(lastX - downX)
        // downward, mostly vertical, and right at the start of the pull
        if (dy > 0 && dy > dx && dy <= slop + ARM_WINDOW_DP * density) armedAt = lastY
        else startedAtTop = false // the page scrolled first: this gesture never refreshes
    }

    private fun disarm() {
        startedAtTop = false
        if (!armedAt.isNaN() || progress > 0f) { armedAt = Float.NaN; progress = 0f; onPull(0f) }
    }

    private companion object {
        const val ARM_WINDOW_DP = 24f
        const val PULL_DP = 72f
        const val EXTRA_ITEM_BASE = 0x6d6900
    }
}

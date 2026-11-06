// Haptic feedback at a gesture's decision points (a swipe passing the point where it acts, a long press opening
// something). Off when the user turns it off (Settings → Appearance → Haptic Feedback); Android's own touch-feedback
// setting applies too, since these are the system's feedback effects.
package com.jlees.mibrowser.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

class Haptics(private val view: View, private val enabled: () -> Boolean) {
    /** A drag crossed a threshold: the gesture will now act (or, going back, no longer will). */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** A long press opened a menu or picked something up to drag. */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(effect: Int) { if (enabled()) view.performHapticFeedback(effect) }
}

/** Provided by App; null in previews. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

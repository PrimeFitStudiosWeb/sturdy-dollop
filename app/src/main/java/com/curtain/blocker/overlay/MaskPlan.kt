package com.curtain.blocker.overlay

import android.graphics.Rect
import com.curtain.blocker.detect.Screen

/**
 * What should be on screen right now. Value semantics matter: the service
 * recomputes this several times a second and only touches the WindowManager
 * when the plan actually changes.
 */
data class MaskPlan(
    val blackout: List<Rect> = emptyList(),
    /** Region in which vertical swipes are swallowed but taps are replayed. */
    val swipeLock: Rect? = null,
    val reason: String = "",
    /**
     * Whether tapping the blackout backs out of the screen. Set for full-screen
     * masks so the user is never stuck staring at an opaque rectangle with no
     * visible control.
     */
    val tapToDismiss: Boolean = false
) {
    val isEmpty: Boolean get() = blackout.isEmpty() && swipeLock == null

    companion object {
        val NONE = MaskPlan()
    }
}

data class ScreenMetrics(
    val width: Int,
    val height: Int,
    val statusBar: Int,
    val navBar: Int,
    val density: Float
) {
    fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}

/** Immutable snapshot of what the service knows when it builds a plan. */
data class Situation(
    val screen: Screen,
    val reelAllowed: Boolean,
    val scrollTripped: Boolean,
    val shortsShelves: List<Rect>
)

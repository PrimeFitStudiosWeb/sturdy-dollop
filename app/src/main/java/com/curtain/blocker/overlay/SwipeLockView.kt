package com.curtain.blocker.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Sits over a single allowed reel. It eats every gesture that would advance to
 * the next clip, and replays anything that looked like a tap so the like /
 * comment / profile / back controls underneath still work.
 */
class SwipeLockView(
    context: Context,
    private val onTap: (x: Float, y: Float) -> Unit
) : View(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout() + 120L

    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var movedBeyondSlop = false
    private var multiTouch = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = System.currentTimeMillis()
                movedBeyondSlop = false
                multiTouch = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    movedBeyondSlop = true
                }
            }

            MotionEvent.ACTION_UP -> {
                val quick = System.currentTimeMillis() - downTime <= tapTimeout
                if (!movedBeyondSlop && !multiTouch && quick) {
                    onTap(downRawX, downRawY)
                }
            }
        }
        // Everything is consumed. Drags never reach Instagram, so the pager
        // never advances.
        return true
    }
}

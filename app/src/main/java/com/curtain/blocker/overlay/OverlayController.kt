package com.curtain.blocker.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Owns every window Curtain puts on screen.
 *
 * Each blackout rectangle is its own window rather than one full-screen view
 * with holes in it: a window only receives touches inside its own bounds, so
 * the uncovered strips (Instagram's top-right icons, the tab bar) stay fully
 * interactive without any private-API region tricks.
 */
class OverlayController(
    private val context: Context,
    private val onTapThrough: (x: Float, y: Float) -> Unit,
    private val onMaskTapped: () -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private val maskViews = ArrayList<View>()
    private var swipeView: View? = null
    private var current: MaskPlan = MaskPlan.NONE
    private var suspended = false

    fun metrics(): ScreenMetrics {
        val wmMetrics = wm.currentWindowMetrics
        val bounds = wmMetrics.bounds
        val insets = wmMetrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars()
        )
        return ScreenMetrics(
            width = bounds.width(),
            height = bounds.height(),
            statusBar = insets.top,
            navBar = insets.bottom,
            density = context.resources.displayMetrics.density
        )
    }

    fun apply(plan: MaskPlan) {
        if (plan == current) return
        val reusable = canReposition(current, plan)
        current = plan
        if (reusable) reposition(plan) else rebuild(plan)
    }

    fun clear() = apply(MaskPlan.NONE)

    /** True while anything at all is on screen. Drives the service watchdog. */
    val isShowing: Boolean get() = maskViews.isNotEmpty() || swipeView != null

    /**
     * Temporarily make the swipe lock non-touchable so a replayed tap reaches
     * the app underneath instead of bouncing back into us.
     */
    fun suspendSwipeLock(millis: Long, then: () -> Unit) {
        val view = swipeView ?: return
        if (suspended) return
        suspended = true
        updateParams(view) { it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
        main.postDelayed({ then() }, TAP_HANDOFF_DELAY_MS)
        main.postDelayed({
            suspended = false
            swipeView?.let { v ->
                updateParams(v) {
                    it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            }
        }, millis)
    }

    fun destroy() {
        main.removeCallbacksAndMessages(null)
        rebuild(MaskPlan.NONE)
        current = MaskPlan.NONE
    }

    // -----------------------------------------------------------------------

    /**
     * YouTube's Shorts shelves move under the finger as the feed scrolls. Tearing
     * the windows down and re-adding them several times a second flickers, so
     * when only the geometry changed the existing windows are moved instead.
     */
    private fun canReposition(old: MaskPlan, new: MaskPlan): Boolean =
        maskViews.isNotEmpty() &&
            maskViews.size == valid(new.blackout).size &&
            old.reason == new.reason &&
            old.tapToDismiss == new.tapToDismiss &&
            (old.swipeLock == null) == (new.swipeLock == null)

    private fun reposition(plan: MaskPlan) {
        valid(plan.blackout).forEachIndexed { index, rect ->
            updateParams(maskViews[index]) { it.moveTo(rect) }
        }
        val lock = plan.swipeLock
        if (lock != null) swipeView?.let { view -> updateParams(view) { it.moveTo(lock) } }
    }

    private fun valid(rects: List<Rect>): List<Rect> =
        rects.filter { it.width() > 0 && it.height() > 0 }

    private fun rebuild(plan: MaskPlan) {
        maskViews.forEach { safeRemove(it) }
        maskViews.clear()
        swipeView?.let { safeRemove(it) }
        swipeView = null
        suspended = false

        // A degenerate rect would make addView throw, so they never get here.
        val rects = valid(plan.blackout)
        val primary = rects.maxByOrNull { it.height().toLong() * it.width() }
        rects.forEach { rect ->
            val view =
                if (rect === primary) labelView(plan.reason, plan.tapToDismiss) else plainView()
            if (plan.tapToDismiss) view.setOnClickListener { onMaskTapped() }
            if (safeAdd(view, params(rect))) maskViews.add(view)
        }

        plan.swipeLock?.takeIf { it.width() > 0 && it.height() > 0 }?.let { rect ->
            val view = SwipeLockView(context, onTapThrough)
            if (safeAdd(view, params(rect))) swipeView = view
        }
    }

    private fun plainView(): View = View(context).apply { setBackgroundColor(Color.BLACK) }

    private fun labelView(reason: String, tapToDismiss: Boolean): View = TextView(context).apply {
        setBackgroundColor(Color.BLACK)
        text = buildString {
            append("Curtain")
            if (reason.isNotEmpty()) append("\n").append(reason)
            if (tapToDismiss) append("\n\nTap to go back")
        }
        setTextColor(Color.parseColor("#2A2A30"))
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    private fun params(rect: Rect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            moveTo(rect)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

    private fun WindowManager.LayoutParams.moveTo(rect: Rect) {
        x = rect.left
        y = rect.top
        width = rect.width()
        height = rect.height()
    }

    private inline fun updateParams(view: View, block: (WindowManager.LayoutParams) -> Unit) {
        val p = view.layoutParams as? WindowManager.LayoutParams ?: return
        block(p)
        runCatching { wm.updateViewLayout(view, p) }
    }

    private fun safeAdd(view: View, p: WindowManager.LayoutParams): Boolean =
        runCatching { wm.addView(view, p) }.isSuccess

    private fun safeRemove(view: View) {
        runCatching { wm.removeViewImmediate(view) }
    }

    private companion object {
        /** Give the window manager a frame to apply NOT_TOUCHABLE before injecting. */
        const val TAP_HANDOFF_DELAY_MS = 40L
    }
}

package com.curtain.blocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.curtain.blocker.data.Settings
import com.curtain.blocker.detect.Classifier
import com.curtain.blocker.detect.NodeScan
import com.curtain.blocker.detect.Screen
import com.curtain.blocker.detect.Signatures
import com.curtain.blocker.overlay.MaskPlan
import com.curtain.blocker.overlay.MaskPlanner
import com.curtain.blocker.overlay.OverlayController
import com.curtain.blocker.overlay.Situation

class CurtainAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: Settings
    private lateinit var overlay: OverlayController
    private val handler = Handler(Looper.getMainLooper())

    /** Where the user has been, most recent first. Used to judge reel provenance. */
    private val recent = ArrayDeque<Screen>()
    private var lastScreen = Screen.UNKNOWN
    private var viewer: ViewerSession? = null

    private var evaluateQueued = false
    private var previewUntil = 0L
    private var watchdogRunning = false

    /**
     * Accessibility events are not guaranteed to arrive when the foreground app
     * changes (task switches and gesture-nav dismissals in particular). While
     * anything is drawn we re-check the active window directly, so a mask can
     * never outlive the app it belongs to.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            watchdogRunning = false
            val previewing = System.currentTimeMillis() < previewUntil
            val pkg = rootInActiveWindow?.packageName?.toString()
            val inScope = pkg == Signatures.PKG_INSTAGRAM || pkg == Signatures.PKG_YOUTUBE
            if (!inScope && !previewing) {
                resetNavigation()
                overlay.clear()
                return
            }
            startWatchdog()
        }
    }

    private class ViewerSession(val allowed: Boolean) {
        var scrollTripped = false
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post { overlay.clear() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings(this)
        overlay = OverlayController(this, ::replayTap, ::goBack)
        settings.registerListener(this)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Notifications.ensureChannel(this)
        Notifications.postStatus(this, settings)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (pkg != Signatures.PKG_INSTAGRAM && pkg != Signatures.PKG_YOUTUBE) {
            // Left the apps we care about. Drop everything immediately —
            // a stale black rectangle over the launcher is the worst failure
            // mode this app has.
            resetNavigation()
            if (System.currentTimeMillis() >= previewUntil) overlay.clear()
            return
        }

        if (!settings.active) {
            overlay.clear()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) noteScroll(event)
        queueEvaluate(urgent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        runCatching { settings.unregisterListener(this) }
        runCatching { unregisterReceiver(screenOffReceiver) }
        handler.removeCallbacksAndMessages(null)
        if (this::overlay.isInitialized) overlay.destroy()
        Notifications.cancel(this)
        if (instance === this) instance = null
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) =
        onSettingsChanged()

    fun onSettingsChanged() {
        handler.post {
            Notifications.postStatus(this, settings)
            if (!settings.active) overlay.clear() else queueEvaluate(urgent = true)
        }
    }

    // -- evaluation ---------------------------------------------------------

    private val evaluateTask = Runnable {
        evaluateQueued = false
        evaluate()
    }

    /**
     * Navigating to a new screen has to be caught before the user can read it;
     * a feed re-rendering under an existing mask does not. Coalescing content
     * churn at a slower rate keeps the tree walk off the hot path.
     */
    private fun queueEvaluate(urgent: Boolean) {
        if (evaluateQueued) {
            if (!urgent) return
            handler.removeCallbacks(evaluateTask)
        }
        evaluateQueued = true
        handler.postDelayed(evaluateTask, if (urgent) URGENT_DELAY_MS else SETTLE_DELAY_MS)
    }

    private fun evaluate() {
        if (System.currentTimeMillis() < previewUntil) return
        if (!settings.active) {
            overlay.clear()
            return
        }

        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (pkg != Signatures.PKG_INSTAGRAM && pkg != Signatures.PKG_YOUTUBE) {
            overlay.clear()
            return
        }

        val metrics = overlay.metrics()
        val scan = NodeScan.of(root, metrics.width) ?: return
        val screen = Classifier.classify(scan)
        trackNavigation(screen)

        val session = viewer
        val situation = Situation(
            screen = screen,
            reelAllowed = session?.allowed == true,
            scrollTripped = session?.scrollTripped == true,
            shortsShelves = scan.shortsShelves
        )
        overlay.apply(MaskPlanner.plan(situation, metrics, settings))
        if (overlay.isShowing) startWatchdog()
    }

    private fun startWatchdog() {
        if (watchdogRunning) return
        if (!overlay.isShowing && System.currentTimeMillis() >= previewUntil) return
        watchdogRunning = true
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    /**
     * A reel is "allowed" when the user arrived at it from a conversation
     * rather than from a feed. The decision is latched for the whole time the
     * player is open, so a mid-session re-scan can't flip it.
     */
    private fun trackNavigation(screen: Screen) {
        if (screen == Screen.IG_REEL_VIEWER) {
            if (viewer == null) viewer = ViewerSession(allowed = arrivedFromAllowedPlace())
        } else {
            viewer = null
            val worthRemembering = screen != Screen.UNKNOWN &&
                screen != Screen.IG_OTHER &&
                screen != Screen.YT_OTHER
            if (worthRemembering && screen != lastScreen) {
                recent.addFirst(screen)
                while (recent.size > HISTORY_DEPTH) recent.removeLast()
            }
        }
        lastScreen = screen
    }

    private fun arrivedFromAllowedPlace(): Boolean {
        val provenance = recent.take(3)
        val fromDm = settings.allowReelsFromDm &&
            provenance.any { it == Screen.IG_DM_THREAD || it == Screen.IG_DM_INBOX }
        val fromProfile = settings.allowReelsFromProfile &&
            provenance.any { it == Screen.IG_PROFILE }
        return fromDm || fromProfile
    }

    private fun resetNavigation() {
        viewer = null
        lastScreen = Screen.UNKNOWN
        recent.clear()
    }

    /**
     * Backstop for the swipe lock: if a scroll gesture ever does reach the
     * clips pager, the shared reel is over and the screen goes black.
     */
    private fun noteScroll(event: AccessibilityEvent) {
        val session = viewer ?: return
        if (!session.allowed || session.scrollTripped) return
        if (!settings.blockScrollInAllowedReel) return
        val sourceId = event.source?.viewIdResourceName?.substringAfter("id/") ?: return
        if (Signatures.IG_CLIPS_VIEWER.any { sourceId.contains(it) }) {
            session.scrollTripped = true
            queueEvaluate(urgent = true)
        }
    }

    // -- tap replay ---------------------------------------------------------

    private fun replayTap(x: Float, y: Float) {
        overlay.suspendSwipeLock(TAP_REPLAY_WINDOW_MS) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            runCatching {
                dispatchGesture(
                    GestureDescription.Builder().addStroke(stroke).build(),
                    null,
                    null
                )
            }
        }
    }

    private fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // -- preview ------------------------------------------------------------

    /** Shows the Instagram home-feed mask for a few seconds so it can be tuned. */
    fun previewFeedMask() {
        handler.post {
            val m = overlay.metrics()
            val top = m.statusBar + m.dp(settings.topBarDp)
            val bottom = m.height - (m.dp(settings.bottomBarDp) + m.navBar)
            overlay.apply(
                MaskPlan(
                    blackout = listOf(
                        Rect(0, 0, m.width - m.dp(settings.topRightDp), top),
                        Rect(0, top, m.width, bottom)
                    ),
                    reason = "preview — tap through is disabled"
                )
            )
            previewUntil = System.currentTimeMillis() + PREVIEW_MS
            handler.postDelayed({
                previewUntil = 0L
                overlay.clear()
            }, PREVIEW_MS)
        }
    }

    companion object {
        private const val URGENT_DELAY_MS = 60L
        private const val SETTLE_DELAY_MS = 220L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val TAP_REPLAY_WINDOW_MS = 300L
        private const val PREVIEW_MS = 4000L
        private const val HISTORY_DEPTH = 6

        @Volatile
        var instance: CurtainAccessibilityService? = null
            private set
    }
}

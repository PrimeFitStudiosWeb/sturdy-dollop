package com.curtain.blocker.overlay

import android.graphics.Rect
import com.curtain.blocker.data.Settings
import com.curtain.blocker.detect.Screen

object MaskPlanner {

    /** Left/right gutters left untouched so the system back gesture still works. */
    private const val EDGE_GUTTER_DP = 20

    fun plan(s: Situation, m: ScreenMetrics, cfg: Settings): MaskPlan {
        val topOfBody = m.statusBar + m.dp(cfg.topBarDp)
        val bottomOfBody = m.height - (m.dp(cfg.bottomBarDp) + m.navBar)
        val rightCutout = m.dp(cfg.topRightDp)

        return when (s.screen) {

            // Home timeline: cover everything except the top-right icons
            // (notifications + DMs) and the bottom tab row.
            Screen.IG_FEED -> if (!cfg.blockIgFeed) MaskPlan.NONE else MaskPlan(
                blackout = listOf(
                    Rect(0, 0, m.width - rightCutout, topOfBody),
                    Rect(0, topOfBody, m.width, bottomOfBody)
                ),
                reason = "Instagram home feed"
            )

            // Explore / "for you" grid. The top bar stays live so search works.
            Screen.IG_EXPLORE -> if (!cfg.blockIgExplore) MaskPlan.NONE else MaskPlan(
                blackout = listOf(Rect(0, topOfBody, m.width, bottomOfBody)),
                reason = "Instagram explore"
            )

            // Reels tab: everything goes except the tab row, which is the way out.
            Screen.IG_REELS_TAB -> if (!cfg.blockIgReelsTab) MaskPlan.NONE else MaskPlan(
                blackout = listOf(Rect(0, 0, m.width, bottomOfBody)),
                reason = "Instagram Reels tab",
                tapToDismiss = true
            )

            // A single reel opened from a DM (or a profile, if allowed).
            Screen.IG_REEL_VIEWER -> when {
                !s.reelAllowed -> MaskPlan(
                    blackout = listOf(Rect(0, 0, m.width, m.height - m.navBar)),
                    reason = "Instagram Reels player",
                    tapToDismiss = true
                )
                s.scrollTripped -> MaskPlan(
                    blackout = listOf(Rect(0, 0, m.width, m.height - m.navBar)),
                    reason = "Scrolled past the shared reel",
                    tapToDismiss = true
                )
                cfg.blockScrollInAllowedReel -> MaskPlan(
                    swipeLock = Rect(
                        m.dp(EDGE_GUTTER_DP), 0,
                        m.width - m.dp(EDGE_GUTTER_DP), m.height - m.navBar
                    ),
                    reason = "Shared reel — scrolling locked"
                )
                else -> MaskPlan.NONE
            }

            Screen.IG_STORY -> if (!cfg.blockIgStories) MaskPlan.NONE else MaskPlan(
                blackout = listOf(Rect(0, 0, m.width, m.height - m.navBar)),
                reason = "Instagram stories",
                tapToDismiss = true
            )

            // Profiles, the DM inbox and individual threads are deliberately untouched.
            Screen.IG_PROFILE, Screen.IG_DM_INBOX, Screen.IG_DM_THREAD, Screen.IG_OTHER ->
                MaskPlan.NONE

            Screen.YT_SHORTS -> if (!cfg.blockYtShorts) MaskPlan.NONE else MaskPlan(
                blackout = listOf(Rect(0, 0, m.width, bottomOfBody)),
                reason = "YouTube Shorts",
                tapToDismiss = true
            )

            Screen.YT_HOME -> if (cfg.blockYtHome) {
                MaskPlan(
                    blackout = listOf(Rect(0, topOfBody, m.width, bottomOfBody)),
                    reason = "YouTube home feed"
                )
            } else {
                shelvesOnly(s, m, cfg)
            }

            Screen.YT_WATCH, Screen.YT_OTHER -> shelvesOnly(s, m, cfg)

            Screen.UNKNOWN -> MaskPlan.NONE
        }
    }

    /**
     * Shorts shelves turn up in the home feed, subscriptions and search
     * results. Cover just the shelf and leave the rest of the page usable.
     */
    private fun shelvesOnly(s: Situation, m: ScreenMetrics, cfg: Settings): MaskPlan {
        if (!cfg.blockYtShortsShelf || s.shortsShelves.isEmpty()) return MaskPlan.NONE
        val clipped = s.shortsShelves.mapNotNull { shelf ->
            val r = Rect(shelf)
            // Rect.intersect leaves the receiver untouched when there is no
            // overlap, so the return value has to be checked.
            if (!r.intersect(0, 0, m.width, m.height)) null
            else if (r.width() > 0 && r.height() > m.dp(24)) r else null
        }
        if (clipped.isEmpty()) return MaskPlan.NONE
        return MaskPlan(blackout = clipped, reason = "YouTube Shorts shelf")
    }
}

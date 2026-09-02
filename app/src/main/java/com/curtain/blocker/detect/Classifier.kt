package com.curtain.blocker.detect

import com.curtain.blocker.detect.Signatures as S

object Classifier {

    fun classify(scan: NodeScan): Screen = when (scan.pkg) {
        S.PKG_INSTAGRAM -> instagram(scan)
        S.PKG_YOUTUBE -> youtube(scan)
        else -> Screen.UNKNOWN
    }

    private fun instagram(scan: NodeScan): Screen {
        // Stories are their own player and must be ruled out before Reels,
        // because both live under "reel_" prefixed ids in some builds.
        if (scan.hasId(S.IG_STORY_MARKERS) && !scan.hasId(S.IG_CLIPS_VIEWER)) return Screen.IG_STORY

        val tabBarPresent = scan.hasId(S.IG_TAB_BAR) ||
            scan.hasId(S.IG_TAB_FEED) ||
            scan.hasId(S.IG_TAB_CLIPS) ||
            scan.hasId(S.IG_TAB_PROFILE)

        if (scan.hasId(S.IG_CLIPS_VIEWER)) {
            val onReelsTab = scan.selected(S.IG_TAB_CLIPS) || scan.selected(S.IG_DESC_REELS)
            val someOtherTabSelected = scan.selected(S.IG_TAB_FEED) ||
                scan.selected(S.IG_TAB_SEARCH) ||
                scan.selected(S.IG_TAB_PROFILE)
            // A clips player with the bottom bar still on screen is the Reels
            // tab. A clips player on its own was pushed from somewhere else —
            // a DM, a profile grid, a share sheet. When the tab bar is there but
            // the selection is unreadable, err towards blocking.
            return when {
                onReelsTab -> Screen.IG_REELS_TAB
                tabBarPresent && !someOtherTabSelected -> Screen.IG_REELS_TAB
                else -> Screen.IG_REEL_VIEWER
            }
        }

        // DMs before Explore: the DM search field looks like the Explore one.
        if (scan.hasId(S.IG_DM_THREAD_MARKERS)) return Screen.IG_DM_THREAD
        if (scan.hasId(S.IG_DM_INBOX_MARKERS)) return Screen.IG_DM_INBOX

        if (scan.hasId(S.IG_PROFILE_MARKERS)) return Screen.IG_PROFILE

        if (scan.selected(S.IG_TAB_SEARCH) || scan.selected(S.IG_DESC_SEARCH) ||
            scan.hasId(S.IG_EXPLORE_MARKERS)
        ) return Screen.IG_EXPLORE

        if (scan.selected(S.IG_TAB_FEED) || scan.selected(S.IG_DESC_HOME) ||
            scan.hasId(S.IG_FEED_MARKERS)
        ) return Screen.IG_FEED

        return Screen.IG_OTHER
    }

    private fun youtube(scan: NodeScan): Screen {
        if (scan.hasId(S.YT_SHORTS_PLAYER)) return Screen.YT_SHORTS
        if (scan.hasId(S.YT_WATCH_MARKERS)) return Screen.YT_WATCH
        if (scan.selected(listOf("home")) || scan.hasId(S.YT_HOME_MARKERS)) return Screen.YT_HOME
        return Screen.YT_OTHER
    }
}

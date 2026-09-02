package com.curtain.blocker.detect

enum class Screen {
    UNKNOWN,

    IG_FEED,          // home timeline
    IG_REELS_TAB,     // the Reels tab in the bottom bar
    IG_EXPLORE,       // search / explore grid ("for you")
    IG_PROFILE,       // someone's profile (yours or theirs)
    IG_DM_INBOX,
    IG_DM_THREAD,
    IG_REEL_VIEWER,   // full-screen clips player reached from somewhere else
    IG_STORY,
    IG_OTHER,

    YT_SHORTS,
    YT_HOME,
    YT_WATCH,
    YT_OTHER;

    val isInstagram: Boolean get() = name.startsWith("IG_")
    val isYouTube: Boolean get() = name.startsWith("YT_")
}

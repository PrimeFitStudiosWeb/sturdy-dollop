package com.curtain.blocker.detect

/**
 * Instagram and YouTube ship obfuscated layouts, but a useful subset of view
 * ids survives release-to-release. Everything matched here is a *substring*
 * check against the part of `viewIdResourceName` after "id/", or against a
 * lower-cased content description.
 *
 * When a future app update breaks detection, this is the only file that
 * normally needs editing. Use `adb shell uiautomator dump` (or Android
 * Studio's Layout Inspector) on the offending screen to find the new ids.
 */
object Signatures {

    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_YOUTUBE = "com.google.android.youtube"

    // ---- Instagram --------------------------------------------------------

    /** Full-screen Reels/clips player. Present on the Reels tab too. */
    val IG_CLIPS_VIEWER = listOf(
        "clips_viewer_view_pager",
        "clips_viewer_media_container",
        "clips_viewer_root",
        "clips_video_container",
        "clips_swipe_refresh_container",
        "clips_viewer_recycler_view"
    )

    /** Bottom navigation bar and its individual tabs. */
    val IG_TAB_BAR = listOf("tab_bar", "bottom_tab_bar", "tab_bar_container")
    val IG_TAB_FEED = listOf("feed_tab")
    val IG_TAB_SEARCH = listOf("search_tab")
    val IG_TAB_CLIPS = listOf("clips_tab")
    val IG_TAB_PROFILE = listOf("profile_tab")
    val IG_TAB_CREATION = listOf("creation_tab", "camera_tab")

    val IG_FEED_MARKERS = listOf(
        "feed_recycler_view",
        "main_feed_recycler_view",
        "action_bar_inbox_button",
        "feed_timeline"
    )

    val IG_EXPLORE_MARKERS = listOf(
        "explore_recycler_view",
        "search_edit_text",
        "action_bar_search_edit_text",
        "explore_grid",
        "discover_home"
    )

    val IG_PROFILE_MARKERS = listOf(
        "profile_header",
        "profile_header_avatar",
        "action_bar_button_follow",
        "profile_tab_layout",
        "row_profile_header"
    )

    val IG_DM_INBOX_MARKERS = listOf(
        "direct_inbox",
        "inbox_recycler_view",
        "direct_inbox_recycler_view",
        "thread_list_recycler_view"
    )

    val IG_DM_THREAD_MARKERS = listOf(
        "row_thread_composer_edittext",
        "thread_message_list",
        "direct_thread",
        "message_list",
        "row_thread_",
        "direct_text_input"
    )

    val IG_STORY_MARKERS = listOf(
        "reel_viewer_root",
        "reel_viewer_media_container",
        "reel_viewer_texture_view",
        "reel_view_pager"
    )

    /** Content descriptions used when view ids are unavailable. */
    val IG_DESC_HOME = listOf("home")
    val IG_DESC_REELS = listOf("reels")
    val IG_DESC_SEARCH = listOf("search and explore", "search")
    val IG_DESC_PROFILE = listOf("profile")
    val IG_DESC_DM = listOf("direct", "messenger", "messages")

    // ---- YouTube ----------------------------------------------------------

    val YT_SHORTS_PLAYER = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_fragment_root",
        "reel_player_underlay",
        "shorts_video_container",
        "reel_progress_bar"
    )

    val YT_WATCH_MARKERS = listOf(
        "watch_player",
        "player_fragment_container",
        "watch_while_player"
    )

    // Deliberately narrow: an id as generic as "results" also matches the
    // search page, which would black out search when the home-feed block is on.
    val YT_HOME_MARKERS = listOf(
        "pivot_bar_home",
        "browse_fragment"
    )

    /** Containers that hold a Shorts shelf inside a normal feed. */
    val YT_SHORTS_SHELF = listOf(
        "reel_shelf",
        "shorts_shelf",
        "reel_shelf_container",
        "reel_item_container"
    )

    val YT_DESC_SHORTS = listOf("shorts")
}

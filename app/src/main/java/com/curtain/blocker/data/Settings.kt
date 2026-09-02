package com.curtain.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

/**
 * Every tunable lives here. The geometry values are in dp and are meant to be
 * adjusted on the device — Instagram moves its chrome around between versions,
 * so "how tall is the top bar" is a knob, not a constant.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, true)
        set(value) = prefs.edit().putBoolean(ENABLED, value).apply()

    /** Epoch millis until which blocking is suspended. */
    var snoozeUntil: Long
        get() = prefs.getLong(SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(SNOOZE_UNTIL, value).apply()

    val active: Boolean
        get() = enabled && System.currentTimeMillis() >= snoozeUntil

    // --- what to block -----------------------------------------------------

    var blockIgFeed: Boolean by BoolPref(IG_FEED, true)
    var blockIgReelsTab: Boolean by BoolPref(IG_REELS, true)
    var blockIgExplore: Boolean by BoolPref(IG_EXPLORE, true)
    var blockIgStories: Boolean by BoolPref(IG_STORIES, false)
    var allowReelsFromDm: Boolean by BoolPref(IG_ALLOW_DM, true)
    var allowReelsFromProfile: Boolean by BoolPref(IG_ALLOW_PROFILE, false)
    var blockScrollInAllowedReel: Boolean by BoolPref(IG_LOCK_SCROLL, true)

    var blockYtShorts: Boolean by BoolPref(YT_SHORTS, true)
    var blockYtShortsShelf: Boolean by BoolPref(YT_SHELF, true)
    var blockYtHome: Boolean by BoolPref(YT_HOME, false)

    // --- geometry (dp) -----------------------------------------------------

    /** Height of the Instagram top bar that stays reachable on the right side. */
    var topBarDp: Int by IntPref(TOP_BAR_DP, 56)

    /** Width of the top-right cut-out (notifications + DM icons). */
    var topRightDp: Int by IntPref(TOP_RIGHT_DP, 120)

    /** Height of the always-visible bottom strip (tab bar + gesture nav). */
    var bottomBarDp: Int by IntPref(BOTTOM_BAR_DP, 56)

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            prefs.getBoolean(key, def)

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }

    private inner class IntPref(val key: String, val def: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            prefs.getInt(key, def)

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            prefs.edit().putInt(key, value).apply()
    }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val FILE = "curtain_prefs"
        const val ENABLED = "enabled"
        const val SNOOZE_UNTIL = "snooze_until"
        const val IG_FEED = "ig_feed"
        const val IG_REELS = "ig_reels"
        const val IG_EXPLORE = "ig_explore"
        const val IG_STORIES = "ig_stories"
        const val IG_ALLOW_DM = "ig_allow_dm"
        const val IG_ALLOW_PROFILE = "ig_allow_profile"
        const val IG_LOCK_SCROLL = "ig_lock_scroll"
        const val YT_SHORTS = "yt_shorts"
        const val YT_SHELF = "yt_shelf"
        const val YT_HOME = "yt_home"
        const val TOP_BAR_DP = "top_bar_dp"
        const val TOP_RIGHT_DP = "top_right_dp"
        const val BOTTOM_BAR_DP = "bottom_bar_dp"
    }
}

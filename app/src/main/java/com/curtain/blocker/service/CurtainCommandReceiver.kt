package com.curtain.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.curtain.blocker.data.Settings

/** Handles the notification actions and the in-app "preview mask" button. */
class CurtainCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        when (intent.action) {
            ACTION_PAUSE_15 -> {
                settings.snoozeUntil = System.currentTimeMillis() + 15 * 60_000L
                CurtainAccessibilityService.instance?.onSettingsChanged()
            }

            ACTION_RESUME -> {
                settings.snoozeUntil = 0L
                settings.enabled = true
                CurtainAccessibilityService.instance?.onSettingsChanged()
            }

            ACTION_PREVIEW -> CurtainAccessibilityService.instance?.previewFeedMask()
        }
        Notifications.postStatus(context, settings)
    }

    companion object {
        const val ACTION_PAUSE_15 = "com.curtain.blocker.PAUSE_15"
        const val ACTION_RESUME = "com.curtain.blocker.RESUME"
        const val ACTION_PREVIEW = "com.curtain.blocker.PREVIEW"
    }
}

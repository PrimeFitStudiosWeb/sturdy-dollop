package com.curtain.blocker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.curtain.blocker.R
import com.curtain.blocker.data.Settings
import com.curtain.blocker.ui.MainActivity

object Notifications {

    const val CHANNEL_ID = "curtain_status"
    const val NOTIF_ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun postStatus(context: Context, settings: Settings) {
        if (!canPost(context)) return
        val paused = !settings.active
        val body = when {
            !settings.enabled -> "Blocking is off"
            paused -> "Resumes shortly"
            else -> "Watching Instagram and YouTube"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    if (paused) R.string.notif_title_paused else R.string.notif_title_active
                )
            )
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (paused) {
            builder.addAction(
                0, context.getString(R.string.notif_resume),
                command(context, CurtainCommandReceiver.ACTION_RESUME)
            )
        } else {
            builder.addAction(
                0, context.getString(R.string.notif_pause),
                command(context, CurtainCommandReceiver.ACTION_PAUSE_15)
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun command(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CurtainCommandReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

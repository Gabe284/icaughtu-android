package com.example.icaughtuandroid.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.icaughtuandroid.MainActivity
import com.example.icaughtuandroid.R

object NotificationUtil {
    const val CHANNEL_CAPTURE = "incident_capture"
    const val CHANNEL_EVENTS = "security_events"
    const val CHANNEL_MESSAGING = "remote_messaging"
    const val CHANNEL_PERMISSION_HEALTH = "permission_health"
    const val CAPTURE_NOTIFICATION_ID = 4101
    const val MESSAGING_NOTIFICATION_ID = 4103
    const val PERMISSION_HEALTH_NOTIFICATION_ID = 4104
    const val PERMISSION_CHANGE_NOTIFICATION_ID = 4105

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURE, "Incident capture", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while aCaughtU captures an anti-theft incident."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "Security events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Failed-unlock, permission, and anti-theft status changes."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGING, "Internet commands", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the optional ntfy command connection active."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PERMISSION_HEALTH, "Protection status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent aCaughtU protection and required-permission status."
                setShowBadge(false)
            }
        )
    }

    fun captureNotification(context: Context, text: String): Notification =
        serviceNotification(context, CHANNEL_CAPTURE, "aCaughtU", text)

    fun messagingNotification(context: Context, text: String): Notification =
        serviceNotification(context, CHANNEL_MESSAGING, "aCaughtU Internet commands", text)

    fun permissionHealthNotification(
        context: Context,
        health: PermissionManager.Health
    ): Notification = serviceNotification(
        context,
        CHANNEL_PERMISSION_HEALTH,
        if (health.ok) "aCaughtU protection ready" else "aCaughtU action required",
        health.summary
    )

    private fun serviceNotification(
        context: Context,
        channel: String,
        title: String,
        text: String
    ): Notification {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_acaughtu)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    fun event(context: Context, title: String, text: String, id: Int = 4102) {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        context.getSystemService(NotificationManager::class.java).notify(
            id,
            Notification.Builder(context, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_acaughtu)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}

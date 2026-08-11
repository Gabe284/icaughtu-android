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
    const val CAPTURE_NOTIFICATION_ID = 4101
    const val MESSAGING_NOTIFICATION_ID = 4103

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURE, "Incident capture", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the device owner captures an anti-theft incident."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "Security events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Failed-unlock and anti-theft status notifications."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGING, "Internet commands", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the optional ntfy command connection active over the Internet."
            }
        )
    }

    fun captureNotification(context: Context, text: String): Notification =
        serviceNotification(context, CHANNEL_CAPTURE, text)

    fun messagingNotification(context: Context, text: String): Notification =
        serviceNotification(context, CHANNEL_MESSAGING, text)

    private fun serviceNotification(context: Context, channel: String, text: String): Notification {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("iCaughtU Android")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    fun event(context: Context, title: String, text: String, id: Int = 4102) {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

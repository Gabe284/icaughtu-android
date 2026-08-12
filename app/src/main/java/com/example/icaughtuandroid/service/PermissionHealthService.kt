package com.example.icaughtuandroid.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.icaughtuandroid.util.NotificationUtil
import com.example.icaughtuandroid.util.PermissionManager
import java.util.Timer
import java.util.TimerTask

class PermissionHealthService : Service() {
    private var timer: Timer? = null
    private var lastSummary: String? = null

    override fun onCreate() {
        super.onCreate()
        val health = PermissionManager.health(this)
        lastSummary = health.summary
        startForeground(
            NotificationUtil.PERMISSION_HEALTH_NOTIFICATION_ID,
            NotificationUtil.permissionHealthNotification(this, health)
        )
        timer = Timer("acaughtu-permission-health", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() = updateHealth()
            }, 30_000L, 60_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateHealth()
        return START_STICKY
    }

    private fun updateHealth() {
        val health = PermissionManager.health(this)
        val current = health.summary
        getSystemService(android.app.NotificationManager::class.java).notify(
            NotificationUtil.PERMISSION_HEALTH_NOTIFICATION_ID,
            NotificationUtil.permissionHealthNotification(this, health)
        )
        if (lastSummary != null && lastSummary != current) {
            NotificationUtil.event(
                this,
                "aCaughtU permission status changed",
                current,
                NotificationUtil.PERMISSION_CHANGE_NOTIFICATION_ID
            )
        }
        lastSummary = current
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PermissionHealthService::class.java))
        }
    }
}

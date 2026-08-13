package com.example.icaughtuandroid.util

import android.Manifest
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.icaughtuandroid.service.PermissionHealthJobService

object PermissionHealthMonitor {
    private const val JOB_ID = 0xAC041
    private const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return

        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, PermissionHealthJobService::class.java)
        )
            .setPeriodic(CHECK_INTERVAL_MS)
            .setPersisted(true)
            .build()

        scheduler.schedule(job)
    }

    fun checkNow(context: Context): PermissionManager.Health {
        val health = PermissionManager.health(context)
        val nm = context.getSystemService(NotificationManager::class.java)

        if (health.ok) {
            nm.cancel(NotificationUtil.PERMISSION_HEALTH_NOTIFICATION_ID)
            return health
        }

        val canNotify = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            nm.notify(
                NotificationUtil.PERMISSION_HEALTH_NOTIFICATION_ID,
                NotificationUtil.permissionHealthNotification(context, health)
            )
        }

        return health
    }
}

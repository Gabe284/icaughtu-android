package com.example.icaughtuandroid.admin

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.service.IncidentCaptureService
import com.example.icaughtuandroid.service.IncidentJobService
import com.example.icaughtuandroid.util.NotificationUtil

class GuardAdminReceiver : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        handlePasswordFailed(context)
    }

    @Suppress("DEPRECATION")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        // Kept for pre-Android 8 compatibility even though minSdk is currently 28.
        handlePasswordFailed(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        IncidentStore.append(context, "unlock", 0, "password_succeeded")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling device administration prevents failed-unlock detection and remote locking."
    }

    private fun handlePasswordFailed(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.armed) return

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val attempts = runCatching { dpm.currentFailedPasswordAttempts }.getOrDefault(1)
        IncidentStore.append(context, "failed_unlock", attempts, "detected")

        if (attempts < prefs.threshold) return

        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        val cameraGranted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (isDeviceOwner && prefs.includePhoto && cameraGranted) {
            val capture = Intent(context, IncidentCaptureService::class.java)
                .putExtra(IncidentCaptureService.EXTRA_ATTEMPTS, attempts)
                .putExtra(IncidentCaptureService.EXTRA_SOURCE, "failed_unlock")
            try {
                context.startForegroundService(capture)
            } catch (t: Throwable) {
                IncidentStore.append(context, "failed_unlock", attempts, "capture_start_failed", detail = t.message)
                IncidentJobService.enqueue(context, attempts, "failed_unlock")
            }
        } else {
            IncidentJobService.enqueue(context, attempts, "failed_unlock")
            if (prefs.includePhoto && !isDeviceOwner) {
                NotificationUtil.event(
                    context,
                    "Failed unlock detected",
                    "Photo capture requires Device Owner mode on modern Android. Location/webhook processing continues."
                )
            }
        }
    }
}

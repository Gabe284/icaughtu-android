package com.example.icaughtuandroid.util

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.Prefs

object PermissionManager {
    data class Health(val missing: List<String>) {
        val ok: Boolean get() = missing.isEmpty()
        val summary: String get() = if (ok) "All required permissions are available"
            else "Missing: ${missing.joinToString(", ")}"
    }

    fun health(context: Context): Health {
        val prefs = Prefs(context)
        val missing = mutableListOf<String>()
        if (!granted(context, Manifest.permission.CAMERA)) missing += "Camera"
        if (!granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) missing += "Location"
        if (Build.VERSION.SDK_INT >= 29 &&
            !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            missing += "Background location"
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            missing += "Notifications"
        }
        if (prefs.smsCommandsEnabled) {
            if (!granted(context, Manifest.permission.RECEIVE_SMS)) missing += "Receive SMS"
            if (!granted(context, Manifest.permission.SEND_SMS)) missing += "Send SMS"
        }
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, GuardAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) missing += "Device Admin"
        return Health(missing)
    }

    fun foregroundRuntimePermissions(context: Context): Array<String> {
        val p = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 28) p += Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Prefs(context).smsCommandsEnabled) {
            p += Manifest.permission.RECEIVE_SMS
            p += Manifest.permission.SEND_SMS
        }
        return p.filterNot { granted(context, it) }.distinct().toTypedArray()
    }

    fun backgroundLocationMissing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 &&
            !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

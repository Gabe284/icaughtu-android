package com.example.icaughtuandroid.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = Prefs(context)
        if (!prefs.ntfyCommandsEnabled) return
        runCatching { NtfyCommandService.start(context) }
            .onFailure { IncidentStore.append(context, "ntfy_listener", 0, "autostart_failed", detail = it.message) }
    }
}

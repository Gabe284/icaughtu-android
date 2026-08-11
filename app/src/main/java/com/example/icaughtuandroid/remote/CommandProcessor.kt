package com.example.icaughtuandroid.remote

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.service.AlarmService
import com.example.icaughtuandroid.util.LocationUtil

object CommandProcessor {
    fun execute(
        context: Context,
        body: String,
        expectedKey: String,
        source: String,
        reply: (String) -> Unit
    ): Boolean {
        val tokens = body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 3 || !tokens[0].equals("ICU", ignoreCase = true)) return false
        if (expectedKey.length < 6 || !constantTimeEquals(tokens[1], expectedKey)) return false

        val command = tokens[2].uppercase()
        val arg = tokens.drop(3).joinToString(" ")
        val prefs = Prefs(context)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, GuardAdminReceiver::class.java)

        IncidentStore.append(context, source, 0, "command_$command")
        when (command) {
            "STATUS" -> reply(
                "ICU status: armed=${prefs.armed}, admin=${dpm.isAdminActive(admin)}, " +
                    "owner=${dpm.isDeviceOwnerApp(context.packageName)}, battery=${batteryPercent(context)}%"
            )

            "LOCATE" -> {
                val location = LocationUtil.bestLastKnownLocation(context)
                if (location == null) {
                    reply("ICU locate: no permitted/known location")
                } else {
                    reply("ICU locate: ${location.latitude},${location.longitude} https://maps.google.com/?q=${location.latitude},${location.longitude}")
                }
            }

            "LOCK" -> {
                if (dpm.isAdminActive(admin)) {
                    reply("ICU: locking device")
                    dpm.lockNow()
                } else reply("ICU: Device Admin is not active")
            }

            "ARM" -> when (arg.trim().uppercase()) {
                "ON" -> { prefs.armed = true; reply("ICU: armed") }
                "OFF" -> { prefs.armed = false; reply("ICU: disarmed") }
                else -> reply("ICU: use ARM ON or ARM OFF")
            }

            "ALARM" -> {
                try {
                    context.startForegroundService(Intent(context, AlarmService::class.java))
                    reply("ICU: alarm requested")
                } catch (_: Throwable) {
                    reply("ICU: alarm start blocked; Device Owner mode may be required")
                }
            }

            "STOPALARM" -> {
                context.stopService(Intent(context, AlarmService::class.java))
                reply("ICU: alarm stopped")
            }

            else -> reply("ICU commands: STATUS LOCATE LOCK ARM ON|OFF ALARM STOPALARM")
        }
        return true
    }

    private fun batteryPercent(context: Context): Int {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aa = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        var diff = aa.size xor bb.size
        val max = maxOf(aa.size, bb.size)
        for (i in 0 until max) {
            val x = if (i < aa.size) aa[i].toInt() else 0
            val y = if (i < bb.size) bb[i].toInt() else 0
            diff = diff or (x xor y)
        }
        return diff == 0
    }
}

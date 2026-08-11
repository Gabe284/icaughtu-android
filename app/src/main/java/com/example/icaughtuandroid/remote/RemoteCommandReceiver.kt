package com.example.icaughtuandroid.remote

import android.Manifest
import android.telephony.SmsManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.Telephony
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.service.AlarmService
import com.example.icaughtuandroid.util.LocationUtil

class RemoteCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return

        val prefs = Prefs(context)
        val trusted = normalizeNumber(prefs.trustedSmsNumber)
        val key = prefs.smsCommandKey
        if (trusted.isBlank() || key.length < 6) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val grouped = messages.groupBy { normalizeNumber(it.originatingAddress.orEmpty()) }
        grouped.forEach { (sender, parts) ->
            if (!numbersMatch(sender, trusted)) return@forEach
            val body = parts.joinToString("") { it.messageBody.orEmpty() }.trim()
            executeIfValid(context, sender, body, key)
        }
    }

    private fun executeIfValid(context: Context, sender: String, body: String, expectedKey: String) {
        val tokens = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 3 || !tokens[0].equals("ICU", ignoreCase = true)) return
        if (!constantTimeEquals(tokens[1], expectedKey)) return

        val command = tokens[2].uppercase()
        val arg = tokens.drop(3).joinToString(" ")
        val prefs = Prefs(context)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, GuardAdminReceiver::class.java)

        IncidentStore.append(context, "sms_command", 0, "command_$command")
        when (command) {
            "STATUS" -> reply(
                context,
                sender,
                "ICU status: armed=${prefs.armed}, admin=${dpm.isAdminActive(admin)}, owner=${dpm.isDeviceOwnerApp(context.packageName)}, battery=${batteryPercent(context)}%"
            )

            "LOCATE" -> {
                val location = LocationUtil.bestLastKnownLocation(context)
                if (location == null) {
                    reply(context, sender, "ICU locate: no permitted/known location")
                } else {
                    reply(context, sender, "ICU locate: ${location.latitude},${location.longitude} https://maps.google.com/?q=${location.latitude},${location.longitude}")
                }
            }

            "LOCK" -> {
                if (dpm.isAdminActive(admin)) {
                    reply(context, sender, "ICU: locking device")
                    dpm.lockNow()
                } else reply(context, sender, "ICU: Device Admin is not active")
            }

            "ARM" -> {
                when (arg.trim().uppercase()) {
                    "ON" -> { prefs.armed = true; reply(context, sender, "ICU: armed") }
                    "OFF" -> { prefs.armed = false; reply(context, sender, "ICU: disarmed") }
                    else -> reply(context, sender, "ICU: use ARM ON or ARM OFF")
                }
            }

            "ALARM" -> {
                try {
                    context.startForegroundService(Intent(context, AlarmService::class.java))
                    reply(context, sender, "ICU: alarm requested")
                } catch (_: Throwable) {
                    reply(context, sender, "ICU: alarm start blocked; Device Owner mode may be required")
                }
            }

            "STOPALARM" -> {
                context.stopService(Intent(context, AlarmService::class.java))
                reply(context, sender, "ICU: alarm stopped")
            }

            else -> reply(context, sender, "ICU commands: STATUS LOCATE LOCK ARM ON|OFF ALARM STOPALARM")
        }
    }

    @Suppress("DEPRECATION")
    private fun reply(context: Context, number: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { SmsManager.getDefault().sendTextMessage(number, null, text.take(1550), null, null) }
    }

    private fun batteryPercent(context: Context): Int {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun normalizeNumber(value: String): String = value.filter { it.isDigit() }

    private fun numbersMatch(a: String, b: String): Boolean =
        a == b || (a.length >= 10 && b.length >= 10 && a.takeLast(10) == b.takeLast(10))

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

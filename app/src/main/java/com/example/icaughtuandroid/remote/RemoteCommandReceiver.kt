package com.example.icaughtuandroid.remote

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SmsManager
import com.example.icaughtuandroid.data.Prefs

class RemoteCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return

        val prefs = Prefs(context)
        if (!prefs.smsCommandsEnabled) return
        val trusted = normalizeNumber(prefs.trustedSmsNumber)
        val key = prefs.smsCommandKey
        if (trusted.isBlank() || key.length < 6) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val grouped = messages.groupBy { normalizeNumber(it.originatingAddress.orEmpty()) }
        grouped.forEach { (sender, parts) ->
            if (!numbersMatch(sender, trusted)) return@forEach
            val body = parts.joinToString("") { it.messageBody.orEmpty() }.trim()
            CommandProcessor.execute(context, body, key, "sms_command") { response ->
                reply(context, sender, response)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reply(context: Context, number: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { SmsManager.getDefault().sendTextMessage(number, null, text.take(1550), null, null) }
    }

    private fun normalizeNumber(value: String): String = value.filter { it.isDigit() }

    private fun numbersMatch(a: String, b: String): Boolean =
        a == b || (a.length >= 10 && b.length >= 10 && a.takeLast(10) == b.takeLast(10))
}

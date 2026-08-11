package com.example.icaughtuandroid.util

import android.content.Context
import java.io.File

object IncidentDelivery {
    data class Result(val ok: Boolean, val detail: String)

    fun sendAll(
        context: Context,
        source: String,
        attempts: Int,
        latitude: Double?,
        longitude: Double?,
        photo: File?
    ): Result {
        val webhook = WebhookClient.sendIncident(context, source, attempts, latitude, longitude, photo)
        val email = SmtpClient.sendIncident(context, source, attempts, latitude, longitude, photo)
        val ntfy = NtfyClient.sendIncident(context, source, attempts, latitude, longitude)
        val ok = webhook.ok && email.ok && ntfy.ok
        return Result(
            ok,
            listOf(
                "webhook=${webhook.detail}",
                "email=${email.detail}",
                "ntfy=${ntfy.detail}"
            ).joinToString("; ")
        )
    }
}

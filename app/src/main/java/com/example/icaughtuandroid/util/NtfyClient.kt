package com.example.icaughtuandroid.util

import android.content.Context
import com.example.icaughtuandroid.data.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NtfyClient {
    data class Result(val ok: Boolean, val detail: String)

    fun publish(
        server: String,
        topic: String,
        token: String,
        message: String,
        title: String? = null
    ): Result {
        if (!server.startsWith("https://", ignoreCase = true)) return Result(false, "ntfy requires HTTPS")
        if (topic.isBlank()) return Result(false, "ntfy topic is blank")

        return try {
            val endpoint = server.trimEnd('/') + "/" + encodeTopic(topic)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setRequestProperty("User-Agent", "ICaughtUAndroid/0.2")
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (!title.isNullOrBlank()) setRequestProperty("Title", title)
            }
            connection.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            Result(code in 200..299, "ntfy HTTP $code")
        } catch (t: Throwable) {
            Result(false, "ntfy ${t.javaClass.simpleName}: ${t.message ?: "request failed"}")
        }
    }

    fun sendIncident(
        context: Context,
        source: String,
        attempts: Int,
        latitude: Double?,
        longitude: Double?
    ): Result {
        val prefs = Prefs(context)
        if (!prefs.ntfyIncidentEnabled) return Result(true, "ntfy incident alerts disabled")
        if (prefs.ntfyIncidentTopic.isBlank()) return Result(false, "ntfy incident topic not configured")

        val message = buildString {
            append("Security incident: source=").append(source)
            append(", attempts=").append(attempts)
            if (prefs.includeLocation && latitude != null && longitude != null) {
                append("\nLocation: ").append(latitude).append(',').append(longitude)
                append("\nhttps://maps.google.com/?q=").append(latitude).append(',').append(longitude)
            }
        }
        return publish(prefs.ntfyServer, prefs.ntfyIncidentTopic, prefs.ntfyToken, message, "iCaughtU security incident")
    }

    fun encodeTopic(topic: String): String =
        URLEncoder.encode(topic.trim(), Charsets.UTF_8.name()).replace("+", "%20")
}

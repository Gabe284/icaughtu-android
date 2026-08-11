package com.example.icaughtuandroid.util

import android.content.Context
import android.os.Build
import android.util.Base64
import com.example.icaughtuandroid.data.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookClient {
    data class Result(val ok: Boolean, val detail: String)

    fun sendIncident(
        context: Context,
        source: String,
        attempts: Int,
        latitude: Double?,
        longitude: Double?,
        photo: File?
    ): Result {
        val prefs = Prefs(context)
        val endpoint = prefs.webhookUrl
        if (endpoint.isBlank()) return Result(true, "local-only; webhook not configured")
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            return Result(false, "webhook rejected: HTTPS is required")
        }

        val photoBase64 = if (prefs.includePhoto && photo != null && photo.exists()) {
            Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP)
        } else null

        val body = buildString {
            append('{')
            append("\"event\":\"failed_unlock\",")
            append("\"source\":\"").append(jsonEscape(source)).append("\",")
            append("\"attempts\":").append(attempts).append(',')
            append("\"timestamp\":").append(System.currentTimeMillis()).append(',')
            append("\"device\":\"")
                .append(jsonEscape("${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
                .append("\"")
            if (prefs.includeLocation && latitude != null && longitude != null) {
                append(",\"latitude\":").append(latitude)
                append(",\"longitude\":").append(longitude)
                append(",\"mapUrl\":\"")
                    .append(jsonEscape("https://maps.google.com/?q=$latitude,$longitude"))
                    .append("\"")
            }
            if (photoBase64 != null) {
                append(",\"photoMime\":\"image/jpeg\"")
                append(",\"photoBase64\":\"").append(photoBase64).append("\"")
            }
            append('}')
        }

        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "ICaughtUAndroid/0.1")
                val secret = prefs.webhookSecret
                if (secret.isNotBlank()) {
                    setRequestProperty("X-ICU-Signature", "sha256=${hmacSha256(secret, body)}")
                }
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = runCatching {
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText().take(300) }.orEmpty()
            }.getOrDefault("")
            connection.disconnect()
            Result(code in 200..299, "HTTP $code${if (response.isBlank()) "" else ": $response"}")
        } catch (t: Throwable) {
            Result(false, "${t.javaClass.simpleName}: ${t.message ?: "request failed"}")
        }
    }

    private fun hmacSha256(secret: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}

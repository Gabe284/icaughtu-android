package com.example.icaughtuandroid.remote

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.util.NotificationUtil
import com.example.icaughtuandroid.util.NtfyClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NtfyCommandService : Service() {
    @Volatile private var running = false
    @Volatile private var connection: HttpURLConnection? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        if (!isConfigured(prefs)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startMessagingForeground()
        if (worker?.isAlive != true) {
            running = true
            worker = thread(name = "icu-ntfy-listener") { listenLoop() }
        }
        return START_STICKY
    }

    private fun startMessagingForeground() {
        val notification = NotificationUtil.messagingNotification(this, "Internet commands enabled")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationUtil.MESSAGING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NotificationUtil.MESSAGING_NOTIFICATION_ID, notification)
        }
    }

    private fun listenLoop() {
        while (running) {
            val prefs = Prefs(this)
            if (!isConfigured(prefs)) break

            try {
                val endpoint = prefs.ntfyServer.trimEnd('/') + "/" + NtfyClient.encodeTopic(prefs.ntfyCommandTopic) + "/json"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    setRequestProperty("Accept", "application/x-ndjson, application/json")
                    setRequestProperty("User-Agent", "ICaughtUAndroid/0.2")
                    if (prefs.ntfyToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${prefs.ntfyToken}")
                    }
                }
                connection = conn
                val code = conn.responseCode
                if (code !in 200..299) {
                    IncidentStore.append(this, "ntfy_listener", 0, "http_$code")
                    conn.disconnect()
                    connection = null
                    sleepReconnect()
                    continue
                }

                conn.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!running) return@forEach
                        val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                        if (obj.optString("event") != "message") return@forEach
                        val body = obj.optString("message").trim()
                        if (body.isBlank()) return@forEach

                        val current = Prefs(this)
                        CommandProcessor.execute(
                            this,
                            body,
                            current.ntfyCommandKey,
                            "ntfy_command"
                        ) { response ->
                            if (current.ntfyResponseTopic.isNotBlank()) {
                                val result = NtfyClient.publish(
                                    current.ntfyServer,
                                    current.ntfyResponseTopic,
                                    current.ntfyToken,
                                    response,
                                    "iCaughtU command response"
                                )
                                if (!result.ok) {
                                    IncidentStore.append(this, "ntfy_response", 0, "delivery_failed", detail = result.detail)
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                connection = null
            } catch (t: Throwable) {
                if (running) {
                    IncidentStore.append(this, "ntfy_listener", 0, "connection_error", detail = t.message)
                }
                runCatching { connection?.disconnect() }
                connection = null
            }

            sleepReconnect()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sleepReconnect() {
        var remaining = 5_000L
        while (running && remaining > 0) {
            val slice = minOf(remaining, 500L)
            try { Thread.sleep(slice) } catch (_: InterruptedException) { return }
            remaining -= slice
        }
    }

    private fun isConfigured(prefs: Prefs): Boolean =
        prefs.ntfyCommandsEnabled &&
            prefs.ntfyServer.startsWith("https://", ignoreCase = true) &&
            prefs.ntfyCommandTopic.isNotBlank() &&
            prefs.ntfyCommandKey.length >= 6

    override fun onDestroy() {
        running = false
        runCatching { connection?.disconnect() }
        worker?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, NtfyCommandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyCommandService::class.java))
        }
    }
}

package com.example.icaughtuandroid.util

import android.content.Context
import android.os.Build
import android.util.Base64
import com.example.icaughtuandroid.data.Prefs
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object SmtpClient {
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
        if (!prefs.emailEnabled) return Result(true, "email disabled")

        val host = prefs.smtpHost
        val port = prefs.smtpPort
        val username = prefs.smtpUsername
        val password = prefs.smtpPassword
        val from = prefs.smtpFrom.ifBlank { username }
        val recipient = prefs.smtpRecipient
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank() || recipient.isBlank()) {
            return Result(false, "email configuration incomplete")
        }
        if (!safeAddress(from) || !safeAddress(recipient)) {
            return Result(false, "invalid email address")
        }

        val body = buildString {
            append("iCaughtU Android security incident\r\n\r\n")
            append("Source: ").append(source).append("\r\n")
            append("Failed attempts: ").append(attempts).append("\r\n")
            append("Timestamp: ").append(System.currentTimeMillis()).append("\r\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\r\n")
            if (prefs.includeLocation && latitude != null && longitude != null) {
                append("Location: ").append(latitude).append(',').append(longitude).append("\r\n")
                append("Map: https://maps.google.com/?q=").append(latitude).append(',').append(longitude).append("\r\n")
            }
        }

        return sendMessage(
            host = host,
            port = port,
            implicitTls = prefs.smtpImplicitTls,
            username = username,
            password = password,
            from = from,
            recipient = recipient,
            subject = "iCaughtU security incident",
            body = body,
            attachment = if (prefs.includePhoto) photo else null
        )
    }

    fun sendTest(context: Context): Result {
        val prefs = Prefs(context)
        val host = prefs.smtpHost
        val username = prefs.smtpUsername
        val password = prefs.smtpPassword
        val from = prefs.smtpFrom.ifBlank { username }
        val recipient = prefs.smtpRecipient
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank() || recipient.isBlank()) {
            return Result(false, "email configuration incomplete")
        }
        return sendMessage(
            host,
            prefs.smtpPort,
            prefs.smtpImplicitTls,
            username,
            password,
            from,
            recipient,
            "iCaughtU SMTP test",
            "This is a test message from iCaughtU Android.\r\n",
            null
        )
    }

    private fun sendMessage(
        host: String,
        port: Int,
        implicitTls: Boolean,
        username: String,
        password: String,
        from: String,
        recipient: String,
        subject: String,
        body: String,
        attachment: File?
    ): Result {
        var socket: Socket? = null
        return try {
            socket = if (implicitTls) createTlsSocket(host, port) else Socket().apply {
                connect(InetSocketAddress(host, port), 15_000)
                soTimeout = 20_000
            }

            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            var writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII))
            expect(reader, setOf(220))

            command(writer, reader, "EHLO android", setOf(250))
            if (!implicitTls) {
                command(writer, reader, "STARTTLS", setOf(220))
                val tls = upgradeTls(socket, host, port)
                socket = tls
                reader = BufferedReader(InputStreamReader(tls.getInputStream(), Charsets.US_ASCII))
                writer = BufferedWriter(OutputStreamWriter(tls.getOutputStream(), Charsets.US_ASCII))
                command(writer, reader, "EHLO android", setOf(250))
            }

            command(writer, reader, "AUTH LOGIN", setOf(334))
            command(writer, reader, b64(username), setOf(334))
            command(writer, reader, b64(password), setOf(235))
            command(writer, reader, "MAIL FROM:<$from>", setOf(250))
            command(writer, reader, "RCPT TO:<$recipient>", setOf(250, 251))
            command(writer, reader, "DATA", setOf(354))

            val mime = buildMime(from, recipient, subject, body, attachment)
            writeData(writer, mime)
            expect(reader, setOf(250))
            runCatching { command(writer, reader, "QUIT", setOf(221)) }
            Result(true, "email sent")
        } catch (t: Throwable) {
            Result(false, "email ${t.javaClass.simpleName}: ${t.message ?: "send failed"}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun createTlsSocket(host: String, port: Int): SSLSocket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket() as SSLSocket
        ssl.connect(InetSocketAddress(host, port), 15_000)
        ssl.soTimeout = 20_000
        enableHostnameVerification(ssl)
        ssl.startHandshake()
        return ssl
    }

    private fun upgradeTls(socket: Socket, host: String, port: Int): SSLSocket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(socket, host, port, true) as SSLSocket
        ssl.soTimeout = 20_000
        enableHostnameVerification(ssl)
        ssl.startHandshake()
        return ssl
    }

    private fun enableHostnameVerification(socket: SSLSocket) {
        val params = socket.sslParameters
        params.endpointIdentificationAlgorithm = "HTTPS"
        socket.sslParameters = params
    }

    private fun command(
        writer: BufferedWriter,
        reader: BufferedReader,
        command: String,
        expected: Set<Int>
    ) {
        writer.write(command)
        writer.write("\r\n")
        writer.flush()
        expect(reader, expected)
    }

    private fun expect(reader: BufferedReader, expected: Set<Int>) {
        val first = reader.readLine() ?: throw IllegalStateException("SMTP connection closed")
        val code = first.take(3).toIntOrNull() ?: throw IllegalStateException("Bad SMTP response")
        if (first.length >= 4 && first[3] == '-') {
            while (true) {
                val line = reader.readLine() ?: throw IllegalStateException("SMTP connection closed")
                if (line.startsWith("$code ")) break
            }
        }
        if (code !in expected) throw IllegalStateException("SMTP $code")
    }

    private fun buildMime(
        from: String,
        recipient: String,
        subject: String,
        body: String,
        attachment: File?
    ): String {
        val safeSubject = subject.replace("\r", " ").replace("\n", " ")
        if (attachment == null || !attachment.exists()) {
            return buildString {
                append("From: ").append(from).append("\r\n")
                append("To: ").append(recipient).append("\r\n")
                append("Subject: ").append(safeSubject).append("\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: 8bit\r\n\r\n")
                append(body)
            }
        }

        val boundary = "icu-${UUID.randomUUID()}"
        val encodedPhoto = Base64.encodeToString(attachment.readBytes(), Base64.NO_WRAP)
        return buildString {
            append("From: ").append(from).append("\r\n")
            append("To: ").append(recipient).append("\r\n")
            append("Subject: ").append(safeSubject).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n\r\n")
            append(body).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("Content-Disposition: attachment; filename=\"incident.jpg\"\r\n\r\n")
            encodedPhoto.chunked(76).forEach { append(it).append("\r\n") }
            append("--").append(boundary).append("--\r\n")
        }
    }

    private fun writeData(writer: BufferedWriter, data: String) {
        data.replace("\r\n", "\n").split('\n').forEach { line ->
            if (line.startsWith('.')) writer.write('.')
            writer.write(line)
            writer.write("\r\n")
        }
        writer.write(".\r\n")
        writer.flush()
    }

    private fun b64(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun safeAddress(value: String): Boolean =
        value.contains('@') && !value.contains('\r') && !value.contains('\n') && !value.contains('<') && !value.contains('>')
}

package com.example.icaughtuandroid.data

import android.content.Context

class Prefs(context: Context) {
    private val storageContext = context.createDeviceProtectedStorageContext()
    private val prefs = storageContext.getSharedPreferences("guard_settings", Context.MODE_PRIVATE)

    var armed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, true)
        set(value) = prefs.edit().putBoolean(KEY_ARMED, value).apply()

    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 1).coerceIn(1, 20)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(1, 20)).apply()

    var includePhoto: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_PHOTO, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_PHOTO, value).apply()

    var includeLocation: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_LOCATION, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_LOCATION, value).apply()

    var webhookEnabled: Boolean
        get() = if (prefs.contains(KEY_WEBHOOK_ENABLED)) prefs.getBoolean(KEY_WEBHOOK_ENABLED, false) else webhookUrl.isNotBlank()
        set(value) = prefs.edit().putBoolean(KEY_WEBHOOK_ENABLED, value).apply()

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var webhookSecret: String
        get() = prefs.getString(KEY_WEBHOOK_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_SECRET, value).apply()

    var smsCommandsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_COMMANDS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMS_COMMANDS_ENABLED, value).apply()

    var trustedSmsNumber: String
        get() = prefs.getString(KEY_TRUSTED_SMS_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRUSTED_SMS_NUMBER, value.trim()).apply()

    var smsCommandKey: String
        get() = prefs.getString(KEY_SMS_COMMAND_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMS_COMMAND_KEY, value.trim()).apply()

    var ntfyCommandsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NTFY_COMMANDS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NTFY_COMMANDS_ENABLED, value).apply()

    var ntfyIncidentEnabled: Boolean
        get() = prefs.getBoolean(KEY_NTFY_INCIDENT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NTFY_INCIDENT_ENABLED, value).apply()

    var ntfyServer: String
        get() = prefs.getString(KEY_NTFY_SERVER, "https://ntfy.sh") ?: "https://ntfy.sh"
        set(value) = prefs.edit().putString(KEY_NTFY_SERVER, value.trim().trimEnd('/')).apply()

    var ntfyCommandTopic: String
        get() = prefs.getString(KEY_NTFY_COMMAND_TOPIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_COMMAND_TOPIC, value.trim()).apply()

    var ntfyResponseTopic: String
        get() = prefs.getString(KEY_NTFY_RESPONSE_TOPIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_RESPONSE_TOPIC, value.trim()).apply()

    var ntfyIncidentTopic: String
        get() = prefs.getString(KEY_NTFY_INCIDENT_TOPIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_INCIDENT_TOPIC, value.trim()).apply()

    var ntfyToken: String
        get() = prefs.getString(KEY_NTFY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_TOKEN, value.trim()).apply()

    var ntfyCommandKey: String
        get() = prefs.getString(KEY_NTFY_COMMAND_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_COMMAND_KEY, value.trim()).apply()

    var emailEnabled: Boolean
        get() = prefs.getBoolean(KEY_EMAIL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EMAIL_ENABLED, value).apply()

    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_HOST, value.trim()).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, 587).coerceIn(1, 65535)
        set(value) = prefs.edit().putInt(KEY_SMTP_PORT, value.coerceIn(1, 65535)).apply()

    var smtpImplicitTls: Boolean
        get() = prefs.getBoolean(KEY_SMTP_IMPLICIT_TLS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMTP_IMPLICIT_TLS, value).apply()

    var smtpUsername: String
        get() = prefs.getString(KEY_SMTP_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_USERNAME, value.trim()).apply()

    var smtpPassword: String
        get() = prefs.getString(KEY_SMTP_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_PASSWORD, value).apply()

    var smtpFrom: String
        get() = prefs.getString(KEY_SMTP_FROM, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_FROM, value.trim()).apply()

    var smtpRecipient: String
        get() = prefs.getString(KEY_SMTP_RECIPIENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMTP_RECIPIENT, value.trim()).apply()

    companion object {
        private const val KEY_ARMED = "armed"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INCLUDE_PHOTO = "include_photo"
        private const val KEY_INCLUDE_LOCATION = "include_location"
        private const val KEY_WEBHOOK_ENABLED = "webhook_enabled"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_SECRET = "webhook_secret"
        private const val KEY_SMS_COMMANDS_ENABLED = "sms_commands_enabled"
        private const val KEY_TRUSTED_SMS_NUMBER = "trusted_sms_number"
        private const val KEY_SMS_COMMAND_KEY = "sms_command_key"
        private const val KEY_NTFY_COMMANDS_ENABLED = "ntfy_commands_enabled"
        private const val KEY_NTFY_INCIDENT_ENABLED = "ntfy_incident_enabled"
        private const val KEY_NTFY_SERVER = "ntfy_server"
        private const val KEY_NTFY_COMMAND_TOPIC = "ntfy_command_topic"
        private const val KEY_NTFY_RESPONSE_TOPIC = "ntfy_response_topic"
        private const val KEY_NTFY_INCIDENT_TOPIC = "ntfy_incident_topic"
        private const val KEY_NTFY_TOKEN = "ntfy_token"
        private const val KEY_NTFY_COMMAND_KEY = "ntfy_command_key"
        private const val KEY_EMAIL_ENABLED = "email_enabled"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_IMPLICIT_TLS = "smtp_implicit_tls"
        private const val KEY_SMTP_USERNAME = "smtp_username"
        private const val KEY_SMTP_PASSWORD = "smtp_password"
        private const val KEY_SMTP_FROM = "smtp_from"
        private const val KEY_SMTP_RECIPIENT = "smtp_recipient"
    }
}

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

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var webhookSecret: String
        get() = prefs.getString(KEY_WEBHOOK_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_SECRET, value).apply()

    var trustedSmsNumber: String
        get() = prefs.getString(KEY_TRUSTED_SMS_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRUSTED_SMS_NUMBER, value.trim()).apply()

    var smsCommandKey: String
        get() = prefs.getString(KEY_SMS_COMMAND_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMS_COMMAND_KEY, value.trim()).apply()

    companion object {
        private const val KEY_ARMED = "armed"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INCLUDE_PHOTO = "include_photo"
        private const val KEY_INCLUDE_LOCATION = "include_location"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_SECRET = "webhook_secret"
        private const val KEY_TRUSTED_SMS_NUMBER = "trusted_sms_number"
        private const val KEY_SMS_COMMAND_KEY = "sms_command_key"
    }
}

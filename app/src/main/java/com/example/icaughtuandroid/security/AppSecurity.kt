package com.example.icaughtuandroid.security

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

object AppSecurity {
    const val MODE_NONE = "none"
    const val MODE_DEVICE = "device"
    const val MODE_PASSPHRASE = "passphrase"

    private const val FILE = "acaughtu_security"
    private const val KEY_MODE = "lock_mode"
    private const val KEY_SALT = "pass_salt"
    private const val KEY_HASH = "pass_hash"
    private const val KEY_UNINSTALL = "prevent_uninstall"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    @Volatile private var unlockedUntil = 0L

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_NONE) ?: MODE_NONE

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode).apply()
        if (mode == MODE_NONE) unlockedUntil = Long.MAX_VALUE
        else unlockedUntil = 0L
    }

    fun setPassphrase(context: Context, passphrase: CharArray) {
        require(passphrase.size >= 8) { "Passphrase must contain at least 8 characters" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(passphrase, salt)
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        passphrase.fill('\u0000')
    }

    fun hasPassphrase(context: Context): Boolean =
        !prefs(context).getString(KEY_HASH, null).isNullOrBlank()

    fun verifyPassphrase(context: Context, candidate: CharArray): Boolean {
        val p = prefs(context)
        val salt64 = p.getString(KEY_SALT, null) ?: return false
        val hash64 = p.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(salt64, Base64.NO_WRAP)
        val expected = Base64.decode(hash64, Base64.NO_WRAP)
        val actual = derive(candidate, salt)
        candidate.fill('\u0000')
        return MessageDigest.isEqual(expected, actual)
    }

    fun isUnlocked(context: Context): Boolean =
        mode(context) == MODE_NONE || SystemClock.elapsedRealtime() < unlockedUntil

    fun markUnlocked(minutes: Int = 2) {
        unlockedUntil = SystemClock.elapsedRealtime() + minutes.coerceAtLeast(1) * 60_000L
    }

    fun lockNow() {
        unlockedUntil = 0L
    }

    fun preventUninstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNINSTALL, false)

    fun setPreventUninstall(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_UNINSTALL, value).apply()
    }

    private fun derive(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

package com.example.icaughtuandroid.security

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AppLockActivity : Activity() {
    private var launchedCredential = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        when (AppSecurity.mode(this)) {
            AppSecurity.MODE_NONE -> {
                AppSecurity.markUnlocked()
                finish()
            }
            AppSecurity.MODE_DEVICE -> showDeviceCredential()
            AppSecurity.MODE_PASSPHRASE -> showPassphraseUi()
            else -> finish()
        }
    }

    private fun showDeviceCredential() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            Toast.makeText(this, "No secure device lock is configured.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val intent = km.createConfirmDeviceCredentialIntent(
            "Unlock aCaughtU",
            "Confirm your device lock to access aCaughtU."
        )
        if (intent == null) {
            Toast.makeText(this, "Device credential confirmation is unavailable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        launchedCredential = true
        startActivityForResult(intent, REQ_DEVICE_CREDENTIAL)
    }

    private fun showPassphraseUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(28))
            setBackgroundColor(Color.rgb(9, 15, 25))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "aCaughtU"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Enter your passphrase to continue"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(10), 0, dp(24))
        })
        val input = EditText(this).apply {
            hint = "Passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "Unlock"
            isAllCaps = false
            setOnClickListener {
                if (AppSecurity.verifyPassphrase(this@AppLockActivity, input.text.toString().toCharArray())) {
                    AppSecurity.markUnlocked()
                    finish()
                } else {
                    input.text.clear()
                    Toast.makeText(this@AppLockActivity, "Incorrect passphrase", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) {
                AppSecurity.markUnlocked()
                finish()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppSecurity.mode(this) == AppSecurity.MODE_DEVICE &&
            launchedCredential &&
            !AppSecurity.isUnlocked(this)
        ) {
            // Result callback decides success/failure.
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_DEVICE_CREDENTIAL = 440
    }
}

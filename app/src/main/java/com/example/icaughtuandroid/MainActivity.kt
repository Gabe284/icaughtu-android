package com.example.icaughtuandroid

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.service.IncidentCaptureService
import com.example.icaughtuandroid.service.IncidentJobService
import com.example.icaughtuandroid.service.AlarmService
import com.example.icaughtuandroid.util.NotificationUtil

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var armedSwitch: Switch
    private lateinit var thresholdInput: EditText
    private lateinit var includePhotoCheck: CheckBox
    private lateinit var includeLocationCheck: CheckBox
    private lateinit var webhookInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var trustedNumberInput: EditText
    private lateinit var smsKeyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, GuardAdminReceiver::class.java)
        NotificationUtil.ensureChannels(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "iCaughtU Android"
            textSize = 28f
            setTextColor(Color.rgb(17, 24, 39))
        })
        root.addView(TextView(this).apply {
            text = "Owner-controlled anti-theft incident capture for Android 16"
            textSize = 14f
            setPadding(0, dp(4), 0, dp(14))
        })

        statusText = sectionText("")
        root.addView(statusText)

        root.addView(button("Activate Device Admin") {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required to detect failed unlock attempts and allow this app to lock the device."
                )
            }
            startActivity(intent)
        })

        root.addView(button("Request camera + location permissions") { requestCorePermissions() })
        root.addView(button("Request SMS command permissions") { requestSmsPermissions() })
        root.addView(button("Open background-location permission settings") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        })

        root.addView(label("Protection"))
        armedSwitch = Switch(this).apply {
            text = "Armed"
            isChecked = prefs.armed
        }
        root.addView(armedSwitch)

        thresholdInput = EditText(this).apply {
            hint = "Failed attempts before incident"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.threshold.toString())
        }
        root.addView(thresholdInput)

        includePhotoCheck = CheckBox(this).apply {
            text = "Capture front-camera photo (Device Owner mode for lock-screen events)"
            isChecked = prefs.includePhoto
        }
        root.addView(includePhotoCheck)

        includeLocationCheck = CheckBox(this).apply {
            text = "Include last-known location"
            isChecked = prefs.includeLocation
        }
        root.addView(includeLocationCheck)

        root.addView(label("Webhook delivery"))
        root.addView(TextView(this).apply {
            text = "Optional. Incident JSON is POSTed only to HTTPS. A configured secret adds an HMAC-SHA256 signature header."
            textSize = 13f
        })

        webhookInput = EditText(this).apply {
            hint = "https://your-server.example/incident"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.webhookUrl)
        }
        root.addView(webhookInput)

        secretInput = EditText(this).apply {
            hint = "Webhook shared secret (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.webhookSecret)
        }
        root.addView(secretInput)

        root.addView(label("Authenticated SMS commands"))
        root.addView(TextView(this).apply {
            text = "Optional sideload feature. Commands are accepted only from the configured number and require the command key. SMS is not end-to-end encrypted, so use a unique key of at least 6 characters."
            textSize = 13f
        })
        trustedNumberInput = EditText(this).apply {
            hint = "Trusted sender number, e.g. +14175551212"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.trustedSmsNumber)
        }
        root.addView(trustedNumberInput)
        smsKeyInput = EditText(this).apply {
            hint = "SMS command key (minimum 6 characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.smsCommandKey)
        }
        root.addView(smsKeyInput)
        root.addView(TextView(this).apply {
            text = "Syntax: ICU <key> STATUS | LOCATE | LOCK | ARM ON | ARM OFF | ALARM | STOPALARM"
            textSize = 12f
            setTextIsSelectable(true)
        })

        root.addView(button("Save settings") {
            val threshold = thresholdInput.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 1
            prefs.armed = armedSwitch.isChecked
            prefs.threshold = threshold
            prefs.includePhoto = includePhotoCheck.isChecked
            prefs.includeLocation = includeLocationCheck.isChecked
            prefs.webhookUrl = webhookInput.text.toString()
            prefs.webhookSecret = secretInput.text.toString()
            prefs.trustedSmsNumber = trustedNumberInput.text.toString()
            prefs.smsCommandKey = smsKeyInput.text.toString()
            thresholdInput.setText(threshold.toString())
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            refreshStatus()
        })

        root.addView(label("Tests and actions"))
        root.addView(button("Capture test incident") {
            val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                startForegroundService(
                    Intent(this, IncidentCaptureService::class.java)
                        .putExtra(IncidentCaptureService.EXTRA_ATTEMPTS, 1)
                        .putExtra(IncidentCaptureService.EXTRA_SOURCE, "manual_test")
                )
            } else {
                IncidentJobService.enqueue(this, 1, "manual_test")
                Toast.makeText(this, "Camera permission missing; testing log/webhook only", Toast.LENGTH_LONG).show()
            }
        })

        root.addView(button("Stop lost-device alarm") { stopService(Intent(this, AlarmService::class.java)) })

        root.addView(button("Lock device now") {
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
            } else {
                Toast.makeText(this, "Activate Device Admin first", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(label("Recent incidents"))
        logText = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(logText)
        root.addView(button("Refresh status/log") { refreshStatus() })

        root.addView(label("Device Owner provisioning"))
        root.addView(TextView(this).apply {
            text = "For full failed-unlock camera capture, provision this app as Device Owner on a fresh/test device. After installing the APK, before normal device setup completes, use:\n\nadb shell dpm set-device-owner com.example.icaughtuandroid/.admin.GuardAdminReceiver"
            textSize = 13f
            setTextIsSelectable(true)
        })

        return scroll
    }

    private fun requestSmsPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS),
            REQUEST_SMS_PERMISSIONS
        )
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun refreshStatus() {
        val adminActive = dpm.isAdminActive(admin)
        val owner = dpm.isDeviceOwnerApp(packageName)
        val camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val fineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = if (Build.VERSION.SDK_INT >= 29) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        statusText.text = buildString {
            append("Mode: ").append(if (owner) "DEVICE OWNER (full)" else if (adminActive) "DEVICE ADMIN (limited)" else "inactive")
            append("\nArmed: ").append(prefs.armed)
            append("  |  threshold: ").append(prefs.threshold)
            append("\nCamera permission: ").append(camera)
            append("\nFine location: ").append(fineLocation)
            append("  |  background location: ").append(backgroundLocation)
            val receiveSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            val sendSms = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            append("\nSMS commands: ").append(receiveSms && sendSms && prefs.trustedSmsNumber.isNotBlank() && prefs.smsCommandKey.length >= 6)
            if (adminActive && !owner) {
                append("\n\nLimited mode can detect failed unlocks, log them, lock the device, and process location/webhooks. Android 14+ blocks automatic lock-screen camera capture unless this DPC is Device Owner.")
            }
        }
        logText.text = IncidentStore.readRecent(this)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(17, 24, 39))
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun sectionText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(Color.rgb(243, 244, 246))
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val REQUEST_SMS_PERMISSIONS = 101
    }
}

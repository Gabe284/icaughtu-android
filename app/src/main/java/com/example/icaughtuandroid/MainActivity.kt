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
import com.example.icaughtuandroid.remote.NtfyCommandService
import com.example.icaughtuandroid.service.AlarmService
import com.example.icaughtuandroid.service.IncidentCaptureService
import com.example.icaughtuandroid.service.IncidentJobService
import com.example.icaughtuandroid.util.NotificationUtil
import com.example.icaughtuandroid.util.MediaPhotoPublisher
import com.example.icaughtuandroid.util.SmtpClient
import kotlin.concurrent.thread

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

    private lateinit var webhookEnabledCheck: CheckBox
    private lateinit var webhookInput: EditText
    private lateinit var secretInput: EditText

    private lateinit var smsEnabledCheck: CheckBox
    private lateinit var trustedNumberInput: EditText
    private lateinit var smsKeyInput: EditText

    private lateinit var ntfyCommandsCheck: CheckBox
    private lateinit var ntfyIncidentCheck: CheckBox
    private lateinit var ntfyServerInput: EditText
    private lateinit var ntfyCommandTopicInput: EditText
    private lateinit var ntfyResponseTopicInput: EditText
    private lateinit var ntfyIncidentTopicInput: EditText
    private lateinit var ntfyTokenInput: EditText
    private lateinit var ntfyKeyInput: EditText

    private lateinit var emailEnabledCheck: CheckBox
    private lateinit var smtpHostInput: EditText
    private lateinit var smtpPortInput: EditText
    private lateinit var smtpImplicitTlsCheck: CheckBox
    private lateinit var smtpUsernameInput: EditText
    private lateinit var smtpPasswordInput: EditText
    private lateinit var smtpFromInput: EditText
    private lateinit var smtpRecipientInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, GuardAdminReceiver::class.java)
        NotificationUtil.ensureChannels(this)
        setContentView(buildUi())
        thread(name = "icu-gallery-sync") {
            val result = MediaPhotoPublisher.syncIncidentPhotos(this)
            if (result.published > 0 || result.failed > 0) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Gallery sync: ${result.published} added, ${result.failed} failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        })

        root.addView(label("Protection"))
        armedSwitch = Switch(this).apply {
            text = "Armed"
            isChecked = prefs.armed
        }
        root.addView(armedSwitch)
        thresholdInput = textInput("Failed attempts before incident", prefs.threshold.toString(), InputType.TYPE_CLASS_NUMBER)
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
        webhookEnabledCheck = CheckBox(this).apply {
            text = "Enable HTTPS webhook incident delivery"
            isChecked = prefs.webhookEnabled
        }
        root.addView(webhookEnabledCheck)
        webhookInput = textInput("https://your-server.example/incident", prefs.webhookUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        root.addView(webhookInput)
        secretInput = passwordInput("Webhook HMAC shared secret (optional)", prefs.webhookSecret)
        root.addView(secretInput)

        root.addView(label("Authenticated SMS commands"))
        smsEnabledCheck = CheckBox(this).apply {
            text = "Enable SMS commands"
            isChecked = prefs.smsCommandsEnabled
        }
        root.addView(smsEnabledCheck)
        root.addView(TextView(this).apply {
            text = "SMS remains independent from Internet commands. Commands are accepted only from the configured number and require a key of at least 6 characters."
            textSize = 13f
        })
        trustedNumberInput = textInput("Trusted sender number, e.g. +14175551212", prefs.trustedSmsNumber, InputType.TYPE_CLASS_PHONE)
        root.addView(trustedNumberInput)
        smsKeyInput = passwordInput("SMS command key (minimum 6 characters)", prefs.smsCommandKey)
        root.addView(smsKeyInput)
        root.addView(commandSyntax())

        root.addView(label("Internet / ntfy commands"))
        ntfyCommandsCheck = CheckBox(this).apply {
            text = "Enable ntfy Internet commands"
            isChecked = prefs.ntfyCommandsEnabled
        }
        root.addView(ntfyCommandsCheck)
        ntfyIncidentCheck = CheckBox(this).apply {
            text = "Send incident notifications to ntfy"
            isChecked = prefs.ntfyIncidentEnabled
        }
        root.addView(ntfyIncidentCheck)
        root.addView(TextView(this).apply {
            text = "Uses an HTTPS ntfy JSON stream while enabled. A visible foreground-service notification remains active. SMS continues to work independently."
            textSize = 13f
        })
        ntfyServerInput = textInput("ntfy server, e.g. https://ntfy.sh", prefs.ntfyServer, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        root.addView(ntfyServerInput)
        ntfyCommandTopicInput = textInput("Command topic", prefs.ntfyCommandTopic)
        root.addView(ntfyCommandTopicInput)
        ntfyResponseTopicInput = textInput("Response topic (optional)", prefs.ntfyResponseTopic)
        root.addView(ntfyResponseTopicInput)
        ntfyIncidentTopicInput = textInput("Incident-alert topic (optional)", prefs.ntfyIncidentTopic)
        root.addView(ntfyIncidentTopicInput)
        ntfyTokenInput = passwordInput("ntfy Bearer token (optional for public topics)", prefs.ntfyToken)
        root.addView(ntfyTokenInput)
        ntfyKeyInput = passwordInput("ntfy command key (minimum 6 characters)", prefs.ntfyCommandKey)
        root.addView(ntfyKeyInput)
        root.addView(commandSyntax())

        root.addView(label("Email / SMTP incident alerts"))
        emailEnabledCheck = CheckBox(this).apply {
            text = "Enable SMTP email alerts"
            isChecked = prefs.emailEnabled
        }
        root.addView(emailEnabledCheck)
        root.addView(TextView(this).apply {
            text = "Direct SMTP login. Use an app password when your provider requires one. Implicit TLS is normally port 465; unchecked uses STARTTLS, normally port 587."
            textSize = 13f
        })
        smtpHostInput = textInput("SMTP host", prefs.smtpHost)
        root.addView(smtpHostInput)
        smtpPortInput = textInput("SMTP port", prefs.smtpPort.toString(), InputType.TYPE_CLASS_NUMBER)
        root.addView(smtpPortInput)
        smtpImplicitTlsCheck = CheckBox(this).apply {
            text = "Use implicit TLS (usually port 465); unchecked = STARTTLS"
            isChecked = prefs.smtpImplicitTls
        }
        root.addView(smtpImplicitTlsCheck)
        smtpUsernameInput = textInput("SMTP username", prefs.smtpUsername)
        root.addView(smtpUsernameInput)
        smtpPasswordInput = passwordInput("SMTP password / app password", prefs.smtpPassword)
        root.addView(smtpPasswordInput)
        smtpFromInput = textInput("From email address (blank = username)", prefs.smtpFrom, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        root.addView(smtpFromInput)
        smtpRecipientInput = textInput("Alert recipient email address", prefs.smtpRecipient, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        root.addView(smtpRecipientInput)

        root.addView(button("Save settings") { saveSettings() })
        root.addView(button("Send SMTP test email") {
            saveSettings(showToast = false)
            thread(name = "icu-email-test") {
                val result = SmtpClient.sendTest(this)
                runOnUiThread {
                    Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show()
                    refreshStatus()
                }
            }
        })

        root.addView(label("Tests and actions"))
        root.addView(button("Publish incident photos to iCaughtU album") {
            thread(name = "icu-gallery-sync-manual") {
                val result = MediaPhotoPublisher.syncIncidentPhotos(this)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Gallery sync: ${result.published} added, ${result.alreadyPublished} already present, ${result.failed} failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
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
                Toast.makeText(this, "Camera permission missing; testing incident delivery without photo", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(button("Start ntfy command listener") {
            saveSettings(showToast = false)
            runCatching { NtfyCommandService.start(this) }
                .onSuccess { Toast.makeText(this, "ntfy listener start requested", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this, "ntfy listener failed: ${it.message}", Toast.LENGTH_LONG).show() }
        })
        root.addView(button("Stop ntfy command listener") {
            NtfyCommandService.stop(this)
            Toast.makeText(this, "ntfy listener stopped", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Stop lost-device alarm") { stopService(Intent(this, AlarmService::class.java)) })
        root.addView(button("Lock device now") {
            if (dpm.isAdminActive(admin)) dpm.lockNow()
            else Toast.makeText(this, "Activate Device Admin first", Toast.LENGTH_SHORT).show()
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

    private fun saveSettings(showToast: Boolean = true) {
        val threshold = thresholdInput.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 1
        val smtpPort = smtpPortInput.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: if (smtpImplicitTlsCheck.isChecked) 465 else 587

        prefs.armed = armedSwitch.isChecked
        prefs.threshold = threshold
        prefs.includePhoto = includePhotoCheck.isChecked
        prefs.includeLocation = includeLocationCheck.isChecked
        prefs.webhookEnabled = webhookEnabledCheck.isChecked
        prefs.webhookUrl = webhookInput.text.toString()
        prefs.webhookSecret = secretInput.text.toString()
        prefs.smsCommandsEnabled = smsEnabledCheck.isChecked
        prefs.trustedSmsNumber = trustedNumberInput.text.toString()
        prefs.smsCommandKey = smsKeyInput.text.toString()
        prefs.ntfyCommandsEnabled = ntfyCommandsCheck.isChecked
        prefs.ntfyIncidentEnabled = ntfyIncidentCheck.isChecked
        prefs.ntfyServer = ntfyServerInput.text.toString()
        prefs.ntfyCommandTopic = ntfyCommandTopicInput.text.toString()
        prefs.ntfyResponseTopic = ntfyResponseTopicInput.text.toString()
        prefs.ntfyIncidentTopic = ntfyIncidentTopicInput.text.toString()
        prefs.ntfyToken = ntfyTokenInput.text.toString()
        prefs.ntfyCommandKey = ntfyKeyInput.text.toString()
        prefs.emailEnabled = emailEnabledCheck.isChecked
        prefs.smtpHost = smtpHostInput.text.toString()
        prefs.smtpPort = smtpPort
        prefs.smtpImplicitTls = smtpImplicitTlsCheck.isChecked
        prefs.smtpUsername = smtpUsernameInput.text.toString()
        prefs.smtpPassword = smtpPasswordInput.text.toString()
        prefs.smtpFrom = smtpFromInput.text.toString()
        prefs.smtpRecipient = smtpRecipientInput.text.toString()

        thresholdInput.setText(threshold.toString())
        smtpPortInput.setText(smtpPort.toString())

        if (prefs.ntfyCommandsEnabled) {
            runCatching { NtfyCommandService.start(this) }
                .onFailure { Toast.makeText(this, "ntfy listener failed: ${it.message}", Toast.LENGTH_LONG).show() }
        } else {
            NtfyCommandService.stop(this)
        }
        if (showToast) Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun requestSmsPermissions() {
        requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS), REQUEST_SMS_PERMISSIONS)
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 28) permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
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
        val receiveSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val sendSms = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        statusText.text = buildString {
            append("Mode: ").append(if (owner) "DEVICE OWNER (full)" else if (adminActive) "DEVICE ADMIN (limited)" else "inactive")
            append("\nArmed: ").append(prefs.armed).append("  |  threshold: ").append(prefs.threshold)
            append("\nCamera permission: ").append(camera)
            append("\nFine location: ").append(fineLocation).append("  |  background location: ").append(backgroundLocation)
            append("\nSMS commands: ").append(prefs.smsCommandsEnabled && receiveSms && sendSms && prefs.trustedSmsNumber.isNotBlank() && prefs.smsCommandKey.length >= 6)
            append("\nntfy commands: ").append(prefs.ntfyCommandsEnabled && prefs.ntfyCommandTopic.isNotBlank() && prefs.ntfyCommandKey.length >= 6)
            append("\nntfy incident alerts: ").append(prefs.ntfyIncidentEnabled)
            append("\nEmail alerts: ").append(prefs.emailEnabled)
            append("\nWebhook: ").append(prefs.webhookEnabled)
            if (adminActive && !owner) {
                append("\n\nLimited mode can detect failed unlocks, log them, lock the device, and process configured network deliveries. Automatic lock-screen camera capture requires Device Owner mode on modern Android.")
            }
        }
        logText.text = IncidentStore.readRecent(this)
    }

    private fun commandSyntax() = TextView(this).apply {
        text = "Syntax: ICU <key> STATUS | LOCATE | LOCK | ARM ON | ARM OFF | ALARM | STOPALARM"
        textSize = 12f
        setTextIsSelectable(true)
    }

    private fun textInput(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        this.hint = hint
        inputType = type
        setText(value)
    }

    private fun passwordInput(hint: String, value: String) = textInput(
        hint,
        value,
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    )

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

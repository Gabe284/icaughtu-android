package com.example.icaughtuandroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.IncidentStore
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.security.AppLockActivity
import com.example.icaughtuandroid.security.AppSecurity
import com.example.icaughtuandroid.service.IncidentCaptureService
import com.example.icaughtuandroid.service.IncidentJobService
import com.example.icaughtuandroid.util.PermissionHealthMonitor
import com.example.icaughtuandroid.util.NotificationUtil
import com.example.icaughtuandroid.util.PermissionManager

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var status: TextView
    private lateinit var incidents: TextView
    private lateinit var armed: Switch
    private var permissionPromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, GuardAdminReceiver::class.java)
        NotificationUtil.ensureChannels(this)
        if (AppSecurity.mode(this) != AppSecurity.MODE_NONE) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContentView(buildUi())
        PermissionHealthMonitor.schedule(this)
        PermissionHealthMonitor.checkNow(this)
        enforceAppLock()
        requestMissingPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        enforceAppLock()
        refresh()
        PermissionHealthMonitor.checkNow(this)
        if (!permissionPromptedThisSession) requestMissingPermissionsIfNeeded()
    }

    private fun enforceAppLock() {
        if (!AppSecurity.isUnlocked(this)) {
            startActivity(Intent(this, AppLockActivity::class.java))
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(8, 14, 24)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "aCaughtU"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(TextView(this).apply {
            text = "Device protection"
            textSize = 14f
            setTextColor(Color.rgb(148, 163, 184))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(20))
        })

        status = card("")
        root.addView(status)

        armed = Switch(this).apply {
            text = "Protection armed"
            isChecked = prefs.armed
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setOnCheckedChangeListener { _, value ->
                prefs.armed = value
                refresh()
            }
        }
        root.addView(armed)

        root.addView(primaryButton("Capture test incident") {
            val camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (camera) {
                startForegroundService(
                    Intent(this, IncidentCaptureService::class.java)
                        .putExtra(IncidentCaptureService.EXTRA_ATTEMPTS, 1)
                        .putExtra(IncidentCaptureService.EXTRA_SOURCE, "manual_test")
                )
            } else {
                IncidentJobService.enqueue(this, 1, "manual_test")
                Toast.makeText(this, "Camera permission is missing", Toast.LENGTH_LONG).show()
            }
        })

        root.addView(menuButton("Settings", "Protection, SMS, media and basic behavior") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(menuButton("Advanced", "ntfy, webhook, SMTP and device-owner tools") {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        })
        root.addView(menuButton("Security", "App lock and uninstall protection") {
            startActivity(Intent(this, SecurityActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "Recent incidents"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(22), 0, dp(8))
        })
        incidents = card("")
        incidents.typeface = android.graphics.Typeface.MONOSPACE
        incidents.textSize = 12f
        incidents.setTextIsSelectable(true)
        root.addView(incidents)

        return scroll
    }

    private fun refresh() {
        val health = PermissionManager.health(this)
        val owner = dpm.isDeviceOwnerApp(packageName)
        val adminActive = dpm.isAdminActive(admin)
        status.text = buildString {
            append(if (health.ok) "✓ Protection ready" else "⚠ Action required")
            append("\n")
            append(health.summary)
            append("\nMode: ")
            append(if (owner) "Device Owner" else if (adminActive) "Device Admin" else "Inactive")
        }
        armed.isChecked = prefs.armed
        incidents.text = IncidentStore.readRecent(this).ifBlank { "No incidents recorded yet." }
    }

    private fun requestMissingPermissionsIfNeeded() {
        permissionPromptedThisSession = true
        val missing = PermissionManager.foregroundRuntimePermissions(this)
        if (missing.isNotEmpty()) {
            requestPermissions(missing, REQ_RUNTIME)
            return
        }
        continuePermissionOnboarding()
    }

    private fun continuePermissionOnboarding() {
        if (!dpm.isAdminActive(admin)) {
            AlertDialog.Builder(this)
                .setTitle("Enable Device Admin")
                .setMessage("aCaughtU needs Device Admin to receive failed-unlock events and remotely lock this device.")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Required for failed-unlock detection and device locking.")
                    })
                }
                .setNegativeButton("Later", null)
                .show()
            return
        }

        if (PermissionManager.backgroundLocationMissing(this)) {
            AlertDialog.Builder(this)
                .setTitle("Allow background location")
                .setMessage("For location reporting after a lock-screen incident, set Location permission to Allow all the time.")
                .setPositiveButton("Open app settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RUNTIME) {
            refresh()
            PermissionHealthMonitor.checkNow(this)
            continuePermissionOnboarding()
        }
    }

    private fun card(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(Color.rgb(226, 232, 240))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setBackgroundColor(Color.rgb(20, 30, 45))
    }

    private fun primaryButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun menuButton(title: String, subtitle: String, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setBackgroundColor(Color.rgb(17, 27, 42))
            val t = TextView(this@MainActivity).apply {
                text = "$title  ›"
                textSize = 18f
                setTextColor(Color.WHITE)
            }
            val s = TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.rgb(148, 163, 184))
            }
            addView(t); addView(s)
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            layoutParams = lp
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_RUNTIME = 501
    }
}

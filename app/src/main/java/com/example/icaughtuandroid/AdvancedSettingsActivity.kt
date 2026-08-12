package com.example.icaughtuandroid

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.remote.NtfyCommandService
import com.example.icaughtuandroid.security.AppSecurity
import com.example.icaughtuandroid.util.SmtpClient
import kotlin.concurrent.thread

class AdvancedSettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var webhookEnabled: CheckBox
    private lateinit var webhookUrl: EditText
    private lateinit var webhookSecret: EditText
    private lateinit var ntfyCommands: CheckBox
    private lateinit var ntfyIncidents: CheckBox
    private lateinit var ntfyServer: EditText
    private lateinit var ntfyCommandTopic: EditText
    private lateinit var ntfyResponseTopic: EditText
    private lateinit var ntfyIncidentTopic: EditText
    private lateinit var ntfyToken: EditText
    private lateinit var ntfyKey: EditText
    private lateinit var emailEnabled: CheckBox
    private lateinit var smtpHost: EditText
    private lateinit var smtpPort: EditText
    private lateinit var smtpTls: CheckBox
    private lateinit var smtpUser: EditText
    private lateinit var smtpPass: EditText
    private lateinit var smtpFrom: EditText
    private lateinit var smtpRecipient: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (AppSecurity.mode(this) != AppSecurity.MODE_NONE) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(34))
            setBackgroundColor(Color.rgb(8, 14, 24))
        }
        root.addView(title("Advanced"))

        root.addView(section("Webhook"))
        webhookEnabled = check("Enable HTTPS webhook", prefs.webhookEnabled); root.addView(webhookEnabled)
        webhookUrl = input("Webhook URL", prefs.webhookUrl); root.addView(webhookUrl)
        webhookSecret = password("Webhook HMAC secret", prefs.webhookSecret); root.addView(webhookSecret)

        root.addView(section("ntfy"))
        ntfyCommands = check("Enable ntfy commands", prefs.ntfyCommandsEnabled); root.addView(ntfyCommands)
        ntfyIncidents = check("Send incidents to ntfy", prefs.ntfyIncidentEnabled); root.addView(ntfyIncidents)
        ntfyServer = input("Server", prefs.ntfyServer); root.addView(ntfyServer)
        ntfyCommandTopic = input("Command topic", prefs.ntfyCommandTopic); root.addView(ntfyCommandTopic)
        ntfyResponseTopic = input("Response topic", prefs.ntfyResponseTopic); root.addView(ntfyResponseTopic)
        ntfyIncidentTopic = input("Incident topic", prefs.ntfyIncidentTopic); root.addView(ntfyIncidentTopic)
        ntfyToken = password("Bearer token", prefs.ntfyToken); root.addView(ntfyToken)
        ntfyKey = password("Command key", prefs.ntfyCommandKey); root.addView(ntfyKey)

        root.addView(section("Email / SMTP"))
        emailEnabled = check("Enable email alerts", prefs.emailEnabled); root.addView(emailEnabled)
        smtpHost = input("SMTP host", prefs.smtpHost); root.addView(smtpHost)
        smtpPort = input("SMTP port", prefs.smtpPort.toString(), InputType.TYPE_CLASS_NUMBER); root.addView(smtpPort)
        smtpTls = check("Implicit TLS (usually port 465)", prefs.smtpImplicitTls); root.addView(smtpTls)
        smtpUser = input("SMTP username", prefs.smtpUsername); root.addView(smtpUser)
        smtpPass = password("SMTP password / app password", prefs.smtpPassword); root.addView(smtpPass)
        smtpFrom = input("From address", prefs.smtpFrom); root.addView(smtpFrom)
        smtpRecipient = input("Recipient", prefs.smtpRecipient); root.addView(smtpRecipient)

        root.addView(Button(this).apply {
            text="Save advanced settings";isAllCaps=false;setOnClickListener{save()}
        })
        root.addView(Button(this).apply {
            text="Send SMTP test";isAllCaps=false
            setOnClickListener {
                save(false)
                thread {
                    val r=SmtpClient.sendTest(this@AdvancedSettingsActivity)
                    runOnUiThread{Toast.makeText(this@AdvancedSettingsActivity,r.detail,Toast.LENGTH_LONG).show()}
                }
            }
        })

        val dpm=getSystemService(DevicePolicyManager::class.java)
        val admin=ComponentName(this,GuardAdminReceiver::class.java)
        root.addView(section("Device management"))
        root.addView(TextView(this).apply{
            text=if(dpm.isDeviceOwnerApp(packageName))
                "Device Owner mode is active. Full lock-screen capture and uninstall blocking are available."
            else if(dpm.isAdminActive(admin))
                "Device Admin mode is active. Uninstall blocking requires Device Owner mode."
            else "Device administration is inactive."
            setTextColor(Color.LTGRAY)
            setTextIsSelectable(true)
        })
        setContentView(ScrollView(this).apply{addView(root)})
    }

    private fun save(show:Boolean=true){
        prefs.webhookEnabled=webhookEnabled.isChecked
        prefs.webhookUrl=webhookUrl.text.toString()
        prefs.webhookSecret=webhookSecret.text.toString()
        prefs.ntfyCommandsEnabled=ntfyCommands.isChecked
        prefs.ntfyIncidentEnabled=ntfyIncidents.isChecked
        prefs.ntfyServer=ntfyServer.text.toString()
        prefs.ntfyCommandTopic=ntfyCommandTopic.text.toString()
        prefs.ntfyResponseTopic=ntfyResponseTopic.text.toString()
        prefs.ntfyIncidentTopic=ntfyIncidentTopic.text.toString()
        prefs.ntfyToken=ntfyToken.text.toString()
        prefs.ntfyCommandKey=ntfyKey.text.toString()
        prefs.emailEnabled=emailEnabled.isChecked
        prefs.smtpHost=smtpHost.text.toString()
        prefs.smtpPort=smtpPort.text.toString().toIntOrNull()?.coerceIn(1,65535) ?: 587
        prefs.smtpImplicitTls=smtpTls.isChecked
        prefs.smtpUsername=smtpUser.text.toString()
        prefs.smtpPassword=smtpPass.text.toString()
        prefs.smtpFrom=smtpFrom.text.toString()
        prefs.smtpRecipient=smtpRecipient.text.toString()
        if(prefs.ntfyCommandsEnabled) runCatching{NtfyCommandService.start(this)}
        else NtfyCommandService.stop(this)
        if(show) Toast.makeText(this,"Advanced settings saved",Toast.LENGTH_SHORT).show()
    }

    private fun title(v:String)=TextView(this).apply{text=v;textSize=28f;setTextColor(Color.WHITE)}
    private fun section(v:String)=TextView(this).apply{text=v;textSize=18f;setTextColor(Color.WHITE);setPadding(0,dp(18),0,dp(6))}
    private fun check(v:String,b:Boolean)=CheckBox(this).apply{text=v;isChecked=b;setTextColor(Color.WHITE)}
    private fun input(h:String,v:String,t:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{hint=h;setText(v);inputType=t;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)}
    private fun password(h:String,v:String)=input(h,v,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

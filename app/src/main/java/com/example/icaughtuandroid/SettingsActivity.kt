package com.example.icaughtuandroid

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import com.example.icaughtuandroid.data.Prefs
import com.example.icaughtuandroid.security.AppSecurity
import com.example.icaughtuandroid.util.MediaPhotoPublisher
import kotlin.concurrent.thread

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var threshold: EditText
    private lateinit var photo: CheckBox
    private lateinit var location: CheckBox
    private lateinit var sms: CheckBox
    private lateinit var smsNumber: EditText
    private lateinit var smsKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (AppSecurity.mode(this) != AppSecurity.MODE_NONE) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
            setBackgroundColor(Color.rgb(8, 14, 24))
        }
        root.addView(title("Settings"))

        threshold = input("Failed attempts before incident", prefs.threshold.toString(), InputType.TYPE_CLASS_NUMBER)
        photo = check("Capture incident photo", prefs.includePhoto)
        location = check("Include location", prefs.includeLocation)
        sms = check("Enable SMS commands", prefs.smsCommandsEnabled)
        smsNumber = input("Trusted SMS number", prefs.trustedSmsNumber, InputType.TYPE_CLASS_PHONE)
        smsKey = input("SMS command key", prefs.smsCommandKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        root.addView(section("Protection"))
        root.addView(threshold); root.addView(photo); root.addView(location)
        root.addView(section("SMS commands"))
        root.addView(sms); root.addView(smsNumber); root.addView(smsKey)
        root.addView(Button(this).apply {
            text = "Save"; isAllCaps = false
            setOnClickListener { save() }
        })
        root.addView(Button(this).apply {
            text = "Publish incident photos to aCaughtU album"; isAllCaps = false
            setOnClickListener {
                thread {
                    val r = MediaPhotoPublisher.syncIncidentPhotos(this@SettingsActivity)
                    runOnUiThread {
                        Toast.makeText(this@SettingsActivity,
                            "${r.published} added, ${r.alreadyPublished} already present, ${r.failed} failed",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun save() {
        prefs.threshold = threshold.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 1
        prefs.includePhoto = photo.isChecked
        prefs.includeLocation = location.isChecked
        prefs.smsCommandsEnabled = sms.isChecked
        prefs.trustedSmsNumber = smsNumber.text.toString()
        prefs.smsCommandKey = smsKey.text.toString()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun title(v: String)=TextView(this).apply{ text=v;textSize=28f;setTextColor(Color.WHITE);setPadding(0,0,0,dp(18)) }
    private fun section(v:String)=TextView(this).apply{ text=v;textSize=18f;setTextColor(Color.WHITE);setPadding(0,dp(18),0,dp(6)) }
    private fun check(v:String,b:Boolean)=CheckBox(this).apply{ text=v;isChecked=b;setTextColor(Color.WHITE) }
    private fun input(h:String,v:String,t:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{ hint=h;setText(v);inputType=t;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

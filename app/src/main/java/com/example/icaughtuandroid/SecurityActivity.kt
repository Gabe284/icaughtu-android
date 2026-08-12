package com.example.icaughtuandroid

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import com.example.icaughtuandroid.admin.GuardAdminReceiver
import com.example.icaughtuandroid.security.AppSecurity

class SecurityActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var uninstall: CheckBox
    private lateinit var state: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm=getSystemService(DevicePolicyManager::class.java)
        admin=ComponentName(this,GuardAdminReceiver::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(24),dp(20),dp(34))
            setBackgroundColor(Color.rgb(8,14,24))
        }
        root.addView(TextView(this).apply{text="Security";textSize=28f;setTextColor(Color.WHITE)})
        state=TextView(this).apply{setTextColor(Color.LTGRAY);setPadding(0,dp(8),0,dp(16))}
        root.addView(state)

        root.addView(Button(this).apply{
            text="Use device lock";isAllCaps=false
            setOnClickListener{
                val km=getSystemService(KeyguardManager::class.java)
                if(!km.isDeviceSecure){
                    Toast.makeText(this@SecurityActivity,"Configure a secure device PIN/password/pattern first.",Toast.LENGTH_LONG).show()
                } else {
                    AppSecurity.setMode(this@SecurityActivity,AppSecurity.MODE_DEVICE)
                    AppSecurity.lockNow()
                    applyUninstallPolicyIfNeeded()
                    refresh()
                }
            }
        })
        root.addView(Button(this).apply{
            text="Use passphrase";isAllCaps=false
            setOnClickListener{promptPassphrase()}
        })
        root.addView(Button(this).apply{
            text="Disable app lock";isAllCaps=false
            setOnClickListener{
                AppSecurity.setMode(this@SecurityActivity,AppSecurity.MODE_NONE)
                if (dpm.isDeviceOwnerApp(packageName) && !uninstall.isChecked) {
                    runCatching { dpm.setUninstallBlocked(admin, packageName, false) }
                }
                refresh()
            }
        })

        uninstall=CheckBox(this).apply{
            text="Prevent uninstallation when Device Owner"
            setTextColor(Color.WHITE)
            isChecked=AppSecurity.preventUninstall(this@SecurityActivity)
            isEnabled=dpm.isDeviceOwnerApp(packageName)
            setOnCheckedChangeListener{_,checked->
                AppSecurity.setPreventUninstall(this@SecurityActivity, checked)
                applyUninstallPolicyIfNeeded()
                refresh()
            }
        }
        root.addView(uninstall)
        root.addView(TextView(this).apply{
            text="Android only allows reliable uninstall blocking to a Device Owner/Profile Owner. Device Admin alone cannot guarantee it."
            setTextColor(Color.GRAY)
            textSize=13f
        })
        setContentView(ScrollView(this).apply{addView(root)})
        refresh()
    }

    private fun promptPassphrase(){
        val input=EditText(this).apply{
            hint="At least 8 characters"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Set aCaughtU passphrase")
            .setView(input)
            .setPositiveButton("Set"){_,_->
                val chars=input.text.toString().toCharArray()
                if(chars.size<8){
                    Toast.makeText(this,"Passphrase must contain at least 8 characters",Toast.LENGTH_LONG).show()
                } else {
                    AppSecurity.setPassphrase(this,chars)
                    AppSecurity.setMode(this,AppSecurity.MODE_PASSPHRASE)
                    AppSecurity.lockNow()
                    applyUninstallPolicyIfNeeded()
                    refresh()
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun applyUninstallPolicyIfNeeded(){
        if(!dpm.isDeviceOwnerApp(packageName)) return
        val desired=AppSecurity.preventUninstall(this) && AppSecurity.mode(this)!=AppSecurity.MODE_NONE
        runCatching{dpm.setUninstallBlocked(admin,packageName,desired)}
            .onFailure{Toast.makeText(this,"Could not change uninstall policy: ${it.message}",Toast.LENGTH_LONG).show()}
    }

    private fun refresh(){
        val owner=dpm.isDeviceOwnerApp(packageName)
        state.text="App lock: ${AppSecurity.mode(this)}\nDevice Owner: $owner\nUninstall protection: ${
            if(owner) runCatching{dpm.isUninstallBlocked(admin,packageName)}.getOrDefault(false) else "unavailable"
        }"
        uninstall.isEnabled=owner
        uninstall.isChecked=AppSecurity.preventUninstall(this@SecurityActivity)
    }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

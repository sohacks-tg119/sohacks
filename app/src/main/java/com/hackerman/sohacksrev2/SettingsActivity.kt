package com.hackerman.sohacksrev2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchAdvancedMode: SwitchMaterial
    private lateinit var switchKeepScreenOn: SwitchMaterial
    private lateinit var switchNewGui: SwitchMaterial
    private lateinit var switchWattBadge: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchAdvancedMode = findViewById(R.id.switchSettingsAdvancedMode)
        switchKeepScreenOn = findViewById(R.id.switchSettingsKeepScreenOn)
        switchNewGui = findViewById(R.id.switchSettingsNewGui)
        switchWattBadge = findViewById(R.id.switchSettingsWattBadge)

        val prefs = getSharedPreferences(AppPreferences.NAME, Context.MODE_PRIVATE)
        switchAdvancedMode.isChecked = prefs.getBoolean(AppPreferences.KEY_ADVANCED_OPTIONS, false)
        switchKeepScreenOn.isChecked = prefs.getBoolean(AppPreferences.KEY_KEEP_SCREEN_ON, false)
        switchNewGui.isChecked = prefs.getBoolean(AppPreferences.KEY_NEW_GUI, true)
        switchWattBadge.isChecked = prefs.getBoolean(AppPreferences.KEY_WATT_BADGE, false)

        fun updateWattBadgeAvailability() {
            switchWattBadge.isEnabled = switchNewGui.isChecked
            switchWattBadge.alpha = if (switchNewGui.isChecked) 1f else 0.45f
        }
        updateWattBadgeAvailability()

        switchNewGui.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(AppPreferences.KEY_NEW_GUI, enabled).apply()
            updateWattBadgeAvailability()
        }
        switchWattBadge.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(AppPreferences.KEY_WATT_BADGE, enabled).apply()
        }
        switchAdvancedMode.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(AppPreferences.KEY_ADVANCED_OPTIONS, enabled).apply()
        }
        switchKeepScreenOn.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(AppPreferences.KEY_KEEP_SCREEN_ON, enabled).apply()
            AppPreferences.applyKeepScreenOn(this)
        }

        findViewById<MaterialButton>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnQuickBubbleSettings).setOnClickListener {
            startActivity(Intent(this, QuickBubbleSettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnOpenSourceLicenses).setOnClickListener {
            startActivity(Intent(this, OpenSourceLicensesActivity::class.java))
        }
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.tvSettingsVersion).text = "Version $versionName"
        findViewById<TextView>(R.id.tvSettingsAdvancedDescription).text =
            "Zeigt die erweiterten Modi und manuellen Steueroptionen in der App an."
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
    }
}

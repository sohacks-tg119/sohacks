package com.hackerman.sohacksrev2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class OpenSourceLicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_source_licenses)

        findViewById<MaterialButton>(R.id.btnLicenseApache).setOnClickListener {
            showBundledLicense("Apache License 2.0", R.raw.apache_license_2_0)
        }
        findViewById<MaterialButton>(R.id.btnLicenseGpl).setOnClickListener {
            showBundledLicense("GNU General Public License v3", R.raw.gpl_3_0)
        }
        findViewById<MaterialButton>(R.id.btnLicenseOsm).setOnClickListener {
            openUrl("https://www.openstreetmap.org/copyright")
        }
        findViewById<MaterialButton>(R.id.btnLicenseSource).setOnClickListener {
            openUrl("https://github.com/sohacks-tg119/sohacks")
        }
        findViewById<MaterialButton>(R.id.btnLicensesBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
    }

    private fun showBundledLicense(title: String, rawResource: Int) {
        val licenseText = resources.openRawResource(rawResource)
            .bufferedReader()
            .use { it.readText() }
        val textView = TextView(this).apply {
            text = licenseText
            setTextColor(getColor(R.color.colorOnSurface))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(20.dp, 12.dp, 20.dp, 20.dp)
        }
        val scrollView = NestedScrollView(this).apply {
            addView(
                textView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Kein Browser verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

package com.hackerman.sohacksrev2

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/** Gemeinsame App-Einstellungen, die auf mehreren Screens gelten. */
object AppPreferences {
    const val NAME = "BLE_Prefs"
    const val KEY_ADVANCED_OPTIONS = "advanced_options_enabled"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on_enabled"
    // Der gespeicherte Key bleibt fuer bestehende Installationen kompatibel.
    const val KEY_NEW_GUI = "easy_mode_enabled"
    const val KEY_WATT_BADGE = "watt_badge_enabled"
    const val KEY_MODEL_ID = "model_id"
    const val KEY_DEVICE_ADDRESS = "device_address"

    fun applyKeepScreenOn(activity: Activity) {
        val enabled = activity
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_ON, false)

        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

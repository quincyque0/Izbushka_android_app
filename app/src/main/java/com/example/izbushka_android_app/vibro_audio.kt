package com.example.izbushka_android_app

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Vibrator
import android.os.Build
import android.os.VibrationEffect

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    companion object {
        private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
    }

    fun isSoundsEnabled(): Boolean = prefs.getBoolean(KEY_SOUNDS_ENABLED, true)
    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    fun getSensitivity(): Int = prefs.getInt(KEY_SENSITIVITY, 50)

    fun setSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun setSensitivity(value: Int) {
        prefs.edit().putInt(KEY_SENSITIVITY, value).apply()
    }

    fun getSharedPreferences(): SharedPreferences = prefs

    fun playSound() {
        if (isSoundsEnabled()) {
            try {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
            } catch (e: Exception) {
            }
        }
    }

    fun vibrate(duration: Long = 100) {
        if (isVibrationEnabled() && vibrator?.hasVibrator() == true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(duration)
                }
            } catch (e: Exception) {
            }
        }
    }
}
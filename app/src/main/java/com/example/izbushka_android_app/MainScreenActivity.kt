package com.example.izbushka_android_app

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainScreenActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var joystickOuter: View
    private lateinit var joystickKnob: View
    private var joystickOuterRadius = 0f
    private var joystickKnobRadius = 0f
    private var maxDistance = 0f
    private var activePointerId = -1
    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)

        if (!settingsManager.isSoundsEnabled()) {
            volumeControlStream = AudioManager.STREAM_MUSIC
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main_screen)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupButtonLeft()
        setupButtonRight()
        setupButtonRules()
        setupButtonAboveJoystick()
        setupButtonAboveMain()
        setupAutoControlSwitch()
        setupJoystick()
    }

    private fun setupButtonLeft() {
        val buttonLeft = findViewById<LinearLayout>(R.id.buttonLeft)
        buttonLeft.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            showParametersDialog()
        }
    }

    private fun setupButtonRight() {
        val buttonRight = findViewById<LinearLayout>(R.id.buttonRight)
        buttonRight.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            finish()
        }
    }

    private fun setupButtonRules() {
        val buttonRules = findViewById<LinearLayout>(R.id.buttonRules)
        buttonRules.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            Toast.makeText(this, "Правила", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtonAboveJoystick() {
        val buttonAboveJoystick = findViewById<LinearLayout>(R.id.buttonAboveJoystick)
        buttonAboveJoystick.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            Toast.makeText(this, "Emoji", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtonAboveMain() {
        val buttonAboveMain = findViewById<LinearLayout>(R.id.buttonAboveMain)
        buttonAboveMain.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            Toast.makeText(this, "Микрофон", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoControlSwitch() {
        val autoControlSwitch = findViewById<SwitchCompat>(R.id.autoControlSwitch)
        autoControlSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "Авто управление включено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Авто управление выключено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupJoystick() {
        joystickOuter = findViewById(R.id.joystickOuter)
        joystickKnob = findViewById(R.id.joystickKnob)

        joystickOuter.post {
            joystickOuterRadius = joystickOuter.width / 2f
            joystickKnobRadius = joystickKnob.width / 2f
            maxDistance = joystickOuterRadius - joystickKnobRadius
        }

        joystickKnob.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    activePointerId = event.getPointerId(0)
                    handleJoystickDrag(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex != -1) {
                            handleJoystickDrag(event, pointerIndex)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    resetJoystick()
                    activePointerId = -1
                    sendJoystickData(0f, 0f)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleJoystickDrag(event: MotionEvent, pointerIndex: Int = 0) {
        val touchX = event.getX(pointerIndex)
        val touchY = event.getY(pointerIndex)

        var dx = touchX - joystickKnobRadius
        var dy = touchY - joystickKnobRadius

        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (distance > maxDistance) {
            dx = dx / distance * maxDistance
            dy = dy / distance * maxDistance
        }

        joystickKnob.translationX = dx
        joystickKnob.translationY = dy

        val normalizedX = dx / maxDistance
        val normalizedY = dy / maxDistance

        sendJoystickData(normalizedX, normalizedY)
    }

    private fun resetJoystick() {
        joystickKnob.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(100)
            .start()
    }

    private fun sendJoystickData(x: Float, y: Float) {
        val sensitivity = settingsManager.getSensitivity() / 50f
        val adjustedX = x * sensitivity
        val adjustedY = y * sensitivity

        val autoControlSwitch = findViewById<SwitchCompat>(R.id.autoControlSwitch)
        val isAutoControl = autoControlSwitch.isChecked

        if (!isAutoControl) {
            Toast.makeText(this, "X: ${String.format("%.2f", adjustedX)}, Y: ${String.format("%.2f", adjustedY)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showParametersDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_parameters)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val switchSounds = dialog.findViewById<SwitchCompat>(R.id.switchSounds)
        val switchVibration = dialog.findViewById<SwitchCompat>(R.id.switchVibration)
        val seekBarSensitivity = dialog.findViewById<SeekBar>(R.id.seekBarSensitivity)
        val editTextAddress = dialog.findViewById<EditText>(R.id.editTextAddress)

        switchSounds.isSoundEffectsEnabled = false
        switchVibration.isSoundEffectsEnabled = false
        seekBarSensitivity.isSoundEffectsEnabled = false

        switchSounds.isChecked = settingsManager.isSoundsEnabled()
        switchVibration.isChecked = settingsManager.isVibrationEnabled()
        seekBarSensitivity.progress = settingsManager.getSensitivity()

        val savedAddress = settingsManager.getSharedPreferences().getString("server_address", "")
        editTextAddress.setText(savedAddress)

        switchSounds.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSoundsEnabled(isChecked)
            val message = if (isChecked) "Звуки включены" else "Звуки выключены"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (isChecked) {
                settingsManager.playSound()
            }
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVibrationEnabled(isChecked)
            val message = if (isChecked) "Вибрация включена" else "Вибрация выключена"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (isChecked) {
                settingsManager.vibrate(50)
            }
        }

        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setSensitivity(progress)
                    if (settingsManager.isVibrationEnabled()) {
                        settingsManager.vibrate(20)
                    }
                    Toast.makeText(this@MainScreenActivity, "Чувствительность: $progress", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.setOnDismissListener {
            val address = editTextAddress.text.toString()
            settingsManager.getSharedPreferences().edit().putString("server_address", address).apply()
        }

        dialog.show()
    }
}
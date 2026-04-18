package com.example.izbushka_android_app

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class MainScreenActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var networkManager: NetworkManager
    private lateinit var joystickOuter: View
    private lateinit var joystickKnob: View
    private var joystickOuterRadius = 0f
    private var joystickKnobRadius = 0f
    private var maxDistance = 0f
    private var activePointerId = -1
    private var isDragging = false
    private var lastCommandTime = 0L
    private val commandInterval = 50L
    private lateinit var connectionStatusHandler: Handler
    private lateinit var connectionCheckRunnable: Runnable
    private var isConnected = false
    private lateinit var videoStreamView: VideoStreamView
    private lateinit var buttonVideo: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        networkManager = NetworkManager(this)

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
        setupButtonVideo()
        startSensorPolling()
        startConnectionCheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoStreamView.stopStream()
        connectionStatusHandler.removeCallbacks(connectionCheckRunnable)
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
            showRulesDialog()
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
            showEmotionDialog()
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
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCommandTime < commandInterval) {
            return
        }
        lastCommandTime = currentTime

        val sensitivity = settingsManager.getSensitivity() / 50f
        val adjustedX = x * sensitivity
        val adjustedY = y * sensitivity

        val autoControlSwitch = findViewById<SwitchCompat>(R.id.autoControlSwitch)
        val isAutoControl = autoControlSwitch.isChecked

        if (!isAutoControl) {
            networkManager.sendMotorCommand(adjustedX, adjustedY) { success, _ ->
                if (!success && isConnected) {
                    isConnected = false
                    updateConnectionStatus(false)
                } else if (success && !isConnected) {
                    isConnected = true
                    updateConnectionStatus(true)
                }
            }
        }
    }

    private fun startSensorPolling() {
        val handler = Handler()
        val runnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    updateSensorData()
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(runnable)
    }

    private fun updateSensorData() {
        networkManager.getSensorData { data ->
            if (data != null) {
                updateUIWithSensorData(data)
            }
        }
    }

    private fun updateUIWithSensorData(data: JSONObject) {
        try {
            val temperature = data.optInt("temperature", 0)
            val temperatureText = findViewById<LinearLayout>(R.id.temperatureRow)?.findViewById<TextView>(1)
            temperatureText?.text = "Температура: $temperature"

            val battery = data.optInt("battery", 0)
            val batteryText = findViewById<LinearLayout>(R.id.batteryRow)?.findViewById<TextView>(1)
            batteryText?.text = "Заряд: $battery%"

            val distance = data.optInt("distance", 0)
            val distanceText = findViewById<LinearLayout>(R.id.distanceRow)?.findViewById<TextView>(1)
            distanceText?.text = "Расстояние: ${distance}м"
        } catch (e: Exception) {
        }
    }

    private fun startConnectionCheck() {
        connectionStatusHandler = Handler()
        connectionCheckRunnable = object : Runnable {
            override fun run() {
                networkManager.pingRobot { success ->
                    if (success != isConnected) {
                        isConnected = success
                        updateConnectionStatus(success)
                    }
                }
                connectionStatusHandler.postDelayed(this, 3000)
            }
        }
        connectionStatusHandler.post(connectionCheckRunnable)
    }

    private fun updateConnectionStatus(connected: Boolean) {
        val robotImage = findViewById<ImageView>(R.id.robotImage)
        if (connected) {
            robotImage.alpha = 1f
            Toast.makeText(this, "Соединение восстановлено", Toast.LENGTH_SHORT).show()
        } else {
            robotImage.alpha = 0.5f
            Toast.makeText(this, "Потеря соединения с роботом", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmotionDialog() {
        val emotions = arrayOf("happy", "sad", "angry", "surprised", "sleepy")
        val emotionNames = arrayOf("Счастливый", "Грустный", "Злой", "Удивленный", "Сонный")

        AlertDialog.Builder(this)
            .setTitle("Выберите эмоцию")
            .setItems(emotionNames) { _, which ->
                val emotion = emotions[which]
                networkManager.sendEmotionCommand(emotion) { success, _ ->
                    if (success) {
                        Toast.makeText(this, "Эмоция: ${emotionNames[which]}", Toast.LENGTH_SHORT).show()
                        if (settingsManager.isVibrationEnabled()) {
                            settingsManager.vibrate(50)
                        }
                    } else {
                        Toast.makeText(this, "Не удалось установить эмоцию", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showRulesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Правила управления")
            .setMessage("1. Используйте джойстик для управления движением\n2. Нажмите на кнопку смайлика для смены эмоции\n3. Используйте переключатель для автономного режима\n4. Настройки доступны по кнопке слева")
            .setPositiveButton("Понятно", null)
            .show()
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
        val switchDarkTheme = dialog.findViewById<SwitchCompat>(R.id.switchDarkTheme)
        val seekBarSensitivity = dialog.findViewById<SeekBar>(R.id.seekBarSensitivity)
        val editTextAddress = dialog.findViewById<EditText>(R.id.editTextAddress)

        switchSounds.isSoundEffectsEnabled = false
        switchVibration.isSoundEffectsEnabled = false
        switchDarkTheme.isSoundEffectsEnabled = false
        seekBarSensitivity.isSoundEffectsEnabled = false

        switchSounds.isChecked = settingsManager.isSoundsEnabled()
        switchVibration.isChecked = settingsManager.isVibrationEnabled()
        switchDarkTheme.isChecked = settingsManager.isDarkThemeEnabled()
        seekBarSensitivity.progress = settingsManager.getSensitivity()

        val savedAddress = networkManager.getServerAddress()
        editTextAddress.setText(savedAddress.replace("http://", ""))

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

        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDarkThemeEnabled(isChecked)
            val message = if (isChecked) "Темная тема включена" else "Светлая тема включена"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }
            recreate()
        }

        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setSensitivity(progress)
                    if (settingsManager.isVibrationEnabled()) {
                        settingsManager.vibrate(20)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.setOnDismissListener {
            val address = editTextAddress.text.toString().trim()
            if (address.isNotEmpty()) {
                networkManager.updateServerAddress(address)
                isConnected = false
                startConnectionCheck()
            }
        }

        dialog.show()
    }

    private fun setupButtonVideo() {
        buttonVideo = findViewById(R.id.buttonVideo)
        videoStreamView = VideoStreamView(this)
        videoStreamView.setNetworkManager(networkManager)

        val root = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.root)
        root.addView(videoStreamView, androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        ))

        buttonVideo.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) {
                settingsManager.playSound()
            }
            if (settingsManager.isVibrationEnabled()) {
                settingsManager.vibrate(50)
            }

            if (videoStreamView.isStreamingActive()) {
                videoStreamView.stopStream()
                buttonVideo.alpha = 1.0f
            } else {
                videoStreamView.startStream()
                buttonVideo.alpha = 0.6f
            }
        }
    }
}
package com.example.izbushka_android_app

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
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
    private var isConnected = false
    private lateinit var videoStreamView: VideoStreamView
    private lateinit var buttonVideo: LinearLayout
    private var reconnectAttempts = 0
    private var sensorHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        networkManager = NetworkManager(this)

        val robotIp = intent.getStringExtra("robot_ip")
        val robotPort = intent.getIntExtra("robot_port", -1)

        if (robotIp != null && robotPort != -1) {
            networkManager.updateServerAddress(robotIp, robotPort)
        }

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

        networkManager.setSensorDataCallback { data ->
            runOnUiThread {
                updateUIWithSensorData(data)
            }
        }

        setupWebSocket()
        setupButtonLeft()
        setupButtonRight()
        setupButtonRules()
        setupButtonAboveJoystick()
        setupButtonAboveMain()
        setupAutoControlSwitch()
        setupJoystick()
        setupButtonVideo()
    }

    private fun setupWebSocket() {
        networkManager.setConnectionStatusCallback { connected ->
            isConnected = connected
            updateConnectionStatus(connected)
            if (connected) {
                reconnectAttempts = 0
                startSensorPolling()
                Toast.makeText(this, "WebSocket подключен", Toast.LENGTH_SHORT).show()
            } else {
                if (reconnectAttempts < 5) {
                    reconnectAttempts++
                    Handler(Looper.getMainLooper()).postDelayed({
                        networkManager.reconnectWebSocket { success, _ ->
                            if (success) isConnected = true
                        }
                    }, 3000)
                }
            }
        }

        networkManager.connectWebSocket { success, message ->
            if (success) {
                isConnected = true
                updateConnectionStatus(true)
                Log.d("WebSocket", "Connected: $message")
            } else {
                isConnected = false
                updateConnectionStatus(false)
                Log.e("WebSocket", "Failed: $message")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoStreamView.stopStream()
        sensorHandler?.removeCallbacksAndMessages(null)
        networkManager.disconnectWebSocket()
    }

    private fun setupButtonLeft() {
        val buttonLeft = findViewById<LinearLayout>(R.id.buttonLeft)
        buttonLeft.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            showParametersDialog()
        }
    }

    private fun setupButtonRight() {
        val buttonRight = findViewById<LinearLayout>(R.id.buttonRight)
        buttonRight.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            finish()
        }
    }

    private fun setupButtonRules() {
        val buttonRules = findViewById<LinearLayout>(R.id.buttonRules)
        buttonRules.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            showRulesDialog()
        }
    }

    private fun setupButtonAboveJoystick() {
        val buttonAboveJoystick = findViewById<LinearLayout>(R.id.buttonAboveJoystick)
        buttonAboveJoystick.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            showEmotionDialog()
        }
    }

    private fun setupButtonAboveMain() {
        val buttonAboveMain = findViewById<LinearLayout>(R.id.buttonAboveMain)
        buttonAboveMain.setOnClickListener {
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            Toast.makeText(this, "Микрофон", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoControlSwitch() {
        val autoControlSwitch = findViewById<SwitchCompat>(R.id.autoControlSwitch)
        autoControlSwitch.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "Авто управление включено" else "Авто управление выключено"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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

        if (!isAutoControl && networkManager.isWebSocketConnected()) {
            val angle = Math.toDegrees(Math.atan2(adjustedY.toDouble(), adjustedX.toDouble())).toFloat()
            val angleNormalized = (angle + 360) % 360

            val action = when {
                angleNormalized in 45.0..135.0 -> "move_forward"
                angleNormalized in 135.0..225.0 -> "turn_left"
                angleNormalized in 225.0..315.0 -> "move_backward"
                else -> "turn_right"
            }

            val force = Math.sqrt((adjustedX * adjustedX + adjustedY * adjustedY).toDouble()).toFloat()
            val speed = (25 + force * 175).toInt().coerceIn(25, 200)

            networkManager.sendMotorCommand(action, speed)
        }
    }

    private fun startSensorPolling() {
        sensorHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    networkManager.getSensorData()
                }
                sensorHandler?.postDelayed(this, 1000)
            }
        }
        sensorHandler?.post(runnable)
    }

    private fun updateUIWithSensorData(data: JSONObject) {
        try {
            val distanceObj = data.optJSONObject("distance")
            val distance = distanceObj?.optInt("distance_cm", 0) ?: 0

            val distanceText = findViewById<LinearLayout>(R.id.distanceRow)?.findViewById<TextView>(1)
            distanceText?.text = "Расстояние: ${distance}см"

            val gyroObj = data.optJSONObject("gyro")
            if (gyroObj != null) {
                val temperature = gyroObj.optInt("temperature", 0)
                val temperatureText = findViewById<LinearLayout>(R.id.temperatureRow)?.findViewById<TextView>(1)
                temperatureText?.text = "Температура: ${temperature}°C"
            }

            val millisObj = data.optJSONObject("millis")
            if (millisObj != null) {
                val uptime = millisObj.optLong("millis", 0) / 1000
                val uptimeText = findViewById<LinearLayout>(R.id.batteryRow)?.findViewById<TextView>(1)
                uptimeText?.text = "Время: ${uptime}с"
            }
        } catch (e: Exception) {
            Log.e("SensorData", "Error: ${e.message}")
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        val robotImage = findViewById<ImageView>(R.id.robotImage)
        if (connected) {
            robotImage.alpha = 1f
            robotImage.setColorFilter(null)
        } else {
            robotImage.alpha = 0.5f
            robotImage.setColorFilter(Color.GRAY)
        }
    }

    private fun showEmotionDialog() {
        val emotions = arrayOf("happy", "sad", "angry", "surprised", "sleepy")
        val emotionNames = arrayOf("Счастливый", "Грустный", "Злой", "Удивленный", "Сонный")

        AlertDialog.Builder(this)
            .setTitle("Выберите эмоцию")
            .setItems(emotionNames) { _, which ->
                val emotion = emotions[which]
                networkManager.sendEmotionCommand(emotion) { success ->
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
        val editTextIp = dialog.findViewById<EditText>(R.id.editTextIp)
        val editTextPort = dialog.findViewById<EditText>(R.id.editTextPort)
        val btnSaveConnection = dialog.findViewById<Button>(R.id.btnSaveConnection)

        switchSounds.isSoundEffectsEnabled = false
        switchVibration.isSoundEffectsEnabled = false
        switchDarkTheme.isSoundEffectsEnabled = false
        seekBarSensitivity.isSoundEffectsEnabled = false

        switchSounds.isChecked = settingsManager.isSoundsEnabled()
        switchVibration.isChecked = settingsManager.isVibrationEnabled()
        switchDarkTheme.isChecked = settingsManager.isDarkThemeEnabled()
        seekBarSensitivity.progress = settingsManager.getSensitivity()

        val savedIp = networkManager.getRobotIp()
        val savedPort = networkManager.getRobotPort()
        editTextIp.setText(savedIp)
        editTextPort.setText(savedPort.toString())

        switchSounds.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSoundsEnabled(isChecked)
            val message = if (isChecked) "Звуки включены" else "Звуки выключены"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (isChecked) settingsManager.playSound()
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVibrationEnabled(isChecked)
            val message = if (isChecked) "Вибрация включена" else "Вибрация выключена"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (isChecked) settingsManager.vibrate(50)
        }

        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDarkThemeEnabled(isChecked)
            val message = if (isChecked) "Темная тема включена" else "Светлая тема включена"
            Toast.makeText(this@MainScreenActivity, message, Toast.LENGTH_SHORT).show()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)
            recreate()
        }

        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setSensitivity(progress)
                    if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(20)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSaveConnection.setOnClickListener {
            val newIp = editTextIp.text.toString().trim()
            val newPort = editTextPort.text.toString().trim().toIntOrNull()

            if (newIp.isEmpty()) {
                Toast.makeText(this, "Введите IP адрес", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPort == null || newPort !in 1..65535) {
                Toast.makeText(this, "Введите корректный порт (1-65535)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            networkManager.updateServerAddress(newIp, newPort)
            isConnected = false
            updateConnectionStatus(false)
            networkManager.reconnectWebSocket { success, _ ->
                if (success) {
                    isConnected = true
                    updateConnectionStatus(true)
                    if (videoStreamView.isStreamingActive()) {
                        videoStreamView.stopStream()
                        videoStreamView.startStream()
                    }
                }
            }
            Toast.makeText(this, "Настройки сохранены: $newIp:$newPort", Toast.LENGTH_LONG).show()
            dialog.dismiss()
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
            if (settingsManager.isSoundsEnabled()) settingsManager.playSound()
            if (settingsManager.isVibrationEnabled()) settingsManager.vibrate(50)

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
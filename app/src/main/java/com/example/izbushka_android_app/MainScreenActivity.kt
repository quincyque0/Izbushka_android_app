package com.example.izbushka_android_app

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private lateinit var temperatureText: TextView
    private lateinit var batteryText: TextView
    private lateinit var distanceText: TextView

    private lateinit var microphoneButton: LinearLayout
    private lateinit var microphoneIcon: ImageView
    private var audioRecorder: AudioRecorder? = null
    private var isMicrophoneActive = false
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

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

        temperatureText = findViewById(R.id.temperatureValue)
        batteryText = findViewById(R.id.batteryValue)
        distanceText = findViewById(R.id.distanceValue)

        networkManager.setSensorDataCallback { data ->
            runOnUiThread {
                updateUIWithSensorData(data)
            }
        }

        setupVideoStream()
        setupWebSocket()
        setupButtonLeft()
        setupButtonRight()
        setupButtonRules()
        setupButtonAboveJoystick()
        setupButtonAboveMain()
        setupAutoControlSwitch()
        setupJoystick()
        setupButtonVideo()

        requestMicrophonePermission()
    }

    private fun setupVideoStream() {
        val videoContainer = findViewById<android.widget.FrameLayout>(R.id.videoContainer)
        videoStreamView = VideoStreamView(this)
        videoStreamView.setNetworkManager(networkManager)
        videoContainer.addView(videoStreamView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
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
        audioRecorder?.stopRecording()
        networkManager.disconnectVoiceWebSocket()
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
        microphoneButton = findViewById(R.id.buttonAboveMain)
        microphoneIcon = findViewById(R.id.microphoneIcon)

        microphoneButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startVoiceRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopVoiceRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            setupMicrophone()
        }
    }

    private fun setupMicrophone() {
        networkManager.connectVoiceWebSocket { success, message ->
            if (success) {
                Log.d("Voice", "Voice WebSocket connected")
            } else {
                Log.e("Voice", "Failed to connect voice WebSocket: $message")
            }
        }

        audioRecorder = AudioRecorder { audioData ->
            if (isMicrophoneActive) {
                val messageId = networkManager.getNextVoiceMessageId()
                networkManager.sendVoiceData(audioData, messageId)
            }
        }
    }

    private fun startVoiceRecording() {
        if (isMicrophoneActive) return

        isMicrophoneActive = true
        microphoneIcon.setImageResource(R.drawable.microphone_active)
        microphoneIcon.imageTintList = null
        if (settingsManager.isVibrationEnabled()) {
            settingsManager.vibrate(30)
        }

        if (!networkManager.isVoiceWebSocketConnected()) {
            networkManager.connectVoiceWebSocket { success, _ ->
                if (success) {
                    startRecordingInternal()
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка подключения голосового канала", Toast.LENGTH_SHORT).show()
                        stopVoiceRecording()
                    }
                }
            }
        } else {
            startRecordingInternal()
        }
    }

    private fun startRecordingInternal() {
        audioRecorder?.startRecording()
        Toast.makeText(this, "🎤 Запись голоса...", Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceRecording() {
        if (!isMicrophoneActive) return

        isMicrophoneActive = false
        microphoneIcon.setImageResource(R.drawable.microphone)
        microphoneIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.black))
        audioRecorder?.stopRecording()
        Toast.makeText(this, "🎤 Запись остановлена", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupMicrophone()
                } else {
                    Toast.makeText(this, "Нет разрешения на запись аудио", Toast.LENGTH_SHORT).show()
                }
            }
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

        if (!isAutoControl) {
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
            distanceText.text = "Расст: ${distance}см"

            val gyroObj = data.optJSONObject("gyro")
            if (gyroObj != null) {
                val temperature = gyroObj.optInt("temperature", 0)
                temperatureText.text = "Темп: ${temperature}°C"
            }

            val millisObj = data.optJSONObject("millis")
            if (millisObj != null) {
                val uptime = millisObj.optLong("millis", 0) / 1000
                batteryText.text = "Время: ${uptime}с"
            }
        } catch (e: Exception) {
            Log.e("SensorData", "Error: ${e.message}")
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
    }

    private fun showEmotionDialog() {
        networkManager.getAvailableEmotions { emotions ->
            val emotionList = if (emotions.isNotEmpty()) emotions else listOf("happy", "sad", "angry", "surprised", "sleepy")

            AlertDialog.Builder(this)
                .setTitle("Выберите эмоцию")
                .setItems(emotionList.toTypedArray()) { _, which ->
                    val emotion = emotionList[which]
                    networkManager.sendEmotionCommand(emotion) { success ->
                        if (success) {
                            Toast.makeText(this, "Эмоция: ${emotion}", Toast.LENGTH_SHORT).show()
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
    }

    private fun showRulesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Правила управления")
            .setMessage("1. Используйте джойстик для управления движением\n2. Нажмите на кнопку смайлика для смены эмоции\n3. Используйте переключатель для автономного режима\n4. Настройки доступны по кнопке слева\n5. Удерживайте кнопку микрофона для голосового управления")
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
            networkManager.reconnectWebSocket { success, _ ->
                if (success) {
                    isConnected = true
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

        videoStreamView.startStream()
        buttonVideo.alpha = 0.6f
    }
}
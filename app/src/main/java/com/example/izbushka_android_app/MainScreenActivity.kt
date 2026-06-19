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

    companion object {
        private const val ACTION_INTERVAL_MS = 17L
        private const val SPEED_INTERVAL_MS = 230L
        private const val SPEED_CRITICAL_INTERVAL_MS = 50L
        private const val SPEED_ZONES = 4
        private const val STOP_RETRY_MS = 100L
        private const val DEAD_ZONE = 0f
        private const val MIN_SPEED = 25
        private const val MAX_SPEED = 200
        private const val MAX_FORCE = 2.0f
    }

    private var lastSentAction: String? = null
    private var lastSentSpeed: Int? = null
    private var lastActionSendTime = 0L
    private var lastSpeedSendTime = 0L
    private var lastCriticalSpeedSendTime = 0L
    private var stopRetryHandler: Handler? = null
    private var stopRetryRunnable: Runnable? = null
    private var isConnected = false
    private lateinit var videoStreamView: VideoStreamView
    private lateinit var buttonVideo: LinearLayout
    private var reconnectAttempts = 0

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



        networkManager.setEmotionChangedCallback { emotion ->
            runOnUiThread {
                Toast.makeText(this, "Эмоция изменена: $emotion", Toast.LENGTH_SHORT).show()
            }
        }

        networkManager.setErrorCallback { message ->
            runOnUiThread {
                Toast.makeText(this, "Ошибка: $message", Toast.LENGTH_SHORT).show()
            }
        }

        setupVideoStream()
        setupWebSocket()
        setupButtonLeft()
        setupButtonRight()
        setupButtonRules()
        setupButtonAboveJoystick()
        setupAutoControlSwitch()
        setupJoystick()
        setupButtonVideo()
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
                    handleJoystickRelease()
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
        val normalizedY = -dy / maxDistance

        sendJoystickData(normalizedX, normalizedY)
    }

    private fun resetJoystick() {
        joystickKnob.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(100)
            .start()
    }


    private fun angleToAction(angleDegrees: Float): String {
        val normalized = ((angleDegrees % 360) + 360) % 360
        return when {
            normalized >= 45 && normalized < 135 -> "move_forward"
            normalized >= 135 && normalized < 225 -> "turn_left"
            normalized >= 225 && normalized < 315 -> "move_backward"
            else -> "turn_right"
        }
    }


    private fun forceToSpeed(force: Float): Int {
        val clamped = force.coerceIn(DEAD_ZONE, MAX_FORCE)
        val ratio = (clamped - DEAD_ZONE) / (MAX_FORCE - DEAD_ZONE)
        return Math.round(MIN_SPEED + ratio * (MAX_SPEED - MIN_SPEED))
    }


    private fun getSpeedZone(speed: Int): Int {
        val zoneSize = (MAX_SPEED - MIN_SPEED).toFloat() / SPEED_ZONES
        return ((speed - MIN_SPEED) / zoneSize).toInt().coerceIn(0, SPEED_ZONES - 1)
    }


    private fun cancelStopRetry() {
        stopRetryRunnable?.let { stopRetryHandler?.removeCallbacks(it) }
        stopRetryRunnable = null
    }


    private fun scheduleStopRetry() {
        cancelStopRetry()
        if (stopRetryHandler == null) {
            stopRetryHandler = Handler(Looper.getMainLooper())
        }
        stopRetryRunnable = Runnable {
            stopRetryRunnable = null
            networkManager.stopMotorsWs()
        }
        stopRetryHandler?.postDelayed(stopRetryRunnable!!, STOP_RETRY_MS)
    }


    private fun handleJoystickRelease() {
        lastSentAction = "stop"
        lastSentSpeed = 0
        networkManager.stopMotorsWs()
        scheduleStopRetry()
    }


    private fun sendJoystickData(x: Float, y: Float) {
        val autoControlSwitch = findViewById<SwitchCompat>(R.id.autoControlSwitch)
        val isAutoControl = autoControlSwitch.isChecked
        if (isAutoControl) return

        val sensitivity = settingsManager.getSensitivity() / 50f
        val adjustedX = x * sensitivity
        val adjustedY = y * sensitivity

        val angle = Math.toDegrees(Math.atan2(adjustedY.toDouble(), adjustedX.toDouble())).toFloat()
        val action = angleToAction(angle)

        val force = Math.sqrt((adjustedX * adjustedX + adjustedY * adjustedY).toDouble()).toFloat()
        val speed = forceToSpeed(force.coerceIn(0f, MAX_FORCE))

        val now = System.currentTimeMillis()
        val actionChanged = action != lastSentAction
        val speedChanged = speed != lastSentSpeed

        if (!actionChanged && !speedChanged) return

        cancelStopRetry()

        if (actionChanged) {
            val elapsed = now - lastActionSendTime
            if (elapsed >= ACTION_INTERVAL_MS) {
                networkManager.sendMotorCommandWs(action, speed)
                lastSentAction = action
                lastSentSpeed = speed
                lastActionSendTime = now
                lastSpeedSendTime = now
            }
        } else {
            val prevZone = getSpeedZone(lastSentSpeed ?: MIN_SPEED)
            val newZone = getSpeedZone(speed)
            val isCritical = newZone != prevZone

            if (isCritical) {
                val elapsed = now - lastCriticalSpeedSendTime
                if (elapsed >= SPEED_CRITICAL_INTERVAL_MS) {
                    networkManager.sendMotorCommandWs(action, speed)
                    lastSentAction = action
                    lastSentSpeed = speed
                    lastCriticalSpeedSendTime = now
                    lastSpeedSendTime = now
                }
            } else {
                val elapsed = now - lastSpeedSendTime
                if (elapsed >= SPEED_INTERVAL_MS) {
                    networkManager.sendMotorCommandWs(action, speed)
                    lastSentAction = action
                    lastSentSpeed = speed
                    lastSpeedSendTime = now
                }
            }
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
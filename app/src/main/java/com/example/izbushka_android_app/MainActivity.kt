package com.example.izbushka_android_app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothIcon: ImageView
    private lateinit var wifiIcon: ImageView
    private lateinit var waitingForConnectText: TextView
    private lateinit var waitTimerText: TextView
    private lateinit var reloadButton: LinearLayout
    private lateinit var btnToggleSettings: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var editTextIp: EditText
    private lateinit var editTextPort: EditText
    private lateinit var settingsPanel: LinearLayout
    private lateinit var networkManager: NetworkManager

    private var isWifiConnected = false
    private val targetWiFiSSID = "Izbushka"
    private var robotIpAddress = "192.168.10.1"
    private var robotPort = 80

    private var timerSeconds = 10
    private var handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var isConnecting = false

    companion object {
        private const val REQUEST_PERMISSION_CODE = 100
        private const val PREFS_NAME = "robot_connection"
        private const val KEY_ROBOT_IP = "robot_ip"
        private const val KEY_ROBOT_PORT = "robot_port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)

        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        networkManager = NetworkManager(this)

        if (!networkManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loadConnectionSettings()
        initViews()
        setupClickListeners()
        requestPermissions()

        // АВТОМАТИЧЕСКИ ЗАПУСКАЕМ ПОДКЛЮЧЕНИЕ
        handler.postDelayed({
            attemptConnection()
        }, 500)
    }

    private fun loadConnectionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        robotIpAddress = prefs.getString(KEY_ROBOT_IP, "192.168.10.1") ?: "192.168.10.1"
        robotPort = prefs.getInt(KEY_ROBOT_PORT, 80)
        networkManager.updateServerAddress(robotIpAddress, robotPort)
    }

    private fun saveConnectionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_ROBOT_IP, robotIpAddress)
            putInt(KEY_ROBOT_PORT, robotPort)
            apply()
        }
    }

    private fun initViews() {
        bluetoothIcon = findViewById(R.id.bluetoothIcon)
        wifiIcon = findViewById(R.id.wifiIcon)

        waitingForConnectText = findViewById(R.id.WaitingForConnect)
        waitTimerText = findViewById(R.id.WaitTimer)
        reloadButton = findViewById(R.id.ReloadButton)
        btnToggleSettings = findViewById(R.id.btnToggleSettings)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        editTextIp = findViewById(R.id.editTextIp)
        editTextPort = findViewById(R.id.editTextPort)
        settingsPanel = findViewById(R.id.settingsPanel)
    }

    private fun setupClickListeners() {
        reloadButton.setOnClickListener {
            if (!isConnecting) {
                attemptConnection()
            } else {
                Toast.makeText(this, "Уже выполняется подключение", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleSettings.setOnClickListener {
            if (settingsPanel.visibility == LinearLayout.VISIBLE) {
                settingsPanel.visibility = LinearLayout.GONE
                btnToggleSettings.text = "Настройки"
            } else {
                editTextIp.setText(robotIpAddress)
                editTextPort.setText(robotPort.toString())
                settingsPanel.visibility = LinearLayout.VISIBLE
                btnToggleSettings.text = "Скрыть"
            }
        }

        btnSaveSettings.setOnClickListener {
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

            robotIpAddress = newIp
            robotPort = newPort
            saveConnectionSettings()
            networkManager.updateServerAddress(robotIpAddress, robotPort)
            settingsPanel.visibility = LinearLayout.GONE
            btnToggleSettings.text = "Настройки"
            Toast.makeText(this, "Настройки сохранены: $robotIpAddress:$robotPort", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Некоторые разрешения не получены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun attemptConnection() {
        isConnecting = true
        waitingForConnectText.text = "Пытаемся подключиться"
        waitingForConnectText.setTextColor(getColor(android.R.color.black))
        waitTimerText.text = "Ожидание : 10 секунд"
        timerSeconds = 10

        isWifiConnected = false
        updateWiFiIcon()

        startConnectionTimer()
        checkWiFiConnection()
    }

    private fun checkWiFiConnection() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isWifi) {
                    val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    val currentSSID = wifiInfo.ssid.replace("\"", "")

                    if (currentSSID == targetWiFiSSID) {
                        isWifiConnected = true
                        updateWiFiIcon()
                        checkRobotReachable()
                        return
                    } else {
                        waitingForConnectText.text = "Подключитесь к Wi-Fi: $targetWiFiSSID"
                        waitingForConnectText.setTextColor(getColor(android.R.color.holo_orange_dark))
                        isWifiConnected = false
                        updateWiFiIcon()
                        checkWiFiConnectionDelayed()
                    }
                } else {
                    waitingForConnectText.text = "Нет Wi-Fi подключения"
                    waitingForConnectText.setTextColor(getColor(android.R.color.holo_red_dark))
                    isWifiConnected = false
                    updateWiFiIcon()
                    checkWiFiConnectionDelayed()
                }
            } else {
                waitingForConnectText.text = "Нет активного подключения"
                waitingForConnectText.setTextColor(getColor(android.R.color.holo_red_dark))
                isWifiConnected = false
                updateWiFiIcon()
                checkWiFiConnectionDelayed()
            }
        }
    }

    private fun checkWiFiConnectionDelayed() {
        if (isConnecting && !isWifiConnected) {
            handler.postDelayed({
                checkWiFiConnection()
            }, 2000)
        }
    }

    private fun checkRobotReachable() {
        Thread {
            try {
                val url = URL("http://$robotIpAddress:$robotPort/api/robot/ping")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 2000
                connection.readTimeout = 2000

                val json = JSONObject()
                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    handler.post {
                        onConnectedToIzbushka("Wi-Fi")
                    }
                } else if (isConnecting) {
                    handler.postDelayed({
                        if (isConnecting && isWifiConnected) {
                            checkRobotReachable()
                        }
                    }, 1000)
                }
            } catch (e: Exception) {
                if (isConnecting && isWifiConnected) {
                    handler.postDelayed({
                        checkRobotReachable()
                    }, 1000)
                }
            }
        }.start()
    }

    private fun onConnectedToIzbushka(connectionType: String) {
        if (isConnecting) {
            waitingForConnectText.text = "Подключено к Избушке"
            waitingForConnectText.setTextColor(getColor(android.R.color.holo_green_dark))
            stopTimer()
            isConnecting = false
            Toast.makeText(this, "Успешно подключено через $connectionType к $robotIpAddress:$robotPort", Toast.LENGTH_LONG).show()

            val intent = Intent(this, MainScreenActivity::class.java)
            intent.putExtra("connection_type", connectionType)
            intent.putExtra("robot_ip", robotIpAddress)
            intent.putExtra("robot_port", robotPort)
            startActivity(intent)
            finish()
        }
    }

    private fun onConnectionFailed() {
        if (isConnecting && !isWifiConnected) {
            waitTimerText.text = "Время ожидания истекло"
            waitingForConnectText.text = "Не удалось подключиться"
            waitingForConnectText.setTextColor(getColor(android.R.color.holo_red_dark))
            isConnecting = false
            Toast.makeText(this, "Не удалось подключиться к роботу $robotIpAddress:$robotPort", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateWiFiIcon() {
        if (isWifiConnected) {
            wifiIcon.imageTintList = getColorStateList(android.R.color.holo_green_dark)
            wifiIcon.alpha = 1.0f
        } else {
            wifiIcon.imageTintList = getColorStateList(android.R.color.darker_gray)
            wifiIcon.alpha = 0.5f
        }
    }

    private fun startConnectionTimer() {
        stopTimer()
        timerSeconds = 10

        timerRunnable = object : Runnable {
            override fun run() {
                if (timerSeconds > 0 && !isWifiConnected && isConnecting) {
                    waitTimerText.text = "Ожидание : $timerSeconds секунд"
                    timerSeconds--
                    handler.postDelayed(this, 1000)
                } else if (timerSeconds == 0 && !isWifiConnected && isConnecting) {
                    onConnectionFailed()
                } else if (timerSeconds > 0 && isWifiConnected && isConnecting) {
                    waitTimerText.text = "Проверка связи с роботом..."
                }
            }
        }

        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothIcon: ImageView
    private lateinit var wifiIcon: ImageView
    private lateinit var waitingForConnectText: TextView
    private lateinit var waitTimerText: TextView
    private lateinit var reloadButton: LinearLayout

    private var isWifiConnected = false
    private val targetWiFiSSID = "Izbushka"

    private var timerSeconds = 10
    private var handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var isConnecting = false

    companion object {
        private const val REQUEST_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.Main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupClickListeners()
        requestPermissions()
    }

    private fun initViews() {
        val dataLayout = findViewById<LinearLayout>(R.id.Data)
        bluetoothIcon = dataLayout.getChildAt(0) as ImageView
        wifiIcon = dataLayout.getChildAt(1) as ImageView

        waitingForConnectText = findViewById(R.id.WaitingForConnect)
        waitTimerText = findViewById(R.id.WaitTimer)
        reloadButton = findViewById(R.id.ReloadButton)
    }

    private fun setupClickListeners() {
        reloadButton.setOnClickListener {
            if (!isConnecting) {
                attemptConnection()
            } else {
                Toast.makeText(this, "Уже выполняется подключение", Toast.LENGTH_SHORT).show()
            }
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


        testCheckWiFiConnection()

        // checkWiFiConnection()
    }


    private fun testCheckWiFiConnection() {
        handler.postDelayed({
            if (isConnecting) {
                isWifiConnected = true
                updateWiFiIcon()
                onConnectedToIzbushka("Wi-Fi (тест)")
            }
        }, 3000)
    }


    private fun checkWiFiConnection() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isWifiConnected) {
                    val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    val currentSSID = wifiInfo.ssid.replace("\"", "")

                    if (currentSSID == targetWiFiSSID) {
                        onConnectedToIzbushka("Wi-Fi")
                        return
                    } else {
                        isWifiConnected = false
                    }
                }
            }
        }

        if (!isWifiConnected) {
            updateWiFiIcon()
            if (isConnecting) {
                handler.postDelayed({
                    if (isConnecting && !isWifiConnected) {
                        onConnectionFailed()
                    }
                }, 3000)
            }
        }
    }

    private fun onConnectedToIzbushka(connectionType: String) {
        if (isConnecting) {
            waitingForConnectText.text = "Подключено к Избушке"
            waitingForConnectText.setTextColor(getColor(android.R.color.holo_green_dark))
            stopTimer()
            isConnecting = false
            Toast.makeText(this, "Успешно подключено через $connectionType", Toast.LENGTH_LONG).show()

            val intent = Intent(this, MainScreenActivity::class.java)
            intent.putExtra("connection_type", connectionType)
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
            Toast.makeText(this, "Не удалось подключиться к Избушке", Toast.LENGTH_LONG).show()
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

    private fun updateBluetoothIcon() {
        bluetoothIcon.imageTintList = getColorStateList(android.R.color.darker_gray)
        bluetoothIcon.alpha = 0.5f
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
package com.example.izbushka_android_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class LoginActivity : AppCompatActivity() {

    private lateinit var networkManager: NetworkManager
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewError: TextView
    private lateinit var textViewIpPort: TextView
    private lateinit var buttonSettings: Button
    private lateinit var settingsPanel: LinearLayout
    private lateinit var editTextIp: EditText
    private lateinit var editTextPort: EditText
    private lateinit var btnSaveSettings: Button

    private var robotIp = "192.168.1.10"
    private var robotPort = 80

    companion object {
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
        setContentView(R.layout.activity_login)

        loadConnectionSettings()

        networkManager = NetworkManager(this)
        networkManager.updateServerAddress(robotIp, robotPort)

        initViews()
        setupClickListeners()

        if (networkManager.isLoggedIn()) {
            startMainActivity()
        }
    }

    private fun loadConnectionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        robotIp = prefs.getString(KEY_ROBOT_IP, "192.168.1.10") ?: "192.168.1.10"
        robotPort = prefs.getInt(KEY_ROBOT_PORT, 80)
    }

    private fun saveConnectionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_ROBOT_IP, robotIp)
            putInt(KEY_ROBOT_PORT, robotPort)
            apply()
        }
    }

    private fun initViews() {
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        progressBar = findViewById(R.id.progressBar)
        textViewError = findViewById(R.id.textViewError)
        textViewIpPort = findViewById(R.id.textViewIpPort)
        buttonSettings = findViewById(R.id.buttonSettings)
        settingsPanel = findViewById(R.id.settingsPanel)
        editTextIp = findViewById(R.id.editTextIp)
        editTextPort = findViewById(R.id.editTextPort)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        textViewIpPort.text = "Подключение к: $robotIp:$robotPort"
    }

    private fun setupClickListeners() {
        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                textViewError.text = "Введите логин и пароль"
                textViewError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            login(username, password)
        }

        buttonSettings.setOnClickListener {
            if (settingsPanel.visibility == LinearLayout.VISIBLE) {
                settingsPanel.visibility = LinearLayout.GONE
                buttonSettings.text = "Настройки"
            } else {
                editTextIp.setText(robotIp)
                editTextPort.setText(robotPort.toString())
                settingsPanel.visibility = LinearLayout.VISIBLE
                buttonSettings.text = "Скрыть"
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

            robotIp = newIp
            robotPort = newPort
            saveConnectionSettings()
            networkManager.updateServerAddress(robotIp, robotPort)
            textViewIpPort.text = "Подключение к: $robotIp:$robotPort"
            settingsPanel.visibility = LinearLayout.GONE
            buttonSettings.text = "Настройки"
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun login(username: String, password: String) {
        progressBar.visibility = android.view.View.VISIBLE
        buttonLogin.isEnabled = false
        textViewError.visibility = android.view.View.GONE

        networkManager.login(username, password) { success, message ->
            progressBar.visibility = android.view.View.GONE
            buttonLogin.isEnabled = true

            if (success) {
                Toast.makeText(this, "Добро пожаловать, $username!", Toast.LENGTH_LONG).show()
                startMainActivity()
            } else {
                textViewError.text = message
                textViewError.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainScreenActivity::class.java)
        intent.putExtra("connection_type", "Wi-Fi")
        intent.putExtra("robot_ip", robotIp)
        intent.putExtra("robot_port", robotPort)
        startActivity(intent)
        finish()
    }
}
package com.example.izbushka_android_app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {

    private lateinit var connectionTypeText: TextView
    private lateinit var btnDisconnect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        val connectionType = intent.getStringExtra("connection_type") ?: "Unknown"

        connectionTypeText = findViewById(R.id.connectionTypeText)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        connectionTypeText.text = "Подключено через: $connectionType"

        btnDisconnect.setOnClickListener {
            finish()
        }
    }
}
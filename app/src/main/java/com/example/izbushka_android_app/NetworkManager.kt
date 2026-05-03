package com.example.izbushka_android_app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class NetworkManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("robot_connection", Context.MODE_PRIVATE)
    private var serverAddress: String = "http://192.168.1.10:80"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val authManager = AuthManager(context)

    private var webSocket: WebSocket? = null
    private var isWebSocketConnected = false
    private var connectionStatusCallback: ((Boolean) -> Unit)? = null
    private var sensorDataCallback: ((JSONObject) -> Unit)? = null
    private var frameCallback: ((Bitmap?) -> Unit)? = null

    private var isStreaming = false
    private var streamThread: Thread? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    init {
        loadServerAddress()
    }

    private fun loadServerAddress() {
        val ip = sharedPreferences.getString("robot_ip", "192.168.1.10") ?: "192.168.1.10"
        val port = sharedPreferences.getInt("robot_port", 80)
        serverAddress = "http://$ip:$port"
    }

    fun updateServerAddress(ip: String, port: Int) {
        serverAddress = "http://$ip:$port"
        sharedPreferences.edit().apply {
            putString("robot_ip", ip)
            putInt("robot_port", port)
            apply()
        }
    }

    fun getRobotIp(): String = sharedPreferences.getString("robot_ip", "192.168.1.10") ?: "192.168.1.10"
    fun getRobotPort(): Int = sharedPreferences.getInt("robot_port", 80)

    fun setConnectionStatusCallback(callback: (Boolean) -> Unit) {
        connectionStatusCallback = callback
    }

    fun setSensorDataCallback(callback: (JSONObject) -> Unit) {
        sensorDataCallback = callback
    }

    fun connectWebSocket(callback: (Boolean, String) -> Unit) {
        if (isWebSocketConnected) {
            callback(true, "Already connected")
            return
        }

        val token = authManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            callback(false, "No auth token")
            return
        }

        val request = Request.Builder()
            .url("$serverAddress/ws?token=$token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isWebSocketConnected = true
                mainHandler.post {
                    connectionStatusCallback?.invoke(true)
                    callback(true, "Connected")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val response = JSONObject(text)
                    val event = response.optString("event")
                    val data = response.optJSONObject("data")

                    when (event) {
                        "sensor.data" -> {
                            if (data != null) {
                                mainHandler.post {
                                    sensorDataCallback?.invoke(data)
                                }
                            }
                        }
                        "system.connection_status" -> {
                            val connected = data?.optBoolean("connected") == true
                            mainHandler.post {
                                connectionStatusCallback?.invoke(connected)
                            }
                        }
                        "command.result" -> {
                            // Результат команды
                        }
                    }
                } catch (e: Exception) {
                    // Не JSON сообщение
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isWebSocketConnected = false
                mainHandler.post {
                    connectionStatusCallback?.invoke(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isWebSocketConnected = false
                mainHandler.post {
                    connectionStatusCallback?.invoke(false)
                    callback(false, t.message ?: "Connection failed")
                }
            }
        })
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isWebSocketConnected = false
    }

    fun reconnectWebSocket(callback: (Boolean, String) -> Unit) {
        disconnectWebSocket()
        Handler(Looper.getMainLooper()).postDelayed({
            connectWebSocket(callback)
        }, 1000)
    }

    fun login(username: String, password: String, callback: (Boolean, String) -> Unit) {
        val url = "$serverAddress/api/authorization/login"
        val json = JSONObject().apply {
            put("nickname", username)
            put("password", password)
        }

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                var token: String? = null
                val cookieHeader = connection.getHeaderField("Set-Cookie")
                if (cookieHeader != null) {
                    val regex = "token=([^;]+)".toRegex()
                    val match = regex.find(cookieHeader)
                    token = match?.groupValues?.get(1)
                }

                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK && token != null) {
                    authManager.saveTokens(token, "", "", username)
                    mainHandler.post {
                        callback(true, "Успешный вход")
                    }
                } else {
                    mainHandler.post {
                        callback(false, "Ошибка авторизации")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, e.message ?: "Ошибка соединения")
                }
            }
        }.start()
    }

    fun sendMotorCommand(action: String, speed: Int) {
        if (!isWebSocketConnected) return

        val data = JSONObject().apply {
            put("action", action)
            put("speed", speed)
            put("wait_response", false)
        }

        val json = JSONObject().apply {
            put("event", "robot.motors")
            put("data", data)
        }

        webSocket?.send(json.toString())
    }

    fun sendEmotionCommand(emotion: String, callback: ((Boolean) -> Unit)? = null) {
        if (!isWebSocketConnected) {
            callback?.invoke(false)
            return
        }

        val data = JSONObject().apply {
            put("emotion", emotion)
        }

        val json = JSONObject().apply {
            put("event", "emotion.set")
            put("data", data)
        }

        webSocket?.send(json.toString())
        callback?.invoke(true)
    }

    fun getSensorData() {
        if (!isWebSocketConnected) return

        val json = JSONObject().apply {
            put("event", "sensor.get_data")
        }
        webSocket?.send(json.toString())
    }

    fun startVideoStream(callback: (Bitmap?) -> Unit) {
        if (isStreaming) {
            stopVideoStream()
        }

        frameCallback = callback
        isStreaming = true
        val token = authManager.getAccessToken() ?: return

        streamThread = Thread {
            val url = "$serverAddress/api/webcam/stream?token=$token"

            while (isStreaming) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val bis = BufferedInputStream(inputStream)

                        while (isStreaming) {
                            var foundStart = false
                            while (!foundStart && isStreaming) {
                                val b = bis.read()
                                if (b == -1) break
                                if (b == 0xFF) {
                                    val b2 = bis.read()
                                    if (b2 == 0xD8) {
                                        foundStart = true
                                        val imageBuffer = java.io.ByteArrayOutputStream()
                                        imageBuffer.write(0xFF)
                                        imageBuffer.write(0xD8)

                                        var prev = 0xFF
                                        while (isStreaming) {
                                            val current = bis.read()
                                            if (current == -1) break
                                            imageBuffer.write(current)
                                            if (prev == 0xFF && current == 0xD9) {
                                                break
                                            }
                                            prev = current
                                        }

                                        val imageData = imageBuffer.toByteArray()
                                        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                                        if (bitmap != null) {
                                            mainHandler.post {
                                                frameCallback?.invoke(bitmap)
                                            }
                                        }
                                    }
                                }
                            }
                            if (!foundStart) break
                        }
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    if (isStreaming) {
                        Thread.sleep(1000)
                    }
                } finally {
                    connection?.disconnect()
                }
            }
        }
        streamThread?.start()
    }

    fun stopVideoStream() {
        isStreaming = false
        streamThread?.interrupt()
        streamThread = null
        frameCallback = null
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()
    fun isWebSocketConnected(): Boolean = isWebSocketConnected

    fun logout() {
        authManager.clearTokens()
        disconnectWebSocket()
    }
}
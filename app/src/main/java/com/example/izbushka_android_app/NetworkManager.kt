package com.example.izbushka_android_app

import android.content.Context
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


    private var voiceWebSocket: WebSocket? = null
    private var isVoiceWebSocketConnected = false
    private var voiceMessageId = 1L

    private var webSocket: WebSocket? = null
    private var isWebSocketConnected = false
    private var connectionStatusCallback: ((Boolean) -> Unit)? = null
    private var sensorDataCallback: ((JSONObject) -> Unit)? = null

    private var isStreaming = false
    private var streamThread: Thread? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
                val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                var token: String? = null
                val cookieHeader = connection.getHeaderField("Set-Cookie")
                if (cookieHeader != null) {
                    val regex = "token=([^;]+)".toRegex()
                    val match = regex.find(cookieHeader)
                    token = match?.groupValues?.get(1)
                }

                if (token == null && responseText.isNotEmpty()) {
                    try {
                        val jsonResponse = JSONObject(responseText)
                        token = jsonResponse.optString("token", null)
                    } catch (e: Exception) { }
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
        val direction = actionToDirection(action)
        val url = "$serverAddress/api/robot/motors/move"
        val json = JSONObject().apply {
            put("direction", direction)
            put("speed", speed.coerceIn(0, 255))
        }
        sendPostRequest(url, json.toString())
    }

    private fun actionToDirection(action: String): String {
        return when (action) {
            "move_forward" -> "forward"
            "move_backward" -> "backward"
            "turn_left" -> "left"
            "turn_right" -> "right"
            else -> "stop"
        }
    }

    fun stopMotors() {
        val url = "$serverAddress/api/robot/motors/stop"
        val json = JSONObject().apply {
            put("mode", "stop")
        }
        sendPostRequest(url, json.toString())
    }

    fun setMotorSpeed(speedLeft: Int, speedRight: Int) {
        val url = "$serverAddress/api/robot/motors/speed"
        val json = JSONObject().apply {
            put("motor_mask", 3)
            put("speed_left", speedLeft.coerceIn(0, 255))
            put("speed_right", speedRight.coerceIn(0, 255))
        }
        sendPostRequest(url, json.toString())
    }

    fun setMotorDirection(directionLeft: Int, directionRight: Int) {
        val url = "$serverAddress/api/robot/motors/direction"
        val json = JSONObject().apply {
            put("motor_mask", 3)
            put("direction_left", directionLeft)
            put("direction_right", directionRight)
        }
        sendPostRequest(url, json.toString())
    }

    fun setServoAngle(channel: Int, angle: Int) {
        val url = "$serverAddress/api/robot/servo/$channel"
        val json = JSONObject().apply {
            put("angle", angle.coerceIn(0, 180))
        }
        sendPostRequest(url, json.toString())
    }

    fun getDistance(callback: (Float?) -> Unit) {
        val url = "$serverAddress/api/robot/sensors/distance"
        sendGetRequest(url) { response ->
            try {
                val json = JSONObject(response)
                callback(json.optDouble("distance_cm", -1.0).toFloat())
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    fun getGyroData(callback: (JSONObject?) -> Unit) {
        val url = "$serverAddress/api/robot/sensors/gyro"
        sendGetRequest(url) { response ->
            try {
                val json = JSONObject(response)
                callback(json)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    fun getMillis(callback: (Long?) -> Unit) {
        val url = "$serverAddress/api/robot/sensors/millis"
        sendGetRequest(url) { response ->
            try {
                val json = JSONObject(response)
                callback(json.optLong("millis", -1))
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    fun getAllSensorsData(callback: (JSONObject?) -> Unit) {
        Thread {
            val result = JSONObject()

            try {
                val url = URL("$serverAddress/api/robot/sensors/distance")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.addRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                conn.connectTimeout = 2000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    result.put("distance", json)
                }
                conn.disconnect()
            } catch (e: Exception) { }

            try {
                val url = URL("$serverAddress/api/robot/sensors/gyro")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.addRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                conn.connectTimeout = 2000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    result.put("gyro", json)
                }
                conn.disconnect()
            } catch (e: Exception) { }

            try {
                val url = URL("$serverAddress/api/robot/sensors/millis")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.addRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                conn.connectTimeout = 2000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    result.put("millis", json)
                }
                conn.disconnect()
            } catch (e: Exception) { }

            mainHandler.post {
                callback(if (result.length() > 0) result else null)
            }
        }.start()
    }

    fun getSensorData() {
        getAllSensorsData { data ->
            if (data != null) {
                sensorDataCallback?.invoke(data)
            }
        }
    }

    fun pingRobot(callback: (Boolean) -> Unit) {
        val url = "$serverAddress/api/robot/ping"
        val json = JSONObject()
        sendPostRequest(url, json.toString()) { success, _ ->
            callback(success)
        }
    }

    fun startVideoStream(callback: (Bitmap?) -> Unit) {
        if (isStreaming) {
            stopVideoStream()
        }

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
                                                callback(bitmap)
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
    }

    fun sendEmotionCommand(emotion: String, callback: ((Boolean) -> Unit)? = null) {
        val url = "$serverAddress/api/emotions/current"
        val json = JSONObject().apply {
            put("emotion", emotion)
        }
        sendPutRequest(url, json.toString()) { success, _ ->
            callback?.invoke(success)
        }
    }

    fun getCurrentEmotion(callback: (String?) -> Unit) {
        val url = "$serverAddress/api/emotions/current"
        sendGetRequest(url) { response ->
            try {
                val json = JSONObject(response)
                callback(json.optString("emotion", null))
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    fun getAvailableEmotions(callback: (List<String>) -> Unit) {
        val url = "$serverAddress/api/emotions"
        sendGetRequest(url) { response ->
            try {
                val json = JSONObject(response)
                val emotionsArray = json.optJSONArray("emotions")
                val emotions = mutableListOf<String>()
                if (emotionsArray != null) {
                    for (i in 0 until emotionsArray.length()) {
                        emotions.add(emotionsArray.getString(i))
                    }
                }
                callback(emotions)
            } catch (e: Exception) {
                callback(emptyList())
            }
        }
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
                    }
                } catch (e: Exception) { }
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
        disconnectVoiceWebSocket()
    }

    fun reconnectWebSocket(callback: (Boolean, String) -> Unit) {
        disconnectWebSocket()
        Handler(Looper.getMainLooper()).postDelayed({
            connectWebSocket(callback)
        }, 1000)
    }

    fun isWebSocketConnected(): Boolean = isWebSocketConnected

    private fun sendPostRequest(url: String, jsonBody: String, callback: ((Boolean, String) -> Unit)? = null) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val httpUrl = URL(url)
                connection = httpUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                connection.outputStream.use { os ->
                    os.write(jsonBody.toByteArray())
                }

                val responseCode = connection.responseCode
                val success = responseCode in 200..299
                mainHandler.post {
                    callback?.invoke(success, if (success) "OK" else "Error $responseCode")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback?.invoke(false, e.message ?: "Unknown error")
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun sendPutRequest(url: String, jsonBody: String, callback: ((Boolean, String) -> Unit)? = null) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val httpUrl = URL(url)
                connection = httpUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                connection.outputStream.use { os ->
                    os.write(jsonBody.toByteArray())
                }

                val responseCode = connection.responseCode
                val success = responseCode in 200..299
                mainHandler.post {
                    callback?.invoke(success, if (success) "OK" else "Error $responseCode")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback?.invoke(false, e.message ?: "Unknown error")
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun sendGetRequest(url: String, callback: (String) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val httpUrl = URL(url)
                connection = httpUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer ${authManager.getAccessToken()}")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    mainHandler.post {
                        callback(response)
                    }
                } else {
                    mainHandler.post {
                        callback("")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback("")
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun logout() {
        authManager.clearTokens()
        disconnectWebSocket()
    }
    fun connectVoiceWebSocket(callback: (Boolean, String) -> Unit) {
        if (isVoiceWebSocketConnected) {
            callback(true, "Already connected")
            return
        }

        val token = authManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            callback(false, "No auth token")
            return
        }

        val request = Request.Builder()
            .url("$serverAddress/api/broadcast/voice?token=$token")
            .build()

        voiceWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isVoiceWebSocketConnected = true
                mainHandler.post {
                    callback(true, "Voice WebSocket connected")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isVoiceWebSocketConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isVoiceWebSocketConnected = false
                mainHandler.post {
                    callback(false, t.message ?: "Connection failed")
                }
            }
        })
    }


    fun sendVoiceData(audioData: ByteArray, messageId: Long) {
        if (!isVoiceWebSocketConnected) {
            return
        }

        try {
            voiceWebSocket?.send(okio.ByteString.of(*audioData))
        } catch (e: Exception) {
        }
    }


    fun disconnectVoiceWebSocket() {
        voiceWebSocket?.close(1000, "Normal closure")
        voiceWebSocket = null
        isVoiceWebSocketConnected = false
    }

    fun isVoiceWebSocketConnected(): Boolean = isVoiceWebSocketConnected


    fun getNextVoiceMessageId(): Long = voiceMessageId++

    // Обновите disconnectWebSocket для закрытия голосового соединения

}
package com.example.izbushka_android_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class NetworkManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("robot_settings", Context.MODE_PRIVATE)
    private var serverAddress: String = "http://192.168.4.1"
    private val timeout = 3000
    private val mainHandler = Handler(Looper.getMainLooper())

    private var streaming = false
    private var streamThread: Thread? = null
    private var frameCallback: ((Bitmap?) -> Unit)? = null

    init {
        loadServerAddress()
    }

    fun sendMotorCommand(x: Float, y: Float, callback: (Boolean, String) -> Unit) {
        val url = "$serverAddress/api/robot/motors/move"
        val json = JSONObject().apply {
            put("x", x.toDouble())
            put("y", y.toDouble())
        }
        sendPostRequest(url, json, callback)
    }

    fun sendServoCommand(channel: Int, angle: Int, callback: (Boolean, String) -> Unit) {
        val url = "$serverAddress/api/robot/servo/$channel"
        val json = JSONObject().apply {
            put("angle", angle)
        }
        sendPostRequest(url, json, callback)
    }

    fun sendEmotionCommand(emotion: String, callback: (Boolean, String) -> Unit) {
        val url = "$serverAddress/api/emotions/current"
        val json = JSONObject().apply {
            put("emotion", emotion)
        }
        sendPutRequest(url, json, callback)
    }

    fun getSensorData(callback: (JSONObject?) -> Unit) {
        val url = "$serverAddress/api/robot/sensors/distance"
        sendGetRequest(url) { success, data ->
            if (success && data != null) {
                callback(data)
            } else {
                callback(null)
            }
        }
    }

    fun pingRobot(callback: (Boolean) -> Unit) {
        val url = "$serverAddress/api/robot/ping"
        sendPostRequest(url, JSONObject()) { success, _ ->
            callback(success)
        }
    }

    fun startVideoStream(callback: (Bitmap?) -> Unit) {
        if (streaming) {
            stopVideoStream()
        }

        frameCallback = callback
        streaming = true

        streamThread = Thread {
            val url = "$serverAddress/api/webcam/stream"

            while (streaming) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "image/jpeg")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val boundary = "--boundary"

                        readMultipartStream(inputStream, boundary) { imageData ->
                            if (imageData != null && streaming) {
                                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                                mainHandler.post {
                                    frameCallback?.invoke(bitmap)
                                }
                            }
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    Thread.sleep(1000)
                }
            }
        }
        streamThread?.start()
    }

    fun stopVideoStream() {
        streaming = false
        streamThread?.interrupt()
        streamThread = null
        frameCallback = null
    }

    fun isVideoStreamAvailable(callback: (Boolean) -> Unit) {
        val url = "$serverAddress/api/webcam/stream"
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = timeout
                val responseCode = connection.responseCode
                mainHandler.post {
                    callback(responseCode == HttpURLConnection.HTTP_OK)
                }
                connection.disconnect()
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false)
                }
            }
        }.start()
    }

    fun setVideoQuality(quality: Int, callback: (Boolean) -> Unit) {
        val url = "$serverAddress/api/webcam/quality"
        val json = JSONObject().apply {
            put("quality", quality.coerceIn(0, 100))
        }
        sendPutRequest(url, json) { success, _ ->
            callback(success)
        }
    }

    private fun readMultipartStream(inputStream: InputStream, boundary: String, onImageData: (ByteArray?) -> Unit) {
        val buffer = ByteArray(4096)
        var inImageData = false
        val imageDataBuffer = java.io.ByteArrayOutputStream()

        try {
            val bis = java.io.BufferedInputStream(inputStream)
            var lastBytes = byteArrayOf()

            while (streaming) {
                val read = bis.read(buffer)
                if (read == -1) break

                val data = buffer.copyOf(read)

                for (i in data.indices) {
                    if (!inImageData) {
                        if (i + 1 < data.size &&
                            data[i].toInt() == 0xFF &&
                            data[i + 1].toInt() == 0xD8) {
                            inImageData = true
                            imageDataBuffer.reset()
                            imageDataBuffer.write(data, i, data.size - i)
                        }
                    } else {
                        imageDataBuffer.write(data[i].toInt())
                        if (i >= 1 &&
                            data[i - 1].toInt() == 0xFF &&
                            data[i].toInt() == 0xD9) {
                            onImageData(imageDataBuffer.toByteArray())
                            inImageData = false
                            imageDataBuffer.reset()
                        }
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) { }
        }
    }

    private fun sendPostRequest(url: String, json: JSONObject, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = timeout
                connection.readTimeout = timeout

                connection.outputStream.use { os ->
                    val input = json.toString().toByteArray()
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                mainHandler.post {
                    callback(responseCode == HttpURLConnection.HTTP_OK, response)
                }
                connection.disconnect()
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, e.message ?: "Ошибка соединения")
                }
            }
        }.start()
    }

    private fun sendPutRequest(url: String, json: JSONObject, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = timeout
                connection.readTimeout = timeout

                connection.outputStream.use { os ->
                    val input = json.toString().toByteArray()
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                mainHandler.post {
                    callback(responseCode == HttpURLConnection.HTTP_OK, if (responseCode == HttpURLConnection.HTTP_OK) "OK" else "Ошибка: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, e.message ?: "Ошибка соединения")
                }
            }
        }.start()
    }

    private fun sendGetRequest(url: String, callback: (Boolean, JSONObject?) -> Unit) {
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = timeout
                connection.readTimeout = timeout

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    mainHandler.post {
                        callback(true, JSONObject(response))
                    }
                } else {
                    mainHandler.post {
                        callback(false, null)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, null)
                }
            }
        }.start()
    }

    private fun loadServerAddress() {
        serverAddress = sharedPreferences.getString("server_address", "http://192.168.4.1") ?: "http://192.168.4.1"
    }

    fun updateServerAddress(address: String) {
        serverAddress = if (address.startsWith("http")) address else "http://$address"
        sharedPreferences.edit().putString("server_address", serverAddress).apply()
    }

    fun getServerAddress(): String {
        return serverAddress
    }
}
package com.example.izbushka_android_app

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*

class AudioRecorder(private val onAudioData: (ByteArray) -> Unit) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (isRecording) return

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.let { record ->
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            isRecording = true
            record.startRecording()

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)

                while (isRecording) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        withContext(Dispatchers.Main) {
                            onAudioData(audioData)
                        }
                    }
                    yield()
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let {
            if (it.state == AudioRecord.STATE_INITIALIZED) {
                it.stop()
            }
            it.release()
        }
        audioRecord = null
    }

    fun isRecordingActive(): Boolean = isRecording
}
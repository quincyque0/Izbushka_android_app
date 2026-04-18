package com.example.izbushka_android_app

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

class VideoStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var imageView: ImageView
    private var progressBar: ProgressBar
    private var errorText: TextView

    private var networkManager: NetworkManager? = null
    private var isStreaming = false

    init {
        inflate(context, R.layout.view_video_stream, this)

        imageView = findViewById(R.id.videoImageView)
        progressBar = findViewById(R.id.videoProgressBar)
        errorText = findViewById(R.id.videoErrorText)

        visibility = View.GONE
    }

    fun setNetworkManager(manager: NetworkManager) {
        this.networkManager = manager
    }

    fun startStream() {
        if (networkManager == null) return

        visibility = View.VISIBLE
        isStreaming = true
        progressBar.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        networkManager?.startVideoStream { bitmap ->
            if (bitmap != null && isStreaming) {
                imageView.setImageBitmap(bitmap)
                progressBar.visibility = View.GONE
                errorText.visibility = View.GONE
            }
        }

        imageView.postDelayed({
            if (isStreaming && imageView.drawable == null) {
                errorText.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            }
        }, 2000)
    }

    fun stopStream() {
        isStreaming = false
        networkManager?.stopVideoStream()
        visibility = View.GONE
        imageView.setImageDrawable(null)
    }

    fun isStreamingActive(): Boolean = isStreaming
}
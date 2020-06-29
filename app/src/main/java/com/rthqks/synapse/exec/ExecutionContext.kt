package com.rthqks.synapse.exec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.gl.GlesManager
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class ExecutionContext constructor(
    val context: Context,
    val videoStorage: VideoStorage,
    val assetManager: AssetManager
) {
    private val videoEncoderDelegate = lazy {
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    }
    private val audioEncoderDelegate = lazy {
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    }
    val videoEncoder: MediaCodec by videoEncoderDelegate
    val audioEncoder: MediaCodec by audioEncoderDelegate
    val dispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    val glesManager = GlesManager(assetManager)
    val cameraManager = CameraManager(context)

    suspend fun setup() {
        glesManager.glContext {
            it.initialize()
        }
        cameraManager.initialize()
    }

    suspend fun release() {
        Log.d(TAG, "releasing - video ${videoEncoderDelegate.isInitialized()}")
        Log.d(TAG, "releasing - audio ${audioEncoderDelegate.isInitialized()}")
        if (videoEncoderDelegate.isInitialized()) {
            videoEncoder.release()
        }
        if (audioEncoderDelegate.isInitialized()) {
            audioEncoder.release()
        }
        cameraManager.release()
        glesManager.release()
        dispatcher.close()
    }

    companion object {
        const val TAG = "ExecutionContext"
    }
}
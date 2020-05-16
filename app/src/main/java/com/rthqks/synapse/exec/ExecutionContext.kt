package com.rthqks.synapse.exec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

class ExecutionContext @Inject constructor(
    val context: Context,
    val videoStorage: VideoStorage,
    val dispatcher: ExecutorCoroutineDispatcher,
    val glesManager: GlesManager,
    val cameraManager: CameraManager,
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

    suspend fun setup() {
        glesManager.glContext {
            it.initialize()
        }
        cameraManager.initialize()
    }

    suspend fun release() {
        if (videoEncoderDelegate.isInitialized()) {
            videoEncoder.release()
        }
        if (audioEncoderDelegate.isInitialized()) {
            audioEncoder.release()
        }
        cameraManager.release()
        glesManager.glContext {
            it.release()
        }
        dispatcher.close()
    }
}
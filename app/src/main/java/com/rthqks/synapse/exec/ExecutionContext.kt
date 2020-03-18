package com.rthqks.synapse.exec

import android.content.Context
import android.media.MediaCodec
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
    val assetManager: AssetManager,
    @Named("video") val videoEncoder: MediaCodec,
    @Named("audio") val audioEncoder: MediaCodec
) {
    val properties = Properties()

    suspend fun setup() {
        glesManager.glContext {
            it.initialize()
        }
        cameraManager.initialize()
    }

    suspend fun release() {
        videoEncoder.release()
        audioEncoder.release()
        cameraManager.release()
        glesManager.glContext {
            it.release()
        }
        dispatcher.close()
    }
}
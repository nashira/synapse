package com.rthqks.synapse.exec

import android.content.Context
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.gl.GlesManager
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import javax.inject.Inject

class ExecutionContext @Inject constructor(
    val context: Context,
    val videoStorage: VideoStorage,
    val dispatcher: ExecutorCoroutineDispatcher,
    val glesManager: GlesManager,
    val cameraManager: CameraManager,
    val assetManager: AssetManager
) {

    suspend fun release() {
        cameraManager.release()
        glesManager.glContext {
            it.release()
        }
        dispatcher.close()
    }
}
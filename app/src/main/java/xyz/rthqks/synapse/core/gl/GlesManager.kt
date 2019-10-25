package xyz.rthqks.synapse.core.gl

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class GlesManager {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private lateinit var eglCore: EglCore
    private lateinit var eglSurface: OffscreenSurface

    suspend fun initialize() = withContext(dispatcher) {
        eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3)
        eglSurface = OffscreenSurface(eglCore, 1, 1)
    }

    suspend fun release() = withContext(dispatcher) {
        eglSurface.release()
        eglCore.release()
        dispatcher.close()
    }
}
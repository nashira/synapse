package xyz.rthqks.synapse.gl

import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class GlesManager {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val thread = HandlerThread("BackgroundHandler")
    private lateinit var handler: Handler
    private lateinit var eglCore: EglCore
    private lateinit var eglSurface: OffscreenSurface

    val backgroundHandler: Handler get() = handler

    // use to wrap calls that need an OpenGL context
    suspend fun <T> withGlContext(block: suspend CoroutineScope.(GlesManager) -> T): T =
        withContext(dispatcher) { block(this@GlesManager) }

    fun initialize() {
        thread.start()
        handler = Handler(thread.looper)
        eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3 or EglCore.FLAG_RECORDABLE)
        eglSurface = OffscreenSurface(eglCore, 1, 1)
        eglSurface.makeCurrent()
    }

    fun release() {
        thread.quitSafely()
        eglSurface.release()
        eglCore.release()
        dispatcher.close()
    }

    fun createWindowSurface(surface: Surface): WindowSurface =
        WindowSurface(eglCore, surface, false)

    companion object {
        private val TAG = GlesManager::class.java.simpleName
        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    }
}
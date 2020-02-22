package com.rthqks.synapse.gl

import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
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
    lateinit var emptyTexture2d: Texture2d
    lateinit var emptyTexture3d: Texture3d
    var supportedDevice = false

    val backgroundHandler: Handler get() = handler

    // use to wrap calls that need an OpenGL context
    suspend fun <T> glContext(block: suspend CoroutineScope.(GlesManager) -> T): T =
        withContext(dispatcher) { block(this@GlesManager) }

    fun initialize() {
        thread.start()
        handler = Handler(thread.looper)
        eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3 or EglCore.FLAG_RECORDABLE)
        eglSurface = OffscreenSurface(eglCore, 1, 1)
        eglSurface.makeCurrent()

        emptyTexture2d = Texture2d()
        emptyTexture2d.initialize()
        emptyTexture2d.initData(
            0,
            GLES30.GL_R8, 1, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE
        )

        emptyTexture3d = Texture3d()
        emptyTexture3d.initialize()
        emptyTexture3d.initData(
            0,
            GLES30.GL_R8, 1, 1, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE
        )

        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS)
        Log.d(TAG, "extensions $extensions")
        supportedDevice = extensions.contains("GL_OES_EGL_image_external_essl3")

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    fun release() {
        emptyTexture2d.release()
        emptyTexture3d.release()
        eglSurface.release()
        eglCore.release()
        dispatcher.close()
        thread.quitSafely()
    }

    fun createWindowSurface(surface: Surface): WindowSurface =
        WindowSurface(eglCore, surface, false)

    companion object {
        val TAG = "GlesManager"
        fun identityMat() = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    }
}
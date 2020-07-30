package com.rthqks.flow.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.rthqks.flow.assets.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class GlesManager(
    private val assetManager: AssetManager
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val thread = HandlerThread("BackgroundHandler")
    private var handler: Handler? = null
    private var eglCore: EglCore? = null
    private var eglSurface: OffscreenSurface? = null
    private val randProgram = Program()
    private val geo = Quad()
    var emptyTexture2d = Texture2d()
    var emptyTexture3d = Texture3d()
    var supportedDevice = false

    val backgroundHandler: Handler get() = handler ?: error("handler is null")

    // use to wrap calls that need an OpenGL context
    suspend fun <T> glContext(block: suspend CoroutineScope.(GlesManager) -> T): T =
        withContext(dispatcher) { block(this@GlesManager) }

    fun initialize() {

        thread.start()
        handler = Handler(thread.looper)
        eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3 or EglCore.FLAG_RECORDABLE).also {
            eglSurface = OffscreenSurface(it, 1, 1)
            eglSurface?.makeCurrent()
        }

        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS)
        Log.d(TAG, "extensions $extensions")
        supportedDevice = extensions.contains("GL_OES_EGL_image_external_essl3")

        if (!supportedDevice) {
            return
        }

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

        emptyTexture2d.initialize()
        emptyTexture2d.initData(
            0,
            GLES30.GL_R8, 1, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE
        )

        emptyTexture3d.initialize()
        emptyTexture3d.initData(
            0,
            GLES30.GL_R8, 1, 1, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE
        )

        val vertex = assetManager.readTextAsset("shader/random.vert")
        val frag = assetManager.readTextAsset("shader/random.frag")
        randProgram.initialize(vertex, frag)
        geo.initialize()
    }

    suspend fun release() {
        glContext {
            emptyTexture2d.release()
            emptyTexture3d.release()
            randProgram.release()
            geo.release()
            eglSurface?.release()
            eglCore?.release()
        }
        thread.quitSafely()
        dispatcher.close()
    }

    fun createWindowSurface(surface: Surface): WindowSurface =
        WindowSurface(eglCore!!, surface, false)

    fun createWindowSurface(surface: SurfaceTexture): WindowSurface =
        WindowSurface(eglCore!!, surface)

    fun fillRandom(framebuffer: Framebuffer, width: Int, height: Int) {
        GLES30.glUseProgram(randProgram.programId)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
        GLES30.glViewport(0, 0, width, height)
        geo.execute()
    }

    companion object {
        val TAG = "GlesManager"
        fun identityMat() = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    }
}
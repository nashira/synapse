package xyz.rthqks.synapse.core.gl

import android.content.Context
import android.opengl.GLES32
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class GlesManager(
    private val context: Context
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val thread = HandlerThread("BackgroundHandler")
    private lateinit var handler: Handler
    private lateinit var eglCore: EglCore
    private lateinit var eglSurface: OffscreenSurface
    private var quadVao = 0

    val backgroundHandler: Handler get() = handler

    // use to wrap calls that need an OpenGL context
    suspend fun <T> withGlContext(block: suspend CoroutineScope.(GlesManager) -> T) =
        withContext(dispatcher) { block(this@GlesManager) }

    fun initialize() {
        thread.start()
        handler = Handler(thread.looper)
        eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3)
        eglSurface = OffscreenSurface(eglCore, 1, 1)
    }

    fun release() {
        thread.quitSafely()
        eglSurface.release()
        eglCore.release()
        dispatcher.close()
    }

    fun createWindowSurface(surface: Surface): WindowSurface =
        WindowSurface(eglCore, surface, false)

    fun createProgram(vertex: String, fragment: String): Program {
        val vertexShader: Int = createShader(GLES32.GL_VERTEX_SHADER, vertex)
        val fragmentShader: Int = createShader(GLES32.GL_FRAGMENT_SHADER, fragment)

        val program = GLES32.glCreateProgram().also {
            GLES32.glAttachShader(it, vertexShader)
            GLES32.glAttachShader(it, fragmentShader)
            GLES32.glLinkProgram(it)
            val linkStatus = IntArray(1)
            GLES32.glGetProgramiv(it, GLES32.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES32.GL_TRUE) {
                Log.e(TAG, "Could not link program: ")
                Log.e(TAG, GLES32.glGetProgramInfoLog(it))
            }
//            GLES32.glDeleteShader(vertexShader)
//            GLES32.glDeleteShader(fragmentShader)
        }

        Log.d(TAG, "created program: $program")
        return Program(program)
    }

    fun createTexture(
        target: Int = GLES32.GL_TEXTURE_2D,
        repeat: Int = GLES32.GL_REPEAT,
        filter: Int = GLES32.GL_LINEAR
    ): Int {
        val textureHandle = IntArray(1)

        GLES32.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {

            GLES32.glBindTexture(target, textureHandle[0])

            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MIN_FILTER, filter)
            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MAG_FILTER, filter)

            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_S, repeat.toFloat())
            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_T, repeat.toFloat())

            GLES32.glBindTexture(target, 0)
        } else {
            throw RuntimeException("Error creating texture.")
        }

        return textureHandle[0]
    }

    private fun createShader(type: Int, source: String): Int =
        GLES32.glCreateShader(type).also { shader ->
            Log.d(TAG, "created shader $shader")
            GLES32.glShaderSource(shader, source)
            GLES32.glCompileShader(shader)
            Log.d(TAG, GLES32.glGetShaderInfoLog(shader))
        }

    fun releaseTexture(inputTexture: Int) {
        GLES32.glDeleteTextures(1, intArrayOf(inputTexture), 0)
    }

    fun releaseProgram(program: Program) {
        GLES32.glDeleteProgram(program.programId)
    }

    fun makeCurrent() {
        eglSurface.makeCurrent()
    }

    fun quadVao(): Int {
        if (quadVao == 0) {
            quadVao = Quad.createVao()
        }
        return quadVao
    }

    companion object {
        private val TAG = GlesManager::class.java.simpleName
        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    }
}
package com.rthqks.synapse.exec2.node

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.util.Log
import android.util.Size
import android.view.TextureView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TextureViewNode(
    context: ExecutionContext,
    properties: Properties
) : NodeExecutor(context) {
    private val glesManager = context.glesManager
    private val assetManager = context.assetManager
    private var textureView: TextureView? = null
    private val mesh = Quad()
    private var program = Program()
    private var surfaceSize = Size(0, 0)
    private var windowSurface: WindowSurface? = null
    private var startJob: Job? = null
    private var previousTexture: Texture2d? = null
    private val string = toString()
    val TAG = "TVN ${string.substring(string.length - 8, string.length)}"

    override suspend fun onSetup() {
        glesManager.glContext {
            mesh.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT -> if (startJob == null) {
                startJob = scope.launch {
                    start()
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            windowSurface?.release()
            mesh.release()
            program.release()
        }
    }

    private suspend fun start() {
        val channel = channel(INPUT) ?: error("missing channel")
        var copyMatrix = true

        for (msg in channel) {
            checkInit(msg.data)

            val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
            val matrix = uniform.data!!
            System.arraycopy(msg.data.matrix, 0, matrix, 0, 16)
            uniform.dirty = true

            var rel = false
            glesManager.glContext {
                if (windowSurface != null) {
                    rel = true
                    windowSurface?.makeCurrent()
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                    GLES30.glUseProgram(program.programId)
                    GLES30.glViewport(0, 0, surfaceSize.width, surfaceSize.height)

                    msg.data.bind(GLES30.GL_TEXTURE0)

                    program.bindUniforms()
                    mesh.execute()
                    msg.release()
                    windowSurface?.swapBuffers()?.let {
                        if (!it) removeTextureView()
                    }
                }
            }
            if (!rel) {
                msg.release()
            }
        }
        Log.d(TAG, "no more messages")
    }

    private suspend fun updateWindowSurface(surfaceTexture: SurfaceTexture?) {
//        Log.d(TAG, "updating input surface")
        glesManager.glContext {
            val ws = windowSurface
            windowSurface = null

            ws?.release()
            if (surfaceTexture != null) {
//                Log.d(TAG, "creating input surface")
                windowSurface = it.createWindowSurface(surfaceTexture)
            }
        }
    }

    suspend fun setTextureView(textureView: TextureView) = await {
        val surfaceTexture = textureView.surfaceTexture
//        Log.d(TAG, "setTextureView $surfaceTexture")

        this.textureView = textureView
        surfaceTexture?.let {
            surfaceSize = Size(textureView.width, textureView.height)
//            Log.d(TAG, "st size $surfaceSize")
            updateWindowSurface(it)
        }
    }

    suspend fun removeTextureView() = await {
//        textureView?.surfaceTextureListener = null
        textureView = null
        updateWindowSurface(null)
    }

    private suspend fun checkInit(texture2d: Texture2d) {
        var recreateProgram = false
//        if (previousTexture?.width != texture2d.width
//            || previousTexture?.height != texture2d.height
//        ) {
//
//        }
//
        if (previousTexture?.oes != texture2d.oes
            || previousTexture?.format != texture2d.format
        ) {
            recreateProgram = true
        }

        if (program.programId == 0) {
            recreateProgram = true
        }

        if (recreateProgram) {
            recreateProgram(texture2d)
        }

        previousTexture = texture2d
    }

    private suspend fun recreateProgram(texture2d: Texture2d) {
        val grayscale = texture2d.format == GLES30.GL_RED

        val vertexSource = assetManager.readTextAsset("shader/vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("shader/copy.frag").let {
            if (texture2d.oes) it.replace("//{EXT}", "#define EXT") else it
        }.let {
            if (grayscale) it.replace("//{RED}", "#define RED") else it
        }

        glesManager.glContext {
            program.apply {
                release()
                initialize(vertexSource, fragmentSource)
                addUniform(
                    Uniform.Type.Mat4,
                    "vertex_matrix0",
                    GlesManager.identityMat()
                )
                addUniform(
                    Uniform.Type.Mat4,
                    "texture_matrix0",
                    GlesManager.identityMat()
                )
                addUniform(
                    Uniform.Type.Int,
                    "input_texture0",
                    0
                )
            }
        }
    }

    companion object {

        val INPUT = Connection.Key<Texture2d>("video_1")
    }
}
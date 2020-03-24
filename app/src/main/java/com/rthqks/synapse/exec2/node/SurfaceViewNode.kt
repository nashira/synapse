package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SurfaceViewNode(
    context: ExecutionContext,
    properties: Properties
) : NodeExecutor(context), SurfaceHolder.Callback {
    private val glesManager = context.glesManager
    private val assetManager = context.assetManager
    private var surfaceView: SurfaceView? = null
    private val mesh = Quad()
    private var program = Program()
    private var surfaceSize = Size(0, 0)
    private var windowSurface: WindowSurface? = null
    private var startJob: Job? = null
    private var previousTexture: Texture2d? = null

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
            surfaceView?.holder?.removeCallback(this@SurfaceViewNode)
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

//                Log.d(TAG, "loop surfaceState $surfaceState")

            if (windowSurface != null) {
//                    Log.d(TAG, "loop pre render")


                val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                val matrix = uniform.data!!
                System.arraycopy(msg.data.matrix, 0, matrix, 0, 16)
                uniform.dirty = true
                // center crop
//            if (cropCenter) {
//                Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
//                Matrix.scaleM(matrix, 0, outScaleX, outScaleY, 1f)
//                Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
//            }

                glesManager.glContext {
                    //                        Log.d(TAG, "loop render")
                    windowSurface?.makeCurrent()
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

                    GLES30.glUseProgram(program.programId)
                    GLES30.glViewport(0, 0, surfaceSize.width, surfaceSize.height)

                    msg.data.bind(GLES30.GL_TEXTURE0)

                    program.bindUniforms()

                    mesh.execute()
                    msg.release()
                    windowSurface?.swapBuffers()
                }
            } else {
                msg.release()
            }
        }
    }

    private suspend fun updateWindowSurface(surface: Surface?) {
        Log.d(TAG, "updating input surface")
        glesManager.glContext {
            windowSurface?.release()
            if (surface?.isValid == true) {
                windowSurface = it.createWindowSurface(surface)
            } else {
                windowSurface = null
            }
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        val valid = surfaceView.holder.surface?.isValid ?: false

        Log.d(TAG, "setSurfaceView $valid")

        this.surfaceView?.holder?.removeCallback(this)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(this)
        if (valid) {
            surfaceSize = Size(surfaceView.width, surfaceView.height)
            updateWindowSurface(surfaceView.holder.surface)
        }
    }

    suspend fun removeSurfaceView() = await {
        surfaceView?.holder?.removeCallback(this)
        surfaceView = null
        updateWindowSurface(null)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {
        runBlocking {
            exec {
                Log.d(TAG, "surfaceChanged: $holder $format $width $height")
                surfaceSize = Size(width, height)
                updateWindowSurface(holder?.surface)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        runBlocking {
            exec {
                Log.d(TAG, "surfaceDestroyed: $holder")
                updateWindowSurface(null)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
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
        const val TAG = "SVN2"
        val INPUT = Connection.Key<Texture2d>("video_1")
    }
}
package com.rthqks.synapse.exec.node

import android.opengl.GLES30.*
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
//import com.rthqks.synapse.logic.ScaleFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GrayscaleNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private var size = Size(0, 0)

    private val texture1 = Texture2d()
    private val framebuffer1 = Framebuffer()

    private val texture2 = Texture2d()
    private val framebuffer2 = Framebuffer()

    private val mesh = Quad()
    private val program = Program()

    private var primed = false

    private val scale = 1

    override suspend fun onSetup() {
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT -> connectInput()
            OUTPUT -> connectOutput()
        }
    }

    private fun connectInput() {
        if (startJob == null) {
            startJob = scope.launch {
                startTexture()
            }
        }
    }

    private suspend fun connectOutput() {
        Log.d(TAG, "output linked")
        if (!primed) {
            primed = true
            connection(OUTPUT)?.prime(texture1, texture2)
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT -> {
                startJob?.join()
                startJob = null
            }
            OUTPUT -> {

            }
        }
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            mesh.release()
            program.release()

            texture1.release()
            framebuffer1.release()
            texture2.release()
            framebuffer2.release()
        }
    }

    private suspend fun checkConfig(texture: Texture2d) {
        when {
            program.programId == 0 -> {
                createProgram(texture)
                size = Size(texture.width, texture.height)
                Log.d(TAG, "size $size")
            }
            size.width != texture.width || size.height != texture.height -> {
                glesManager.glContext {
                    texture1.initData(0, GL_R8, texture.width, texture.height, GL_RED, GL_UNSIGNED_BYTE)
                    texture2.initData(0, GL_R8, texture.width, texture.height, GL_RED, GL_UNSIGNED_BYTE)
                }
                Log.d(TAG, "size $size")
                size = Size(texture.width, texture.height)
            }
        }
    }

    private suspend fun createProgram(texture: Texture2d) {
        val vertexSource = assetManager.readTextAsset("shader/vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("shader/grayscale.frag").let {
            if (texture.oes) {
                it.replace("//{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.glContext {
            texture1.initialize()
            texture1.initData(0, GL_R8, texture.width, texture.height, GL_RED, GL_UNSIGNED_BYTE)
            Log.d(TAG, "glGetError() ${glGetError()}")
            framebuffer1.initialize(texture1)

            texture2.initialize()
            texture2.initData(0, GL_R8, texture.width, texture.height, GL_RED, GL_UNSIGNED_BYTE)
            Log.d(TAG, "glGetError() ${glGetError()}")
            framebuffer2.initialize(texture2)

            mesh.initialize()

            program.apply {
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

    private suspend fun startTexture() {
        val input = channel(INPUT) ?: error("missing input")

        var copyMatrix = true

        for (msg in input) {
//            Log.d(TAG, "event received ${msg.count}")

            checkConfig(msg.data)

            val outMsg = channel(OUTPUT)?.receive()?.apply {
                timestamp = msg.timestamp
            }

//            if (copyMatrix) {
//                copyMatrix = false
                val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                System.arraycopy(msg.data.matrix, 0, uniform.data!!, 0, 16)
                uniform.dirty = true
//            }

            outMsg?.let {
                glesManager.glContext {
                    if (outMsg.data == texture1) {
                        framebuffer1.bind()
                    } else {
                        framebuffer2.bind()
                    }
                    executeGl(msg.data)
                }
            }
            msg.release()
            outMsg?.queue()
        }
    }

    private fun executeGl(texture: Texture2d) {
        glUseProgram(program.programId)
        glViewport(0, 0, texture.width, texture.height)

        texture.bind(GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    companion object {
        const val TAG = "GrayscaleNode"
        val INPUT = Connection.Key<Texture2d>("video_1")
        val OUTPUT = Connection.Key<Texture2d>("video_2")
    }
}
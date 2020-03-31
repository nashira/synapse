package com.rthqks.synapse.exec2.node

import android.opengl.GLES30.*
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.Message
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.BlurSize
import com.rthqks.synapse.logic.NumPasses
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.ScaleFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BlurNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {

    private var needsPriming = true
    private val gl = context.glesManager
    private val am = context.assetManager
    private var startJob: Job? = null
    private var inputSize = Size(0, 0)
    private var size = Size(0, 0)
    private var config: Texture2d? = null

    private val texture1 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val texture2 = Texture2d()
    private val framebuffer2 = Framebuffer()
    private val texture3 = Texture2d()
    private val framebuffer3 = Framebuffer()

    private val mesh = Quad()
    private val program1 = Program()
    private val program2 = Program()

    private val blurSize: Int get() = properties[BlurSize]
    private val passes: Int get() = properties[NumPasses]
    private val scale: Int get() = properties[ScaleFactor]

    override suspend fun onSetup() {
        gl.glContext {
            mesh.initialize()
            texture1.initialize()
            texture2.initialize()
            texture3.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture2, texture3)
                }
            }
            INPUT -> {
                if (startJob == null) {
                    startJob = scope.launch {
                        onStart()
                    }
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        Log.d(TAG, "discon() ${key}")
        when (key) {
            INPUT -> {
                onStop()
            }
            OUTPUT -> {
            }
        }
    }

    private suspend fun checkConfig(texture: Texture2d) {
        val programChanged = config?.oes != texture.oes
        val sizeChanged = inputSize.width != texture.width
                || inputSize.height != texture.height

        if (sizeChanged) {
            inputSize = Size(texture.width, texture.height)
            size = inputSize
//            size = Size(inputSize.width / scale, inputSize.height / scale)
            gl.glContext {
                listOf(texture1, texture2, texture3).forEach {
                    it.initData(
                        0,
                        texture.internalFormat,
                        size.width,
                        size.height,
                        texture.format,
                        texture.type
                    )
                    Log.d(TAG, "glGetError() ${glGetError()}")
                }
                framebuffer1.release()
                framebuffer1.initialize(texture1)
                framebuffer2.release()
                framebuffer2.initialize(texture2)
                framebuffer3.release()
                framebuffer3.initialize(texture3)

            }
        }

        if (programChanged) {
            initializeProgram(program1, texture.oes)
            initializeProgram(program2, false)

            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 0f
                data[1] = 1f
            }
            config = texture
        }
    }

    private suspend fun initializeProgram(
        program: Program,
        oes: Boolean
    ) {
        val fragName = when (blurSize) {
            13 -> "shader/blur_13.frag"
            5 -> "shader/blur_5.frag"
            else -> "shader/blur_9.frag"
        }

        val vertexSource = am.readTextAsset("shader/blur.vert")
        val fragmentSource = am.readTextAsset(fragName).let {
            if (oes) {
                it.replace("//{EXT}", "#define EXT")
            } else {
                it
            }
        }

        gl.glContext {
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

                addUniform(
                    Uniform.Type.Vec2,
                    "resolution0",
                    floatArrayOf(size.width.toFloat(), size.height.toFloat())
                )

                addUniform(
                    Uniform.Type.Vec2,
                    "direction",
                    floatArrayOf(1f, 0f)
                )

            }
        }
    }

    private suspend fun onStart() {
        val input = channel(INPUT) ?: error("missing input")

        var copyMatrix = true

        for (inEvent in input) {
            val outEvent = channel(OUTPUT)?.receive()
            outEvent?.timestamp = inEvent.timestamp

            checkConfig(inEvent.data)

            if (copyMatrix) {
                copyMatrix = false
                val uniform = program1.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                System.arraycopy(inEvent.data.matrix, 0, uniform.data!!, 0, 16)
                uniform.dirty = true
            }

            if (outEvent != null) {
                val targetTexture: Texture2d
                val targetFB: Framebuffer
                if (outEvent.data == texture2) {
                    targetTexture = texture2
                    targetFB = framebuffer2
                } else {
                    targetTexture = texture3
                    targetFB = framebuffer3
                }

                gl.glContext {
                    executeGl(
                        inEvent,
                        framebuffer1, texture1,
                        targetFB, targetTexture
                    )
                }
                outEvent.queue()
            }

            inEvent.release()
        }
    }

    private fun executeGl(
        msg: Message<Texture2d>,
        framebuffer1: Framebuffer, texture1: Texture2d,
        framebuffer2: Framebuffer, texture2: Texture2d
    ) {
        val passes = passes

        glViewport(0, 0, size.width, size.height)

        program2.getUniform(Uniform.Type.Vec2, "resolution0").let {
            val data = it.data!!
            data[0] = size.width.toFloat()
            data[1] = size.height.toFloat()
            it.dirty = true
        }

        framebuffer1.bind()
        glUseProgram(program1.programId)
        msg.data.bind(GL_TEXTURE0)
        program1.bindUniforms()
        mesh.execute()

        framebuffer2.bind()
        glUseProgram(program2.programId)
        texture1.bind(GL_TEXTURE0)
        program2.bindUniforms()
        mesh.execute()

        for (i in 1 until passes) {
            framebuffer1.bind()
            texture2.bind(GL_TEXTURE0)
            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 1f
                data[1] = 0f
                it.dirty = true
            }
            program2.bindUniforms()
            mesh.execute()

            framebuffer2.bind()
            texture1.bind(GL_TEXTURE0)

            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 0f
                data[1] = 1f
                it.dirty = true
            }
            program2.bindUniforms()
            mesh.execute()
        }
    }

    private suspend fun onStop() {
        Log.d(TAG, "onStop")
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        gl.glContext {
            mesh.release()

            texture1.release()
            texture2.release()
            texture3.release()
            framebuffer1.release()
            framebuffer2.release()
            framebuffer3.release()
            program1.release()
            program2.release()
        }
    }

    companion object {
        const val TAG = "BlurNode"
        val INPUT = Connection.Key<Texture2d>("blur_input")
        val OUTPUT = Connection.Key<Texture2d>("blur_output")
    }
}
package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.Message
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class Lut3dNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var inputJob: Job? = null
    private var lutJob: Job? = null
    private var matrixJob: Job? = null
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var outputSize = properties[VideoSize]

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var inputEvent: Message<Texture2d>? = null
    private var lutEvent: Message<Texture3d>? = null
    private var matrixEvent: Message<FloatArray>? = null

    private val debounce = AtomicBoolean()
    private var frameCount = 0
    private var lastExecutionTime = 0L


    override suspend fun onSetup() {
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT -> {
                if (inputJob == null) {
                    inputJob = scope.launch {
                        startInput()
                    }
                }
            }
            INPUT_LUT -> {
                if (lutJob == null) {
                    lutJob = scope.launch {
                        startLut()
                    }
                }
            }
            LUT_MATRIX -> {
                if (matrixJob == null) {
                    matrixJob = scope.launch {
                        startMatrix()
                    }
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT -> {
                inputJob?.join()
                inputJob = null
            }
            INPUT_LUT -> {
                lutJob?.join()
                lutJob = null
            }
            LUT_MATRIX -> {
                matrixJob?.join()
                matrixJob = null
            }
        }
    }

    private suspend fun checkConfig() {
        val inputEvent = inputEvent ?: return
        val lutEvent = lutEvent ?: return
        if (program.programId != 0) {
            return
        }

        connection(OUTPUT)?.prime(texture1, texture2)

        outputSize = Size(inputEvent.data.width, inputEvent.data.height)

        glesManager.glContext {
            initRenderTarget(
                framebuffer1,
                texture1,
                lutEvent.data
            )
            initRenderTarget(
                framebuffer2,
                texture2,
                lutEvent.data
            )

            val vertex = assetManager.readTextAsset("shader/lut_3d.vert")
            val frag = assetManager.readTextAsset("shader/lut_3d.frag").let {
                if (inputEvent.data.oes) {
                    it.replace("//{EXT_INPUT}", "#define EXT_INPUT")
                } else {
                    it
                }
            }

            quadMesh.initialize()

            program.apply {
                initialize(vertex, frag)
                addUniform(
                    Uniform.Type.Mat4,
                    INPUT_MATRIX,
                    GlesManager.identityMat()
                )
                addUniform(
                    Uniform.Type.Mat4,
                    LUT_MATRIX.id,
                    GlesManager.identityMat()
                )
                addUniform(
                    Uniform.Type.Int,
                    "input_texture",
                    INPUT_TEXTURE_LOCATION
                )
                addUniform(
                    Uniform.Type.Int,
                    "lut_texture",
                    LUT_TEXTURE_LOCATION
                )
            }
        }
    }

    private fun initRenderTarget(
        framebuffer: Framebuffer,
        texture: Texture2d,
        config: Texture3d
    ) {
        texture.initialize()
        texture.initData(
            0,
            config.internalFormat,
            outputSize.width,
            outputSize.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
        framebuffer.initialize(texture)
    }

    private suspend fun startInput() {
        val channel = channel(INPUT) ?: error("missing input")
        for (msg in channel) {
            inputEvent?.release()
            inputEvent = msg
            checkConfig()
            execute()
//            debounceExecute()
        }
        inputEvent?.let { Log.d(TAG, "got ${it.count} input events") }
        inputEvent?.release()
        inputEvent = null
    }

    private suspend fun startLut() {
        val channel = channel(INPUT_LUT) ?: error("missing input lut")
        for (msg in channel) {
            lutEvent?.release()
            lutEvent = msg
            checkConfig()
//            debounceExecute()
        }
        lutEvent?.let { Log.d(TAG, "got ${it.count} lut events") }
        lutEvent?.release()
        lutEvent = null
    }

    private suspend fun startMatrix() {
        val channel = channel(LUT_MATRIX) ?: error("missing input matrix")
        for (msg in channel) {
            matrixEvent?.release()
            matrixEvent = msg
//            debounceExecute()
        }
        matrixEvent?.let { Log.d(TAG, "got ${it.count} matrix events") }
        matrixEvent?.release()
        matrixEvent = null
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            texture1.release()
            texture2.release()
            framebuffer1.release()
            framebuffer2.release()
            quadMesh.release()
            program.release()
        }
    }

    private suspend fun execute() {
        val inputTexture = inputEvent?.data ?: return
        val lutTexture = lutEvent?.data ?: return
        val output = channel(OUTPUT) ?: return
        if (program.programId == 0) return

        val outEvent = output.receive()

        val timestamp = max(
            inputEvent?.timestamp ?: 0,
            lutEvent?.timestamp ?: 0
        )

        val framebuffer = if (outEvent.data == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        val uniform = program.getUniform(Uniform.Type.Mat4, INPUT_MATRIX)
        System.arraycopy(inputTexture.matrix, 0, uniform.data!!, 0, 16)
        uniform.dirty = true

        matrixEvent?.let {
            program.getUniform(Uniform.Type.Mat4, LUT_MATRIX.id).set(it.data)
        }

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            inputTexture.bind(GLES30.GL_TEXTURE0)
            lutTexture.bind(GLES30.GL_TEXTURE1)
            program.bindUniforms()
            quadMesh.execute()
        }

        outEvent.let {
            it.timestamp = timestamp
            it.queue()
        }
    }

    companion object {
        const val TAG = "Lut3dNode"
        const val INPUT_TEXTURE_LOCATION = 0
        const val LUT_TEXTURE_LOCATION = 1
        const val INPUT_MATRIX = "input_matrix"
        val INPUT = Connection.Key<Texture2d>("input")
        val INPUT_LUT = Connection.Key<Texture3d>("input_lut")
        val LUT_MATRIX = Connection.Key<FloatArray>("lut_matrix")
        val OUTPUT = Connection.Key<Texture2d>("output")
    }
}
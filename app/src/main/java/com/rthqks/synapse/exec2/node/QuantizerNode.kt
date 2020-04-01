package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.Message
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.CropSize
import com.rthqks.synapse.logic.NumElements
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QuantizerNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val am = context.assetManager
    private val gl = context.glesManager
    private var outScaleX: Float = 1f
    private var outScaleY: Float = 1f
    private var startJob: Job? = null
//    private val outputSize: Size get() = properties[CropSize]
    private var outputSize: Size = Size(0, 0)
    private val numElements: FloatArray get() = properties[NumElements]
    private var inputSize = Size(0, 0)
    private var outputConfig: Texture2d? = null

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()
    private val program = Program()
    private val quadMesh = Quad()
    private var needsPriming = true

    override suspend fun onSetup() {
        Log.d(TAG, "onSetup")
        gl.glContext {
            quadMesh.initialize()
            texture1.initialize()
            texture2.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture1, texture2)
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
        when (key) {
            INPUT -> {
                onStop()
            }
            OUTPUT -> {
            }
        }
    }

    private suspend fun checkConfig(texture2d: Texture2d) {
        val inputSizeChanged = inputSize.width != texture2d.width
                || inputSize.height != texture2d.height

        val outputSizeChanged = texture2d.width != outputSize.width
                || texture2d.height != outputSize.height

        val oesChanged = outputConfig?.oes != texture2d.oes

        if (oesChanged) {
            outputConfig = texture2d
            gl.glContext {
                val vertex = am.readTextAsset("shader/quantizer.vert")
                val frag = am.readTextAsset("shader/quantizer.frag").let {
                    if (texture2d.oes) {
                        it.replace("//{EXT}", "#define EXT")
                    } else {
                        it
                    }
                }

                program.apply {
                    release()
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Mat4,
                        "input_matrix",
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "input_texture",
                        INPUT_TEXTURE_LOCATION
                    )
                    addUniform(
                        Uniform.Type.Vec3,
                        NumElements.name,
                        numElements
                    )
                }
            }
        }

        if (outputSizeChanged) {
            outputSize = Size(texture2d.width, texture2d.height)
            gl.glContext {
                initTextureData(texture1, texture2d)
                initTextureData(texture2, texture2d)

                framebuffer1.release()
                framebuffer1.initialize(texture1)
                framebuffer2.release()
                framebuffer2.initialize(texture2)
            }
        }

        if (inputSizeChanged) {
            inputSize = Size(texture2d.width, texture2d.height)
            val inAspect = inputSize.width / inputSize.height.toFloat()
            val outAspect = outputSize.width / outputSize.height.toFloat()

            outScaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
            outScaleY = if (inAspect < outAspect) inAspect / outAspect else 1f
        }
    }

    private fun initTextureData(
        texture: Texture2d,
        config: Texture2d
    ) {
        texture.initData(
            0,
            config.internalFormat,
            outputSize.width,
            outputSize.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
    }

    private suspend fun onStart() {
        Log.d(TAG, "onStart")
        val inputIn = channel(INPUT) ?: error("missing input")
        var copyMatrix = true

        for (msg in inputIn) {
            checkConfig(msg.data)
            if (copyMatrix) {
                copyMatrix = false
                val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
                val matrix = uniform.data!!
                System.arraycopy(msg.data.matrix, 0, matrix, 0, 16)
                Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                Matrix.scaleM(matrix, 0, outScaleX, outScaleY, 1f)
                Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                uniform.dirty = true
            }
            program.getUniform(Uniform.Type.Vec3, NumElements.name).set(numElements)
            execute(msg)
            msg.release()
        }
    }

    private suspend fun onStop() {
        Log.d(TAG, "onStop")

        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        gl.glContext {
            texture1.release()
            texture2.release()
            framebuffer1.release()
            framebuffer2.release()
            quadMesh.release()
            program.release()
        }
    }

    private suspend fun execute(msg: Message<Texture2d>) {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val framebuffer = if (outEvent.data == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        gl.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            msg.data.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            quadMesh.execute()
        }

        outEvent.timestamp = msg.timestamp
        outEvent.queue()
    }

    companion object {
        const val TAG = "Quantizer"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT = Connection.Key<Texture2d>("input")
        val OUTPUT = Connection.Key<Texture2d>("output")
    }
}
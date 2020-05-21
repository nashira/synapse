package com.rthqks.synapse.exec.node

import android.opengl.GLES30.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.BlurSize
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.CropEnabled
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.CropSize
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.GrayEnabled
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.NumPasses
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class CropGrayBlurNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {

    private var needsPriming = true
    private val gl = context.glesManager
    private val am = context.assetManager
    private var startJob: Job? = null
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

    private val passes: Int get() = properties[NumPasses]
    private val scale: Boolean get() = properties[CropEnabled]
    private var grayscale = 0
    private var blurSize: Int = 0
    private var inputSize = Size(0, 0)
    private var outputSize = Size(0, 0)
    private var outScaleX: Float = 1f
    private var outScaleY: Float = 1f
    private var stepSize = FloatArray(4)
    private var stepSizeRot = FloatArray(8)

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

        val inputSizeChanged = inputSize.width != texture.width
                || inputSize.height != texture.height

        val cropSize = properties[CropSize]

        val outputSizeChanged = if (scale) texture2.width != cropSize.width
                || texture2.height != cropSize.height
        else texture2.width != texture.width
                || texture2.height != texture.height

        val grayEnabled = if (properties[GrayEnabled]) 1 else 0
        val blurSizeChanged = blurSize != properties[BlurSize]
        val grayscaleChanged = grayscale != grayEnabled

        if (inputSizeChanged || outputSizeChanged) {
            inputSize = Size(texture.width, texture.height)
            outputSize = if (scale) cropSize else inputSize
            // camera sends matrix to transform uv to correct space, need to transform offsets too
            stepSize[0] = 1f / outputSize.width
            stepSize[1] = 1f / outputSize.height
            stepSizeRot[0] = 1f / outputSize.width
            stepSizeRot[1] = 1f / outputSize.height

            val inAspect = inputSize.width / inputSize.height.toFloat()
            val outAspect = outputSize.width / outputSize.height.toFloat()

            outScaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
            outScaleY = if (inAspect < outAspect) inAspect / outAspect else 1f
        }

        if (outputSizeChanged) {
            gl.glContext {
                initializeFramebuffer(texture1, framebuffer1, texture)
                initializeFramebuffer(texture2, framebuffer2, texture)
                initializeFramebuffer(texture3, framebuffer3, texture)
            }
        }

        if (programChanged) {
            initializeProgram(program1, texture.oes)
            initializeProgram(program2, false)
            config = texture
        }

        if (blurSizeChanged) {
//            if (blurSize == 0) {
//
//            } else {
//
//            }
            blurSize = properties[BlurSize]
            program1.getUniform(Uniform.Type.Int, "mode").set(blurSize)
            program2.getUniform(Uniform.Type.Int, "mode").set(blurSize)
        }

        if (grayscaleChanged) {
            grayscale = grayEnabled
            program1.getUniform(Uniform.Type.Int, "grayscale").set(grayscale)
        }
    }

    private fun initializeFramebuffer(target: Texture2d, framebuffer: Framebuffer, config: Texture2d) {
        target.initData(
            0,
            config.internalFormat,
            outputSize.width,
            outputSize.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${glGetError()}")
        framebuffer.release()
        framebuffer.initialize(target)
    }

    private suspend fun initializeProgram(
        program: Program,
        oes: Boolean
    ) {
        val vertexSource = am.readTextAsset("shader/blur.vert")
        val fragmentSource = am.readTextAsset("shader/blur.frag").let {
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
                    "step_size",
                    floatArrayOf(0f, 0f)
                )
                addUniform(
                    Uniform.Type.Int,
                    "mode",
                    blurSize
                )
                addUniform(
                    Uniform.Type.Int,
                    "grayscale",
                    0
                )
            }
        }
    }

    private suspend fun onStart() {
        val input = channel(INPUT) ?: error("missing input")

        for (inEvent in input) {
            val outEvent = channel(OUTPUT)?.receive()
            outEvent?.timestamp = inEvent.timestamp

            checkConfig(inEvent.data)

            if (outEvent != null) {
                val uniform = program1.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                val matrix = uniform.data!!
                // multiply our coords by input matrix before we scale or do anything to it
                Matrix.multiplyMV(stepSizeRot, 4, matrix, 0, stepSizeRot, 0)
                System.arraycopy(inEvent.data.matrix, 0, matrix, 0, 16)
                Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                Matrix.scaleM(matrix, 0, outScaleX, outScaleY, 1f)
                Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                uniform.dirty = true

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
                    if (blurSize > 0) {
                        executeBlur(
                            inEvent,
                            framebuffer1, texture1,
                            targetFB, targetTexture
                        )
                    } else {
                        executeNonBlur(
                            inEvent,
                            targetFB
                        )
                    }
                }
                outEvent.timestamp = inEvent.timestamp
                outEvent.queue()
            }

            inEvent.release()
        }
    }

    private fun executeNonBlur(
        msg: Message<Texture2d>,
        framebuffer: Framebuffer
    ) {
        glViewport(0, 0, outputSize.width, outputSize.height)

        framebuffer.bind()
        glUseProgram(program1.programId)
        msg.data.bind(GL_TEXTURE0)
        program1.bindUniforms()
        mesh.execute()
    }

    private fun executeBlur(
        msg: Message<Texture2d>,
        framebuffer1: Framebuffer, texture1: Texture2d,
        framebuffer2: Framebuffer, texture2: Texture2d
    ) {
        val passes = passes

        program1.getUniform(
            Uniform.Type.Vec2,
            "step_size"
        ).let {
            it.dirty = true
            it.data!![0] = abs(stepSizeRot[4])
            it.data!![1] = abs(stepSizeRot[5])
        }

        program2.getUniform(
            Uniform.Type.Vec2,
            "step_size"
        ).let {
            it.dirty = true
            it.data!![0] = -stepSize[0]
            it.data!![1] = stepSize[1]
        }

        glViewport(0, 0, outputSize.width, outputSize.height)

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
            program2.getUniform(Uniform.Type.Vec2, "step_size").let {
                val data = it.data!!
                data[0] = stepSize[0]
                data[1] = stepSize[1]
                it.dirty = true
            }
            program2.bindUniforms()
            mesh.execute()

            framebuffer2.bind()
            texture1.bind(GL_TEXTURE0)

            program2.getUniform(Uniform.Type.Vec2, "step_size").let {
                val data = it.data!!
                data[0] = -stepSize[0]
                data[1] = stepSize[1]
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
        val INPUT = Connection.Key<Texture2d>(CropGrayBlur.INPUT.key)
        val OUTPUT = Connection.Key<Texture2d>(CropGrayBlur.OUTPUT.key)
    }
}
package com.rthqks.flow.exec.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import com.rthqks.flow.exec.*
import com.rthqks.flow.gl.*
import com.rthqks.flow.logic.NodeDef.Sobel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SobelNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val am = context.assetManager
    private val gl = context.glesManager
    private var startJob: Job? = null
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

        val oesChanged = outputConfig?.oes != texture2d.oes

        if (inputSizeChanged) {
            inputSize = Size(texture2d.width, texture2d.height)

            gl.glContext {
                initTextureData(texture1, texture2d)
                initTextureData(texture2, texture2d)

                framebuffer1.release()
                framebuffer1.initialize(texture1)
                framebuffer2.release()
                framebuffer2.initialize(texture2)
            }
        }

        if (oesChanged) {
            outputConfig = texture2d
            gl.glContext {
                val vertex = am.readTextAsset("shader/sobel.vert")
                val frag = am.readTextAsset("shader/sobel.frag").let {
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
                        Uniform.Type.Vec2,
                        "input_size",
                        floatArrayOf(1f / inputSize.width, 1f / inputSize.height)
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "frame_count",
                        0
                    )
                }
            }
        }
    }

    private fun initTextureData(
        texture: Texture2d,
        config: Texture2d
    ) {
        texture.initData(
            0,
            config.internalFormat,
            inputSize.width,
            inputSize.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
    }

    private suspend fun onStart() {
        Log.d(TAG, "onStart")
        val inputIn = channel(INPUT) ?: error("missing input")

        for (msg in inputIn) {
            checkConfig(msg.data)
            val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
            val matrix = uniform.data!!
            System.arraycopy(msg.data.matrix, 0, matrix, 0, 16)
            uniform.dirty = true
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
            GLES30.glViewport(0, 0, inputSize.width, inputSize.height)
            msg.data.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            quadMesh.execute()
        }

        outEvent.timestamp = msg.timestamp
        outEvent.queue()
    }

    companion object {
        const val TAG = "SobelNode"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT = Connection.Key<Texture2d>(Sobel.INPUT.key)
        val OUTPUT = Connection.Key<Texture2d>(Sobel.OUTPUT.key)
    }
}
package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.Message
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.BlendMode
import com.rthqks.synapse.logic.Opacity
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ImageBlendNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val gl = context.glesManager
    private var baseJob: Job? = null
    private var blendJob: Job? = null
    private val blendMode: Int get() = properties[BlendMode]
    private val opacity: Float get() = properties[Opacity]
    private var needsPriming = true
    private var outputSize = Size(0, 0)

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var baseEvent: Message<Texture2d>? = null
    private var blendEvent: Message<Texture2d>? = null
    private var baseConfig: Texture2d? = null
    private var blendConfig: Texture2d? = null

    override suspend fun onSetup() {
        gl.glContext {
            quadMesh.initialize()
            texture1.initialize()
            texture2.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT_BASE -> {
                if (baseJob == null) {
                    baseJob = scope.launch {
                        startBase()
                    }
                }
            }
            INPUT_BLEND -> {
                if (blendJob == null) {
                    blendJob = scope.launch {
                        startBlend()
                    }
                }
            }
            OUTPUT -> if (needsPriming) {
                needsPriming = false
                connection(OUTPUT)?.prime(texture1, texture2)
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT_BASE -> {
                baseJob?.join()
                baseJob = null
            }
            INPUT_BLEND -> {
                blendJob?.join()
                blendJob = null
            }
        }
    }

    private suspend fun checkConfig() {
        val base = baseEvent?.data ?: return
        val blend = blendEvent?.data ?: return

        val oesChanged = base.oes != baseConfig?.oes || blend.oes != blendConfig?.oes

        val sizeChanged = outputSize.width != base.width
                || outputSize.height != base.height

        if (oesChanged) {
            baseConfig = base
            blendConfig = blend

            gl.glContext {
                val vertex = assetManager.readTextAsset("shader/image_blend.vert")
                val frag = assetManager.readTextAsset("shader/image_blend.frag").let {
                    val s = if (base.oes) {
                        it.replace("//{EXT_BASE}", "#define EXT_BASE")
                    } else {
                        it
                    }
                    if (blend.oes) {
                        s.replace("//{EXT_BLEND}", "#define EXT_BLEND")
                    } else {
                        s
                    }
                }

                program.apply {
                    release()
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Mat4,
                        "base_matrix",
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Mat4,
                        "blend_matrix",
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "base_texture",
                        BASE_TEXTURE_LOCATION
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "blend_texture",
                        BLEND_TEXTURE_LOCATION
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "blend_mode",
                        blendMode
                    )
                    addUniform(
                        Uniform.Type.Float,
                        UNI_OPACITY,
                        opacity
                    )
                }
            }
        }

        if (sizeChanged) {
            outputSize = Size(base.width, base.height)
            gl.glContext {
                initRenderTarget(framebuffer1, texture1, base)
                initRenderTarget(framebuffer2, texture2, base)
            }
        }
    }

    private fun initRenderTarget(
        framebuffer: Framebuffer,
        texture: Texture2d,
        config: Texture2d
    ) {
        texture.initData(
            0,
            config.internalFormat,
            config.width,
            config.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
        framebuffer.release()
        framebuffer.initialize(texture)
    }


    private suspend fun startBase() {
        val channel = channel(INPUT_BASE) ?: error("missing base input")
        for (msg in channel) {
            baseEvent?.release()
            baseEvent = msg
            checkConfig()
            execute()
        }
        baseEvent?.let { Log.d(TAG, "got ${it.count} input events") }
        baseEvent?.release()
        baseEvent = null
    }

    private suspend fun startBlend() {
        val channel = channel(INPUT_BLEND) ?: error("missing input blend")
        for (msg in channel) {
            blendEvent?.release()
            blendEvent = msg
        }
        blendEvent?.let { Log.d(TAG, "got ${it.count} lut events") }
        blendEvent?.release()
        blendEvent = null
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

    private suspend fun execute() {
        val output = channel(OUTPUT) ?: return
        val baseInTexture = baseEvent?.data ?: return
        val blendInTexture = blendEvent?.data ?: return

        val outEvent = output.receive()

        val framebuffer = if (outEvent.data == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        program.getUniform(Uniform.Type.Mat4, "base_matrix").let {
            System.arraycopy(baseInTexture.matrix, 0, it.data!!, 0, 16)
            it.dirty = true
        }

        program.getUniform(Uniform.Type.Mat4, "blend_matrix").let {
            System.arraycopy(blendInTexture.matrix, 0, it.data!!, 0, 16)
            it.dirty = true
        }

        program.getUniform(Uniform.Type.Int, "blend_mode").apply {
            data = blendMode
            dirty = true
        }

        program.getUniform(Uniform.Type.Float, UNI_OPACITY).apply {
            data = opacity
            dirty = true
        }

        gl.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            baseInTexture.bind(GLES30.GL_TEXTURE0)
            blendInTexture.bind(GLES30.GL_TEXTURE1)
            program.bindUniforms()
            quadMesh.execute()
        }
        baseEvent?.let {
            outEvent.timestamp = it.timestamp
        }
        outEvent.queue()
    }

    companion object {
        const val TAG = "ImageBlendNode"
        const val BASE_TEXTURE_LOCATION = 0
        const val BLEND_TEXTURE_LOCATION = 1
        const val UNI_OPACITY = "opacity"
        val INPUT_BASE = Connection.Key<Texture2d>("input_base")
        val INPUT_BLEND = Connection.Key<Texture2d>("input_blend")
        val OUTPUT = Connection.Key<Texture2d>("output")
    }
}
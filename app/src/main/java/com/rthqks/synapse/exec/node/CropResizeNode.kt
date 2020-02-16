package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CropResizeNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var outScaleX: Float = 1f
    private var outScaleY: Float = 1f
    private var startJob: Job? = null
    private val outputSize = properties[VideoSize]
    private var inputSize = Size(0, 0)
    private var outputConfig = DEFAULT_CONFIG

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var frameCount = 0

    private val config: VideoConfig by lazy {
        VideoConfig(
            GLES30.GL_TEXTURE_2D,
            outputSize.width,
            outputSize.height,
            outputConfig.internalFormat,
            outputConfig.format,
            outputConfig.type,
            outputConfig.fps
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                if (linked(INPUT)) {
                    outputConfig = configAsync(INPUT).await()
                    inputSize = outputConfig.size
                }
                Log.d(TAG, "after output $outputSize")
                config as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun create() {
    }

    override suspend fun initialize() {
        val input = connection(INPUT)
        val output = connection(OUTPUT)

        output?.prime(VideoEvent(texture1), VideoEvent(texture2))

        val inAspect = inputSize.width / inputSize.height.toFloat()
        val outAspect = outputSize.width / outputSize.height.toFloat()

        outScaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
        outScaleY = if (inAspect < outAspect) inAspect / outAspect else 1f

        glesManager.glContext {
            initRenderTarget(framebuffer1, texture1, config)
            initRenderTarget(framebuffer2, texture2, config)

            val vertex = assetManager.readTextAsset("shader/crop_resize.vert")
            val frag = assetManager.readTextAsset("shader/crop_resize.frag").let {
                if (input?.config?.isOes == true) {
                    it.replace("//{EXT}", "#define EXT")
                } else {
                    it
                }
            }

            quadMesh.initialize()

            program.apply {
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
            }
        }
    }

    private fun initRenderTarget(
        framebuffer: Framebuffer,
        texture: Texture2d,
        config: VideoConfig
    ) {
        texture.initialize()
        texture.initData(
            0,
            config.internalFormat,
            config.width,
            config.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
        framebuffer.initialize(texture)
    }

    override suspend fun start() = coroutineScope {
        frameCount = 0

        startJob = launch {
            val inputLinked = linked(INPUT)
            if (!inputLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var copyMatrix = true
            val inputIn = channel(INPUT) ?: return@launch

            while (isActive) {
                val inputEvent = inputIn.receive()
                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
                    val matrix = uniform.data!!
                    System.arraycopy(inputEvent.matrix, 0, matrix, 0, 16)
                    Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                    Matrix.scaleM(matrix, 0, outScaleX, outScaleY, 1f)
                    Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                    uniform.dirty = true
                }
                execute(inputEvent.texture)
                inputIn.send(inputEvent)
                if (inputEvent.eos) {
                    startJob?.cancel()
                }
            }
        }
    }

    override suspend fun stop() {
        startJob?.join()
        val output = channel(OUTPUT)

        output?.receive()?.also {
            it.eos = true
            output.send(it)
        }
    }

    override suspend fun release() {
        texture1.release()
        texture2.release()
        framebuffer1.release()
        framebuffer2.release()
        quadMesh.release()
        program.release()
    }

    private suspend fun execute(inputTexture: Texture2d) {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val framebuffer = if (outEvent.texture == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            inputTexture.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            quadMesh.execute()
        }

        frameCount++

        outEvent.let {
            it.count = frameCount
            it.eos = false
            output.send(it)
        }
    }

    companion object {
        const val TAG = "CropResizeNode"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("input")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("output")
        val DEFAULT_CONFIG = VideoConfig(
            0, 0, 0, GLES30.GL_RGB8, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, 0
        )
    }
}
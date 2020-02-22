package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.HistorySize
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RingBufferNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var outScaleX: Float = 1f
    private var outScaleY: Float = 1f
    private var startJob: Job? = null
    private var outputSize = properties[VideoSize]
    private val depth: Int get() = properties[HistorySize]
    private var inputSize = Size(0, 0)
    private var outputConfig = DEFAULT_CONFIG

    private val texture = Texture3d()
    private var currentLevel = 0
    private val framebuffers = List(depth) { Framebuffer() }

    private val program = Program()
    private val quadMesh = Quad()

    private var frameCount = 0

    private val config: Texture3dConfig by lazy {
        Texture3dConfig(
            outputSize.width,
            outputSize.height,
            depth,
            outputConfig.internalFormat,
            outputConfig.format,
            outputConfig.type
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                if (linked(INPUT)) {
                    val inputConfig = configAsync(INPUT).await()
                    outputSize = inputConfig.size
                    outputConfig = Texture3dConfig(
                        0, 0, 0,
                        inputConfig.internalFormat,
                        inputConfig.format,
                        inputConfig.type
                    )
                    inputSize = inputConfig.size
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

        output?.prime(Texture3dEvent(texture), Texture3dEvent(texture))

        val inAspect = inputSize.width / inputSize.height.toFloat()
        val outAspect = outputSize.width / outputSize.height.toFloat()

        outScaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
        outScaleY = if (inAspect < outAspect) inAspect / outAspect else 1f

        glesManager.glContext {
            texture.initialize()
            texture.initData(
                0,
                config.internalFormat,
                outputSize.width,
                outputSize.height,
                depth,
                config.format,
                config.type
            )
            framebuffers.forEachIndexed { index, framebuffer ->
                framebuffer.initialize(texture, index)
                Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
            }

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
                execute(inputEvent.texture, inputEvent.timestamp)
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
        glesManager.glContext {
            framebuffers.forEach { it.release() }
            texture.release()
            quadMesh.release()
            program.release()
        }
    }

    private suspend fun execute(
        inputTexture: Texture2d,
        timestamp: Long
    ) {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val framebuffer = framebuffers[currentLevel]
        currentLevel = (currentLevel + 1) % framebuffers.size

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
            it.timestamp = timestamp
            it.index = currentLevel
            output.send(it)
        }
    }

    companion object {
        const val TAG = "RingBufferNode"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("input")
        val OUTPUT = Connection.Key<Texture3dConfig, Texture3dEvent>("output")
        val DEFAULT_CONFIG = Texture3dConfig(
            0, 0, 0, GLES30.GL_RGB8, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE
        )
    }
}
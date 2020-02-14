package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.SliceDepth
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class Slice3dNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private val sliceDepth: Float get() = properties[SliceDepth]
    private var outputSize = properties[VideoSize]
    private var outputConfig = DEFAULT_CONFIG

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var inputEvent: VideoEvent? = null
    private var t3dEvent: Texture3dEvent? = null

    private val debounce = AtomicBoolean()
    private var frameCount = 0
    private var lastExecutionTime = 0L

    private val config: VideoConfig by lazy {
        VideoConfig(
            GLES30.GL_TEXTURE_2D,
            outputSize.width,
            outputSize.height,
            outputConfig.internalFormat,
            outputConfig.format,
            outputConfig.type
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                Log.d(TAG, "before output $outputSize")
//                if (linked(INPUT)) {
//                    val config = configAsync(INPUT).await()
//                    outputSize = config.size
//                    if (config.format != GLES30.GL_RGB) {
//                        Log.w(TAG, "input doesn't have 3 components")
//                    }
//                }

                if (linked(INPUT_3D)) {
                    outputConfig = configAsync(INPUT_3D).await()
                    outputSize = Size(outputConfig.width, outputConfig.height)
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
//        val input = connection(INPUT)
        val output = connection(OUTPUT)

        output?.prime(VideoEvent(texture1), VideoEvent(texture2))

        glesManager.glContext {
            initRenderTarget(framebuffer1, texture1, config)
            initRenderTarget(framebuffer2, texture2, config)

            val vertex = assetManager.readTextAsset("shader/slice_3d.vert")
            val frag = assetManager.readTextAsset("shader/slice_3d.frag")

            quadMesh.initialize()

            program.apply {
                initialize(vertex, frag)
                addUniform(
                    Uniform.Type.Mat4,
                    "input_matrix",
                    GlesManager.identityMat()
                )
//                addUniform(
//                    Uniform.Type.Int,
//                    "input_texture",
//                    INPUT_TEXTURE_LOCATION
//                )
                addUniform(
                    Uniform.Type.Int,
                    T3D_TEXTURE,
                    LUT_TEXTURE_LOCATION
                )
                addUniform(
                    Uniform.Type.Float,
                    T3D_LAYER,
                    0f
                )
                addUniform(
                    Uniform.Type.Float,
                    T3D_DEPTH,
                    (outputConfig.depth - 1f) / outputConfig.depth
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
//            val inputLinked = linked(INPUT)
            val t3dLinked = linked(INPUT_3D)
            if (!t3dLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var running = 0
            var copyMatrix = true
//            val inputIn = channel(INPUT)
            val t3dIn = channel(INPUT_3D)
//            if (inputLinked) running++
            if (t3dLinked) running++

            whileSelect {
//                inputIn?.onReceive {
////                    Log.d(TAG, "input receive")
//                    inputEvent?.let { inputIn.send(it) }
//                    inputEvent = it
//                    if (copyMatrix) {
//                        copyMatrix = false
//                        val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
//                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
//                        uniform.dirty = true
//                    }
//                    debounceExecute(this@launch)
//                    if (it.eos) running--
//                    running > 0
//                }
                t3dIn?.onReceive {
//                    Log.d(TAG, "lut receive")
                    t3dEvent?.let { t3dIn.send(it) }
                    t3dEvent = it
                    debounceExecute(this@launch)
                    if (it.eos) running--
                    running > 0
                }
            }

//            inputEvent?.let { Log.d(TAG, "got ${it.count} input events") }
            t3dEvent?.let { Log.d(TAG, "got ${it.count} lut events") }

//            inputEvent?.let { inputIn?.send(it) }
            t3dEvent?.let { t3dIn?.send(it) }
            inputEvent = null
            t3dEvent = null
        }
    }

    override suspend fun stop() {
        startJob?.join()
        val output = channel(OUTPUT)

        output?.receive()?.also {
            it.eos = true
            output.send(it)
        }
        Log.d(TAG, "sent $frameCount")
    }

    override suspend fun release() {
        texture1.release()
        texture2.release()
        framebuffer1.release()
        framebuffer2.release()
        quadMesh.release()
        program.release()
    }

    private suspend fun execute() {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val inputTexture = inputEvent?.texture ?: glesManager.emptyTexture2d
        val t3dTexture = t3dEvent?.texture ?: glesManager.emptyTexture3d
        val t3dLayer = t3dEvent?.let { it.index/outputConfig.depth.toFloat() + 0.5f/outputConfig.depth } ?: 0f

        val framebuffer = if (outEvent.texture == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        val slice = (sliceDepth + t3dLayer) % 1f
//        Log.d(TAG, "slice $t3dLayer $sliceDepth $slice")
        program.getUniform(Uniform.Type.Float, T3D_LAYER).set(slice)

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
//            inputTexture.bind(GLES30.GL_TEXTURE0)
            t3dTexture.bind(GLES30.GL_TEXTURE0)
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

    private suspend fun debounceExecute(scope: CoroutineScope) {
        if (!debounce.getAndSet(true)) {
            scope.launch {
                while (debounce.getAndSet(false)) {
                    val delay = max(
                        0,
                        frameDuration - (SystemClock.elapsedRealtime() - lastExecutionTime)
                    )
                    delay(delay)
                    lastExecutionTime = SystemClock.elapsedRealtime()
                    execute()
                }
            }
        }
    }

    companion object {
        const val TAG = "Slice3dNode"
        const val INPUT_TEXTURE_LOCATION = 0
        const val LUT_TEXTURE_LOCATION = 0
        const val T3D_TEXTURE = "t3d_texture"
        const val T3D_LAYER = "t3d_layer"
        const val T3D_DEPTH = "t3d_depth"
//        val INPUT = Connection.Key<VideoConfig, VideoEvent>("input")
        val INPUT_3D = Connection.Key<Texture3dConfig, Texture3dEvent>("input_3d")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("output")
        val DEFAULT_CONFIG = Texture3dConfig(
            0, 0, 0, GLES30.GL_RGB8, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE
        )
    }
}
package com.rthqks.synapse.exec_dep.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec_dep.NodeExecutor
import com.rthqks.synapse.exec_dep.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class Lut2dNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var outputSize = properties[VideoSize]
    private var outputConfig = DEFAULT_CONFIG

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var inputEvent: VideoEvent? = null
    private var lutEvent: VideoEvent? = null

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
            outputConfig.type,
            properties[FrameRate]
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                Log.d(TAG, "before output $outputSize")
                if (linked(INPUT)) {
                    val config = configAsync(INPUT).await()
                    outputSize = config.size
                    if (config.format != GLES30.GL_RG && config.format != GLES30.GL_RED) {
                        Log.w(TAG, "can't lookup a 3D value from a 2D texture")
                    }
                }

                if (linked(INPUT_LUT)) {
                    outputConfig = configAsync(INPUT_LUT).await()
                }
                Log.d(TAG, "after output $outputSize")
                config as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun onCreate() {
    }

    override suspend fun onInitialize() {
        val input = connection(INPUT)
        val inputLut = connection(INPUT_LUT)
        val output = connection(OUTPUT)

        output?.prime(VideoEvent(texture1), VideoEvent(texture2))

        glesManager.glContext {
            initRenderTarget(framebuffer1, texture1, config)
            initRenderTarget(framebuffer2, texture2, config)

            val vertex = assetManager.readTextAsset("shader/lut_2d.vert")
            val frag = assetManager.readTextAsset("shader/lut_2d.frag").let {
                val s = if (input?.config?.isOes == true) {
                    it.replace("//{EXT_INPUT}", "#define EXT_INPUT")
                } else {
                    it
                }
                if (inputLut?.config?.isOes == true) {
                    s.replace("//{EXT_LUT}", "#define EXT_LUT")
                } else {
                    s
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

    override suspend fun onStart() {
        frameCount = 0


        startJob = scope.launch {
            val inputLinked = linked(INPUT)
            val lutLinked = linked(INPUT_LUT)
            if (!inputLinked && !lutLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var running = 0
            var copyMatrix = true
            val inputIn = channel(INPUT)
            val lutIn = channel(INPUT_LUT)
            if (inputLinked) running++
            if (lutLinked) running++

            whileSelect {
                if (inputIn != null)
                inputIn.onReceive {
                    //                    Log.d(TAG, "agent receive")
                    inputEvent?.release()
                    inputEvent = it
                    if (copyMatrix) {
                        copyMatrix = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    debounceExecute(this@launch)
                    if (it.eos) running--
                    running > 0
                }
                if (lutIn != null)
                lutIn.onReceive {
                    //                    Log.d(TAG, "env receive")
                    lutEvent?.release()
                    lutEvent = it
                    debounceExecute(this@launch)
                    if (it.eos) running--
                    running > 0
                }
            }

            inputEvent?.let { Log.d(TAG, "got ${it.count} input events") }
            lutEvent?.let { Log.d(TAG, "got ${it.count} lut events") }

            inputEvent?.release()
            lutEvent?.release()
            inputEvent = null
            lutEvent = null
        }
    }

    override suspend fun onStop() {
        startJob?.join()
        val output = channel(OUTPUT)

        output?.receive()?.also {
            it.eos = true
            it.queue()
        }
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
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val inputTexture = inputEvent?.texture ?: glesManager.emptyTexture2d
        val lutTexture = lutEvent?.texture ?: glesManager.emptyTexture2d

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
            lutTexture.bind(GLES30.GL_TEXTURE1)
            program.bindUniforms()
            quadMesh.execute()
        }

        frameCount++

        outEvent.let {
            it.count = frameCount
            it.eos = false
            it.queue()
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
        const val TAG = "Lut2dNode"
        const val INPUT_TEXTURE_LOCATION = 0
        const val LUT_TEXTURE_LOCATION = 1
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("input")
        val INPUT_LUT = Connection.Key<VideoConfig, VideoEvent>("input_lut")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("output")
        val DEFAULT_CONFIG = VideoConfig(
            0, 0, 0, GLES30.GL_RGB8, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, 0
        )
    }
}
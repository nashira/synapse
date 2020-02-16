package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class ShapeNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var outputSize = properties[VideoSize]
    private var outputConfig = DEFAULT_CONFIG
    private var inputSize = Size(1, 1)

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()

    private val quadMesh = Quad()
    private val shape2d = Shape2d(4)

    private var inputEvent: VideoEvent? = null

    private val debounce = AtomicBoolean()
    private var running = AtomicInteger()
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
                config as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun create() {
    }

    override suspend fun initialize() {
        val input = connection(INPUT_POS)
        val output = connection(OUTPUT)

        input?.config?.let {
            inputSize = it.size
            shape2d.instances = inputSize.width * inputSize.height
        }

        output?.prime(VideoEvent(texture1), VideoEvent(texture2))

        glesManager.glContext {
            initRenderTarget(framebuffer1, texture1, config)
            initRenderTarget(framebuffer2, texture2, config)

            val vertex = assetManager.readTextAsset("shader/shape.vert")
            val frag = assetManager.readTextAsset("shader/shape.frag").let {
                if (input?.config?.isOes == true) {
                    it.replace("//{EXT_POS}", "#define EXT_POS")
                } else {
                    it
                }
            }

            quadMesh.initialize()
            shape2d.initialize()

            program.apply {
                initialize(vertex, frag)
                addUniform(
                    Uniform.Type.Mat4,
                    POS_MATRIX,
                    FloatArray(16).also {
                        Matrix.orthoM(
                            it,
                            0,
                            -1f,
                            1f,
                            -1f,
                            1f,
                            -1f,
                            1f
                        )
                    }
                )
                addUniform(
                    Uniform.Type.Int,
                    POS_TEXTURE,
                    INPUT_TEXTURE_LOCATION
                )
                addUniform(
                    Uniform.Type.Vec2,
                    RESOLUTION,
                    floatArrayOf(inputSize.width.toFloat(), inputSize.height.toFloat())
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
            val inputLinked = linked(INPUT_POS)
            if (!inputLinked) {
                Log.d(TAG, "no connection")
            }
            running.set(1)
            var copyMatrix = true
            val inputIn = channel(INPUT_POS)
            if (inputLinked) running.incrementAndGet()

            whileSelect {
                inputIn?.onReceive {
                    //                    Log.d(TAG, "agent receive")
                    inputEvent?.let { inputIn.send(it) }
                    inputEvent = it
//                    if (copyMatrix) {
//                        copyMatrix = false
//                        val uniform = program.getUniform(Uniform.Type.Mat4, POS_MATRIX)
//                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
//                        uniform.dirty = true
//                    }
                    debounceExecute(this@launch)
                    if (it.eos) running.decrementAndGet() > 0 else true
                }
                if (inputIn == null) {
                    onTimeout(frameDuration) {
                        //                        Log.d(TAG, "timeout")
                        execute()
                        running.get() > 0
                    }
                }
            }

            inputEvent?.let { Log.d(TAG, "got ${it.count} input events") }

            inputEvent?.let { inputIn?.send(it) }
            inputEvent = null
        }
    }

    override suspend fun stop() {
        running.decrementAndGet()
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
        shape2d.release()
        program.release()
    }

    private suspend fun execute() {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val inputTexture = inputEvent?.texture ?: glesManager.emptyTexture2d

        val framebuffer = if (outEvent.texture == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            inputTexture.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            GLES30.glLineWidth(2f)
            shape2d.execute()
//            quadMesh.execute()
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
        const val TAG = "ShapeNode"
        const val POS_MATRIX = "pos_matrix"
        const val POS_TEXTURE = "pos_texture"
        const val RESOLUTION = "resolution"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT_POS = Connection.Key<VideoConfig, VideoEvent>("input_position")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("output")
        val DEFAULT_CONFIG = VideoConfig(
            0, 0, 0, GLES30.GL_RGB8, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, 0
        )
    }
}
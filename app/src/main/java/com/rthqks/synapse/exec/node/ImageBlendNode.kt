package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class ImageBlendNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val blendMode: Int get() = properties[BlendMode]
    private val opacity: Float get() = properties[Opacity]
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var outputSize = properties[VideoSize]

    private val texture1 = Texture2d(filter = GLES30.GL_NEAREST)
    private val texture2 = Texture2d(filter = GLES30.GL_NEAREST)
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var baseEvent: VideoEvent? = null
    private var blendEvent: VideoEvent? = null

    private val debounce = AtomicInteger()
    private var frameCount = 0
    private var lastExecutionTime = 0L

    private val config: VideoConfig by lazy {
        VideoConfig(
            GLES30.GL_TEXTURE_2D,
            outputSize.width,
            outputSize.height,
            GLES30.GL_RGB8,
            GLES30.GL_RGB,
            GLES30.GL_UNSIGNED_BYTE
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                Log.d(TAG, "before output $outputSize")
                if (linked(INPUT_BASE)) {
                    val config = configAsync(INPUT_BASE).await()
                    outputSize = config.size
                } else if (linked(INPUT_BLEND)) {
                    val config = configAsync(INPUT_BLEND).await()
                    outputSize = config.size
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
        val baseIn = connection(INPUT_BASE)
        val blendIn = connection(INPUT_BLEND)
        val output = connection(OUTPUT)

        output?.prime(VideoEvent(texture1), VideoEvent(texture2))

        glesManager.glContext {
            initRenderTarget(framebuffer1, texture1, config)
            initRenderTarget(framebuffer2, texture2, config)

            val vertex = assetManager.readTextAsset("shader/image_blend.vert")
            val frag = assetManager.readTextAsset("shader/image_blend.frag").let {
                val s = if (baseIn?.config?.isOes == true) {
                    it.replace("//{EXT_BASE}", "#define EXT_BASE")
                } else {
                    it
                }
                if (blendIn?.config?.isOes == true) {
                    s.replace("//{EXT_BLEND}", "#define EXT_BLEND")
                } else {
                    s
                }
            }

            quadMesh.initialize()

            program.apply {
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
                addUniform(Uniform.Type.Int,
                    "blend_mode",
                    blendMode)
                addUniform(Uniform.Type.Float,
                    UNI_OPACITY,
                    opacity)
            }
        }
    }

    private fun initRenderTarget(framebuffer: Framebuffer, texture: Texture2d, config: VideoConfig) {
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
        framebuffer.initialize(texture.id)
    }

    override suspend fun start() = coroutineScope {
        frameCount = 0


        startJob = launch {
            val baseLinked = linked(INPUT_BASE)
            val blendLinked = linked(INPUT_BLEND)
            if (!baseLinked && !blendLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var running = 0
            var copyMatrixBase = true
            var copyMatrixBlend = true
            val baseIn = channel(INPUT_BASE)
            val blendIn = channel(INPUT_BLEND)
            if (baseLinked) running++
            if (blendLinked) running++

            whileSelect {
                baseIn?.onReceive {
                    //                    Log.d(TAG, "agent receive")
                    baseEvent?.let { baseIn.send(it) }
                    baseEvent = it
                    if (copyMatrixBase) {
                        copyMatrixBase = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "base_matrix")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    debounceExecute(this@launch)
                    if (it.eos) running--
                    running > 0
                }
                blendIn?.onReceive {
                    //                    Log.d(TAG, "env receive")
                    blendEvent?.let { blendIn.send(it) }
                    blendEvent = it
                    if (copyMatrixBlend) {
                        copyMatrixBlend = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "blend_matrix")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    debounceExecute(this@launch)
                    if (it.eos) running--
                    running > 0
                }
            }
            baseEvent?.let { Log.d(TAG, "got ${it.count} base events") }
            blendEvent?.let { Log.d(TAG, "got ${it.count} blend events") }

            baseEvent?.let { baseIn?.send(it) }
            blendEvent?.let { blendIn?.send(it) }
            baseEvent = null
            blendEvent = null
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

    private suspend fun execute() {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val baseInTexture = baseEvent?.texture ?: glesManager.emptyTexture2d
        val blendInTexture = blendEvent?.texture ?: glesManager.emptyTexture2d

        val framebuffer = if (outEvent.texture == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        program.getUniform(Uniform.Type.Int, "blend_mode").apply {
            data = blendMode
            dirty = true
        }

        program.getUniform(Uniform.Type.Float, UNI_OPACITY).apply {
            data = opacity
            dirty = true
        }

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
            baseInTexture.bind(GLES30.GL_TEXTURE0)
            blendInTexture.bind(GLES30.GL_TEXTURE1)
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
        if (debounce.compareAndSet(0, 1)) {
            scope.launch {
                do {
                    val delay = max(
                        0,
                        frameDuration - (SystemClock.elapsedRealtime() - lastExecutionTime)
                    )
                    delay(delay)
                    lastExecutionTime = SystemClock.elapsedRealtime()
                    execute()
                } while (debounce.compareAndSet(2, 1))
                // TODO: use compareAndSet(1, 0)
                debounce.set(0)
            }
        } else {
            // TODO: use compareAndSet
            debounce.set(2)
        }
    }

    companion object {
        const val TAG = "ImageBlendNode"
        const val BASE_TEXTURE_LOCATION = 0
        const val BLEND_TEXTURE_LOCATION = 1
        const val UNI_OPACITY = "opacity"
        val INPUT_BASE = Connection.Key<VideoConfig, VideoEvent>("input_base")
        val INPUT_BLEND = Connection.Key<VideoConfig, VideoEvent>("input_blend")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("output")
    }
}
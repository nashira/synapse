package com.rthqks.synapse.exec.node

import android.opengl.GLES30.*
import android.util.Log
import android.util.Size
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.BlurSize
import com.rthqks.synapse.logic.NumPasses
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.ScaleFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BlurNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val connectMutex = Mutex(true)
    private var inputSize = Size(0, 0)
    private var size = Size(0, 0)

    private lateinit var texture1: Texture2d
    private lateinit var framebuffer1: Framebuffer

    private lateinit var texture2: Texture2d
    private lateinit var framebuffer2: Framebuffer

    private lateinit var texture3: Texture2d
    private lateinit var framebuffer3: Framebuffer

    private val mesh = Quad()
    private val program1 = Program()
    private val program2 = Program()

    private val blurSize: Int get() = properties[BlurSize]
    private val passes: Int get() = properties[NumPasses]
    private val scale: Int get() = properties[ScaleFactor]

    override suspend fun create() {
    }

    override suspend fun initialize() {
        connection(INPUT)?.let {
            val config = it.config
            texture1 = Texture2d()
            framebuffer1 = Framebuffer()

            texture2 = Texture2d()
            framebuffer2 = Framebuffer()

            texture3 = Texture2d()
            framebuffer3 = Framebuffer()

            glesManager.glContext {
                mesh.initialize()

                texture3.initialize()
                texture3.initData(
                    0,
                    config.internalFormat,
                    size.width,
                    size.height,
                    config.format,
                    config.type
                )
                Log.d(TAG, "glGetError() ${glGetError()}")

                framebuffer3.initialize(texture3.id)
            }

            initializeProgram(program1, texture1, framebuffer1, config.isOes)
            initializeProgram(program2, texture2, framebuffer2, false)

            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 0f
                data[1] = 1f
            }
            program2.getUniform(Uniform.Type.Vec2, "resolution0").let {
                val data = it.data!!
                data[0] = size.width.toFloat()
                data[1] = size.height.toFloat()
            }
        }

        connection(OUTPUT)?.prime(VideoEvent(texture2), VideoEvent(texture3))
    }

    private suspend fun initializeProgram(
        program: Program,
        texture: Texture2d,
        framebuffer: Framebuffer,
        oes: Boolean
    ) {
        val connection = connection(INPUT) ?: error("missing input connection")
        val config = connection.config

        val fragName = when (blurSize) {
            13 -> "shader/blur_13.frag"
            5 -> "shader/blur_5.frag"
            else -> "shader/blur_9.frag"
        }

        val vertexSource = assetManager.readTextAsset("shader/blur.vert")
        val fragmentSource = assetManager.readTextAsset(fragName).let {
            if (oes) {
                it.replace("//{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.glContext {
            texture.initialize()
            texture.initData(
                0,
                config.internalFormat,
                size.width,
                size.height,
                config.format,
                config.type
            )
            Log.d(TAG, "glGetError() ${glGetError()}")

            framebuffer.initialize(texture.id)

            program.apply {
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
                    "resolution0",
                    floatArrayOf(inputSize.width.toFloat(), inputSize.height.toFloat())
                )

                addUniform(
                    Uniform.Type.Vec2,
                    "direction",
                    floatArrayOf(1f, 0f)
                )

            }
        }
    }

    override suspend fun start() = coroutineScope {
        startJob = launch {
            val output = channel(OUTPUT) ?: return@launch
            val input = channel(INPUT) ?: return@launch

            var copyMatrix = true

            while (isActive) {
                val outEvent = output.receive()
                val inEvent = input.receive()

                outEvent.eos = inEvent.eos
                outEvent.count = inEvent.count
                outEvent.timestamp = inEvent.timestamp


                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program1.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }

                val (targetTexture, targetFB) = if (outEvent.texture == texture2) {
                    Pair(texture2, framebuffer2)
                } else {
                    Pair(texture3, framebuffer3)
                }

                glesManager.glContext {
                    executeGl(
                        inEvent.texture,
                        framebuffer1, texture1,
                        targetFB, targetTexture
                    )
                }

                input.send(inEvent)
                output.send(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private fun executeGl(
        inputTexture: Texture2d,
        framebuffer1: Framebuffer, texture1: Texture2d,
        framebuffer2: Framebuffer, texture2: Texture2d
    ) {
        val passes = passes

        glViewport(0, 0, size.width, size.height)

        framebuffer1.bind()
        glUseProgram(program1.programId)
        inputTexture.bind(GL_TEXTURE0)
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
            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 1f
                data[1] = 0f
                it.dirty = true
            }
            program2.bindUniforms()
            mesh.execute()

            framebuffer2.bind()
            texture1.bind(GL_TEXTURE0)

            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 0f
                data[1] = 1f
                it.dirty = true
            }
            program2.bindUniforms()
            mesh.execute()
        }
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        glesManager.glContext {
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

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                connectMutex.withLock {}
                val config = config(INPUT)!!

                inputSize = config.size
                size = Size(inputSize.width / scale, inputSize.height / scale)

                VideoConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    config.internalFormat,
                    config.format,
                    config.type
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        if (key == INPUT) connectMutex.unlock()
    }

    companion object {
        const val TAG = "BlurNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_2")
    }
}
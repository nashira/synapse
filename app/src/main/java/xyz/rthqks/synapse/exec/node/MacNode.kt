package xyz.rthqks.synapse.exec.node

import android.opengl.GLES32.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.*
import xyz.rthqks.synapse.logic.AccumulateFactor
import xyz.rthqks.synapse.logic.MultiplyFactor
import xyz.rthqks.synapse.logic.Properties

class MacNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private var size = Size(0, 0)
    private val connectMutex = Mutex(true)

    private val mesh = Quad()
    private val program = Program()
    private val texture1 = Texture(
        GL_TEXTURE_2D,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val texture2 = Texture(
        GL_TEXTURE_2D,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private var framebuffer = framebuffer1

    private val multiplyFactor: Float get() = properties[MultiplyFactor]
    private val accumulateFactor: Float get() = properties[AccumulateFactor]

    override suspend fun create() {
    }

    override suspend fun initialize() {
        config(INPUT)?.let {
            createProgram(it.isOes)
            createTextures()
        }
        connection(OUTPUT)?.prime(VideoEvent(texture1))
    }

    private suspend fun createTextures() {
        val config = config(INPUT) ?: error("missing input connection")

        val intFormat = config.internalFormat
        val format = config.format
        val type = config.type

        glesManager.withGlContext {
            texture1.initialize()
            texture2.initialize()
            texture1.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "2 glGetError() ${glGetError()}")
            texture2.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "3 glGetError() ${glGetError()}")

            framebuffer1.initialize(texture1.id)
            framebuffer2.initialize(texture2.id)
        }
    }

    private suspend fun createProgram(oesTexture: Boolean) {
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("multiply_accumulate.frag").let {
            if (oesTexture) {
                it.replace("#{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.withGlContext {
            mesh.initialize()

            program.apply {
                initialize(vertexSource, fragmentSource)
                addUniform(
                    Uniform.Type.Mat4,
                    "vertex_matrix0",
                    FloatArray(16).also { Matrix.setIdentityM(it, 0) })
                addUniform(
                    Uniform.Type.Mat4,
                    "texture_matrix0",
                    FloatArray(16).also { Matrix.setIdentityM(it, 0) })

                addUniform(
                    Uniform.Type.Int,
                    "input_texture0",
                    0
                )

                addUniform(
                    Uniform.Type.Int,
                    "input_texture1",
                    1
                )

                addUniform(Uniform.Type.Float, "multiply_factor", multiplyFactor)
                addUniform(Uniform.Type.Float, "accumulate_factor", accumulateFactor)

            }
        }
    }


    override suspend fun start() = coroutineScope {
        startJob = launch {
            val input = channel(INPUT) ?: return@launch
            val output = channel(OUTPUT) ?: return@launch

            var copyMatrix = true

            while (isActive) {
                val inEvent = input.receive()

                val outEvent = output.receive().apply {
                    eos = inEvent.eos
                    count = inEvent.count
                    timestamp = inEvent.timestamp
                }

                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }

                glesManager.withGlContext {
                    framebuffer.bind()
                    executeGl(inEvent.texture)
                }

                input.send(inEvent)

                val outTexture: Texture
                if (framebuffer == framebuffer1) {
                    framebuffer = framebuffer2
                    outTexture = texture1
                } else {
                    framebuffer = framebuffer1
                    outTexture = texture2
                }

                outEvent.let {
                    it.texture = outTexture
                    output.send(it)
                }

                if (inEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private fun executeGl(texture: Texture) {
        glUseProgram(program.programId)
        glViewport(0, 0, size.width, size.height)

        texture.bind(GL_TEXTURE0)

        if (framebuffer == framebuffer1) {
            texture2.bind(GL_TEXTURE1)
        } else {
            texture1.bind(GL_TEXTURE1)
        }

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        connection(INPUT)?.let {
            glesManager.withGlContext {
                texture1.release()
                texture2.release()
                framebuffer1.release()
                framebuffer2.release()
                mesh.release()
                program.release()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                connectMutex.withLock {}
                val config = config(INPUT)!!
                size = config.size

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
        const val TAG = "MacNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_2")
    }
}
package xyz.rthqks.synapse.exec.node

import android.opengl.GLES32.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.*

class MacNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val multiplyFactor: Float,
    private val accumulateFactor: Float
) : NodeExecutor() {
    private var startJob: Job? = null
    private var size = Size(0, 0)
    private var inputConnection: Connection<TextureConfig, TextureEvent>? = null
    private var inputChannel: Channel<TextureEvent>? = null
    private val connectMutex = Mutex(true)

    private var outputConnection: Connection<TextureConfig, TextureEvent>? = null

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

    override suspend fun create() {
    }

    override suspend fun initialize() {
        inputConnection?.config?.isOes?.let {
            createProgram(it)
            createTextures()
        }
        outputConnection?.prime(
            TextureEvent(
                texture1,
                FloatArray(16).also { Matrix.setIdentityM(it, 0) })
        )
    }

    private suspend fun createTextures() {
        val connection = inputConnection ?: error("missing input connection")
        val config = connection.config

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
        val input = inputChannel ?: return@coroutineScope
        val output = outputConnection ?: return@coroutineScope

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.receive()

                val outEvent = output.dequeue().apply {
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
                    outputConnection?.queue(it)
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
        inputConnection?.let {
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

    override suspend fun output(key: String): Connection<*, *>? = when (key) {
        PortType.TEXTURE_1 -> {
            connectMutex.withLock {}
            val connection = inputConnection ?: error("missing input connection")
            val config = connection.config

            SingleConsumer<TextureConfig, TextureEvent>(
                TextureConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    config.internalFormat,
                    config.format,
                    config.type
                )
            ).also {
                outputConnection = it
            }
        }
        else -> null
    }

    override suspend fun <C: Config, T : Event> input(key: String, connection: Connection<C, T>) {
        when (key) {
            PortType.TEXTURE_1 -> {
                inputConnection = connection as Connection<TextureConfig, TextureEvent>
                inputChannel = connection.consumer()
                size = connection.config.size

                connectMutex.unlock()
            }
        }
    }

    companion object {
        private val TAG = MacNode::class.java.simpleName
    }
}
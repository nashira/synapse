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

class FrameDifferenceNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : NodeExecutor() {
    private var startJob: Job? = null
    private var size = Size(0, 0)
    private var inputConnection: Connection<TextureConfig, TextureEvent>? = null
    private var inputChannel: Channel<TextureEvent>? = null
    private val connectMutex = Mutex(true)

    private var outputConnection1: Connection<TextureConfig, TextureEvent>? = null
    private var outputConnection2: Connection<TextureConfig, TextureEvent>? = null

    private val mesh = Quad()
    private val program = Program()
    private val diffTexture = Texture(
        GL_TEXTURE_2D,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val lastFrameTexture1 = Texture(
        GL_TEXTURE_2D,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val lastFrameTexture2 = Texture(
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
        outputConnection1?.prime(
            TextureEvent(
                lastFrameTexture1,
                FloatArray(16).also { Matrix.setIdentityM(it, 0) })
        )
        outputConnection2?.prime(
            TextureEvent(diffTexture, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
        )
    }

    private suspend fun createTextures() {
        val connection = inputConnection ?: error("missing input connection")
        val config = connection.config

        val intFormat = config.internalFormat
        val format = config.format
        val type = config.type

        glesManager.withGlContext {
            diffTexture.initialize()
            lastFrameTexture1.initialize()
            lastFrameTexture2.initialize()
            diffTexture.initData(
                0,
                GL_R8, size.width, size.height,
                GL_RED,
                GL_UNSIGNED_BYTE
            )
            Log.d(TAG, "1 glGetError() ${glGetError()}")
            lastFrameTexture1.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "2 glGetError() ${glGetError()}")
            lastFrameTexture2.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "3 glGetError() ${glGetError()}")

            framebuffer1.initialize(lastFrameTexture1.id, diffTexture.id)
            framebuffer2.initialize(lastFrameTexture2.id, diffTexture.id)
        }
    }

    private suspend fun createProgram(oesTexture: Boolean) {
        val vertexSource = assetManager.readTextAsset("frame_difference.vert")
        val fragmentSource = assetManager.readTextAsset("frame_difference.frag").let {
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

            }
        }
    }


    override suspend fun start() = coroutineScope {
        if (outputConnection1 == null && outputConnection2 == null) {
            Log.d(TAG, "no outputs, not starting")
            return@coroutineScope
        }
        val input = inputChannel ?: return@coroutineScope

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.receive()

                val outEvent1 = outputConnection1?.dequeue()
                val outEvent2 = outputConnection2?.dequeue()

                outEvent1?.apply {
                    eos = inEvent.eos
                    count = inEvent.count
                    timestamp = inEvent.timestamp
                }

                outEvent2?.apply {
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
                    outTexture = lastFrameTexture1
                } else {
                    framebuffer = framebuffer1
                    outTexture = lastFrameTexture2
                }

                outEvent1?.let {
                    it.texture = outTexture
                    outputConnection1?.queue(it)
                }

                outEvent2?.let { outputConnection2?.queue(it) }

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
            lastFrameTexture2.bind(GL_TEXTURE1)
        } else {
            lastFrameTexture1.bind(GL_TEXTURE1)
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
                diffTexture.release()
                lastFrameTexture1.release()
                lastFrameTexture2.release()
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
                outputConnection1 = it
            }
        }
        PortType.TEXTURE_2 -> {
            connectMutex.withLock {}

            SingleConsumer<TextureConfig, TextureEvent>(
                TextureConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    GL_R8,
                    GL_RED,
                    GL_UNSIGNED_BYTE
                )
            ).also {
                outputConnection2 = it
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
        private val TAG = FrameDifferenceNode::class.java.simpleName
    }
}
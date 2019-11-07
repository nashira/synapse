package xyz.rthqks.synapse.core.node

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
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.TextureConnection
import xyz.rthqks.synapse.core.edge.TextureEvent
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.gl.*

class FrameDifferenceNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var startJob: Job? = null
    private var size = Size(0, 0)
    private var inputConnection: TextureConnection? = null
    private val connectMutex = Mutex(true)

    private var outputConnection1: TextureConnection? = null
    private var outputConnection2: TextureConnection? = null

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

    override suspend fun initialize() {
    }

    private suspend fun createTextures() {
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
                GL_RGB8, size.width, size.height,
                GL_RGB,
                GL_UNSIGNED_BYTE
            )
            Log.d(TAG, "2 glGetError() ${glGetError()}")
            lastFrameTexture2.initData(
                0,
                GL_RGB8, size.width, size.height,
                GL_RGB,
                GL_UNSIGNED_BYTE
            )
            Log.d(TAG, "3 glGetError() ${glGetError()}")

            framebuffer1.initialize(lastFrameTexture1.id, diffTexture.id)
            framebuffer2.initialize(lastFrameTexture2.id, diffTexture.id)
//            framebuffer1.initialize(diffTexture.id, lastFrameTexture1.id)
//            framebuffer2.initialize(diffTexture.id, lastFrameTexture2.id)
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
            //            texture.initialize()
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
                    Uniform.Type.Integer,
                    "input_texture0",
                    0
                )

                addUniform(
                    Uniform.Type.Integer,
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
        val input = inputConnection ?: return@coroutineScope

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.acquire()

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

                val outTexture: Texture
                if (framebuffer == framebuffer1) {
                    framebuffer = framebuffer2
                    outTexture = lastFrameTexture1
                } else {
                    framebuffer = framebuffer1
                    outTexture = lastFrameTexture2
                }

                input.release(inEvent)

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

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.TEXTURE_1 -> {
            TextureConnection {
                TextureEvent(lastFrameTexture1, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
            }.also { connection ->
                outputConnection1 = connection
                connectMutex.withLock {
                    connection.size = size
                }
            }
        }
        PortType.TEXTURE_2 -> {
            TextureConnection {
                TextureEvent(diffTexture, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
            }.also { connection ->
                outputConnection2 = connection
                connectMutex.withLock {
                    connection.size = size
                }
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.TEXTURE_1 -> {
                connection as TextureConnection
                inputConnection = connection
                size = connection.size
                createProgram(connection.isOes)
                createTextures()

                connectMutex.unlock()
            }
        }
    }

    companion object {
        private val TAG = FrameDifferenceNode::class.java.simpleName
    }
}
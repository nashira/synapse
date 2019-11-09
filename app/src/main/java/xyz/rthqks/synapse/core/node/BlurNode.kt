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

class BlurNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val blurSize: Int = 9,
    private val passes: Int = 1
) : Node() {
    private var inputConnection: TextureConnection? = null
    private var startJob: Job? = null
    private val connectMutex = Mutex(true)
    private var size = Size(0, 0)

    private var outputTextureConnection: TextureConnection? = null
    private lateinit var texture1: Texture
    private lateinit var framebuffer1: Framebuffer

    private lateinit var texture2: Texture
    private lateinit var framebuffer2: Framebuffer

    private val mesh = Quad()
    private val program1 = Program()
    private val program2 = Program()

    override suspend fun create() {

    }

    override suspend fun initialize() {

        inputConnection?.let {
            texture1 = Texture(
                GL_TEXTURE_2D,
                GL_CLAMP_TO_EDGE,
                GL_LINEAR
            )
            framebuffer1 = Framebuffer()

            texture2 = Texture(
                GL_TEXTURE_2D,
                GL_CLAMP_TO_EDGE,
                GL_LINEAR
            )
            framebuffer2 = Framebuffer()

            glesManager.withGlContext {
                mesh.initialize()
            }

            initializeProgram(program1, texture1, framebuffer1, it.isOes)
            initializeProgram(program2, texture2, framebuffer2, false)
            program2.getUniform(Uniform.Type.Vec2, "direction").let {
                val data = it.data!!
                data[0] = 0f
                data[1] = 1f
            }
        }
    }

    private suspend fun initializeProgram(program: Program, texture: Texture, framebuffer: Framebuffer, oes: Boolean) {
        val connection = inputConnection ?: error("missing input connection")

        val fragName = when (blurSize) {
            13 -> "blur_13.frag"
            5 -> "blur_5.frag"
            else -> "blur_9.frag"
        }

        val vertexSource = assetManager.readTextAsset("blur.vert")
        val fragmentSource = assetManager.readTextAsset(fragName).let {
            if (oes) {
                it.replace("#{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.withGlContext {

            texture.initialize()
            texture.initData(
                0,
                connection.internalFormat,
                size.width,
                size.height,
                connection.format,
                connection.type
            )
            Log.d(TAG, "glGetError() ${glGetError()}")

            framebuffer.initialize(texture.id)

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
                    Uniform.Type.Vec2,
                    "resolution0",
                    floatArrayOf(size.width.toFloat(), size.height.toFloat())
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
        val output = outputTextureConnection ?: return@coroutineScope
        val input = inputConnection ?: return@coroutineScope

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.acquire()

                val outEvent = output.dequeue()
                outEvent.eos = inEvent.eos
                outEvent.count = inEvent.count
                outEvent.timestamp = inEvent.timestamp


                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program1.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }
                glesManager.withGlContext {
                    executeGl(inEvent.texture)
                }

                input.release(inEvent)
                output.queue(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private fun executeGl(inputTexture: Texture) {
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
        glesManager.withGlContext {
            mesh.release()

            texture1.release()
            texture2.release()
            framebuffer1.release()
            framebuffer2.release()
            program1.release()
            program2.release()
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.TEXTURE_1 -> {
            connectMutex.withLock {}
            val connection = inputConnection!!
            TextureConnection(
                GL_TEXTURE_2D,
                size.width,
                size.height,
                connection.internalFormat,
                connection.format,
                connection.type
            ) {
                TextureEvent(texture2, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
            }.also {
                outputTextureConnection = it
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.TEXTURE_1 -> {
                connection as TextureConnection
                inputConnection = connection
                size = connection.size.let { Size(it.width, it.height) }

                connectMutex.unlock()
            }
        }
    }

    companion object {
        private val TAG = BlurNode::class.java.simpleName
    }
}
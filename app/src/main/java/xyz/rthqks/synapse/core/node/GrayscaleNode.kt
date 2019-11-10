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
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.edge.TextureConnection
import xyz.rthqks.synapse.core.edge.TextureEvent
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.gl.*

class GrayscaleNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val scale: Int
) : Node() {
    private var inputConnection: TextureConnection? = null
    private var startJob: Job? = null
    private val connectMutex = Mutex(true)
    private var size = Size(0, 0)

    private var outputTextureConnection: TextureConnection? = null
    private var texture: Texture? = null
    private var framebuffer: Framebuffer? = null

    private var outputSurfaceConnection: SurfaceConnection? = null
    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()

    override suspend fun create() {

    }

    override suspend fun initialize() {
        val connection = inputConnection ?: error("missing input connection")
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("grayscale.frag").let {
            if (connection.isOes) {
                it.replace("#{EXT}", "#define EXT")
            } else {
                it
            }
        }
        texture = Texture(
            GL_TEXTURE_2D,
            GL_CLAMP_TO_EDGE,
            GL_LINEAR
        )
        framebuffer = Framebuffer()
        glesManager.withGlContext {
            val texture = texture!!
            val framebuffer = framebuffer!!

            texture.initialize()
            texture.initData(0, GL_R8, size.width, size.height, GL_RED, GL_UNSIGNED_BYTE)
            Log.d(TAG, "glGetError() ${glGetError()}")

            framebuffer.initialize(texture.id)
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

            }
        }
    }

    override suspend fun start() = when {
        outputSurfaceConnection != null -> startSurface()
        outputTextureConnection != null -> startTexture()
        else -> {
            Log.w(TAG, "no connection on start")
            Unit
        }
    }

    private suspend fun startTexture() = coroutineScope {
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
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }
                glesManager.withGlContext {
                    framebuffer?.bind()
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

    private suspend fun startSurface() = coroutineScope {
        val output = outputSurfaceConnection ?: return@coroutineScope
        val input = inputConnection ?: return@coroutineScope

        updateOutputSurface(output)

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.acquire()

                val outEvent = output.dequeue()
                outEvent.eos = inEvent.eos
                outEvent.count = inEvent.count.toLong()
                outEvent.timestamp = inEvent.timestamp


                if (output.hasSurface()) {
                    if (copyMatrix) {
                        copyMatrix = false

                        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                        System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    glesManager.withGlContext {
                        glBindFramebuffer(GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture)
                        outputSurfaceWindow?.swapBuffers()
                    }
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

    private suspend fun updateOutputSurface(output: SurfaceConnection) {
        val surface = output.getSurface()
        Log.d(TAG, "creating output surface")
        glesManager.withGlContext {
            outputSurfaceWindow?.release()
            outputSurfaceWindow = it.createWindowSurface(surface)
            outputSurfaceWindow?.makeCurrent()
        }
    }

    private fun executeGl(texture: Texture) {
        glUseProgram(program.programId)
        glViewport(0, 0, size.width, size.height)

        texture.bind(GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        outputSurfaceWindow?.release()

        glesManager.withGlContext {
            mesh.release()
            program.release()

            texture?.release()
            framebuffer?.release()
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> {
            SurfaceConnection().also { connection ->
                outputSurfaceConnection = connection
                connectMutex.withLock {
                    connection.configure(size, 0)
                }
            }
        }
        PortType.TEXTURE_1 -> {
            connectMutex.withLock {}
            TextureConnection(
                GL_TEXTURE_2D,
                size.width,
                size.height,
                GL_R8,
                GL_RED,
                GL_UNSIGNED_BYTE
            ) {
                TextureEvent(texture!!, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
            }.also { connection ->
                outputTextureConnection = connection
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.TEXTURE_1 -> {
                connection as TextureConnection
                inputConnection = connection
                size = connection.size.let { Size(it.width / scale, it.height / scale) }

                connectMutex.unlock()
            }
        }
    }

    companion object {
        private val TAG = GrayscaleNode::class.java.simpleName
    }
}
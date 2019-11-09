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

class OverlayFilterNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var startJob: Job? = null
    private var contentInput: TextureConnection? = null
    private var maskInput: TextureConnection? = null
    private var contentSize = Size(0, 0)
    private val connectMutex = Mutex(true)

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
        val connection = contentInput ?: error("missing input connection")
        val vertexSource = assetManager.readTextAsset("overlay_filter.vert")
        val fragmentSource = assetManager.readTextAsset("overlay_filter.frag").let {
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
            texture.initData(
                0,
                connection.internalFormat,
                contentSize.width,
                contentSize.height,
                connection.format,
                connection.type
            )
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

                addUniform(
                    Uniform.Type.Integer,
                    "input_texture1",
                    1
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
        val output = outputTextureConnection ?: error("no output connection")
        val input = contentInput ?: error("no content connection")
        val mask = maskInput ?: error("no mask connection")

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent1 = input.acquire()
                val maskEvent = mask.acquire()

                val outEvent = output.dequeue()
                outEvent.eos = inEvent1.eos
                outEvent.count = inEvent1.count
                outEvent.timestamp = inEvent1.timestamp

                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent1.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }
                glesManager.withGlContext {
                    framebuffer?.bind()
                    executeGl(inEvent1.texture, maskEvent.texture)
                }

                input.release(inEvent1)
                mask.release(maskEvent)

                output.queue(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }

//        launch(startJob!!) {
//            while (isActive) {
//                val inEvent = mask.acquire()
//            }
//        }
    }

    private suspend fun startSurface() = coroutineScope {
        val output = outputSurfaceConnection ?: return@coroutineScope
        val input = contentInput ?: return@coroutineScope
        val mask = maskInput ?: error("no mask connection")

        updateOutputSurface(output)

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent = input.acquire()
                val maskEvent = mask.acquire()

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
                        executeGl(inEvent.texture, maskEvent.texture)
                        outputSurfaceWindow?.swapBuffers()
                    }
                }

                input.release(inEvent)
                mask.release(maskEvent)
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

    private fun executeGl(content: Texture, mask: Texture) {
        glUseProgram(program.programId)
        glViewport(0, 0, contentSize.width, contentSize.height)

        content.bind(GL_TEXTURE0)
        mask.bind(GL_TEXTURE1)

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
                    connection.configure(contentSize, 0)
                }
            }
        }
        PortType.TEXTURE_1 -> {
            connectMutex.withLock {}
            val connection = contentInput!!
            TextureConnection(
                GL_TEXTURE_2D,
                contentSize.width,
                contentSize.height,
                connection.internalFormat,
                connection.format,
                connection.type
            ) {
                TextureEvent(texture!!, FloatArray(16).also { Matrix.setIdentityM(it, 0) })
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
                contentInput = connection
                contentSize = connection.size

                connectMutex.unlock()
            }
            PortType.TEXTURE_2 -> {
                connection as TextureConnection
                maskInput = connection
//                contentSize = connection.size.let { Size(it.width, it.height) }
            }
        }
    }

    companion object {
        private val TAG = OverlayFilterNode::class.java.simpleName
    }
}
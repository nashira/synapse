package xyz.rthqks.synapse.core.node

import android.opengl.GLES32.glUseProgram
import android.opengl.GLES32.glViewport
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.edge.TextureConnection
import xyz.rthqks.synapse.core.gl.*
import xyz.rthqks.synapse.data.PortType

class GlNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var outputConnection: SurfaceConnection? = null
    private var inputConnection: TextureConnection? = null
    private var startJob: Job? = null
    private val connectMutex = Mutex(true)
    private var size = Size(0, 0)
    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()
//    private val texture = Texture(
//        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
//        GL_TEXTURE0,
//        GL_CLAMP_TO_EDGE,
//        GL_LINEAR
//    )

    fun fragmentFileName() = "lut.frag"

    override suspend fun initialize() {
        createProgram()
    }

    private suspend fun createProgram() {
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset(fragmentFileName())
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

            }
        }
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope
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
                        System.arraycopy(inEvent.matrix, 0, uniform.data, 0, 16)
                        uniform.dirty = true
                    }
                    glesManager.withGlContext {
                        outputSurfaceWindow?.makeCurrent()
                        executeGl(inEvent.texture)
                        outputSurfaceWindow?.setPresentationTime(inEvent.timestamp)
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

        texture.bind()

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
//            texture.release()
            mesh.release()
            program.release()
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> {
            SurfaceConnection().also { connection ->
                outputConnection = connection
                connectMutex.lock()
                connectMutex.unlock()
                connection.configure(size, 0)
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

                connectMutex.unlock()
            }
        }
    }

    companion object {
        private val TAG = GlNode::class.java.simpleName
    }
}
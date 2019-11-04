package xyz.rthqks.synapse.core.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.gl.*
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.util.SuspendableGet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GlNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var outputConnection: SurfaceConnection? = null
    private var inputConnection: SurfaceConnection? = null
    private var startJob: Job? = null
    private val suspendSize = SuspendableGet<Size>()
    private var size: Size? = null
    private var inputRotation = 0
    private var inputSurface: Surface? = null
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()
    private val texture = Texture(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GLES32.GL_TEXTURE0,
        GLES32.GL_CLAMP_TO_EDGE,
        GLES32.GL_LINEAR
    )

    fun fragmentFileName() = "lut.frag"
    fun onProgramCreated() {}

    override suspend fun initialize() {
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset(fragmentFileName())
        glesManager.withGlContext {
            texture.initialize()
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

                addTexture("input_texture0", texture)
            }
            onProgramCreated()
        }

        Log.d(TAG, "texture id $texture")
        inputSurfaceTexture = SurfaceTexture(texture.id)
        inputSurface = Surface(inputSurfaceTexture)
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope

        updateOutputSurface(output)
        inputConnection?.setSurface(inputSurface)

        startJob = launch {
            suspendCoroutine<Unit> {
                setOnFrameAvailableListener(this, it)
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

    private fun setOnFrameAvailableListener(
        scope: CoroutineScope,
        continuation: Continuation<Unit>
    ) {
        Log.d(TAG, "setOnFrameAvailableListener $inputSurfaceTexture")

        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
        var textureMatrix = uniform.data
        uniform.dirty = true
        inputSurfaceTexture?.setOnFrameAvailableListener({ st ->
            //            Log.d(TAG, "onFrameAvailable")
            scope.launch {
                onFrame(st, continuation, textureMatrix)
                textureMatrix = null
            }
        }, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        surfaceTexture: SurfaceTexture,
        continuation: Continuation<Unit>,
        textureMatrix: FloatArray?
    ) {
        val input = inputConnection ?: error("input connection missing")
        val output = outputConnection ?: error("output connection missing")

        var inEvent = input.acquire()

        glesManager.withGlContext {
            surfaceTexture.updateTexImage()
        }

        while (surfaceTexture.timestamp > inEvent.timestamp && !inEvent.eos) {
            Log.d(
                TAG,
                "skipped frame! ${inEvent.count} ${surfaceTexture.timestamp} ${inEvent.timestamp}"
            )
            input.release(inEvent)
            inEvent = input.acquire()
        }

        val outEvent = output.dequeue()
        outEvent.eos = inEvent.eos
        outEvent.count = inEvent.count
        outEvent.timestamp = inEvent.timestamp

        input.release(inEvent)

        if (output.hasSurface()) {
            textureMatrix?.let { inputSurfaceTexture?.getTransformMatrix(it) }
            glesManager.withGlContext {
                executeGl()
                outputSurfaceWindow?.setPresentationTime(surfaceTexture.timestamp)
                outputSurfaceWindow?.swapBuffers()
            }
        }

        output.queue(outEvent)

        if (outEvent.eos) {
            Log.d(TAG, "got EOS")
            inputSurfaceTexture?.setOnFrameAvailableListener(null)
            inputConnection?.setSurface(null)
            continuation.resume(Unit)
            startJob?.cancel()
        }
    }

    private fun executeGl() {
        val size = size!!
        GLES32.glUseProgram(program.programId)
        GLES32.glViewport(0, 0, size.width, size.height)

        program.bindTextures()

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        inputSurfaceTexture?.release()
        inputSurface?.release()
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
            texture.release()
            mesh.release()
            program.release()
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> {
            SurfaceConnection().also { connection ->
                outputConnection = connection
                connection.configure(suspendSize.get(), 0)
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.SURFACE_1 -> {
                connection as SurfaceConnection
                inputConnection = connection
                val size = connection.getSize()
                val rotation = connection.getRotation()
                val rotatedSize =
                    if (rotation == 90 || rotation == 270)
                        Size(size.height, size.width) else size

                this.size = rotatedSize
                suspendSize.set(rotatedSize)
                inputRotation = rotation

                connection.setSurface(inputSurface)
                inputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
            }
        }
    }

    companion object {
        private val TAG = GlNode::class.java.simpleName
    }
}
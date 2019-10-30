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
import xyz.rthqks.synapse.core.edge.SurfaceEvent
import xyz.rthqks.synapse.core.gl.*
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.util.SuspendableGet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LutNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var outputConnection: SurfaceConnection? = null
    private var inputConnection: SurfaceConnection? = null
    private var startJob: Job? = null
    private val suspendSize = SuspendableGet<Size>()
    private var inputRotation = 0
    private var inputSurface: Surface? = null
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var outputSurfaceWindow: WindowSurface? = null

    private lateinit var program: Program
    private var inputTexture = 0
    private var programVao = 0

    override suspend fun initialize() {
        val vertexSource = assetManager.readTextAsset("lut.vert")
        val fragmentSource = assetManager.readTextAsset("lut.frag")
        glesManager.withGlContext {
            it.makeCurrent()
            program = it.createProgram(vertexSource, fragmentSource).apply {
                addUniform(Uniform.Type.Integer, "input_texture0", 0)
                addUniform(Uniform.Type.Mat4, "vertex_matrix0", FloatArray(16))
                addUniform(Uniform.Type.Mat4, "texture_matrix0", FloatArray(16))
                Matrix.setIdentityM(
                    getUniform(Uniform.Type.Mat4, "texture_matrix0"), 0
                )
                Matrix.setIdentityM(
                    getUniform(Uniform.Type.Mat4, "vertex_matrix0"), 0
                )
                Log.d(TAG, "mat ${getUniform(Uniform.Type.Mat4, "vertex_matrix0")?.joinToString()}")
            }

            inputTexture = it.createTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_CLAMP_TO_EDGE,
                GLES32.GL_LINEAR
            )

            programVao = Quad.createVao()
        }

        inputSurfaceTexture = SurfaceTexture(inputTexture)
        inputSurface = Surface(inputSurfaceTexture)
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope

        updateOutputSurface(output)

        startJob = launch {
            suspendCoroutine<Unit> {
                setOnFrameAvailableListener(this, it)
            }
        }
    }

    private suspend fun updateOutputSurface(output: SurfaceConnection) {
        val surface = output.getSurface()
        if (outputSurfaceWindow?.surface != surface) {
            Log.d(TAG, "creating output surface")
            glesManager.withGlContext {
                it.makeCurrent()
                outputSurfaceWindow?.release()
                outputSurfaceWindow = it.createWindowSurface(surface)
            }
        }
    }

    private fun setOnFrameAvailableListener(
        scope: CoroutineScope,
        continuation: Continuation<Unit>
    ) {
        Log.d(TAG, "setOnFrameAvailableListener")
        inputSurfaceTexture?.setOnFrameAvailableListener { st ->
            scope.launch {
                onFrame(st, continuation)
            }
        }
    }

    private suspend fun onFrame(surfaceTexture: SurfaceTexture, continuation: Continuation<Unit>) {
        val input = inputConnection ?: error("input connection missing")
        val output = outputConnection ?: error("output connection missing")

        var inEvent = input.acquire()
//        Log.d(TAG, "onFrame ${inEvent.count}")

        var outEvent: SurfaceEvent

        glesManager.withGlContext {
            outputSurfaceWindow?.makeCurrent()
            surfaceTexture.updateTexImage()
            while (surfaceTexture.timestamp > inEvent.timestamp && !inEvent.eos) {
                Log.d(TAG, "skipped frame! ${surfaceTexture.timestamp} ${inEvent.timestamp}")
                input.release(inEvent)
                inEvent = input.acquire()
            }

            outEvent = output.dequeue()
            outEvent.eos = inEvent.eos
            outEvent.count = inEvent.count
            outEvent.timestamp = inEvent.timestamp

            input.release(inEvent)

            if (output.hasSurface() || outEvent.eos) {
                if (output.hasSurface()) {
                    GLES32.glUseProgram(program.programId)
                    executeGl()
                }
                // swap to send eos
                outputSurfaceWindow?.setPresentationTime(surfaceTexture.timestamp)
                outputSurfaceWindow?.swapBuffers()
            }

            output.queue(outEvent)

            if (outEvent.eos) {
                Log.d(TAG, "got EOS")
                inputSurfaceTexture?.setOnFrameAvailableListener(null)
                continuation.resume(Unit)
            }
        }
    }

    private suspend fun executeGl() {
        val size = suspendSize.get()
        GLES32.glViewport(0, 0, size.width, size.height)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTexture)

        program.bindUniforms()

        GLES32.glBindVertexArray(programVao)

        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
    }

    override suspend fun stop() {
        startJob?.join()
//        outputConnection?.let { output ->
//            val outEvent = output.dequeue()
//            outEvent.eos = true
//            Log.d(TAG, "sending EOS")
//            output.queue(outEvent)
//        }
    }

    override suspend fun release() {
        inputSurfaceTexture?.release()
        inputSurface?.release()
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
            it.releaseTexture(inputTexture)
            it.releaseProgram(program)
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
            PortType.SURFACE_1 -> coroutineScope {
                connection as SurfaceConnection
                inputConnection = connection
                val size = connection.getSize()
                val rotation = connection.getRotation()
                val rotatedSize =
                    if (rotation == 90 || rotation == 270)
                        Size(size.height, size.width) else size

                suspendSize.set(rotatedSize)
                inputRotation = rotation

                connection.setSurface(inputSurface)
                inputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)

                val matrix = program.getUniform(Uniform.Type.Mat4, "vertex_matrix0")
                program.setUniform(Uniform.Type.Mat4, "vertex_matrix0", matrix)
                Matrix.rotateM(
                    matrix,
                    0,
                    GlesManager.IDENTITY,
                    0,
                    inputRotation.toFloat(),
                    0f,
                    0f,
                    -1f
                )
                Log.d(TAG, "mat ${matrix?.joinToString()}")
            }
        }
    }

    companion object {
        private val TAG = LutNode::class.java.simpleName
    }
}
package xyz.rthqks.synapse.core.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.*
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.gl.GlProgram
import xyz.rthqks.synapse.core.gl.GlesManager
import xyz.rthqks.synapse.core.gl.WindowSurface
import xyz.rthqks.synapse.data.PortType
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LutNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var size: Size = Size(0, 0)
    private var startContinuation: Continuation<Unit>? = null
    private var outputConnection: SurfaceConnection? = null
    private var inputConnection: SurfaceConnection? = null
    private var startJob: Job? = null
    private var inputSurface: Surface? = null
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputTexture = 0
    private var outputSurfaceWindow: WindowSurface? = null
    private var program: GlProgram? = null

    override suspend fun initialize() {
        val vertexSource = assetManager.readTextAsset("lut.vert")
        val fragmentSource = assetManager.readTextAsset("lut.frag")
        inputTexture = glesManager.withGlContext {
            it.makeCurrent()
            program = it.createProgram(vertexSource, fragmentSource)
            it.createTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_CLAMP_TO_EDGE,
                GLES32.GL_NEAREST
            )
        }

        inputSurfaceTexture = SurfaceTexture(inputTexture)
        inputSurface = Surface(inputSurfaceTexture)
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope

        val surface = output.getSurface()
        if (outputSurfaceWindow?.surface != surface) {
            Log.d(TAG, "creating output surface")
            glesManager.withGlContext {
                it.makeCurrent()
                outputSurfaceWindow?.release()
                outputSurfaceWindow = it.createWindowSurface(surface)
            }
        }

        startJob = launch(SupervisorJob()) {
            setOnFrameAvailableListener(this)
            suspendCoroutine<Unit> { startContinuation = it }
        }
    }

    private fun setOnFrameAvailableListener(scope: CoroutineScope) {
        inputSurfaceTexture?.setOnFrameAvailableListener { st ->
            Log.d(TAG, "onFrame")
            scope.launch {
                onFrame(st)
            }
        }
    }

    private suspend fun onFrame(surfaceTexture: SurfaceTexture) {
        val input = inputConnection ?: error("input connection missing")
        val output = outputConnection ?: error("output connection missing")

        val inEvent = input.acquire()
        if (inEvent.eos) {
            Log.d(TAG, "got EOS")
            input.release(inEvent)

            val outEvent = output.dequeue()
            outEvent.eos = true

            Log.d(TAG, "sending EOS")
            output.queue(outEvent)
            startContinuation?.resume(Unit)
            inputSurfaceTexture?.setOnFrameAvailableListener(null)
            return
        }

        val outEvent = output.dequeue()
        outEvent.eos = false
        outEvent.count = inEvent.count

        glesManager.withGlContext {
            outputSurfaceWindow?.makeCurrent()
            surfaceTexture.updateTexImage()
            if (output.hasSurface()) {
                GLES32.glViewport(0, 0, size.width, size.height)
                GLES32.glClearColor(1f, 0f, 0f, 1f)
                GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)

                outputSurfaceWindow?.swapBuffers()
            }
        }

        input.release(inEvent)
        output.queue(outEvent)
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        inputSurfaceTexture?.release()
        inputSurface?.release()
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
            it.releaseTexture(inputTexture)
            program?.let { p -> it.releaseProgram(p) }
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> SurfaceConnection().also { connection ->
            outputConnection = connection
            Log.d(TAG, "output $outputConnection $inputConnection")
            inputConnection?.let {
                connection.configure(it.getSize())
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.SURFACE_1 -> {
                inputConnection = connection as SurfaceConnection
                Log.d(TAG, " input $outputConnection $inputConnection")
                size = connection.getSize()
                outputConnection?.configure(size)

                connection.setSurface(inputSurface)
                inputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
            }
        }
    }

    companion object {
        private val TAG = LutNode::class.java.simpleName
    }
}
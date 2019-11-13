package xyz.rthqks.synapse.core.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLES32.GL_CLAMP_TO_EDGE
import android.opengl.GLES32.GL_LINEAR
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.core.CameraManager
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.*
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.gl.Texture

class CameraNode(
    private val cameraManager: CameraManager,
    private val glesManager: GlesManager,
    private val facing: Int,
    private val requestedSize: Size,
    private val frameRate: Int
) : Node() {
    private lateinit var size: Size
    private lateinit var cameraId: String
    private var surfaceRotation = 0
    private var surfaceConnection: Connection<SurfaceConfig, SurfaceEvent>? = null
    private var textureConnection: Connection<TextureConfig, TextureEvent>? = null
    private var startJob: Job? = null
    private val mutex = Mutex()
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )

    override suspend fun create() {
        val conf = cameraManager.resolve(facing, requestedSize, frameRate)
        Log.d(TAG, conf.toString())
        cameraId = conf.id
        size = conf.size
        surfaceRotation = conf.rotation
    }

    override suspend fun initialize() {
        textureConnection?.prime(TextureEvent(outputTexture, FloatArray(16)))

        surfaceConnection?.prime(SurfaceEvent())
        surfaceConnection?.prime(SurfaceEvent())
        surfaceConnection?.prime(SurfaceEvent())
    }

    override suspend fun start() = when {
        surfaceConnection != null -> startSurface()
        textureConnection != null -> startTexture()
        else -> {
            Log.w(TAG, "no connection, not starting")
            Unit
        }
    }

    private suspend fun startSurface() = coroutineScope {
        val connection = surfaceConnection ?: return@coroutineScope
        val config = connection.config
        val surface = config.getSurface()
        mutex.lock()
        startJob = launch {
            cameraManager.start(cameraId, surface, frameRate) { count, timestamp, eos ->
                launch {
                    val frame = connection.dequeue()
                    frame.eos = eos
                    frame.count = count
                    frame.timestamp = timestamp
                    connection.queue(frame)
                    if (eos) {
                        Log.d(TAG, "sending EOS")
                        Log.d(TAG, "sent frames $count")
                        mutex.unlock()
                    }
                }
            }
            // lock again to suspend until we unlock on EOS
            mutex.lock()
            mutex.unlock()
        }
    }

    private suspend fun startTexture() = coroutineScope {
        val connection = textureConnection ?: return@coroutineScope
        val surface = outputSurface ?: return@coroutineScope
        mutex.lock()

        startJob = launch {

            cameraManager.start(cameraId, surface, frameRate) { count, timestamp, eos ->
                //                launch {
//                    val frame = connection.dequeue()
//                    frame.eos = eos
//                    frame.count = count
//                    frame.timestamp = timestamp
//                    connection.queue(frame)
//                Log.d(TAG, "camera $timestamp")
                if (eos) {
                    Log.d(TAG, "got EOS from cam")
                    Log.d(TAG, "sent frames $count")
                    outputSurfaceTexture?.setOnFrameAvailableListener(null)

                    launch {
                        val event = connection.dequeue()
                        event.eos = true
                        connection.queue(event)
                        mutex.unlock()
                    }
                }
            }

            var copyMatrix = true
            setOnFrameAvailableListener {
                launch {
                    onFrame(connection, it, copyMatrix)
                    copyMatrix = false
                }
            }

            // lock again to suspend until we unlock on EOS
            mutex.withLock { }
        }
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        connection: Connection<TextureConfig, TextureEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {

        val event = connection.dequeue()
        glesManager.withGlContext {
            surfaceTexture.updateTexImage()
            if (copyMatrix) {
                surfaceTexture.getTransformMatrix(event.matrix)
            }
//            Log.d(TAG, "surface ${surfaceTexture.timestamp}")
        }
        event.eos = false
        connection.queue(event)
    }

    override suspend fun stop() {
        cameraManager.stop()
        startJob?.join()
    }

    override suspend fun release() {
        outputSurface?.release()
        outputSurfaceTexture?.release()
        outputTexture.release()
    }

    override suspend fun output(key: String): Connection<*, *>? = when (key) {
        PortType.SURFACE_1 -> SingleConsumer<SurfaceConfig, SurfaceEvent>(
            SurfaceConfig(size, surfaceRotation)
        ).also {
            surfaceConnection = it
        }
        PortType.TEXTURE_1 -> {
            glesManager.withGlContext {
                outputTexture.initialize()
            }
            outputSurfaceTexture = SurfaceTexture(outputTexture.id)
            outputSurface = Surface(outputSurfaceTexture)

            val rotatedSize =
                if (surfaceRotation == 90 || surfaceRotation == 270)
                    Size(size.height, size.width) else size

            outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)

            SingleConsumer<TextureConfig, TextureEvent>(
                TextureConfig(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    rotatedSize.width,
                    rotatedSize.height,
                    GLES32.GL_RGB8,
                    GLES32.GL_RGB,
                    GLES32.GL_UNSIGNED_BYTE
                )
            ).also {
                textureConnection = it
            }
        }
        else -> null
    }

    override suspend fun <C : Config, T : Event> input(key: String, connection: Connection<C, T>) {
        throw IllegalStateException("$TAG has no inputs")
    }

    companion object {
        private val TAG = CameraNode::class.java.simpleName
    }
}

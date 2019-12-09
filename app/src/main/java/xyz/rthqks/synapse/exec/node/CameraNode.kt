package xyz.rthqks.synapse.exec.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLES32.GL_CLAMP_TO_EDGE
import android.opengl.GLES32.GL_LINEAR
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.exec.CameraManager
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.gl.Texture

class CameraNode(
    private val cameraManager: CameraManager,
    private val glesManager: GlesManager,
    private val facing: Int,
    private val requestedSize: Size,
    private val frameRate: Int
) : NodeExecutor() {
    private lateinit var size: Size
    private lateinit var cameraId: String
    private var surfaceRotation = 0
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
        val connection = connection(OUTPUT) ?: return
        val config = connection.config

        if (config.acceptsSurface) {
            repeat(3) { connection.prime(VideoEvent()) }
        } else {
            glesManager.withGlContext {
                outputTexture.initialize()
            }

            outputSurfaceTexture = SurfaceTexture(outputTexture.id)
            outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
            outputSurface = Surface(outputSurfaceTexture)

            connection.prime(
                VideoEvent(outputTexture)
            )
        }
    }

    override suspend fun start() = when (config(OUTPUT)?.acceptsSurface) {
        true -> startSurface()
        false -> startTexture()
        else -> {
            Log.w(TAG, "no connection, not starting")
            Unit
        }
    }

    private suspend fun startSurface() = coroutineScope {
        val connection = connection(OUTPUT) ?: return@coroutineScope
        val config = connection.config
        val surface = config.surface.get()
        mutex.lock()
        startJob = launch {
            cameraManager.start(cameraId, surface, frameRate) { count, timestamp, eos ->
                launch {
                    val frame = connection.dequeue()
                    frame.eos = eos
                    frame.count = count.toInt()
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
        val connection = connection(OUTPUT) ?: return@coroutineScope
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
                    outputSurfaceTexture?.setOnFrameAvailableListener(null)

                    runBlocking {
                        Log.d(TAG, "got EOS from cam")
                        if (mutex.isLocked) {
                            val event = connection.dequeue()
                            event.eos = true
                            connection.queue(event)
                            mutex.unlock()
                        }
                        Log.d(TAG, "sent frames $count")
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
        connection: Connection<VideoConfig, VideoEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {

        val event = connection.dequeue()
        glesManager.withGlContext {
            surfaceTexture.updateTexImage()
            if (copyMatrix) {
                surfaceTexture.getTransformMatrix(event.matrix)
                when (cameraManager.displayRotation) {
                    Surface.ROTATION_90 -> {
                        Matrix.translateM(event.matrix, 0, 0.5f, 0.5f, 0f)
                        Matrix.rotateM(event.matrix, 0, 90f, 0f, 0f, -1f)
                        Matrix.translateM(event.matrix, 0, -0.5f, -0.5f, 0f)
                    }
                    Surface.ROTATION_270 -> {
                        Matrix.translateM(event.matrix, 0, 0.5f, 0.5f, 0f)
                        Matrix.rotateM(event.matrix, 0, 270f, 0f, 0f, -1f)
                        Matrix.translateM(event.matrix, 0, -0.5f, -0.5f, 0f)
                    }
                }
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

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                val rotatedSize =
                    if (surfaceRotation == 90 || surfaceRotation == 270)
                        Size(size.height, size.width) else size

                VideoConfig(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    rotatedSize.width,
                    rotatedSize.height,
                    GLES32.GL_RGB8,
                    GLES32.GL_RGB,
                    GLES32.GL_UNSIGNED_BYTE,
                    surfaceRotation,
                    offersSurface = true
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    companion object {
        const val TAG = "CameraNode"
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
    }
}

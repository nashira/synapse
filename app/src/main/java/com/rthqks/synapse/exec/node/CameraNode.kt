package com.rthqks.synapse.exec.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLES30.GL_CLAMP_TO_EDGE
import android.opengl.GLES30.GL_LINEAR
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.Texture2d
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

class CameraNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val cameraManager = context.cameraManager
    private val glesManager = context.glesManager
    private lateinit var size: Size
    private lateinit var cameraId: String
    private var surfaceRotation = 0
    private var startJob: Job? = null
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture2d(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )

    private val facing: Int get() = properties[CameraFacing]
    private val requestedSize: Size get() = properties[VideoSize]
    private val frameRate: Int get() = properties[FrameRate]
    private val stabilize: Boolean get() = properties[Stabilize]

    override suspend fun onCreate() {
        updateCameraConfig()
    }

    override suspend fun onInitialize() {
        val connection = connection(OUTPUT) ?: return
        val config = connection.config

        if (config.acceptsSurface) {
            repeat(3) { connection.prime(VideoEvent()) }
        } else {
            glesManager.glContext {
                outputTexture.initialize()
            }

            outputSurfaceTexture = SurfaceTexture(outputTexture.id).also {
                it.setDefaultBufferSize(size.width, size.height)
                outputSurface = Surface(it)
            }

            connection.prime(
                VideoEvent(outputTexture),
                VideoEvent(outputTexture)
            )
        }
    }

    private fun updateCameraConfig() {
        val conf = cameraManager.resolve(facing, requestedSize, frameRate, stabilize)
        Log.d(TAG, conf.toString())
        cameraId = conf.id
        size = conf.size
        surfaceRotation = conf.rotation
    }

    override suspend fun onStart() {
        return
        updateCameraConfig()
        when (config(OUTPUT)?.acceptsSurface) {
            true -> {
                startJob = scope.launch { startSurface() }
            }
            false -> {
                startJob = scope.launch { startTexture() }
            }
            else -> {
                Log.w(TAG, "no connection, not starting")
                Unit
            }
        }
    }

    private suspend fun startSurface() {
//        val output = channel(OUTPUT) ?: return
//        val config = config(OUTPUT) ?: return
//        val surface = config.surface.get()
//        val cameraChannel = Channel<CameraManager.Event>(6)
////        cameraManager.start(cameraId, surface, frameRate, stabilize, cameraChannel)
//        do {
//            val (count, timestamp, eos) = cameraChannel.receive()
//            val frame = output.receive()
//            frame.count = count
//            frame.timestamp = timestamp
//            frame.eos = eos
//            frame.queue()
//            if (eos) {
//                Log.d(TAG, "sending EOS")
//                Log.d(TAG, "sent frames $count")
//            }
//        } while (!eos)
    }

    private suspend fun startTexture() {
//        val channel = channel(OUTPUT) ?: return
//        val surface = outputSurface ?: return
//        val cameraChannel = Channel<CameraManager.Event>(10)
//
//        var copyMatrix = 0
//        setOnFrameAvailableListener {
//            runBlocking {
//                onFrame(channel, it, copyMatrix < 2)
//                copyMatrix++
//            }
//        }
//
////        cameraManager.start(cameraId, surface, frameRate, stabilize, cameraChannel)
//        do {
//            val (count, timestamp, eos) = cameraChannel.receive()
//            if (eos) {
//                Log.d(TAG, "got EOS from cam")
//                outputSurfaceTexture?.setOnFrameAvailableListener(null)
//                Log.d(TAG, "frame listener = null")
//                val event = channel.receive()
//                Log.d(TAG, "receive event")
//                event.count = count
//                event.timestamp = timestamp
//                event.eos = true
//                event.queue()
//                Log.d(TAG, "sent frames $count")
//            }
//        } while (!eos)
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        channel: ReceiveChannel<VideoEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {
//        Log.d(TAG, "onFrame")
        val event = channel.receive()
        glesManager.glContext {
            surfaceTexture.updateTexImage()
            event.timestamp = surfaceTexture.timestamp
            if (copyMatrix) {
                surfaceTexture.getTransformMatrix(event.matrix)
//                Log.d(TAG, "matrix ${event.matrix.joinToString()}")
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

        }
        event.eos = false
        event.queue()
    }

    override suspend fun onStop() {
//        cameraManager.stop()
        startJob?.join()
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            outputSurface?.release()
            outputSurfaceTexture?.release()
            outputTexture.release()
        }
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
                    GLES30.GL_RGB8,
                    GLES30.GL_RGB,
                    GLES30.GL_UNSIGNED_BYTE,
                    frameRate,
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

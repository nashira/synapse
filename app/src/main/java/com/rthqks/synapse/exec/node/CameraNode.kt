package com.rthqks.synapse.exec.node

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLES32.GL_CLAMP_TO_EDGE
import android.opengl.GLES32.GL_LINEAR
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.exec.CameraManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.gl.Texture
import com.rthqks.synapse.logic.CameraFacing
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.VideoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CameraNode(
    private val cameraManager: CameraManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {
    private lateinit var size: Size
    private lateinit var cameraId: String
    private var surfaceRotation = 0
    private var startJob: Job? = null
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )

    private val facing: Int get() =  properties[CameraFacing]
    private val requestedSize: Size get() = properties[VideoSize]
    private val frameRate: Int get() = properties[FrameRate]

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

    override suspend fun start() = coroutineScope {
        when (config(OUTPUT)?.acceptsSurface) {
            true -> {
                startJob = launch { startSurface() }
            }
            false -> {
                startJob = launch { startTexture() }
            }
            else -> {
                Log.w(TAG, "no connection, not starting")
                Unit
            }
        }
    }

    private suspend fun startSurface() {
        val output = channel(OUTPUT) ?: return
        val config = config(OUTPUT) ?: return
        val surface = config.surface.get()
        val cameraChannel = Channel<CameraManager.Event>(3)
        cameraManager.start(cameraId, surface, frameRate, cameraChannel)
        do {
            val (count, timestamp, eos) = cameraChannel.receive()
            val frame = output.receive()
            frame.count = count
            frame.timestamp = timestamp
            frame.eos = eos
            output.send(frame)
            if (eos) {
                Log.d(TAG, "sending EOS")
                Log.d(TAG, "sent frames $count")
            }
        } while (!eos)
    }

    private suspend fun startTexture() {
        val channel = channel(OUTPUT) ?: return
        val surface = outputSurface ?: return
        val cameraChannel = Channel<CameraManager.Event>(3)

        var copyMatrix = 0
        setOnFrameAvailableListener {
            runBlocking {
                onFrame(channel, it, copyMatrix < 2)
                copyMatrix++
            }
        }

        cameraManager.start(cameraId, surface, frameRate, cameraChannel)
        do {
            val (count, timestamp, eos) = cameraChannel.receive()
            if (eos) {
                outputSurfaceTexture?.setOnFrameAvailableListener(null)
                Log.d(TAG, "got EOS from cam")
                val event = channel.receive()
                event.count = count
                event.timestamp = timestamp
                event.eos = true
                channel.send(event)
                Log.d(TAG, "sent frames $count")
            }
        } while (!eos)
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        channel: Channel<VideoEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {

        val event = channel.receive()
        glesManager.glContext {
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
        channel.send(event)
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

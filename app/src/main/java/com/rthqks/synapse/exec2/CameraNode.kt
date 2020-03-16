package com.rthqks.synapse.exec2

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30.GL_CLAMP_TO_EDGE
import android.opengl.GLES30.GL_LINEAR
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.exec.CameraManager
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.gl.Texture2d
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    private var cameraConfig: CameraManager.Conf? = null
    private val camera = cameraManager.getCamera()

    private val facing: Int get() = properties[CameraFacing]
    private val requestedSize: Size get() = properties[VideoSize]
    private val frameRate: Int get() = properties[FrameRate]
    private val stabilize: Boolean get() = properties[Stabilize]

    override suspend fun onSetup() {
        Log.d(TAG, "onSetup")
        updateCameraConfig()
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        Log.d(TAG, "onConnect ${key.id}")
        when (key) {
            OUTPUT -> if (startJob == null) {
                startJob = scope.launch {
                    updateCameraConfig()
                    outputSurface ?: run { initialize() }
                    startTexture()
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        Log.d(TAG, "onDisconnect ${key.id}")
        if (key == OUTPUT && !linked(OUTPUT)) {
            camera.stopRequest()
            startJob?.join()
            startJob = null
        }
    }

    private suspend fun initialize() {
        glesManager.glContext {
            outputTexture.initialize()
        }

        outputTexture.width = size.width
        outputTexture.height = size.height

        outputSurfaceTexture = SurfaceTexture(outputTexture.id).also {
            it.setDefaultBufferSize(size.width, size.height)
            outputSurface = Surface(it)
        }

        connection(OUTPUT)?.prime(outputTexture, outputTexture)

        camera.open(cameraId)
        outputSurface?.let { camera.openSession(it) }
    }

    private fun updateCameraConfig() {
        val conf = cameraManager.resolve(facing, requestedSize, frameRate, stabilize)
        Log.d(TAG, conf.toString())
        cameraId = conf.id
        size = conf.size
        surfaceRotation = conf.rotation

        cameraConfig = conf
    }

    private suspend fun startTexture() {
        val channel = channel(OUTPUT) ?: return

        val surface = outputSurface ?: return

        var copyMatrix = 0
        setOnFrameAvailableListener {
            runBlocking {
                onFrame(channel, it, copyMatrix < 2)
                copyMatrix++
            }
        }

        cameraConfig?.let { camera.startRequest(it) }
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        channel: ReceiveChannel<Message<Texture2d>>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {
//        Log.d(TAG, "onFrame")
        val event = channel.receive()
        val updateMatrix = checkConfig()
        glesManager.glContext {
            surfaceTexture.updateTexImage()
            event.timestamp = surfaceTexture.timestamp
//            if (copyMatrix || updateMatrix) {
                val matrix = event.data.matrix
                surfaceTexture.getTransformMatrix(matrix)
//                Log.d(TAG, "matrix ${event.matrix.joinToString()}")
                when (cameraManager.displayRotation) {
                    Surface.ROTATION_90 -> {
                        Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                        Matrix.rotateM(matrix, 0, 90f, 0f, 0f, -1f)
                        Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                    }
                    Surface.ROTATION_270 -> {
                        Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                        Matrix.rotateM(matrix, 0, 270f, 0f, 0f, -1f)
                        Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                    }
                }
//            }
        }
        event.queue()
    }

    private suspend fun checkConfig(): Boolean {
        var restartRequest = false
        var restartSession = false
        var reopenCamera = false

        if (facing != cameraConfig?.facing) {
            reopenCamera = true
        }

        if (requestedSize != size) {
            restartSession = true
        }

        if (frameRate != cameraConfig?.fps) {
            restartRequest = true
        }

        if (stabilize != cameraConfig?.stabilize) {
            restartRequest = true
        }

        when {
            reopenCamera -> {
                updateCameraConfig()
                outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
                outputTexture.width = size.width
                outputTexture.height = size.height
                cameraConfig?.let {
                    camera.close()
                    camera.open(cameraId)
                    outputSurface?.let { surface -> camera.openSession(surface) }
                    cameraConfig?.let { conf -> camera.startRequest(conf) }
                }
                return true
            }

            restartSession -> {
                updateCameraConfig()
                outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
                outputTexture.width = size.width
                outputTexture.height = size.height
                cameraConfig?.let {
                    outputSurface?.let { surface -> camera.openSession(surface) }
                    cameraConfig?.let { conf -> camera.startRequest(conf) }
                }
            }

            restartRequest -> {
                updateCameraConfig()
                cameraConfig?.let { conf -> camera.startRequest(conf) }
            }
        }
        return false
    }

    override suspend fun onRelease() {
        Log.d(TAG, "onRelease")
        camera.closeSession()
        camera.close()
        glesManager.glContext {
            outputSurface?.release()
            outputSurfaceTexture?.release()
            outputTexture.release()
        }
    }

    companion object {
        const val TAG = "CameraNode2"
        val OUTPUT = Connection.Key<Texture2d>("video_1")
    }
}

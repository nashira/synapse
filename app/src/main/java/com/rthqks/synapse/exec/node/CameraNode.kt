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
import com.rthqks.synapse.exec.*
import com.rthqks.synapse.gl.Texture2d
import com.rthqks.synapse.logic.NodeDef.Camera
import com.rthqks.synapse.logic.NodeDef.Camera.CameraFacing
import com.rthqks.synapse.logic.NodeDef.Camera.FrameRate
import com.rthqks.synapse.logic.NodeDef.Camera.Stabilize
import com.rthqks.synapse.logic.NodeDef.Camera.VideoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CameraNode(
    context: ExecutionContext,
    private var properties: Properties
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

    var cameraConfig: CameraManager.Conf? = null
        private set

    private val camera = cameraManager.getCamera()

    private val facing: Int get() = properties[CameraFacing]
    private val requestedSize: Size get() = properties[VideoSize]
    private val frameRate: Int get() = properties[FrameRate]
    private val stabilize: Boolean get() = properties[Stabilize]

    fun setProperties(properties: Properties) {
        this.properties = properties
    }

    override suspend fun onSetup() {
        Log.d(TAG, "onSetup")
        updateCameraConfig()
    }

    override suspend fun onPause() {
        camera.closeSession()
        camera.close()
        outputSurfaceTexture?.setOnFrameAvailableListener(null)
        startJob?.join()
        startJob = null
    }

    override suspend fun onResume() {
        maybeStart()
    }

    private suspend fun maybeStart() {
        if (isResumed && startJob == null && connected(OUTPUT)) {
            startJob = scope.launch {
                updateCameraConfig()
                outputSurface ?: run { initialize() }
                startTexture()
            }
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> maybeStart()
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {

    }

    private suspend fun initialize() {
        glesManager.glContext {
            outputTexture.initialize()
        }

        outputTexture.format = GLES30.GL_RGB
        outputTexture.internalFormat = GLES30.GL_RGB8
        outputTexture.type = GLES30.GL_UNSIGNED_BYTE

        outputSurfaceTexture = SurfaceTexture(outputTexture.id)
        updateOutputSize()
        outputSurface = Surface(outputSurfaceTexture)

        connection(OUTPUT)?.prime(outputTexture, outputTexture)
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

        var copyMatrix = 0
        setOnFrameAvailableListener {
            runBlocking {
                onFrame(channel, it, copyMatrix < 2)
                copyMatrix++
            }
        }

        camera.open(cameraId)
        outputSurface?.let { camera.openSession(it) }
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
//        Log.d(TAG, "checkConfig")
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
                updateOutputSize()
                cameraConfig?.let {
                    camera.closeSession()
                    camera.close()
                    camera.open(cameraId)
                    outputSurface?.let { surface -> camera.openSession(surface) }
                    cameraConfig?.let { conf -> camera.startRequest(conf) }
                }
                return true
            }

            restartSession -> {
                updateCameraConfig()
                updateOutputSize()
                cameraConfig?.let {
                    camera.closeSession()
                    outputSurface?.let { surface -> camera.openSession(surface) }
                    cameraConfig?.let { conf -> camera.startRequest(conf) }
                }
            }

            restartRequest -> {
                updateCameraConfig()
                cameraConfig?.let { conf -> camera.startRequest(conf) }
            }
        }
//        Log.d(TAG, "checkConfig done")
        return false
    }

    private fun updateOutputSize() {
        outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)

        val rotatedSize =
            if (surfaceRotation == 90 || surfaceRotation == 270)
                Size(size.height, size.width) else size

        outputTexture.width = rotatedSize.width
        outputTexture.height = rotatedSize.height
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
//
//    suspend fun stopCamera() = await {
//        camera.closeSession()
//        camera.close()
//    }
//
//    suspend fun resumeCamera() = await {
//        camera.open(cameraId)
//        outputSurface?.let { camera.openSession(it) }
//    }

    companion object {
        const val TAG = "CameraNode"
        val OUTPUT = Connection.Key<Texture2d>(Camera.OUTPUT.key)
    }
}

package xyz.rthqks.synapse.core.node

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.util.Log
import android.util.Size
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import java.util.concurrent.Executor

class CameraNode(
    private val context: Context,
    private val facing: Int,
    size: Size,
    frameRate: Int
) : Node() {
    private lateinit var cameraId: String
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null

    override suspend fun initialize() {
        cameraManager = context.getSystemService(CameraManager::class.java)!!
        val ids = cameraManager.cameraIdList
        ids.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.keys.forEach {
                Log.d(TAG, "id: $id $it = ${characteristics[it]}")
            }
            val facing = characteristics[CameraCharacteristics.LENS_FACING]
            if (facing == this.facing) {
                cameraId = id
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createRequest()
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }

        }, null)
    }

    private fun createRequest() {
        cameraDevice?.createCaptureSession(listOf(), object : CameraCaptureSession.StateCallback(){
            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

            override fun onConfigured(session: CameraCaptureSession) {
                val request = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)!!
                session.setRepeatingRequest(request.build(), object : CameraCaptureSession.CaptureCallback(){
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d(TAG, "frame captured")
                    }
                }, null)
            }

        }, null)
    }

    override suspend fun stop() {
    }

    override suspend fun release() {

    }

    override suspend fun <T> output(key: String, connection: Connection<T>) {

    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {

    }

    companion object {
        private val TAG = CameraNode::class.java.simpleName
    }
}

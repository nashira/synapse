package xyz.rthqks.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraManager(
    private val context: Context
) {
    private lateinit var manager: android.hardware.camera2.CameraManager
    private val cameraMap = mutableMapOf<String, CameraCharacteristics>()
    private val thread = HandlerThread(TAG)
    private lateinit var handler: Handler
    var displayRotation = 0
    var isEos: Boolean = false

    fun initialize() {
        thread.start()
        handler = Handler(thread.looper)

        val display =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        Log.d(TAG, "display.rotation ${display.rotation}")

        displayRotation = display.rotation

        manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)!!
        val ids = manager.cameraIdList
        ids.forEach { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            cameraMap[id] = characteristics
            val orientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION]
//            Log.d(TAG, "id: $id orientation $orientation")
        }
    }

    suspend fun start(
        cameraId: String,
        surface: Surface,
        fps: Int,
        onFrame: ((Long, Long, Boolean) -> Unit)
    ) {
        isEos = false
        val c = openCamera(cameraId)
        val s = createSession(c, surface)
        val request = c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).also {
            it.addTarget(surface)
            it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        }.build()

        startRequest(c, s, request, onFrame)
    }

    fun stop() {
        Log.d(TAG, "stop")
        isEos = true
        Log.d(TAG, "stopped")
    }

    fun release() {
        Log.d(TAG, "release")
        thread.quitSafely()
    }

    private fun findCameraId(facing: Int, fps: Int): String {
        cameraMap.forEach {
            val face = it.value[CameraCharacteristics.LENS_FACING]
            if (face == facing) {
                return it.key
            }
        }
        return "0"
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraId: String): CameraDevice = suspendCoroutine {
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d(TAG, "onOpened")
                it.resume(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.d(TAG, "onDisconnected")
                camera.close()
                it.resumeWithException(RuntimeException("camera open exception: disconnected"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d(TAG, "onError")
                it.resumeWithException(RuntimeException("camera open exception: $error"))
            }

            override fun onClosed(camera: CameraDevice) {
                Log.d(TAG, "onClosed")
            }
        }, handler)
    }

    private suspend fun createSession(
        cameraDevice: CameraDevice,
        surface: Surface
    ): CameraCaptureSession = suspendCoroutine {
        cameraDevice.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    it.resumeWithException(
                        RuntimeException(
                            "can't create session, camera: $cameraDevice surface: $surface"
                        )
                    )
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "onConfigured")
                    it.resume(session)
                }
            },
            handler
        )
    }

    private fun startRequest(
        camera: CameraDevice,
        session: CameraCaptureSession,
        request: CaptureRequest,
        onFrame: ((Long, Long, Boolean) -> Unit)
    ) {
        session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val eos = isEos

                if (eos) {
                    Log.d(TAG, "got eos")
                    session.close()
                    camera.close()
                }

                val time = result[CaptureResult.SENSOR_TIMESTAMP]!!
                onFrame.invoke(result.frameNumber, time, eos)
            }
        }, handler)
    }

    fun resolve(facing: Int, size: Size, frameRate: Int): Conf {
        val id = findCameraId(facing, frameRate)
        val characteristics = cameraMap[id]!!
        val orientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION] ?: 0
        val surfaceRotation = ORIENTATIONS[displayRotation] ?: 0
        val rotation = (surfaceRotation + orientation + 270) % 360
        return Conf(id, size, frameRate, rotation)
    }

    companion object {
        private val TAG = CameraManager::class.java.simpleName
        private val ORIENTATIONS = mapOf(
            Surface.ROTATION_0 to 90,
            Surface.ROTATION_90 to 0,
            Surface.ROTATION_180 to 270,
            Surface.ROTATION_270 to 180
        )
    }

    data class Conf(val id: String, val size: Size, val fps: Int, val rotation: Int)
}
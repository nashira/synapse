package com.rthqks.synapse.exec

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred


class CameraManager(
    private val context: Context
) {

    private lateinit var manager: android.hardware.camera2.CameraManager
    private val cameraMap = mutableMapOf<String, CameraCharacteristics>()
    private val thread = HandlerThread(TAG)
    private lateinit var handler: Handler
    private lateinit var camera: Camera
    var displayRotation = 0

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
        }
        camera = Camera(manager, cameraMap, handler)
    }

    fun getCamera() = camera

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

    fun resolve(facing: Int, size: Size, frameRate: Int, stabilize: Boolean): Conf {
        val id = findCameraId(facing, frameRate)
        val characteristics = cameraMap[id]!!
        val orientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION] ?: 0
        val surfaceRotation = ORIENTATIONS[displayRotation] ?: 0
        val rotation = (surfaceRotation + orientation + 270) % 360
        return Conf(
            id,
            size,
            frameRate,
            rotation,
            stabilize,
            facing
        )
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

    data class Conf(
        val id: String,
        val size: Size,
        val fps: Int,
        val rotation: Int,
        val stabilize: Boolean,
        val facing: Int
    )
}

class Camera(
    private val cameraManager: android.hardware.camera2.CameraManager,
    private val cameraMap: Map<String, CameraCharacteristics>,
    private val handler: Handler
) {
    private var openDeferred: CompletableDeferred<Boolean>? = null
    private var closeDeferred: CompletableDeferred<Boolean>? = null
    private var sessionReadyDeferred: CompletableDeferred<Boolean>? = null
    private var sessionClosedDeferred: CompletableDeferred<Boolean>? = null

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var requestBuilder: CaptureRequest.Builder? = null

    private val cameraCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@Camera.camera = camera
            openDeferred?.complete(true)
        }

        override fun onDisconnected(camera: CameraDevice) {
            openDeferred?.complete(false)
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            openDeferred?.complete(false)
            this@Camera.camera = null
        }

        override fun onClosed(camera: CameraDevice) {
            closeDeferred?.complete(true)
            this@Camera.camera = null
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            sessionReadyDeferred?.complete(false)
        }

        override fun onReady(session: CameraCaptureSession) {
            this@Camera.session = session
            sessionReadyDeferred?.complete(true)
        }

        override fun onClosed(session: CameraCaptureSession) {
            sessionClosedDeferred?.complete(true)
            this@Camera.session = null
        }

        override fun onConfigured(session: CameraCaptureSession) {
            // wait for onReady
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun open(cameraId: String) {
        Log.d(TAG, "open")
        openDeferred = CompletableDeferred()
        closeDeferred = CompletableDeferred()
        cameraManager.openCamera(cameraId, cameraCallback, handler)
        openDeferred?.await()
        openDeferred = null
    }

    suspend fun close() {
        Log.d(TAG, "close")
        camera?.close()
        camera = null
        requestBuilder = null
        closeDeferred?.await()
        closeDeferred = null
    }

    suspend fun openSession(surface: Surface) {
        Log.d(TAG, "openSession")
        sessionReadyDeferred = CompletableDeferred()
        sessionClosedDeferred = CompletableDeferred()
        requestBuilder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        requestBuilder?.addTarget(surface)
        camera?.createCaptureSession(listOf(surface), sessionCallback, handler)
        sessionReadyDeferred?.await()
        sessionReadyDeferred = null
    }

    suspend fun closeSession() {
        Log.d(TAG, "closeSession")
        session?.close()
        session = null
        sessionClosedDeferred?.await()
        sessionClosedDeferred = null
    }

    suspend fun startRequest(conf: CameraManager.Conf) {
        Log.d(TAG, "startRequest")
        getRequest(conf)?.let {
            session?.setRepeatingRequest(it, null, null)
        }
    }

    suspend fun stopRequest() {
        Log.d(TAG, "stopRequest")
        sessionReadyDeferred = CompletableDeferred()
        session?.abortCaptures()
        sessionReadyDeferred?.await()
        sessionReadyDeferred = null
    }

    private fun getRequest(conf: CameraManager.Conf): CaptureRequest? {
        requestBuilder?.also {
            it.set(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD
            )
            it.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            it.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            it.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            it.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            val range =
                cameraMap[conf.id]?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.firstOrNull { it.lower == conf.fps && it.upper == conf.fps }

            it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            if (conf.stabilize) {
                it.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
        }

        return requestBuilder?.build()
    }

    companion object {
        const val TAG = "*cam*"
    }
}
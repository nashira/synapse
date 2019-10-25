package xyz.rthqks.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
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
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var startContinuation: Continuation<Unit>? = null

    fun initialize() {
        thread.start()
        handler = Handler(thread.looper)

        manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)!!
        val ids = manager.cameraIdList
        ids.forEach { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            cameraMap[id] = characteristics
//            characteristics.keys.forEach {
//                Log.d(TAG, "id: $id $it = ${characteristics[it]}")
//            }
        }
    }

    suspend fun start(surface: Surface, facing: Int, fps: Int, onFrame: suspend () -> Unit) {
        val id = findCameraId(facing, fps)
        val c = openCamera(id)
        val s = createSession(c, surface)
        camera = c
        session = s
        val request = c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).also {
            it.addTarget(surface)
            it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        }.build()
        coroutineScope {
            suspendCoroutine<Unit> {
                startContinuation = it
                startRequest(s, request, onFrame, this)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "stop")
        session?.close()
        camera?.close()
        startContinuation?.resume(Unit)
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
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d(TAG, "onError")
                it.resumeWithException(RuntimeException("camera open exception: $error"))
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
        session: CameraCaptureSession,
        request: CaptureRequest,
        onFrame: suspend () -> Unit,
        scope: CoroutineScope
    ) {
        session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                runBlocking(scope.coroutineContext) {
                    onFrame()
                }
            }
        }, handler)
    }

    companion object {
        private val TAG = CameraManager::class.java.simpleName
    }
}
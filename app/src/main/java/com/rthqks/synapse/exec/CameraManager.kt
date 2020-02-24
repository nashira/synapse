package com.rthqks.synapse.exec

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.Channel
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
    private var eventIndex = 0
    private val events = Array(10) {
        Event(0, 0, false)
    }
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
        }
    }

    suspend fun start(
        cameraId: String,
        surface: Surface,
        fps: Int,
        stabilize: Boolean,
        channel: Channel<Event>
    ) {
        isEos = false
        val c = openCamera(cameraId)
        val request = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        request.addTarget(surface)
        val s = createSession(c, surface)

        request.also {
            it.set(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW
            )
            it.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            it.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            it.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            it.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            val range =
                cameraMap[cameraId]?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.firstOrNull { it.lower == fps && it.upper == fps }
            Log.d(TAG, "setting range $range")
            it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            if (stabilize) {
                it.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
        }

//        request.keys.forEach {
//            Log.d(TAG, "${it.name} ${request[it]}")
//        }

        startRequest(c, s, request.build(), channel)
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
        var continuation: Continuation<CameraDevice>? = it
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d(TAG, "onOpened")
                continuation?.resume(camera)
                continuation = null
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.d(TAG, "onDisconnected")
                camera.close()
                continuation?.resumeWithException(RuntimeException("camera open exception: disconnected"))
                continuation = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d(TAG, "onError")
                continuation?.resumeWithException(RuntimeException("camera open exception: $error"))
                continuation = null
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
        channel: Channel<Event>
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
//                    session.stopRepeating()
                    session.close()
                    camera.close()
                }

                val time = result[CaptureResult.SENSOR_TIMESTAMP]!!
                val event = nextEvent()
                event.set(result.frameNumber.toInt(), time, eos)

                runBlocking {
                    channel.send(event)
                }
            }
        }, handler)
    }

    private fun nextEvent(): Event {
        eventIndex = (eventIndex + 1) % events.size
        return events[eventIndex]
    }

    fun resolve(facing: Int, size: Size, frameRate: Int, stabilize: Boolean): Conf {
        val id = findCameraId(facing, frameRate)
        val characteristics = cameraMap[id]!!
        val orientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION] ?: 0
        val surfaceRotation = ORIENTATIONS[displayRotation] ?: 0
        val rotation = (surfaceRotation + orientation + 270) % 360
        return Conf(id, size, frameRate, rotation, stabilize)
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
        val stabilize: Boolean
    )

    data class Event(
        var count: Int,
        var timestamp: Long,
        var eos: Boolean
    ) {
        fun set(count: Int, timestamp: Long, eos: Boolean) {
            this.count = count
            this.timestamp = timestamp
            this.eos = eos
        }
    }
}
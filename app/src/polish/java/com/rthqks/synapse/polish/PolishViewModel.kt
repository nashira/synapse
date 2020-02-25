package com.rthqks.synapse.polish

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.R
import com.rthqks.synapse.exec.Executor
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.ops.Analytics
import kotlinx.coroutines.launch
import javax.inject.Inject

class PolishViewModel @Inject constructor(
    private val executor: Executor,
    private val context: Context,
    private val analytics: Analytics
) : ViewModel() {
    val deviceSupported = MutableLiveData<Boolean>()
    private var currentEffect: Effect? = null
    private var recordingStart = 0L

    init {
        viewModelScope.launch {
            executor.initialize(false)
            executor.await()
            deviceSupported.value = executor.deviceSupported
        }
    }


    private var surfaceView: SurfaceView? = null
    private var network: Network? = null

    val properties = Properties().apply {
        put(
            Property(
                CameraFacing,
                ChoiceType(
                    R.string.property_name_camera_device,
                    R.drawable.ic_switch_camera,
                    Choice(
                        CameraCharacteristics.LENS_FACING_BACK,
                        R.string.property_label_camera_lens_facing_back
                    ),
                    Choice(
                        CameraCharacteristics.LENS_FACING_FRONT,
                        R.string.property_label_camera_lens_facing_front
                    )
                ), CameraCharacteristics.LENS_FACING_BACK, true
            ), IntConverter
        )
        put(
            Property(
                FrameRate,
                ChoiceType(
                    R.string.property_name_frame_rate,
                    R.drawable.ic_speed,
                    Choice(10, R.string.property_label_camera_fps_10),
                    Choice(15, R.string.property_label_camera_fps_15),
                    Choice(20, R.string.property_label_camera_fps_20),
                    Choice(30, R.string.property_label_camera_fps_30),
                    Choice(60, R.string.property_label_camera_fps_60)
                ), 30, true
            ), IntConverter
        )
        put(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_camera_capture_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(3840, 2160), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1920, 1080), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(1280, 720), R.string.property_label_camera_capture_size_720),
                    Choice(Size(640, 480), R.string.property_label_camera_capture_size_480)
                ), Size(1280, 720), true
            ), SizeConverter
        )
        put(
            Property(
                Stabilize,
                ToggleType(
                    R.string.property_name_camera_stabilize, R.drawable.ic_texture,
                    R.string.property_label_on,
                    R.string.property_label_off
                ), value = true, requiresRestart = true
            ), BooleanConverter
        )
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
        val facing = when (properties[CameraFacing]) {
            CameraCharacteristics.LENS_FACING_BACK ->
                CameraCharacteristics.LENS_FACING_FRONT
            CameraCharacteristics.LENS_FACING_FRONT ->
                CameraCharacteristics.LENS_FACING_BACK
            else -> CameraCharacteristics.LENS_FACING_BACK
        }
        properties[CameraFacing] = facing
        restartNetwork()

        val string = if (facing == 0) "front" else "back"
        analytics.logEvent(Analytics.Event.EditSetting(CameraFacing.name, string))
    }

    private fun restartNetwork() {
        executor.stop()
        executor.start()
    }

    fun startExecution() {
        executor.start()
    }

    fun stopExecution() {
        executor.stop()
    }

    fun startRecording() {
        recordingStart = SystemClock.elapsedRealtime()
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        network?.apply {
            getNode(4)?.properties?.set(Recording, true)
        }
    }

    fun stopRecording() {
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStop(effectName, SystemClock.elapsedRealtime() - recordingStart))
        network?.apply {
            getNode(4)?.properties?.set(Recording, false)
        }
    }

    fun <T> editProperty(
        key: Property.Key<T>,
        value: T,
        restart: Boolean = false,
        recreate: Boolean = false
    ) {
        analytics.logEvent(Analytics.Event.EditSetting(key.name, value.toString()))
        properties[key] = value
        Log.d(TAG, "${key.name} = $value")
        when {
            recreate -> network?.let { recreateNetwork(it) }
            restart -> restartNetwork()
        }
    }

    fun setEffect(effect: Effect): Boolean {
        currentEffect = effect
        analytics.logEvent(Analytics.Event.SetEffect(effect.title))
        return when (effect) {
            Effect.None -> {
                EffectNetworks.none.getNode(1)?.properties?.plusAssign(properties)
                recreateNetwork(EffectNetworks.none)
                true
            }
            Effect.TimeWarp -> {
                EffectNetworks.timeWarp.getNode(1)?.properties?.plusAssign(properties)
                EffectNetworks.timeWarp.getNode(5)?.properties?.plusAssign(properties)
                recreateNetwork(EffectNetworks.timeWarp)
                true
            }
            Effect.More -> {
                Toast.makeText(context, "Coming Soon!", Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    private fun recreateNetwork(network: Network) {
        this.network = network
        viewModelScope.launch {
            executor.stop()
            executor.releaseNetwork()
            executor.initializeNetwork(network)
            executor.start()
            executor.await()
            surfaceView?.let { executor.setSurfaceView(it) }
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")
        Log.d(TAG, "release")
        executor.releaseNetwork()
        executor.release()
        Log.d(TAG, "released")
        super.onCleared()
    }

    fun setDeviceOrientation(orientation: Int) {
        network?.apply {
            getNode(4)?.properties?.set(Rotation, orientation)
        }
    }

    

    companion object {
        const val TAG = "PolishViewModel"
    }
}

package com.rthqks.synapse.polish

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class PolishViewModel @Inject constructor(
    private val executor: Executor,
    private val context: Context
) : ViewModel() {
    val deviceSupported = MutableLiveData<Boolean>()

    init {
        viewModelScope.launch {
            executor.initialize(false)
            executor.await()
            deviceSupported.value = executor.deviceSupported
        }
    }


    private var surfaceView: SurfaceView? = null
    private var network: Network? = null

    private val properties = Properties().apply {
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
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
        when (properties[CameraFacing]) {
            CameraCharacteristics.LENS_FACING_BACK -> properties[CameraFacing] =
                CameraCharacteristics.LENS_FACING_FRONT
            CameraCharacteristics.LENS_FACING_FRONT -> properties[CameraFacing] =
                CameraCharacteristics.LENS_FACING_BACK
        }
    }

    fun startExecution() {
        executor.start()
    }

    fun stopExecution() {
        executor.stop()
    }

    fun startRecording() {
        network?.apply {
            getNode(4)?.properties?.set(Recording, true)
        }
    }

    fun stopRecording() {
        network?.apply {
            getNode(4)?.properties?.set(Recording, false)
        }
    }

    fun <T> editProperty(key: Property.Key<T>, value: T) {
        properties[key] = value
        Log.d(TAG, "${key.name} = $value")
    }

    fun setEffect(effect: Effect) {
        when (effect) {
            Effect.None -> {
                EffectNetworks.none.getNode(1)?.properties?.plusAssign(properties)
                restartNetwork(EffectNetworks.none)
            }
            Effect.TimeWarp -> {
                EffectNetworks.timeWarp.getNode(1)?.properties?.plusAssign(properties)
                restartNetwork(EffectNetworks.timeWarp)
            }
            Effect.Sparkle,
            Effect.Trip,
            Effect.Topography -> {
                Toast.makeText(context, "Coming Soon", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restartNetwork(network: Network) {
        this.network = network
        viewModelScope.launch {
            executor.stop()
            executor.releaseNetwork()
            executor.initializeNetwork(network)
            surfaceView?.let { executor.setSurfaceView(it) }
            executor.start()
            executor.await()
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

    companion object {
        const val TAG = "PolishViewModel"
    }
}

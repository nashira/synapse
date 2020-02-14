package com.rthqks.synapse.polish

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.*
import javax.inject.Inject

class PolishViewModel @Inject constructor() : ViewModel() {

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

    }

    fun flipCamera() {
        when (properties[CameraFacing]) {
            CameraCharacteristics.LENS_FACING_BACK -> properties[CameraFacing] =
                CameraCharacteristics.LENS_FACING_FRONT
            CameraCharacteristics.LENS_FACING_FRONT -> properties[CameraFacing] =
                CameraCharacteristics.LENS_FACING_BACK
        }
    }

    fun startRecording() {

    }

    fun stopRecording() {

    }

    fun <T> editProperty(key: Property.Key<T>, value: T) {
        properties[key] = value
        Log.d(TAG, "${key.name} = $value")
    }

    fun setEffect(effect: Effect) {

    }

    companion object {
        const val TAG = "PolishViewModel"
    }

}

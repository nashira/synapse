package com.rthqks.synapse.polish

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.*

class Effect(
    val network: Network
) {
    val properties = Properties().apply {
        put(
            Property(
                Title,
                TextType(
                    R.string.property_name_network_name,
                    R.drawable.ic_text_fields
                ), "Synapse Effect"
            ), TextConverter
        )
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
                    Choice(
                        10,
                        R.string.property_label_camera_fps_10
                    ),
                    Choice(
                        15,
                        R.string.property_label_camera_fps_15
                    ),
                    Choice(
                        20,
                        R.string.property_label_camera_fps_20
                    ),
                    Choice(
                        30,
                        R.string.property_label_camera_fps_30
                    ),
                    Choice(
                        60,
                        R.string.property_label_camera_fps_60
                    )
                ), 30, true
            ), IntConverter
        )
        put(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_camera_capture_size,
                    R.drawable.ic_photo_size_select,
                    Choice(
                        Size(3840, 2160),
                        R.string.property_label_camera_capture_size_2160
                    ),
                    Choice(
                        Size(1920, 1080),
                        R.string.property_label_camera_capture_size_1080
                    ),
                    Choice(
                        Size(1280, 720),
                        R.string.property_label_camera_capture_size_720
                    ),
                    Choice(
                        Size(640, 480),
                        R.string.property_label_camera_capture_size_480
                    )
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
        put(
            Property(
                Recording,
                ToggleType(
                    R.string.property_name_recording,
                    R.drawable.ic_movie,
                    R.string.property_name_recording,
                    R.string.property_name_recording
                ), false
            ), BooleanConverter
        )
    }

    val title: String get() = properties[Title]

    fun flipCamera(): Int {
        val facing = when (properties[CameraFacing]) {
            CameraCharacteristics.LENS_FACING_BACK ->
                CameraCharacteristics.LENS_FACING_FRONT
            CameraCharacteristics.LENS_FACING_FRONT ->
                CameraCharacteristics.LENS_FACING_BACK
            else -> CameraCharacteristics.LENS_FACING_BACK
        }
        properties[CameraFacing] = facing
        return facing
    }

    companion object {
        const val ID_CAMERA = 1
        const val ID_MIC = 2
        const val ID_SURFACE_VIEW = 3
        const val ID_ENCODER = 4
        val Title = Property.Key<String>("name")
    }
}
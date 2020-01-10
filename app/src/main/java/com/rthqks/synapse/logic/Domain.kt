package com.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Property.Companion.RangeType

val AudioSampleRate = Property.Key<Int>("audio_sample_rate")
val AudioEncoding = Property.Key<Int>("audio_encoding")
val AudioChannel = Property.Key<Int>("audio_channel")
val AudioSource = Property.Key<Int>("audio_source")
val CameraFacing = Property.Key<Int>("camera_facing")
val CameraCaptureSize = Property.Key<Size>("camera_capture_size")
val CameraFrameRate = Property.Key<Int>("camera_frame_rate")
val BlurSize = Property.Key<Int>("blur_size")
val NumPasses = Property.Key<Int>("num_passes")
val ScaleFactor = Property.Key<Int>("scale_factor")
val AccumulateFactor = Property.Key<Float>("accumulate_factor")
val MultiplyFactor = Property.Key<Float>("multiply_factor")
val Uri = Property.Key<String>("uri")
val CropToFit = Property.Key<Boolean>("crop_to_fit")

val Nodes = listOf(
    Node(Node.Type.Camera).apply {
        add(Port(Port.Type.Video, "video_1", "Video", true))
        add(
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
        add(
            Property(
                CameraFrameRate,
                ChoiceType(
                    R.string.property_name_camera_frame_rate,
                    R.drawable.ic_speed,
                    Choice(10, R.string.property_label_camera_fps_10),
                    Choice(15, R.string.property_label_camera_fps_15),
                    Choice(20, R.string.property_label_camera_fps_20),
                    Choice(30, R.string.property_label_camera_fps_30),
                    Choice(60, R.string.property_label_camera_fps_60)
                ), 30, true
            ), IntConverter
        )
        add(
            Property(
                CameraCaptureSize,
                ChoiceType(
                    R.string.property_name_camera_capture_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(3840, 2160), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1920, 1080), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(1280, 720), R.string.property_label_camera_capture_size_720),
                    Choice(Size(640, 480), R.string.property_label_camera_capture_size_480)
                ), Size(1920, 1080), true
            ), SizeConverter
        )
    },
    Node(Node.Type.Microphone).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
    },
    Node(Node.Type.MediaFile).apply {
        add(Port(Port.Type.Video, "video_1", "Video", true))
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
    },
    Node(Node.Type.FrameDifference).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Difference", true))
    },
    Node(Node.Type.GrayscaleFilter).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Grayscale", true))
        add(
            Property(
                ScaleFactor,
                RangeType(
                    R.string.property_name_downsample_factor,
                    R.drawable.ic_photo_size_select,
                    (1..10)
                ), 1, true
            ), IntConverter
        )
    },
    Node(Node.Type.MultiplyAccumulate).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Accumulated", true))
        add(
            Property(
                MultiplyFactor,
                RangeType(
                    R.string.property_name_multiply_factor,
                    R.drawable.ic_clear,
                    (0f..1f)
                ), 0.9f
            ), FloatConverter
        )
        add(
            Property(
                AccumulateFactor,
                RangeType(
                    R.string.property_name_accumulate_factor,
                    R.drawable.ic_add,
                    (0f..2f)
                ), 0.9f
            ), FloatConverter
        )
    },
    Node(Node.Type.OverlayFilter).apply {
        add(Port(Port.Type.Video, "video_1", "Content", false))
        add(Port(Port.Type.Video, "video_2", "Mask", false))
        add(Port(Port.Type.Video, "video_3", "Combined", true))
    },
    Node(Node.Type.BlurFilter).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Blurred", true))
        add(
            Property(
                ScaleFactor,
                RangeType(
                    R.string.property_name_scale_factor,
                    R.drawable.ic_photo_size_select,
                    (1..10)
                ), 1, true
            ), IntConverter
        )
        add(
            Property(
                NumPasses,
                RangeType(
                    R.string.property_name_num_passes,
                    R.drawable.ic_repeat,
                    (1..10)
                ), 1
            ), IntConverter
        )
        add(
            Property(
                BlurSize,
                ChoiceType(
                    R.string.property_name_blur_size,
                    R.drawable.ic_blur,
                    Choice(5, R.string.property_label_blur_size_5),
                    Choice(9, R.string.property_label_blur_size_9),
                    Choice(13, R.string.property_label_blur_size_13)
                ), 9, true
            ), IntConverter
        )
    },
    Node(Node.Type.LutFilter).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Colored", true))
    },
    Node(Node.Type.Screen).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(
            Property(
                CropToFit,
                ChoiceType(
                    R.string.property_title_crop_to_fit,
                    R.drawable.ic_crop,
                    Choice(true, R.string.property_subtitle_crop_to_fit_enabled),
                    Choice(false, R.string.property_subtitle_crop_to_fit_disabled)
                ), false
            ), BooleanConverter
        )
    },
    Node(Node.Type.Speakers).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", false))
    },
    Node(Node.Type.Properties).also { it.id = -2 },
    Node(Node.Type.Connection).also { it.id = -3 },
    Node(Node.Type.Creation).also { it.id = -4 }
)

val NodeMap = Nodes.map { it.type to it }.toMap()

fun GetNode(type: Node.Type) = NodeMap[type] ?: error("missing node $type")

fun NewNode(type: Node.Type, networkId: Int = -1, id: Int = -1) =
    (NodeMap[type] ?: error("missing node $type")).copy(networkId, id)
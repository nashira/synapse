package com.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size
import com.rthqks.synapse.R
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.PropertyType.Companion.RangeType

val AudioSampleRate = Property.Key<Int>("audio_sample_rate")
val AudioEncoding = Property.Key<Int>("audio_encoding")
val AudioChannel = Property.Key<Int>("audio_channel")
val AudioSource = Property.Key<Int>("audio_source")
val CameraFacing = Property.Key<Int>("camera_facing")
val VideoSize = Property.Key<Size>("video_size")
val HistorySize = Property.Key<Int>("history_size")
val FrameRate = Property.Key<Int>("frame_rate")
val Stabilize = Property.Key<Boolean>("stabilize")
val Rotation = Property.Key<Int>("rotation")
val BlurSize = Property.Key<Int>("blur_size")
val NumPasses = Property.Key<Int>("num_passes")
val ScaleFactor = Property.Key<Int>("scale_factor")
val AccumulateFactor = Property.Key<Float>("accumulate_factor")
val MultiplyFactor = Property.Key<Float>("multiply_factor")
val MediaUri = Property.Key<Uri>("media_uri")
val CropToFit = Property.Key<Boolean>("crop_to_fit")
val NetworkName = Property.Key<String>("network_name")
val NumAgents = Property.Key<Int>("num_agents")
val FixedWidth = Property.Key<Boolean>("fixed_width")
val BlendMode = Property.Key<Int>("blend_mode")
val Opacity = Property.Key<Float>("opacity")
val SensorAngle = Property.Key<Float>("sensor_angle")
val SensorDistance = Property.Key<Float>("sensor_distance")
val TravelAngle = Property.Key<Float>("travel_angle")
val TravelDistance = Property.Key<Float>("travel_distance")
val SliceDepth = Property.Key<Float>("slice_depth")
val Recording = Property.Key<Boolean>("recording")

val Nodes = listOf(
    Node(NodeType.Camera).apply {
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
        add(
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
        add(
            Property(
                Stabilize,
                ToggleType(
                    R.string.property_name_camera_stabilize, R.drawable.ic_texture,
                    R.string.property_label_on,
                    R.string.property_label_off
                ), value = true, requiresRestart = true
            ), BooleanConverter
        )
    },
    Node(NodeType.Microphone).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
        add(
            Property(
                AudioSampleRate, ChoiceType(
                    R.string.property_name_sample_rate,
                    R.drawable.ic_audio,
                    Choice(16000, R.string.property_label_sample_rate_16000),
                    Choice(22050, R.string.property_label_sample_rate_22050),
                    Choice(32000, R.string.property_label_sample_rate_32000),
                    Choice(44100, R.string.property_label_sample_rate_44100),
                    Choice(48000, R.string.property_label_sample_rate_48000)
                ), 44100
            ),
            IntConverter
        )
        add(
            Property(
                AudioEncoding, ChoiceType(
                    R.string.property_name_audio_encoding,
                    R.drawable.ic_audio,
                    Choice(
                        AudioFormat.ENCODING_PCM_8BIT,
                        R.string.property_label_encoding_PCM_8BIT
                    ),
                    Choice(
                        AudioFormat.ENCODING_PCM_16BIT,
                        R.string.property_label_encoding_PCM_16BIT
                    ),
                    Choice(
                        AudioFormat.ENCODING_PCM_FLOAT,
                        R.string.property_label_encoding_PCM_FLOAT
                    )
                ),
                AudioFormat.ENCODING_PCM_16BIT
            ),
            IntConverter
        )
        add(
            Property(
                AudioChannel, ChoiceType(
                    R.string.property_name_audio_channels,
                    R.drawable.ic_audio,
                    Choice(AudioFormat.CHANNEL_IN_DEFAULT, R.string.property_label_channel_default),
                    Choice(AudioFormat.CHANNEL_IN_MONO, R.string.property_label_channel_mono),
                    Choice(AudioFormat.CHANNEL_IN_STEREO, R.string.property_label_channel_stereo)
                ),
                AudioFormat.CHANNEL_IN_DEFAULT
            ),
            IntConverter
        )
        add(
            Property(
                AudioSource, ChoiceType(
                    R.string.property_name_audio_source,
                    R.drawable.ic_audio,
                    Choice(MediaRecorder.AudioSource.MIC, R.string.property_label_audio_source_mic),
                    Choice(
                        MediaRecorder.AudioSource.CAMCORDER,
                        R.string.property_label_audio_source_camcorder
                    ),
                    Choice(
                        MediaRecorder.AudioSource.DEFAULT,
                        R.string.property_label_audio_source_default
                    ),
                    Choice(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        R.string.property_label_audio_source_voice_communication
                    ),
                    Choice(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        R.string.property_label_audio_source_voice_recognition
                    )
                ), MediaRecorder.AudioSource.DEFAULT
            ),
            IntConverter
        )
    },
    Node(NodeType.MediaFile).apply {
        add(Port(Port.Type.Video, "video_1", "Video", true))
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
        add(
            Property(
                MediaUri,
                UriType(R.string.property_name_uri, R.drawable.ic_movie, "video/*"),
                Uri.parse("none://"),
                true
            ), UriConverter
        )
    },
    Node(NodeType.Image).apply {
        add(Port(Port.Type.Video, "image_1", "Image", true))
        add(
            Property(
                MediaUri,
                UriType(R.string.property_name_uri, R.drawable.ic_image, "image/*"),
                Uri.parse("assets:///img/ic_launcher_web.png"), true
            ), UriConverter
        )
    },
    Node(NodeType.CubeImport).apply {
        add(Port(Port.Type.Texture3D, CubeImportNode.OUTPUT.id, "LUT", true))
        add(
            Property(
                MediaUri,
                UriType(R.string.property_name_uri, R.drawable.ic_image, "*/*"),
                Uri.parse("assets:///cube/invert.cube"), true
            ), UriConverter
        )
    },
    Node(NodeType.FrameDifference).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Difference", true))
    },
    Node(NodeType.GrayscaleFilter).apply {
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
    Node(NodeType.MultiplyAccumulate).apply {
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
    Node(NodeType.BlurFilter).apply {
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
    Node(NodeType.Lut2d).apply {
        add(Port(Port.Type.Video, Lut2dNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, Lut2dNode.INPUT_LUT.id, "LUT", false))
        add(Port(Port.Type.Video, Lut2dNode.OUTPUT.id, "Output", true))
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_environment_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
    },
    Node(NodeType.Lut3d).apply {
        add(Port(Port.Type.Video, Lut3dNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Texture3D, Lut3dNode.INPUT_LUT.id, "LUT", false))
        add(Port(Port.Type.Matrix, Lut3dNode.LUT_MATRIX.id, "LUT Matrix", false))
        add(Port(Port.Type.Video, Lut3dNode.OUTPUT.id, "Output", true))
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_environment_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
    },
    Node(NodeType.CropResize).apply {
        add(Port(Port.Type.Video, CropResizeNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, CropResizeNode.OUTPUT.id, "Output", true))
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_environment_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480),
                    Choice(Size(320, 320), R.string.property_label_camera_capture_size_320)
                ), Size(320, 320), true
            ), SizeConverter
        )
    },
    Node(NodeType.Screen).apply {
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
    Node(NodeType.Speakers).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", false))
    },
    Node(NodeType.SlimeMold).apply {
        add(Port(Port.Type.Video, PhysarumNode.INPUT_ENV.id, "Environment", false))
        add(Port(Port.Type.Video, PhysarumNode.INPUT_AGENT.id, "Agent", false))
        add(Port(Port.Type.Video, PhysarumNode.OUTPUT_ENV.id, "Environment", true))
        add(Port(Port.Type.Video, PhysarumNode.OUTPUT_AGENT.id, "Agent", true))
        add(
            Property(
                NumAgents,
                RangeType(
                    R.string.property_name_num_agents,
                    R.drawable.ic_scatter_plot,
                    1000..100_000
                ), 10_000, true
            ), IntConverter
        )
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_environment_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
        add(
            Property(
                SensorAngle,
                RangeType(R.string.property_name_sensor_angle, R.drawable.ic_rotate_left, 0f..90f),
                2f
            ), FloatConverter
        )
        add(
            Property(
                SensorDistance,
                RangeType(R.string.property_name_sensor_distance, R.drawable.ic_height, 1f..50f),
                11f
            ), FloatConverter
        )
        add(
            Property(
                TravelAngle,
                RangeType(
                    R.string.property_name_travel_angle,
                    R.drawable.ic_rotate_left,
                    -90f..90f
                ),
                4f
            ), FloatConverter
        )
        add(
            Property(
                TravelDistance,
                RangeType(R.string.property_name_travel_distance, R.drawable.ic_height, 1f..10f),
                1.1f
            ), FloatConverter
        )
    },
    Node(NodeType.ImageBlend).apply {
        add(Port(Port.Type.Video, ImageBlendNode.INPUT_BASE.id, "Base", false))
        add(Port(Port.Type.Video, ImageBlendNode.INPUT_BLEND.id, "Blend", false))
        add(Port(Port.Type.Video, ImageBlendNode.OUTPUT.id, "Output", true))
        add(
            Property(
                BlendMode,
                ChoiceType(
                    R.string.property_name_blend_mode,
                    R.drawable.ic_filter_b_and_w,
                    Choice(1, R.string.property_label_blend_mode_add),
                    Choice(2, R.string.property_label_blend_mode_average),
                    Choice(3, R.string.property_label_blend_mode_color_burn),
                    Choice(4, R.string.property_label_blend_mode_color_dodge),
                    Choice(5, R.string.property_label_blend_mode_darken),
                    Choice(6, R.string.property_label_blend_mode_difference),
                    Choice(7, R.string.property_label_blend_mode_exclusion),
                    Choice(8, R.string.property_label_blend_mode_glow),
                    Choice(9, R.string.property_label_blend_mode_hard_light),
                    Choice(10, R.string.property_label_blend_mode_hard_mix),
                    Choice(11, R.string.property_label_blend_mode_lighten),
                    Choice(12, R.string.property_label_blend_mode_linear_burn),
                    Choice(13, R.string.property_label_blend_mode_linear_dodge),
                    Choice(14, R.string.property_label_blend_mode_linear_light),
                    Choice(15, R.string.property_label_blend_mode_multiply),
                    Choice(16, R.string.property_label_blend_mode_negation),
                    Choice(17, R.string.property_label_blend_mode_normal),
                    Choice(18, R.string.property_label_blend_mode_overlay),
                    Choice(19, R.string.property_label_blend_mode_phoenix),
                    Choice(20, R.string.property_label_blend_mode_pin_light),
                    Choice(21, R.string.property_label_blend_mode_reflect),
                    Choice(22, R.string.property_label_blend_mode_screen),
                    Choice(23, R.string.property_label_blend_mode_soft_light),
                    Choice(24, R.string.property_label_blend_mode_subtract),
                    Choice(25, R.string.property_label_blend_mode_vivid_light)
                ), 1
            ), IntConverter
        )
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_environment_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
        add(
            Property(
                Opacity,
                RangeType(R.string.property_name_opacity, R.drawable.ic_gradient, 0f..1f),
                1f
            ), FloatConverter
        )
    },
    Node(NodeType.Shape).apply {
        //        add(Port(Port.Type.Video, ShapeNode.INPUT_POS.id, "Positions", false))
        add(Port(Port.Type.Video, ShapeNode.OUTPUT.id, "Output", true))
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_output_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
    },
    Node(NodeType.RingBuffer).apply {
        add(Port(Port.Type.Video, RingBufferNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Texture3D, RingBufferNode.OUTPUT.id, "Output", true))
        add(
            Property(
                HistorySize,
                RangeType(
                    R.string.property_name_history_size,
                    R.drawable.ic_layers,
                    1..60
                ), 10, true
            ), IntConverter
        )
        add(
            Property(
                VideoSize,
                ChoiceType(
                    R.string.property_name_output_video_size,
                    R.drawable.ic_photo_size_select,
                    Choice(Size(2160, 3840), R.string.property_label_camera_capture_size_2160),
                    Choice(Size(1080, 1920), R.string.property_label_camera_capture_size_1080),
                    Choice(Size(720, 1280), R.string.property_label_camera_capture_size_720),
                    Choice(Size(1024, 1024), R.string.property_label_camera_capture_size_1024sq),
                    Choice(Size(480, 640), R.string.property_label_camera_capture_size_480)
                ), Size(720, 1280), true
            ), SizeConverter
        )
    },
    Node(NodeType.Slice3d).apply {
        add(Port(Port.Type.Texture3D, Slice3dNode.INPUT_3D.id, "Input", false))
        add(Port(Port.Type.Video, Slice3dNode.OUTPUT.id, "Output", true))
        add(
            Property(
                SliceDepth,
                RangeType(
                    R.string.property_name_slice_depth,
                    R.drawable.ic_layers,
                    0f..1f
                ), 0f, false
            ), FloatConverter
        )
        add(
            Property(
                FrameRate,
                RangeType(
                    R.string.property_name_frame_rate,
                    R.drawable.ic_speed,
                    10..60
                ), 30, false
            ), IntConverter
        )
    },
    Node(NodeType.MediaEncoder).apply {
        add(Port(Port.Type.Video, EncoderNode.INPUT_VIDEO.id, "Video", false))
        add(Port(Port.Type.Audio, EncoderNode.INPUT_AUDIO.id, "Audio", false))
        add(
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
                ), 30, false
            ), IntConverter
        )
        add(
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
        add(
            Property(
                Rotation,
                ChoiceType(
                    R.string.property_name_rotation,
                    R.drawable.ic_rotate_left,
                    Choice(0, R.string.property_label_rotation_0),
                    Choice(90, R.string.property_label_rotation_90),
                    Choice(270, R.string.property_label_rotation_270),
                    Choice(180, R.string.property_label_rotation_180)
                ), 0
            ), IntConverter
        )
    },
    Node(NodeType.RotateMatrix).apply {
        add(Port(Port.Type.Matrix, RotateMatrixNode.OUTPUT.id, "Matrix", true))
        add(
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
                ), 30, false
            ), IntConverter
        )
    },
    Node(NodeType.Properties).also { it.id = -2 },
    Node(NodeType.Connection).also { it.id = -3 },
    Node(NodeType.Creation).also { it.id = -4 }
)

val NodeMap = Nodes.map { it.type to it }.toMap()

val NodeTypes = listOf(
    NodeType.Camera,
    NodeType.Microphone,
    NodeType.MediaFile,
    NodeType.FrameDifference,
    NodeType.GrayscaleFilter,
    NodeType.MultiplyAccumulate,
    NodeType.BlurFilter,
    NodeType.Image,
    NodeType.CubeImport,
    NodeType.AudioFile,
    NodeType.Lut2d,
    NodeType.Lut3d,
    NodeType.ShaderFilter,
    NodeType.Speakers,
    NodeType.Screen,
    NodeType.SlimeMold,
    NodeType.ImageBlend,
    NodeType.CropResize,
    NodeType.Shape,
    NodeType.RingBuffer,
    NodeType.Slice3d,
    NodeType.MediaEncoder,
    NodeType.RotateMatrix,
    NodeType.Properties,
    NodeType.Creation,
    NodeType.Connection
).map { it.key to it }.toMap()

fun GetNode(type: NodeType) = NodeMap[type] ?: error("missing node $type")

fun NewNode(type: NodeType, id: Int = -1) =
    (NodeMap[type] ?: error("missing node $type")).copy(id = id)
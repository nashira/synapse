package com.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size

@Suppress("LeakingThis")
sealed class NodeDef(
    val key: String
) {
    var ports: List<PortDef> = emptyList()
        protected set
    var properties: Map<Property.Key<*>, Any?> = emptyMap()
        protected set

    init {
        MAP[key] = this
    }

    fun toNode(id: Int = -1) = Node(key, id).also { n ->
        ports.forEach { p -> n.add(Port(p.type, p.key, p.output)) }
        properties.forEach { p -> n.properties[p.key as Property.Key<Any?>] = p.value }
    }

    object Camera : NodeDef("camera") {
        val OUTPUT = PortDef(PortType.Video, "camera_out", true)

        val CameraFacing = Property.Key("camera_facing", Int::class.java)
        val VideoSize = Property.Key("video_size", Size::class.java)
        val FrameRate = Property.Key("frame_rate", Int::class.java)
        val Stabilize = Property.Key("stabilize", Boolean::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                CameraFacing to CameraCharacteristics.LENS_FACING_BACK,
                VideoSize to Size(1280, 720),
                FrameRate to 30,
                Stabilize to true
            )
        }
    }

    object Microphone : NodeDef("microphone") {
        val OUTPUT = PortDef(PortType.Audio, "audio_out", true)

        val AudioSampleRate = Property.Key("audio_sample_rate", Int::class.java)
        val AudioEncoding = Property.Key("audio_encoding", Int::class.java)
        val AudioChannel = Property.Key("audio_channel", Int::class.java)
        val AudioSource = Property.Key("audio_source", Int::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                AudioSampleRate to 44100,
                AudioEncoding to AudioFormat.ENCODING_PCM_16BIT,
                AudioChannel to AudioFormat.CHANNEL_IN_DEFAULT,
                AudioSource to  MediaRecorder.AudioSource.DEFAULT
            )
        }
    }

    object MediaDecoder : NodeDef("media_decoder") {
        val AUDIO_OUT = PortDef(PortType.Audio, "audio_out", true)
        val VIDEO_OUT = PortDef(PortType.Video, "video_out", true)
        val MediaUri = Property.Key("media_uri", Uri::class.java)

        init {
            ports = listOf(AUDIO_OUT, VIDEO_OUT)
            properties = mapOf(
                MediaUri to Uri.parse("none://")
            )
        }
    }

    object FrameDifference : NodeDef("frame_difference") {
        val SOURCE_IN = PortDef(PortType.Video, "source_in", false)
        val DIFF_OUT = PortDef(PortType.Video, "diff_out", true)

        init {
            ports = listOf(SOURCE_IN, DIFF_OUT)
        }
    }

    object MultiplyAccumulate : NodeDef(
        "multiply_accumulate"
    )

    object CropResize : NodeDef("crop_resize") {
        val INPUT = PortDef(PortType.Video, "crop_in", false)
        val OUTPUT = PortDef(PortType.Video, "crop_out", true)

        val CropSize = Property.Key("crop_size", Size::class.java)

        init {
            ports = listOf(INPUT, OUTPUT)
            properties = mapOf(
                CropSize to Size(320, 320)
            )
        }
    }

    object CropGrayBlur : NodeDef("crop_gray_blur") {
        val INPUT = PortDef(PortType.Video, "cgb_in", false)
        val OUTPUT = PortDef(PortType.Video, "cgb_out", true)

        val CropSize = Property.Key("crop_size", Size::class.java)
        val BlurSize = Property.Key("blur_size", Int::class.java)
        val NumPasses = Property.Key("num_passes", Int::class.java)
        val CropEnabled = Property.Key("crop_enabled", Boolean::class.java)
        val GrayEnabled = Property.Key("gray_enabled", Boolean::class.java)

        init {
            ports = listOf(INPUT, OUTPUT)
            properties = mapOf(
                NumPasses to 1,
                BlurSize to 0,
                CropEnabled to false,
                GrayEnabled to false,
                CropSize to Size(320, 320)
            )
        }
    }

    object Image : NodeDef("image") {
        val OUTPUT = PortDef(PortType.Video, "image_out", true)
        val MediaUri = Property.Key("media_uri", Uri::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                MediaUri to Uri.parse("assets:///img/ic_launcher_web.png")
            )
        }
    }

    object Lut2d : NodeDef(
        "lut_2d"
    )

    object Lut3d : NodeDef("lut_3d") {
        val SOURCE_IN = PortDef(PortType.Video, "source_in", false)
        val LUT_IN = PortDef(PortType.Texture3D, "lut_in", false)
        val MATRIX_IN = PortDef(PortType.Matrix, "matrix_in", false)
        val OUTPUT = PortDef(PortType.Video, "output", true)
        val LutStrength = Property.Key("lut_strength", Float::class.java)

        init {
            ports = listOf(SOURCE_IN, LUT_IN, MATRIX_IN, OUTPUT)
            properties = mapOf(
                LutStrength to 1f
            )
        }

    }

    object Speakers : NodeDef("speakers") {
        val INPUT = PortDef(PortType.Audio, "speakers_in", false)

        init {
            ports = listOf(INPUT)
        }
    }

    object Screen : NodeDef("screen") {
        val INPUT = PortDef(PortType.Video, "screen_in", false)

        init {
            ports = listOf(INPUT)
        }
    }

    object SlimeMold : NodeDef(
        "physarum"
    )

    object ImageBlend : NodeDef("image_blend") {
        val BASE_IN = PortDef(PortType.Video, "base_in", false)
        val BLEND_IN = PortDef(PortType.Video, "blend_in", false)
        val OUTPUT = PortDef(PortType.Video, "output", true)

        val BlendMode = Property.Key("blend_mode", Int::class.java)
        val Opacity = Property.Key("opacity", Float::class.java)

        init {
            ports = listOf(BASE_IN, BLEND_IN, OUTPUT)
            properties = mapOf(
                BlendMode to 1,
                Opacity to 1f
            )
        }
    }

    object CubeImport : NodeDef("cube_import") {
        val OUTPUT = PortDef(PortType.Texture3D, "cube_out", true)
        val LutUri = Property.Key("lut_uri", Uri::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                LutUri to Uri.parse("assets:///cube/identity.cube")
            )
        }
    }

    object BCubeImport : NodeDef("bcube_import") {
        val OUTPUT = PortDef(PortType.Texture3D, "bcube_out", true)
        val LutUri = Property.Key("lut_uri", Uri::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                LutUri to Uri.parse("assets:///cube/identity.bcube")
            )
        }
    }

    object Shape : NodeDef(
        "shape"
    )

    object RingBuffer : NodeDef("ring_buffer") {
        val INPUT = PortDef(PortType.Video, "rb_input", false)
        val OUTPUT = PortDef(PortType.Texture3D, "rb_output", true)
        val Depth = Property.Key("depth", Int::class.java)

        init {
            ports = listOf(INPUT, OUTPUT)
            properties = mapOf(
                Depth to 10
            )
        }
    }

    object Slice3d : NodeDef("slice_3d") {
        val INPUT = PortDef(PortType.Texture3D, "slice_input", false)
        val OUTPUT = PortDef(PortType.Video, "slice_output", true)
        val SliceDirection = Property.Key("slice_direction", Int::class.java)

        init {
            ports = listOf(INPUT, OUTPUT)
            properties = mapOf(
                SliceDirection to 0
            )
        }
    }

    object MediaEncoder : NodeDef("media_encoder") {
        val VIDEO_IN = PortDef(PortType.Video, "video_in", false)
        val AUDIO_IN = PortDef(PortType.Audio, "audio_in", false)
        val Rotation = Property.Key("rotation", Int::class.java)
        val FrameRate = Property.Key("frame_rate", Int::class.java)
        val Recording = Property.Key("recording", Boolean::class.java)

        init {
            ports = listOf(AUDIO_IN, VIDEO_IN)
            properties = mapOf(
                Rotation to 0,
                FrameRate to 30,
                Recording to false
            )
        }
    }

    object RotateMatrix : NodeDef("matrix_rotate") {
        val OUTPUT = PortDef(PortType.Matrix, "matrix_out", true)
        val Speed = Property.Key("speed", Float::class.java)
        val FrameRate = Property.Key("frame_rate", Int::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                FrameRate to 30,
                Speed to 1f
            )
        }
    }

    object TextureView : NodeDef("texture_view") {
        val INPUT = PortDef(PortType.Video, "texture_view_in", false)

        init {
            ports = listOf(INPUT)
        }
    }

    object CellAuto : NodeDef("cellular_automaton") {
        val OUTPUT = PortDef(PortType.Video, "cell_out", true)
        val FrameRate = Property.Key("frame_rate", Int::class.java)
        val GridSize = Property.Key("grid_size", Size::class.java)

        init {
            ports = listOf(OUTPUT)
            properties = mapOf(
                FrameRate to 30,
                GridSize to Size(180, 320)
            )
        }
    }

    object Quantizer : NodeDef("quantizer") {
        val INPUT = PortDef(PortType.Video, "q_input", false)
        val OUTPUT = PortDef(PortType.Video, "q_output", true)
        val NumElements = Property.Key("num_elements", FloatArray::class.java)

        init {
            ports = listOf(INPUT, OUTPUT)
            properties = mapOf(
                NumElements to floatArrayOf(6f, 6f, 6f)
            )
        }
    }

    object Sobel : NodeDef("sobel") {
        val INPUT = PortDef(PortType.Video, "sobel_in", false)
        val OUTPUT = PortDef(PortType.Video, "sobel_out", true)

        init {
            ports = listOf(INPUT, OUTPUT)
        }
    }

    companion object {
        private val MAP = mutableMapOf<String, NodeDef>()

        operator fun get(key: String) = MAP[key] ?: error("unknown node type: $key")
    }
}
package xyz.rthqks.synapse.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

sealed class NodeType(
    val key: String,
    @StringRes val name: Int,
    @DrawableRes val icon: Int,
    ports: List<PortType>,
    propertyList: List<PropertyType<*>>
) {
    val inputs: List<PortType> = ports.filter { it.direction == PortType.INPUT }
    val outputs: List<PortType> = ports.filter { it.direction == PortType.OUTPUT }
    val properties: Map<Key<*>, PropertyType<*>> = propertyList.map { Pair(it.key, it) }.toMap()

    object Camera : NodeType(
        "camera",
        R.string.name_node_type_camera, R.drawable.ic_camera,
        listOf(
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        listOf(
            PropertyType.CameraFacing,
            PropertyType.CameraCaptureSize,
            PropertyType.CameraFrameRate
        )
    )

    object Microphone : NodeType(
        "microphone",
        R.string.name_node_type_microphone, R.drawable.ic_mic,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.OUTPUT)),
        listOf(
            PropertyType.AudioChannel,
            PropertyType.AudioEncoding,
            PropertyType.AudioSampleRate,
            PropertyType.AudioSource
        )
    )

    object FrameDifference : NodeType(
        "frame_difference",
        R.string.name_node_type_frame_difference, R.drawable.ic_difference,
        listOf(
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_2, PortType.OUTPUT)
        ),
        emptyList()
    )

    object GrayscaleFilter : NodeType(
        "grayscale_filter",
        R.string.name_node_type_grayscale_filter, R.drawable.ic_tune,
        listOf(
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        listOf(
            PropertyType.ScaleFactor,
            PropertyType.AspectRatio
        )
    )

    object OverlayFilter : NodeType(
        "overlay_filter",
        R.string.name_node_type_overlay_filter, R.drawable.ic_layers,
        listOf(
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_2, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        emptyList()
    ) {
        const val MASK_TEXTURE = "mask_texture"
        const val CONTENT_TEXTURE = "content_texture"
    }

    object BlurFilter : NodeType(
        "blur_filter",
        R.string.name_node_type_blur_filter, R.drawable.ic_blur,
        listOf(
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        listOf(
            PropertyType.BlurSize,
            PropertyType.NumPasses,
            PropertyType.ScaleFactor
        )
    )

    object AudioWaveform : NodeType(
        "audio_waveform",
        R.string.name_node_type_audio_waveform, R.drawable.ic_audio,
        listOf(
            PortType.AudioBuffer(PortType.AUDIO_1, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT)
        ),
        emptyList()
    )

    object Image : NodeType(
        "image",
        R.string.name_node_type_image, R.drawable.ic_image,
        listOf(PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)),
        emptyList()
    )

    object AudioFile : NodeType(
        "audio_file",
        R.string.name_node_type_audio_file,
        R.drawable.ic_audio_file,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.OUTPUT)),
        emptyList()
    )

    object VideoFile : NodeType(
        "video_file",
        R.string.name_node_type_video_file, R.drawable.ic_movie,
        listOf(PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT)),
        emptyList()
    )

    object LutFilter : NodeType(
        "lut_filter",
        R.string.name_node_type_color_filter, R.drawable.ic_tune,
        listOf(
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        emptyList()
    )

    object ShaderFilter : NodeType(
        "shader_filter",
        R.string.name_node_type_shader_filter, R.drawable.ic_texture,
        listOf(
            PortType.Surface(PortType.SURFACE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        emptyList()
    )

    object Speakers : NodeType(
        "speakers",
        R.string.name_node_type_speaker, R.drawable.ic_speaker,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.INPUT)),
        emptyList()
    )

    object Screen : NodeType(
        "screen",
        R.string.name_node_type_screen,
        R.drawable.ic_display,
        listOf(
            PortType.Surface(PortType.SURFACE_1, PortType.INPUT)
        ),
        emptyList()
    )

    companion object {
        const val SIZE = 9
    }
}
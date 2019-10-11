package xyz.rthqks.synapse.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

sealed class NodeType(
    @StringRes val name: Int,
    @DrawableRes val icon: Int,
    ports: List<PortType>,
    propertyList: List<Property>
) {
    val inputs: List<PortType> = ports.filter { it.direction == PortType.INPUT }
    val outputs: List<PortType> = ports.filter { it.direction == PortType.OUTPUT }
    val properties: Map<String, Property> = propertyList.map { Pair(it.key, it) }.toMap()

    object Camera : NodeType(
        R.string.name_node_type_camera, R.drawable.ic_camera,
        listOf(PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT)),
        emptyList()
    )

    object Microphone : NodeType(
        R.string.name_node_type_microphone, R.drawable.ic_mic,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.OUTPUT)),
        listOf(
            PropertyType.AUDIO_CHANNEL,
            PropertyType.AUDIO_ENCODING,
            PropertyType.AUDIO_SAMPLE_RATE,
            PropertyType.AUDIO_SOURCE
        )
    )

    object Image : NodeType(
        R.string.name_node_type_image, R.drawable.ic_image,
        listOf(PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)),
        emptyList()
    )

    object AudioFile : NodeType(
        R.string.name_node_type_audio_file,
        R.drawable.ic_audio_file,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.OUTPUT)),
        emptyList()
    )

    object VideoFile : NodeType(
        R.string.name_node_type_video_file, R.drawable.ic_movie,
        listOf(PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT)),
        emptyList()
    )

    object ColorFilter : NodeType(
        R.string.name_node_type_color_filter, R.drawable.ic_tune,
        listOf(
            PortType.Surface(PortType.SURFACE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT),
            PortType.Surface(PortType.SURFACE_1, PortType.OUTPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.OUTPUT)
        ),
        emptyList()
    )

    object ShaderFilter : NodeType(
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
        R.string.name_node_type_speaker, R.drawable.ic_speaker,
        listOf(PortType.AudioBuffer(PortType.AUDIO_1, PortType.INPUT)),
        emptyList()
    )

    object Screen : NodeType(
        R.string.name_node_type_screen,
        R.drawable.ic_display,
        listOf(
            PortType.Surface(PortType.SURFACE_1, PortType.INPUT),
            PortType.Texture(PortType.TEXTURE_1, PortType.INPUT)
        ),
        emptyList()
    )

    companion object {
        const val SIZE = 9
    }
}
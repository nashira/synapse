package xyz.rthqks.proc.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.proc.R

sealed class NodeType(
    @StringRes val name: Int,
    @DrawableRes val icon: Int,
    val inputs: List<DataType>,
    val outputs: List<DataType>,
    propertyList: List<Property>
) {
    val properties: Map<String, Property> = propertyList.map { Pair(it.key, it) }.toMap()

    object Camera : NodeType(
        R.string.name_node_type_camera, R.drawable.ic_camera,
        emptyList(), listOf(DataType.Surface),
        emptyList()
    )

    object Microphone : NodeType(
        R.string.name_node_type_microphone, R.drawable.ic_mic,
        emptyList(), listOf(DataType.AudioBuffer),
        listOf(
            PropertyType.AUDIO_CHANNEL,
            PropertyType.AUDIO_ENCODING,
            PropertyType.AUDIO_SAMPLE_RATE,
            PropertyType.AUDIO_SOURCE
        )
    )

    object Image : NodeType(
        R.string.name_node_type_image, R.drawable.ic_image,
        emptyList(), listOf(DataType.Texture),
        emptyList()
    )

    object AudioFile : NodeType(
        R.string.name_node_type_audio_file,
        R.drawable.ic_audio_file,
        emptyList(), listOf(DataType.AudioBuffer),
        emptyList()
    )

    object VideoFile : NodeType(
        R.string.name_node_type_video_file, R.drawable.ic_movie,
        emptyList(), listOf(DataType.Surface),
        emptyList()
    )

    object ColorFilter : NodeType(
        R.string.name_node_type_color_filter, R.drawable.ic_tune,
        listOf(DataType.Surface, DataType.Texture),
        listOf(DataType.Surface, DataType.Texture),
        emptyList()
    )

    object ShaderFilter : NodeType(
        R.string.name_node_type_shader_filter, R.drawable.ic_texture,
        listOf(DataType.Surface, DataType.Texture),
        listOf(DataType.Surface, DataType.Texture),
        emptyList()
    )

    object Speakers : NodeType(
        R.string.name_node_type_speaker, R.drawable.ic_speaker,
        listOf(DataType.AudioBuffer), emptyList(),
        emptyList()
    )

    object Screen : NodeType(
        R.string.name_node_type_screen,
        R.drawable.ic_display,
        listOf(DataType.Surface, DataType.Texture), emptyList(),
        emptyList()
    )

    companion object {
        const val SIZE = 9
    }
}
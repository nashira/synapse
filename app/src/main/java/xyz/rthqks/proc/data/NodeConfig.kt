package xyz.rthqks.proc.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.proc.R

sealed class NodeConfig(
    @StringRes val name: Int,
    @DrawableRes val icon: Int,
    val inputs: List<PortConfig>,
    val outputs: List<PortConfig>
) {
    data class Camera(val id: Int = -1) : NodeConfig(
        R.string.name_node_type_camera, R.drawable.ic_camera,
        emptyList(), listOf(PortConfig.Surface)
    )

    data class Microphone(val id: Int = -1) : NodeConfig(
        R.string.name_node_type_microphone, R.drawable.ic_mic,
        emptyList(), listOf(PortConfig.AudioBuffer)
    )

    data class Image(val filename: String = "") : NodeConfig(
        R.string.name_node_type_image, R.drawable.ic_image,
        emptyList(), listOf(PortConfig.Texture)
    )

    data class AudioFile(val filename: String = "") : NodeConfig(
        R.string.name_node_type_audio_file,
        R.drawable.ic_audio_file,
        emptyList(), listOf(PortConfig.AudioBuffer)
    )

    data class VideoFile(val filename: String = "") : NodeConfig(
        R.string.name_node_type_video_file, R.drawable.ic_movie,
        emptyList(), listOf(PortConfig.Surface)
    )

    data class ColorFilter(val color: Int = 0) : NodeConfig(
        R.string.name_node_type_color_filter, R.drawable.ic_tune,
        listOf(PortConfig.Surface, PortConfig.Texture),
        listOf(PortConfig.Surface, PortConfig.Texture)
    )

    data class ShaderFilter(val source: String = "") : NodeConfig(
        R.string.name_node_type_shader_filter, R.drawable.ic_texture,
        listOf(PortConfig.Surface, PortConfig.Texture),
        listOf(PortConfig.Surface, PortConfig.Texture, PortConfig.Surface, PortConfig.Texture, PortConfig.Surface, PortConfig.Texture, PortConfig.Surface, PortConfig.Texture, PortConfig.Surface, PortConfig.Texture)
    )

    object Speakers : NodeConfig(
        R.string.name_node_type_speaker, R.drawable.ic_speaker,
        listOf(PortConfig.AudioBuffer), emptyList()
    )

    object Screen : NodeConfig(
        R.string.name_node_type_screen,
        R.drawable.ic_display,
        listOf(PortConfig.Surface, PortConfig.Texture), emptyList()
    )

    companion object {
        const val SIZE = 9
    }
}
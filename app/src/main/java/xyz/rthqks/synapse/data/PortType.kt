package xyz.rthqks.synapse.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

sealed class PortType(
    val key: String,
    val direction: Int,
    @StringRes val name: Int,
    @DrawableRes val icon: Int
) {
    class Surface(key: String, direction: Int) : PortType(key, direction, R.string.name_data_type_surface, R.drawable.ic_image)
    class Texture(key: String, direction: Int) : PortType(key, direction, R.string.name_data_type_texture, R.drawable.ic_texture)
    class AudioBuffer(key: String, direction: Int) : PortType(key, direction, R.string.name_data_type_audio_buffer, R.drawable.ic_audio)

    companion object {
        const val INPUT = 0
        const val OUTPUT = 1

        const val SURFACE_1 = "surface_1"
        const val AUDIO_1 = "audio_1"
        const val TEXTURE_1 = "texture_1"
        const val TEXTURE_2 = "texture_2"
    }
}
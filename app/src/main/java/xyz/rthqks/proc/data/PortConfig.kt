package xyz.rthqks.proc.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.proc.R

sealed class PortConfig(
    @StringRes val name: Int,
    @DrawableRes val icon: Int
) {
    class Surface : PortConfig(R.string.name_data_type_surface, R.drawable.ic_image)
    class Texture : PortConfig(R.string.name_data_type_texture, R.drawable.ic_texture)
    class AudioBuffer : PortConfig(R.string.name_data_type_audio_buffer, R.drawable.ic_audio)
}
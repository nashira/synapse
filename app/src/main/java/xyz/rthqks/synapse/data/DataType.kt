package xyz.rthqks.synapse.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

sealed class DataType(
    @StringRes val name: Int,
    @DrawableRes val icon: Int
) {
    object Surface : DataType(R.string.name_data_type_surface, R.drawable.ic_image)
    object Texture : DataType(R.string.name_data_type_texture, R.drawable.ic_texture)
    object AudioBuffer : DataType(R.string.name_data_type_audio_buffer, R.drawable.ic_audio)
}
package com.rthqks.synapse.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rthqks.synapse.R

sealed class NodeType(
    val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val flags: Int = 0
) {

    companion object {
//        private val byKey = values().map { it.key to it }.toMap()
//        operator fun get(key: String) = byKey[key]

        const val FLAG_PRODUCER = 1
        const val FLAG_CONSUMER = 1 shl 1
    }

    object Camera : NodeType(
        "camera",
        R.string.name_node_type_camera,
        R.drawable.ic_camera,
        FLAG_PRODUCER
    )

    object Microphone : NodeType(
        "microphone",
        R.string.name_node_type_microphone,
        R.drawable.ic_mic,
        FLAG_PRODUCER
    )

    object MediaFile : NodeType(
        "media_file",
        R.string.name_node_type_media_file,
        R.drawable.ic_movie,
        FLAG_PRODUCER
    )

    object FrameDifference : NodeType(
        "frame_difference",
        R.string.name_node_type_frame_difference,
        R.drawable.ic_difference
    )

    object GrayscaleFilter : NodeType(
        "grayscale",
        R.string.name_node_type_grayscale_filter,
        R.drawable.ic_tune
    )

    object MultiplyAccumulate : NodeType(
        "multiply_accumulate",
        R.string.name_node_type_multiply_accumulate,
        R.drawable.ic_add
    )

    object BlurFilter : NodeType(
        "blur",
        R.string.name_node_type_blur_filter,
        R.drawable.ic_blur
    )

    object Image : NodeType(
        "image",
        R.string.name_node_type_image,
        R.drawable.ic_image,
        FLAG_PRODUCER
    )

    object AudioFile : NodeType(
        "audio",
        R.string.name_node_type_audio_file,
        R.drawable.ic_audio_file,
        FLAG_PRODUCER
    )

    object Lut2d : NodeType(
        "lut_2d",
        R.string.name_node_type_lut2d_filter,
        R.drawable.ic_tune
    )

    object Lut3d : NodeType(
        "lut_3d",
        R.string.name_node_type_lut3d_filter,
        R.drawable.ic_tune
    )

    object ShaderFilter : NodeType(
        "shader",
        R.string.name_node_type_shader_filter,
        R.drawable.ic_texture
    )

    object Speakers : NodeType(
        "speakers",
        R.string.name_node_type_speaker,
        R.drawable.ic_speaker
    )

    object Screen : NodeType(
        "screen",
        R.string.name_node_type_screen,
        R.drawable.ic_display
    )

    object Properties : NodeType(
        "properties",
        R.string.name_node_type_properties,
        R.drawable.ic_tune
    )

    object Creation : NodeType(
        "creation",
        R.string.name_node_type_create,
        R.drawable.ic_add
    )

    object SlimeMold : NodeType(
        "physarum",
        R.string.name_node_type_slime_mold,
        R.drawable.ic_slime_mold,
        FLAG_PRODUCER
    )

    object ImageBlend : NodeType(
        "image_blend",
        R.string.name_node_type_image_blend,
        R.drawable.ic_filter_b_and_w
    )

    object Connection : NodeType("connection", 0, 0)

    object CubeImport : NodeType(
        "cube_import",
        R.string.name_node_type_cube_importer,
        R.drawable.ic_3d_rotation,
        FLAG_PRODUCER
    )

    object CropResize : NodeType(
        "crop_resize",
        R.string.name_node_type_crop_resize,
        R.drawable.ic_crop
    )

    object Shape : NodeType(
        "shape",
        R.string.name_node_type_shape,
        R.drawable.ic_change_history,
        FLAG_PRODUCER
    )

    object RingBuffer : NodeType(
        "ring_buffer",
        R.string.name_node_type_ring_buffer,
        R.drawable.ic_layers
    )

    object Slice3d : NodeType(
        "slice_3d",
        R.string.name_node_type_slice_3d,
        R.drawable.ic_layers
    )
}
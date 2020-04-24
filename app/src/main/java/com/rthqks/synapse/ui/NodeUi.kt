package com.rthqks.synapse.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.NodeDef

class NodeUi(
    @StringRes val title: Int,
    @DrawableRes val icon: Int
) {

    companion object {
        private val MAP = mapOf(
            NodeDef.Camera.key to NodeUi(
                R.string.name_node_type_camera,
                R.drawable.ic_camera
            ),

            NodeDef.Microphone.key to NodeUi(
                R.string.name_node_type_microphone,
                R.drawable.ic_mic
            ),

            NodeDef.MediaEncoder.key to NodeUi(
                R.string.name_node_type_media_file,
                R.drawable.ic_movie
            ),

            NodeDef.FrameDifference.key to NodeUi(
                R.string.name_node_type_frame_difference,
                R.drawable.ic_difference
            ),

            NodeDef.MultiplyAccumulate.key to NodeUi(
                R.string.name_node_type_multiply_accumulate,
                R.drawable.ic_add
            ),

            NodeDef.CropGrayBlur.key to NodeUi(
                R.string.name_node_type_blur_filter,
                R.drawable.ic_blur
            ),

            NodeDef.Image.key to NodeUi(
                R.string.name_node_type_image,
                R.drawable.ic_image
            ),

            NodeDef.Lut2d.key to NodeUi(
                R.string.name_node_type_lut2d_filter,
                R.drawable.ic_tune
            ),

            NodeDef.Lut3d.key to NodeUi(
                R.string.name_node_type_lut3d_filter,
                R.drawable.ic_tune
            ),

            NodeDef.Speakers.key to NodeUi(
                R.string.name_node_type_speaker,
                R.drawable.ic_speaker
            ),

            NodeDef.Screen.key to NodeUi(
                R.string.name_node_type_screen,
                R.drawable.ic_display
            ),

            NodeDef.SlimeMold.key to NodeUi(
                R.string.name_node_type_slime_mold,
                R.drawable.ic_slime_mold
            ),

            NodeDef.ImageBlend.key to NodeUi(
                R.string.name_node_type_image_blend,
                R.drawable.ic_filter_b_and_w
            ),

            NodeDef.CubeImport.key to NodeUi(
                R.string.name_node_type_cube_importer,
                R.drawable.ic_3d_rotation
            ),

            NodeDef.BCubeImport.key to NodeUi(
                R.string.name_node_type_bcube_importer,
                R.drawable.ic_3d_rotation
            ),

            NodeDef.CropResize.key to NodeUi(
                R.string.name_node_type_crop_resize,
                R.drawable.ic_crop
            ),

            NodeDef.Shape.key to NodeUi(
                R.string.name_node_type_shape,
                R.drawable.ic_change_history
            ),

            NodeDef.RingBuffer.key to NodeUi(
                R.string.name_node_type_ring_buffer,
                R.drawable.ic_layers
            ),

            NodeDef.Slice3d.key to NodeUi(
                R.string.name_node_type_slice_3d,
                R.drawable.ic_layers
            ),

            NodeDef.MediaEncoder.key to NodeUi(
                R.string.name_node_type_media_encoder,
                R.drawable.ic_movie
            ),

            NodeDef.RotateMatrix.key to NodeUi(
                R.string.name_node_type_matrix_rotate,
                R.drawable.ic_3d_rotation
            ),

            NodeDef.TextureView.key to NodeUi(
                R.string.name_node_type_texture_view,
                R.drawable.ic_display
            ),

            NodeDef.CellAuto.key to NodeUi(
                R.string.name_node_type_cell_auto,
                R.drawable.ic_view_module
            ),

            NodeDef.Quantizer.key to NodeUi(
                R.string.name_node_type_quantizer,
                R.drawable.ic_view_module
            ),

            NodeDef.Sobel.key to NodeUi(
                R.string.name_node_type_sobel,
                R.drawable.ic_difference
            )
        )

        operator fun get(key: String) = MAP[key] ?: error("unknown node type $key")
    }
}
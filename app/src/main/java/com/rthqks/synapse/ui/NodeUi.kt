package com.rthqks.synapse.ui

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.NodeDef.*
import com.rthqks.synapse.logic.Property

class NodeUi(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    private val prop: Map<Property.Key<*>, PropertyUi<*>> = emptyMap()
) {
    operator fun <T: Any> get(key: Property.Key<T>) = prop[key] //?: error("unknown property key: $key")

    companion object {

        operator fun get(key: String) = MAP[key] ?: error("unknown node type $key")

        private val MAP = mapOf(
            Camera.key to NodeUi(
                R.string.name_node_type_camera,
                R.drawable.ic_camera,
                mapOf(
                    Camera.CameraFacing to toggleUi(
                        R.string.property_name_camera_device,
                        R.drawable.ic_flip_camera,
                        Choice(
                            CameraCharacteristics.LENS_FACING_BACK,
                            0,
                            R.drawable.ic_camera_rear
                        ),
                        Choice(
                            CameraCharacteristics.LENS_FACING_FRONT,
                            0,
                            R.drawable.ic_camera_front
                        )
                    ),
                    Camera.VideoSize to menuUi(
                        R.string.property_name_capture_size,
                        R.drawable.ic_photo_size_select,
                        Choice(
                            Size(640, 480),
                            R.string.property_label_camera_capture_size_480,
                            0
                        ),
                        Choice(
                            Size(1280, 720),
                            R.string.property_label_camera_capture_size_720,
                            0
                        ),
                        Choice(
                            Size(1920, 1080),
                            R.string.property_label_camera_capture_size_1080,
                            0
                        ),
                        Choice(
                            Size(3840, 2160),
                            R.string.property_label_camera_capture_size_2160,
                            0
                        )
                    ),
                    Camera.FrameRate to menuUi(
                        R.string.property_name_frame_rate, R.drawable.ic_speed,
                        Choice(10, R.string.property_label_camera_fps_10, 0),
                        Choice(15, R.string.property_label_camera_fps_15, 0),
                        Choice(24, R.string.property_label_camera_fps_24, 0),
                        Choice(30, R.string.property_label_camera_fps_30, 0),
                        Choice(60, R.string.property_label_camera_fps_60, 0)
                    ),
                    Camera.Stabilize to toggleUi(
                        R.string.property_name_camera_stabilize, R.drawable.ic_control_camera,
                        Choice(true, R.string.property_label_on, 0),
                        Choice(false, R.string.property_label_off, 0)
                    )
                )
            ),

            Microphone.key to NodeUi(
                R.string.name_node_type_microphone,
                R.drawable.ic_mic
//            listOf(
//                        16000, 22050, 32000, 44100, 48000
//                    )
//            listOf(
//                        AudioFormat.ENCODING_PCM_8BIT,
//                        AudioFormat.ENCODING_PCM_16BIT,
//                        AudioFormat.ENCODING_PCM_FLOAT
//                    )
//            listOf(
//                        AudioFormat.CHANNEL_IN_DEFAULT,
//                        AudioFormat.CHANNEL_IN_MONO,
//                        AudioFormat.CHANNEL_IN_STEREO
//                    )
//            listOf(
//                        MediaRecorder.AudioSource.CAMCORDER,
//                        MediaRecorder.AudioSource.DEFAULT,
//                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
//                        MediaRecorder.AudioSource.VOICE_RECOGNITION
//                    )
            ),

            MediaEncoder.key to NodeUi(
                R.string.name_node_type_media_file,
                R.drawable.ic_movie
            ),

            FrameDifference.key to NodeUi(
                R.string.name_node_type_frame_difference,
                R.drawable.ic_difference
            ),

            MultiplyAccumulate.key to NodeUi(
                R.string.name_node_type_multiply_accumulate,
                R.drawable.ic_add
            ),

            CropGrayBlur.key to NodeUi(
                R.string.name_node_type_blur_filter,
                R.drawable.ic_blur,
                mapOf(
                    CropGrayBlur.CropSize to toggleUi(
                        R.string.property_name_crop_size,
                        R.drawable.ic_crop,
                        Choice(
                            Size(180, 320),
                            R.string.property_label_s,
                            0
                        ),
                        Choice(
                            Size(270, 480),
                            R.string.property_label_m,
                            0
                        ),
                        Choice(
                            Size(540, 960),
                            R.string.property_label_l,
                            0
                        ),
                        Choice(
                            Size(1080, 1920),
                            R.string.property_label_xl,
                            0
                        )
                    ),
                    CropGrayBlur.CropEnabled to toggleUi(
                        R.string.property_name_crop_enabled,
                        R.drawable.ic_crop,
                        Choice(
                            false,
                            R.string.property_label_off,
                            0
                        ),
                        Choice(
                            true,
                            R.string.property_label_on,
                            0
                        )
                    ),
                    CropGrayBlur.GrayEnabled to toggleUi(
                        R.string.property_name_gray_enabled,
                        R.drawable.ic_filter_b_and_w,
                        Choice(
                            false,
                            R.string.property_label_off,
                            0
                        ),
                        Choice(
                            true,
                            R.string.property_label_on,
                            0
                        )
                    ),
                    CropGrayBlur.BlurSize to toggleUi(
                        R.string.property_name_blur_size,
                        R.drawable.ic_blur,
                        Choice(
                            0,
                            R.string.property_label_0,
                            0
                        ),
                        Choice(
                            5,
                            R.string.property_label_5,
                            0
                        ),
                        Choice(
                            9,
                            R.string.property_label_9,
                            0
                        ),
                        Choice(
                            13,
                            R.string.property_label_13,
                            0
                        )
                    ),
                    CropGrayBlur.NumPasses to toggleUi(
                        R.string.property_name_num_passes,
                        R.drawable.ic_layers,
                        Choice(
                            1,
                            R.string.property_label_1,
                            0
                        ),
                        Choice(
                            2,
                            R.string.property_label_2,
                            0
                        ),
                        Choice(
                            4,
                            R.string.property_label_4,
                            0
                        ),
                        Choice(
                            8,
                            R.string.property_label_8,
                            0
                        )
                    )
                )
//            1..10
//            listOf(5, 9, 13)
//            listOf(true, false)
            ),

            Image.key to NodeUi(
                R.string.name_node_type_image,
                R.drawable.ic_image
            ),

            Lut2d.key to NodeUi(
                R.string.name_node_type_lut2d_filter,
                R.drawable.ic_tune
            ),

            Lut3d.key to NodeUi(
                R.string.name_node_type_lut3d_filter,
                R.drawable.ic_tune
//            0f..1f
            ),

            Speakers.key to NodeUi(
                R.string.name_node_type_speaker,
                R.drawable.ic_speaker
            ),

            Screen.key to NodeUi(
                R.string.name_node_type_screen,
                R.drawable.ic_display
            ),

            SlimeMold.key to NodeUi(
                R.string.name_node_type_slime_mold,
                R.drawable.ic_slime_mold
            ),

            ImageBlend.key to NodeUi(
                R.string.name_node_type_image_blend,
                R.drawable.ic_filter_b_and_w
//            1..25
//            0f..1f
            ),

            CubeImport.key to NodeUi(
                R.string.name_node_type_cube_importer,
                R.drawable.ic_3d_rotation
            ),

            BCubeImport.key to NodeUi(
                R.string.name_node_type_bcube_importer,
                R.drawable.ic_3d_rotation
            ),

            CropResize.key to NodeUi(
                R.string.name_node_type_crop_resize,
                R.drawable.ic_crop
            ),

            Shape.key to NodeUi(
                R.string.name_node_type_shape,
                R.drawable.ic_change_history
            ),

            RingBuffer.key to NodeUi(
                R.string.name_node_type_ring_buffer,
                R.drawable.ic_layers,
                mapOf(
                    RingBuffer.Depth to toggleUi(
                        R.string.property_name_history_size,
                        R.drawable.ic_layers,
                        Choice(
                            20,
                            R.string.property_label_20,
                            0
                        ),
                        Choice(
                            30,
                            R.string.property_label_30,
                            0
                        ),
                        Choice(
                            45,
                            R.string.property_label_45,
                            0
                        ),
                        Choice(
                            60,
                            R.string.property_label_60,
                            0
                        )
                    )
                )
            ),

            Slice3d.key to NodeUi(
                R.string.name_node_type_slice_3d,
                R.drawable.ic_layers,
                mapOf(
                    Slice3d.SliceDirection to expandedUi(
                        R.string.property_name_slice_direction,
                        R.drawable.ic_arrow_forward,
                        Choice(
                            0,
                            R.string.property_label_top,
                            R.drawable.ic_arrow_upward
                        ),
                        Choice(
                            1,
                            R.string.property_label_bottom,
                            R.drawable.ic_arrow_downward
                        ),
                        Choice(
                            2,
                            R.string.property_label_left,
                            R.drawable.ic_arrow_back
                        ),
                        Choice(
                            3,
                            R.string.property_label_right,
                            R.drawable.ic_arrow_forward
                        )
                    )
                )
            ),

            MediaEncoder.key to NodeUi(
                R.string.name_node_type_media_encoder,
                R.drawable.ic_movie
//            listOf(0, 90, 180, 270)
//            listOf(10, 15, 20, 30, 60)
//            listOf(true, false)
            ),

            RotateMatrix.key to NodeUi(
                R.string.name_node_type_matrix_rotate,
                R.drawable.ic_360,
                mapOf(
                    RotateMatrix.Speed to rangeUi(
                        R.string.property_name_speed,
                        R.drawable.ic_360,
                        1f..100f
                    ),
                    RotateMatrix.FrameRate to menuUi(
                        R.string.property_name_frame_rate, R.drawable.ic_speed,
                        Choice(10, R.string.property_label_camera_fps_10, 0),
                        Choice(15, R.string.property_label_camera_fps_15, 0),
                        Choice(20, R.string.property_label_camera_fps_24, 0),
                        Choice(30, R.string.property_label_camera_fps_30, 0),
                        Choice(60, R.string.property_label_camera_fps_60, 0)
                    )
                )
            ),

            TextureView.key to NodeUi(
                R.string.name_node_type_texture_view,
                R.drawable.ic_display
            ),

            CellAuto.key to NodeUi(
                R.string.name_node_type_cell_auto,
                R.drawable.ic_view_module,
                mapOf(
                    CellAuto.GridSize to toggleUi(
                        R.string.property_name_grid_size,
                        R.drawable.ic_add,
                        Choice(
                            Size(180, 320),
                            R.string.property_label_s,
                            0
                        ),
                        Choice(
                            Size(270, 480),
                            R.string.property_label_m,
                            0
                        ),
                        Choice(
                            Size(540, 960),
                            R.string.property_label_l,
                            0
                        ),
                        Choice(
                            Size(1080, 1920),
                            R.string.property_label_xl,
                            0
                        )
                    )
                )
//            listOf(10, 15, 20, 30, 60)
            ),

            Quantizer.key to NodeUi(
                R.string.name_node_type_quantizer,
                R.drawable.ic_view_module,
                mapOf(
                    Quantizer.NumElements to toggleUi(
                        R.string.property_name_quant_depth,
                        R.drawable.ic_layers,
                        Choice(
                            floatArrayOf(4f, 4f, 4f),
                            R.string.property_label_4,
                            0
                        ),
                        Choice(
                            floatArrayOf(6f, 6f, 6f),
                            R.string.property_label_6,
                            0
                        ),
                        Choice(
                            floatArrayOf(8f, 8f, 8f),
                            R.string.property_label_8,
                            0
                        ),
                        Choice(
                            floatArrayOf(10f, 10f, 10f),
                            R.string.property_label_10,
                            0
                        )
                    )
                )
            ),

            Sobel.key to NodeUi(
                R.string.name_node_type_sobel,
                R.drawable.ic_difference
            )
        )
    }
}
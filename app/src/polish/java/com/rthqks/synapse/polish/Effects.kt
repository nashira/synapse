package com.rthqks.synapse.polish

import android.util.Size
import com.rthqks.synapse.R
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3
    const val ID_SQUARES = 4

    val none = Network(ID_NONE).let {
        val camera = it.addNode(NewNode(NodeType.Camera))

        Effect(it, "none", Pair(camera.id, CameraNode.OUTPUT.id))
    }

    val timeWarp = Network(ID_TIME_WARP).let {
        val camera = it.addNode(NewNode(NodeType.Camera))
        val ringBuffer = it.addNode(NewNode(NodeType.RingBuffer))
        val slice = it.addNode(NewNode(NodeType.Slice3d))

        it.addLink(
            Link(camera.id, CameraNode.OUTPUT.id, ringBuffer.id, RingBufferNode.INPUT.id)
        )
        it.addLink(
            Link(ringBuffer.id, RingBufferNode.OUTPUT.id, slice.id, Slice3dNode.INPUT_3D.id)
        )
        ringBuffer.properties[HistorySize] = 30
        Effect(it, "Time Warp", Pair(slice.id, Slice3dNode.OUTPUT.id)).apply {
            ringBuffer.properties.getProperty(HistorySize)?.let {
                addProperty(
                    it, ToggleType(
                        R.string.property_name_history_size,
                        R.drawable.ic_layers,
                        Choice(20, R.string.property_label_20, R.drawable.square),
                        Choice(30, R.string.property_label_30, R.drawable.square),
                        Choice(45, R.string.property_label_45, R.drawable.square),
                        Choice(60, R.string.property_label_60, R.drawable.square)
                    )
                )
            }

            val sd = slice.properties.getProperty(SliceDirection)!!
            addProperty(
                sd, ExpandedType(
                    R.string.property_name_slice_direction,
                    R.drawable.ic_arrow_forward,
                    Choice(0, R.string.property_label_top, R.drawable.ic_arrow_upward),
                    Choice(1, R.string.property_label_bottom, R.drawable.ic_arrow_downward),
                    Choice(2, R.string.property_label_left, R.drawable.ic_arrow_back),
                    Choice(3, R.string.property_label_right, R.drawable.ic_arrow_forward)
                )
            )
        }
    }

    val rotoHue = Network(ID_ROTO_HUE).let {
        val camera = it.addNode(NewNode(NodeType.Camera))
        val rotate = it.addNode(NewNode(NodeType.RotateMatrix))
        it.addLink(
            Link(rotate.id, RotateMatrixNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.LUT_MATRIX.id)
        )
        Effect(it, "Roto-Hue", Pair(camera.id, CameraNode.OUTPUT.id)).apply {
            val rotateSpeed = rotate.properties.getProperty(RotationSpeed)!!
            addProperty(
                rotateSpeed, ToggleType(
                    R.string.property_name_rotation,
                    R.drawable.ic_speed,
                    Choice(5f, R.string.property_label_5, R.drawable.circle),
                    Choice(10f, R.string.property_label_10, R.drawable.circle),
                    Choice(30f, R.string.property_label_30, R.drawable.circle),
                    Choice(60f, R.string.property_label_60, R.drawable.circle)
                )
            )
        }
    }

    val squares = Network(ID_SQUARES).let {
        val cell = it.addNode(NewNode(NodeType.CellAuto))

        Effect(it, "Squares", Pair(cell.id, CellularAutoNode.OUTPUT.id)).apply {
            val prop = cell.properties.getProperty(CellularAutoNode.GridSize)!!
            addProperty(
                prop, ToggleType(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(Size(180, 320), R.string.property_label_s, R.drawable.circle),
                    Choice(Size(270, 480), R.string.property_label_m, R.drawable.circle),
                    Choice(Size(540, 960), R.string.property_label_l, R.drawable.circle),
                    Choice(Size(1080, 1920), R.string.property_label_xl, R.drawable.circle)
                )
            )
        }
    }

    val quantizer = Network(5).let {
        val camera = it.addNode(NewNode(NodeType.Camera))
        val blur = it.addNode(NewNode(NodeType.BlurFilter))
        val sobel = it.addNode(NewNode(NodeType.Sobel))
        val quantizer = it.addNode(NewNode(NodeType.Quantizer))
        val blend = it.addNode(NewNode(NodeType.ImageBlend))
        it.addLink(
            Link(camera.id, CameraNode.OUTPUT.id, blur.id, BlurNode.INPUT.id)
        )
        it.addLink(
            Link(blur.id, BlurNode.OUTPUT.id, quantizer.id, QuantizerNode.INPUT.id)
        )
//        it.addLinkNoCompute(
//            Link(
//                sobel.id,
//                SobelNode.OUTPUT.id,
//                Effect.ID_LUT,
//                Lut3dNode.INPUT.id
//            )
//        )
//        it.addLinkNoCompute(
//            Link(
//                sobel.id,
//                SobelNode.OUTPUT.id,
//                quantizer.id,
//                QuantizerNode.INPUT.id
//            )
//        )
        it.addLink(
            Link(quantizer.id, QuantizerNode.OUTPUT.id, blend.id, ImageBlendNode.INPUT_BLEND.id)
        )

        it.addLink(
            Link(camera.id, CameraNode.OUTPUT.id, blend.id, ImageBlendNode.INPUT_BASE.id)
        )
        blur.properties[BlurSize] = 0
        blend.properties[BlendMode] = 23
        Effect(it, "Squares", Pair(blend.id, ImageBlendNode.OUTPUT.id)).apply {
            val prop = blur.properties.getProperty(CropSize)!!
            prop.value = Size(1080, 1920)
            addProperty(
                prop, ToggleType(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(Size(180, 320), R.string.property_label_s, R.drawable.square),
                    Choice(Size(270, 480), R.string.property_label_m, R.drawable.square),
                    Choice(Size(540, 960), R.string.property_label_l, R.drawable.square),
                    Choice(Size(1080, 1920), R.string.property_label_xl, R.drawable.square)
                )
            )
            val propCrop = blur.properties.getProperty(CropEnabled)!!
            propCrop.value = false
            addProperty(
                propCrop, ToggleType(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(false, R.string.property_label_off, R.drawable.square),
                    Choice(true, R.string.property_label_on, R.drawable.square)
                )
            )

            addProperty(
                blur.properties.getProperty(GrayEnabled)!!, ToggleType(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(false, R.string.property_label_off, R.drawable.circle),
                    Choice(true, R.string.property_label_on, R.drawable.circle)
                )
            )
            addProperty(
                blur.properties.getProperty(BlurSize)!!, ToggleType(
                    R.string.property_name_num_passes,
                    R.drawable.ic_layers,
                    Choice(0, R.string.property_label_0, R.drawable.square),
                    Choice(5, R.string.property_label_5, R.drawable.square),
                    Choice(9, R.string.property_label_9, R.drawable.square),
                    Choice(13, R.string.property_label_13, R.drawable.square)
                )
            )

            val np = blur.properties.getProperty(NumPasses)!!
            np.value = 1
            addProperty(
                np, ToggleType(
                    R.string.property_name_num_passes,
                    R.drawable.ic_layers,
                    Choice(1, R.string.property_label_1, R.drawable.circle),
                    Choice(2, R.string.property_label_2, R.drawable.circle),
                    Choice(4, R.string.property_label_4, R.drawable.circle),
                    Choice(8, R.string.property_label_8, R.drawable.circle)
                )
            )
            val dp = quantizer.properties.getProperty(NumElements)!!
            addProperty(
                dp, ToggleType(
                    R.string.property_name_num_passes,
                    R.drawable.ic_layers,
                    Choice(floatArrayOf(4f, 4f, 4f), R.string.property_label_4, R.drawable.circle),
                    Choice(floatArrayOf(6f, 6f, 6f), R.string.property_label_6, R.drawable.circle),
                    Choice(floatArrayOf(8f, 8f, 8f), R.string.property_label_8, R.drawable.circle),
                    Choice(floatArrayOf(10f, 10f, 10f), R.string.property_label_10, R.drawable.circle)
                )
            )
        }
    }
}
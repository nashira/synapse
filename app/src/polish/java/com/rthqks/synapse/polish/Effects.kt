package com.rthqks.synapse.polish

import android.util.Size
import com.rthqks.synapse.R
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.logic.NodeDef.*
import com.rthqks.synapse.logic.NodeDef.CellAuto.GridSize
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.BlurSize
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.CropEnabled
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.CropSize
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.GrayEnabled
import com.rthqks.synapse.logic.NodeDef.CropGrayBlur.NumPasses
import com.rthqks.synapse.logic.NodeDef.ImageBlend.BlendMode
import com.rthqks.synapse.logic.NodeDef.Quantizer.NumElements
import com.rthqks.synapse.logic.NodeDef.RingBuffer.Depth
import com.rthqks.synapse.logic.NodeDef.RotateMatrix.Speed
import com.rthqks.synapse.logic.NodeDef.Slice3d.SliceDirection
import com.rthqks.synapse.polish.EffectExecutor.Companion.ID_LUT


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3
    const val ID_SQUARES = 4

    val none = Network(ID_NONE).let {
        val camera = it.addNode(Camera.toNode())

        it.setExposed(camera.id, Camera.OUTPUT.key, true)

        Effect(it, "none")
    }

    val timeWarp = Network(ID_TIME_WARP).let {
        val camera = it.addNode(Camera.toNode())
        val ringBuffer = it.addNode(RingBuffer.toNode())
        val slice = it.addNode(Slice3d.toNode())

        it.setExposed(slice.id, Slice3d.OUTPUT.key, true)

        it.addLink(
            Link(camera.id, Camera.OUTPUT.key, ringBuffer.id, RingBuffer.INPUT.key)
        )
        it.addLink(
            Link(ringBuffer.id, RingBuffer.OUTPUT.key, slice.id, Slice3d.INPUT.key)
        )
        ringBuffer.properties[Depth] = 30
        ringBuffer.properties.getProperty(Depth)?.exposed = true

        Effect(it, "Time Warp").apply {
            ringBuffer.properties.getProperty(Depth)?.let {
                addProperty(
                    it, ToggleHolder(
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
                sd, ExpandedHolder(
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
        val camera = it.addNode(Camera.toNode())
        val rotate = it.addNode(RotateMatrix.toNode())
        it.addLink(
            Link(rotate.id, RotateMatrixNode.OUTPUT.id, ID_LUT, Lut3dNode.MATRIX_IN.id)
        )
        it.setExposed(camera.id, Camera.OUTPUT.key, true)

        Effect(it, "Roto-Hue").apply {
            val rotateSpeed = rotate.properties.getProperty(Speed)!!
            addProperty(
                rotateSpeed, ToggleHolder(
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
        val cell = it.addNode(CellAuto.toNode())
        it.setExposed(cell.id, CellAuto.OUTPUT.key, true)

        Effect(it, "Squares").apply {
            val prop = cell.properties.getProperty(GridSize)!!
            addProperty(
                prop, ToggleHolder(
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
        val camera = it.addNode(Camera.toNode())
        val blur = it.addNode(CropGrayBlur.toNode())
        val sobel = it.addNode(Sobel.toNode())
        val quantizer = it.addNode(Quantizer.toNode())
        val blend = it.addNode(ImageBlend.toNode())
        it.addLink(
            Link(camera.id, CameraNode.OUTPUT.id, blur.id, CropGrayBlurNode.INPUT.id)
        )
        it.addLink(
            Link(blur.id, CropGrayBlurNode.OUTPUT.id, quantizer.id, QuantizerNode.INPUT.id)
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

        it.setExposed(blend.id, ImageBlend.OUTPUT.key, true)

        Effect(it, "Squares").apply {
            val prop = blur.properties.getProperty(CropSize)!!
            prop.value = Size(1080, 1920)
            addProperty(
                prop, ToggleHolder(
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
                propCrop, ToggleHolder(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(false, R.string.property_label_off, R.drawable.square),
                    Choice(true, R.string.property_label_on, R.drawable.square)
                )
            )

            addProperty(
                blur.properties.getProperty(GrayEnabled)!!, ToggleHolder(
                    R.string.property_name_grid_size,
                    R.drawable.ic_add,
                    Choice(false, R.string.property_label_off, R.drawable.circle),
                    Choice(true, R.string.property_label_on, R.drawable.circle)
                )
            )
            addProperty(
                blur.properties.getProperty(BlurSize)!!, ToggleHolder(
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
                np, ToggleHolder(
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
                dp, ToggleHolder(
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
package com.rthqks.synapse.polish

import com.rthqks.synapse.R
import com.rthqks.synapse.exec2.node.*
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.logic.PropertyType.Companion.RangeType


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3
    const val ID_SQUARES = 4

    val none = Network(ID_NONE).let {
        it.addLink(Link(Effect.ID_CAMERA, CameraNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.INPUT.id))
        Effect(it, "none")
    }

    val timeWarp = Network(ID_TIME_WARP).let {
        val ringBuffer = it.addNode(NewNode(NodeType.RingBuffer))
        val slice = it.addNode(NewNode(NodeType.Slice3d))

        it.addLinkNoCompute(
            Link(
                Effect.ID_CAMERA,
                CameraNode.OUTPUT.id,
                ringBuffer.id,
                RingBufferNode.INPUT.id
            )
        )
        it.addLinkNoCompute(
            Link(
                ringBuffer.id,
                RingBufferNode.OUTPUT.id,
                slice.id,
                Slice3dNode.INPUT_3D.id
            )
        )
        it.addLinkNoCompute(
            Link(
                slice.id,
                Slice3dNode.OUTPUT.id,
                Effect.ID_LUT,
                Lut3dNode.INPUT.id
            )
        )
        ringBuffer.properties[HistorySize] = 30
        Effect(it, "Time Warp").apply {
            ringBuffer.properties.getProperty(HistorySize)?.let {
//                addProperty(
//                    it, RangeType(
//                        R.string.property_name_history_size,
//                        R.drawable.ic_layers,
//                        1..60
//                    )
//                )
            }

            val sd = slice.properties.getProperty(SliceDirection)!!
            addProperty(sd, ChoiceType(
                R.string.property_name_slice_direction,
                R.drawable.ic_arrow_forward,
                Choice(0, R.string.property_label_top, R.drawable.ic_arrow_upward),
                Choice(1, R.string.property_label_bottom, R.drawable.ic_arrow_downward),
                Choice(2, R.string.property_label_left, R.drawable.ic_arrow_back),
                Choice(3, R.string.property_label_right, R.drawable.ic_arrow_forward)
            ))
        }
    }

    val rotoHue = Network(ID_ROTO_HUE).let {
        val rotate = it.addNode(NewNode(NodeType.RotateMatrix))
        it.addLinkNoCompute(
            Link(
                rotate.id,
                RotateMatrixNode.OUTPUT.id,
                Effect.ID_LUT,
                Lut3dNode.LUT_MATRIX.id
            )
        )
        it.addLinkNoCompute(
            Link(
                Effect.ID_CAMERA,
                CameraNode.OUTPUT.id,
                Effect.ID_LUT,
                Lut3dNode.INPUT.id
            )
        )
        Effect(it, "Roto-Hue")
    }

    val squares = Network(ID_SQUARES).let {
        val shape = it.addNode(NewNode(NodeType.Shape))
        it.addLinkNoCompute(
            Link(
                shape.id,
                RotateMatrixNode.OUTPUT.id,
                Effect.ID_LUT,
                Lut3dNode.LUT_MATRIX.id
            )
        )
        it.addLinkNoCompute(Link(shape.id, CameraNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.INPUT.id))
        Effect(it, "Squares")
    }
}
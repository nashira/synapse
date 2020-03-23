package com.rthqks.synapse.polish

import com.rthqks.synapse.exec2.node.*
import com.rthqks.synapse.logic.*


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3

    val none = Network(ID_NONE).let {
        it.addLink(Link(Effect.ID_CAMERA, CameraNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.INPUT.id))
        Effect(it)
    }.apply {
        properties[Effect.Title] = "None"
    }

    val timeWarp = Network(ID_TIME_WARP).let {
        val ringBuffer = it.addNode(NewNode(NodeType.RingBuffer))
        val slice = it.addNode(NewNode(NodeType.Slice3d))

        it.addLinkNoCompute(Link(Effect.ID_CAMERA, CameraNode.OUTPUT.id, ringBuffer.id, RingBufferNode.INPUT.id))
        it.addLinkNoCompute(Link(ringBuffer.id, RingBufferNode.OUTPUT.id, slice.id, Slice3dNode.INPUT_3D.id))
        it.addLinkNoCompute(Link(slice.id, Slice3dNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.INPUT.id))
        ringBuffer.properties[HistorySize] = 30
        Effect(it).apply {
            properties[Effect.Title] = "Time Warp"
        }
    }

    val rotoHue = Network(ID_ROTO_HUE).let {
        val rotate = it.addNode(NewNode(NodeType.RotateMatrix))
        it.addLinkNoCompute(Link(rotate.id, RotateMatrixNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.LUT_MATRIX.id))
        it.addLinkNoCompute(Link(Effect.ID_CAMERA, CameraNode.OUTPUT.id, Effect.ID_LUT, Lut3dNode.INPUT.id))
        Effect(it)
    }.apply {
        properties[Effect.Title] = "Roto-Hue"
    }
}
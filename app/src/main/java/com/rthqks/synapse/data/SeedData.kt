package com.rthqks.synapse.data

import android.util.Size
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef
import com.rthqks.synapse.polish.EffectExecutor

val SeedNetworks = listOf(
    Network(1, "none").also {
        val camera = it.addNode(NodeDef.Camera.toNode())

        it.setExposed(camera.id, NodeDef.Camera.OUTPUT.key, true)
        it.setExposed(camera.id, NodeDef.Camera.CameraFacing, true)
    }, Network(2, "Time Warp").also {
        val camera = it.addNode(NodeDef.Camera.toNode())
        val ringBuffer = it.addNode(NodeDef.RingBuffer.toNode())
        val slice = it.addNode(NodeDef.Slice3d.toNode())

        ringBuffer.properties[NodeDef.RingBuffer.Depth] = 30
        it.setExposed(slice.id, NodeDef.Slice3d.OUTPUT.key, true)
        it.setExposed(slice.id, NodeDef.Slice3d.SliceDirection, true)
        it.setExposed(ringBuffer.id, NodeDef.RingBuffer.Depth, true)

        it.addLink(
            Link(camera.id, NodeDef.Camera.OUTPUT.key, ringBuffer.id, NodeDef.RingBuffer.INPUT.key)
        )
        it.addLink(
            Link(ringBuffer.id, NodeDef.RingBuffer.OUTPUT.key, slice.id, NodeDef.Slice3d.INPUT.key)
        )
    }, Network(3, "Roto-Hue").also {
        val camera = it.addNode(NodeDef.Camera.toNode())
        val rotate = it.addNode(NodeDef.RotateMatrix.toNode())
        it.addLink(
            Link(
                rotate.id,
                RotateMatrixNode.OUTPUT.id,
                EffectExecutor.ID_LUT,
                Lut3dNode.MATRIX_IN.id
            )
        )
        it.setExposed(camera.id, NodeDef.Camera.OUTPUT.key, true)
        it.setExposed(rotate.id, NodeDef.RotateMatrix.Speed, true)
    }, Network(4, "Squares").also {
        val cell = it.addNode(NodeDef.CellAuto.toNode())
        it.setExposed(cell.id, NodeDef.CellAuto.OUTPUT.key, true)
    }, Network(5, "Quantizer").also {
        val camera = it.addNode(NodeDef.Camera.toNode())
        val blur = it.addNode(NodeDef.CropGrayBlur.toNode())
        val sobel = it.addNode(NodeDef.Sobel.toNode())
        val quantizer = it.addNode(NodeDef.Quantizer.toNode())
        val blend = it.addNode(NodeDef.ImageBlend.toNode())
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
        blend.properties[NodeDef.ImageBlend.BlendMode] = 23
        blur.properties[NodeDef.CropGrayBlur.BlurSize] = 0
        blur.properties[NodeDef.CropGrayBlur.CropSize] = Size(1080, 1920)
        blur.properties[NodeDef.CropGrayBlur.CropEnabled] = false
        blur.properties[NodeDef.CropGrayBlur.NumPasses] = 1

        it.setExposed(blend.id, NodeDef.ImageBlend.OUTPUT.key, true)
        it.setExposed(blend.id, NodeDef.ImageBlend.BlendMode, true)
        it.setExposed(blur.id, NodeDef.CropGrayBlur.BlurSize, true)
        it.setExposed(blur.id, NodeDef.CropGrayBlur.CropSize, true)
        it.setExposed(blur.id, NodeDef.CropGrayBlur.CropEnabled, true)
        it.setExposed(blur.id, NodeDef.CropGrayBlur.NumPasses, true)
        it.setExposed(quantizer.id, NodeDef.Quantizer.NumElements, true)
    }
)
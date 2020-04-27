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
import com.rthqks.synapse.ui.Choice
import com.rthqks.synapse.ui.ExpandedHolder
import com.rthqks.synapse.ui.ToggleHolder


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3
    const val ID_SQUARES = 4

    val none = Network(ID_NONE).let {
        val camera = it.addNode(Camera.toNode())

        it.setExposed(camera.id, Camera.OUTPUT.key, true)
        it.setExposed(camera.id, Camera.CameraFacing, true)

        Effect(it, "none")
    }

    val timeWarp = Network(ID_TIME_WARP).let {
        val camera = it.addNode(Camera.toNode())
        val ringBuffer = it.addNode(RingBuffer.toNode())
        val slice = it.addNode(Slice3d.toNode())

        ringBuffer.properties[Depth] = 30
        it.setExposed(slice.id, Slice3d.OUTPUT.key, true)
        it.setExposed(slice.id, SliceDirection, true)
        it.setExposed(ringBuffer.id, Depth, true)

        it.addLink(
            Link(camera.id, Camera.OUTPUT.key, ringBuffer.id, RingBuffer.INPUT.key)
        )
        it.addLink(
            Link(ringBuffer.id, RingBuffer.OUTPUT.key, slice.id, Slice3d.INPUT.key)
        )

        Effect(it, "Time Warp")
    }

    val rotoHue = Network(ID_ROTO_HUE).let {
        val camera = it.addNode(Camera.toNode())
        val rotate = it.addNode(RotateMatrix.toNode())
        it.addLink(
            Link(rotate.id, RotateMatrixNode.OUTPUT.id, ID_LUT, Lut3dNode.MATRIX_IN.id)
        )
        it.setExposed(camera.id, Camera.OUTPUT.key, true)
        it.setExposed(rotate.id, Speed, true)

        Effect(it, "Roto-Hue")
    }

    val squares = Network(ID_SQUARES).let {
        val cell = it.addNode(CellAuto.toNode())
        it.setExposed(cell.id, CellAuto.OUTPUT.key, true)

        Effect(it, "Squares")
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
        blend.properties[BlendMode] = 23
        blur.properties[BlurSize] = 0
        blur.properties[CropSize] = Size(1080, 1920)
        blur.properties[CropEnabled] = false
        blur.properties[NumPasses] = 1

        it.setExposed(blend.id, ImageBlend.OUTPUT.key, true)
        it.setExposed(blend.id, BlendMode, true)
        it.setExposed(blur.id, BlurSize, true)
        it.setExposed(blur.id, CropSize, true)
        it.setExposed(blur.id, CropEnabled, true)
        it.setExposed(blur.id, NumPasses, true)
        it.setExposed(quantizer.id, NumElements, true)

        Effect(it, "Squares")
    }
}
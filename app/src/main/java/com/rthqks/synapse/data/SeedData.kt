package com.rthqks.synapse.data

import android.hardware.camera2.CameraCharacteristics
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size
import com.rthqks.synapse.build.BuildExecutor
import com.rthqks.synapse.exec.node.Lut3dNode
import com.rthqks.synapse.exec.node.RotateMatrixNode
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.*
import com.rthqks.synapse.polish.EffectExecutor

object SeedData {
    const val BaseEffectId = 100
    const val BuildNetworkId = 101

    val BaseEffect = Network(BaseEffectId, "base").also {
        val camera = it.addNode(Camera, EffectExecutor.ID_CAMERA)
        val microphone = it.addNode(Microphone, EffectExecutor.ID_MIC)
        val screen = it.addNode(Screen, EffectExecutor.ID_SURFACE_VIEW)
        val encoder = it.addNode(MediaEncoder, EffectExecutor.ID_ENCODER)
        val cube = it.addNode(BCubeImport, EffectExecutor.ID_LUT_IMPORT)
        val lut = it.addNode(Lut3d, EffectExecutor.ID_LUT)
        val crop = it.addNode(CropResize, EffectExecutor.ID_CROP)

        it.setProperty(camera.id, Camera.CameraFacing, CameraCharacteristics.LENS_FACING_BACK)
        it.setProperty(camera.id, Camera.FrameRate, 30)
        it.setProperty(camera.id, Camera.VideoSize, Size(1280, 720))
        it.setProperty(camera.id, Camera.Stabilize, true)
        it.setProperty(encoder.id, MediaEncoder.Recording, false)
        it.setProperty(encoder.id, MediaEncoder.Rotation, 0)
        it.setProperty(cube.id, BCubeImport.LutUri, Uri.parse("assets:///cube/identity.bcube"))
        it.setProperty(lut.id, Lut3d.LutStrength, 1f)
        it.setProperty(microphone.id, Microphone.AudioSource, MediaRecorder.AudioSource.CAMCORDER)
        it.setProperty(crop.id, CropResize.CropSize, Size(320, 320))

        it.addLink(Link(lut.id, Lut3d.OUTPUT.key, screen.id, Screen.INPUT.key))
        it.addLink(Link(lut.id, Lut3d.OUTPUT.key, encoder.id, MediaEncoder.VIDEO_IN.key))
        it.addLink(Link(cube.id, BCubeImport.OUTPUT.key, lut.id, Lut3d.LUT_IN.key))
        it.addLink(
            Link(
                microphone.id,
                Microphone.OUTPUT.key,
                encoder.id,
                MediaEncoder.AUDIO_IN.key
            )
        )
    }

    val BuildNetwork = Network(BuildNetworkId, "builder").also {
        val camera = it.addNode(Camera, BuildExecutor.ID_CAMERA)
        val screen = it.addNode(Screen, BuildExecutor.ID_SURFACE_VIEW)
    }

    val SeedNetworks = listOf(
        Network(1, "none"),
        Network(2, "Time Warp").also {
            val ringBuffer = it.addNode(RingBuffer)
            val slice = it.addNode(Slice3d)

            it.setProperty(ringBuffer.id, RingBuffer.Depth, 30)

            it.setExposed(slice.id, Slice3d.OUTPUT.key, true)
            it.setExposed(ringBuffer.id, RingBuffer.INPUT.key, true)
            it.setExposed(slice.id, Slice3d.SliceDirection, true)
            it.setExposed(ringBuffer.id, RingBuffer.Depth, true)

            it.addLink(
                Link(ringBuffer.id, RingBuffer.OUTPUT.key, slice.id, Slice3d.INPUT.key)
            )
        }, Network(3, "Roto-Hue").also {
            val rotate = it.addNode(RotateMatrix)
            it.addLink(
                Link(
                    rotate.id,
                    RotateMatrixNode.OUTPUT.id,
                    EffectExecutor.ID_LUT,
                    Lut3dNode.MATRIX_IN.id
                )
            )
            it.setExposed(rotate.id, RotateMatrix.Speed, true)
        }, Network(4, "Squares").also {
            val cell = it.addNode(CellAuto)
            it.setExposed(cell.id, CellAuto.GridSize, true)
            it.setExposed(cell.id, CellAuto.OUTPUT.key, true)
        }, Network(5, "Quantizer").also {
            val blur = it.addNode(CropGrayBlur)
//        val sobel = it.addNode(Sobel)
            val quantizer = it.addNode(Quantizer)
            val blend = it.addNode(ImageBlend)
            it.addLink(
                Link(blur.id, CropGrayBlur.OUTPUT.key, quantizer.id, Quantizer.INPUT.key)
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
                Link(quantizer.id, Quantizer.OUTPUT.key, blend.id, ImageBlend.BLEND_IN.key)
            )

            it.setProperty(blend.id, ImageBlend.BlendMode, 23)
            it.setProperty(blur.id, CropGrayBlur.BlurSize, 0)
            it.setProperty(blur.id, CropGrayBlur.CropSize, Size(1080, 1920))
            it.setProperty(blur.id, CropGrayBlur.CropEnabled, false)
            it.setProperty(blur.id, CropGrayBlur.NumPasses, 1)

            it.setExposed(blend.id, ImageBlend.BASE_IN.key, true)
            it.setExposed(blur.id, CropGrayBlur.INPUT.key, true)

            it.setExposed(blend.id, ImageBlend.OUTPUT.key, true)
            it.setExposed(blend.id, ImageBlend.BlendMode, true)
            it.setExposed(blur.id, CropGrayBlur.BlurSize, true)
            it.setExposed(blur.id, CropGrayBlur.CropSize, true)
            it.setExposed(blur.id, CropGrayBlur.CropEnabled, true)
            it.setExposed(blur.id, CropGrayBlur.NumPasses, true)
            it.setExposed(quantizer.id, Quantizer.NumElements, true)
        }
    )
}
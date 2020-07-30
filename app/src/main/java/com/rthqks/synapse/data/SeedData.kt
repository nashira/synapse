package com.rthqks.synapse.data

import android.hardware.camera2.CameraCharacteristics
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size
import com.rthqks.synapse.build.BuildExecutor
import com.rthqks.flow.exec.node.Lut3dNode
import com.rthqks.flow.exec.node.RotateMatrixNode
import com.rthqks.flow.logic.Link
import com.rthqks.flow.logic.Network
import com.rthqks.flow.logic.NodeDef.*
import com.rthqks.synapse.polish.EffectExecutor

object SeedData {
    const val BaseEffectId = "6f8dfca8-3c09-4d51-b7d8-e2594df487d2"
    const val SystemUser = "deb08317-61ad-4d11-a1f8-c17e731e520d"
    private const val BuildNetworkId = "8b82e801-58d9-4eb8-b1b7-6c6c692cc727"

    val BaseEffect = Network(BaseEffectId, SystemUser, "base").also {
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

    val BuildNetwork = Network(BuildNetworkId, SystemUser, "builder").also {
        val camera = it.addNode(Camera, BuildExecutor.ID_CAMERA)
        val screen = it.addNode(Screen, BuildExecutor.ID_SURFACE_VIEW)
    }

    val SeedNetworks = listOf(
        Network("4b6466f3-981c-44c2-9027-a3514055ebae", SystemUser, "none"),
        Network("aa85bcfb-b864-404b-b020-0e996a53cffa", SystemUser, "Time Warp").also {
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
        }, Network("cb5cf58a-9556-4a38-b9b8-92957c06ee27", SystemUser, "Roto-Hue").also {
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
        }, Network("6368ca1f-bd52-4004-8cf3-f414cc0c99c3", SystemUser, "Squares").also {
            val cell = it.addNode(CellAuto)
            it.setExposed(cell.id, CellAuto.GridSize, true)
            it.setExposed(cell.id, CellAuto.OUTPUT.key, true)
        }, Network("dcd5d876-d011-433a-bf96-63d65596d2db", SystemUser, "Quantizer").also {
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
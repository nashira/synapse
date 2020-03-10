package com.rthqks.synapse.polish

import android.media.MediaRecorder
import android.net.Uri
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*


object Effects {
    const val ID_NONE = 1
    const val ID_TIME_WARP = 2
    const val ID_ROTO_HUE = 3

    val none = Network(ID_NONE).apply {
        val camera = addNode(NewNode(NodeType.Camera, Effect.ID_CAMERA))
        val microphone = addNode(NewNode(NodeType.Microphone, Effect.ID_MIC))
        val screen = addNode(NewNode(NodeType.Screen, Effect.ID_SURFACE_VIEW))
        val encoder = addNode(NewNode(NodeType.MediaEncoder, Effect.ID_ENCODER))

        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
    }.let {
        Effect(it)
    }.apply {
        properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        properties[Effect.Title] = "None"
    }

    val timeWarp = Network(ID_TIME_WARP).apply {
        val camera = addNode(NewNode(NodeType.Camera, Effect.ID_CAMERA))
        val microphone = addNode(NewNode(NodeType.Microphone, Effect.ID_MIC))
        val screen = addNode(NewNode(NodeType.Screen, Effect.ID_SURFACE_VIEW))
        val encoder = addNode(NewNode(NodeType.MediaEncoder, Effect.ID_ENCODER))

        val ringBuffer = addNode(NewNode(NodeType.RingBuffer))
        val slice = addNode(NewNode(NodeType.Slice3d))

        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, ringBuffer.id, RingBufferNode.INPUT.id))
        addLinkNoCompute(Link(ringBuffer.id, RingBufferNode.OUTPUT.id, slice.id, Slice3dNode.INPUT_3D.id))
        addLinkNoCompute(Link(slice.id, Slice3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(slice.id, Slice3dNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
        Effect(this).apply {
            properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
            properties[Effect.Title] = "Time Warp"
            properties[HistorySize] = 30
        }
        ringBuffer.properties.put(properties.find(HistorySize)!!, IntConverter)
    }

    val rotoHue = Network(ID_ROTO_HUE).apply {
        val camera = addNode(NewNode(NodeType.Camera, Effect.ID_CAMERA))
        val microphone = addNode(NewNode(NodeType.Microphone, Effect.ID_MIC))
        val screen = addNode(NewNode(NodeType.Screen, Effect.ID_SURFACE_VIEW))
        val encoder = addNode(NewNode(NodeType.MediaEncoder, Effect.ID_ENCODER))

        val lut = addNode(NewNode(NodeType.Lut3d))
        val cube = addNode(NewNode(NodeType.CubeImport))
        val rotate = addNode(NewNode(NodeType.RotateMatrix))

        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        cube.properties[MediaUri] = Uri.parse("assets:///cube/identity.cube")

        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, lut.id, Lut3dNode.INPUT.id))
        addLinkNoCompute(Link(rotate.id, RotateMatrixNode.OUTPUT.id, lut.id, Lut3dNode.LUT_MATRIX.id))
        addLinkNoCompute(Link(cube.id, CubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id))

        addLinkNoCompute(Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(lut.id, Lut3dNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
    }.let {
        Effect(it)
    }.apply {
        properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        properties[Effect.Title] = "Roto-Hue"
    }
}
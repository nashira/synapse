package com.rthqks.synapse.polish

import android.media.MediaRecorder
import android.net.Uri
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*


object EffectNetworks {
    val none = Network(1).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val microphone = addNode(NewNode(NodeType.Microphone))
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        val screen = addNode(NewNode(NodeType.Screen))
        val encoder = addNode(NewNode(NodeType.MediaEncoder))
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
    }
    val timeWarp = Network(2).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val microphone = addNode(NewNode(NodeType.Microphone))
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        val ringBuffer = addNode(NewNode(NodeType.RingBuffer))
        val encoder = addNode(NewNode(NodeType.MediaEncoder))
        val slice = addNode(NewNode(NodeType.Slice3d))
        val screen = addNode(NewNode(NodeType.Screen))
        ringBuffer.properties[HistorySize] = 30
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, ringBuffer.id, RingBufferNode.INPUT.id))
        addLinkNoCompute(Link(ringBuffer.id, RingBufferNode.OUTPUT.id, slice.id, Slice3dNode.INPUT_3D.id))
        addLinkNoCompute(Link(slice.id, Slice3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(slice.id, Slice3dNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
    }
    val rotoHue = Network(3).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val microphone = addNode(NewNode(NodeType.Microphone))
        val screen = addNode(NewNode(NodeType.Screen))
        val encoder = addNode(NewNode(NodeType.MediaEncoder))

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
    }
}
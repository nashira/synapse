package com.rthqks.synapse.polish

import android.media.MediaRecorder
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*


object EffectNetworks {
    val none = Network(1).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val microphone = addNode(NewNode(NodeType.Microphone))
        val screen = addNode(NewNode(NodeType.Screen))
        val encoder = addNode(NewNode(NodeType.MediaEncoder))
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        addLinkNoCompute(Link(camera.id, CameraNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
        computeComponents()
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
    }
    val timeWarp = Network(2).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val microphone = addNode(NewNode(NodeType.Microphone))
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
}
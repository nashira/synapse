package com.rthqks.synapse.polish

import com.rthqks.synapse.exec.node.CameraNode
import com.rthqks.synapse.exec.node.RingBufferNode
import com.rthqks.synapse.exec.node.Slice3dNode
import com.rthqks.synapse.exec.node.SurfaceViewNode
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NewNode
import com.rthqks.synapse.logic.NodeType


object EffectNetworks {
    val none = Network(1).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val screen = addNode(NewNode(NodeType.Screen))
        addLink(Link(camera.id, CameraNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
    }
    val timeWarp = Network(2).apply {
        val camera = addNode(NewNode(NodeType.Camera))
        val ringBuffer = addNode(NewNode(NodeType.RingBuffer))
        val slice = addNode(NewNode(NodeType.Slice3d))
        val screen = addNode(NewNode(NodeType.Screen))
        addLink(Link(camera.id, CameraNode.OUTPUT.id, ringBuffer.id, RingBufferNode.INPUT.id))
        addLink(Link(ringBuffer.id, RingBufferNode.OUTPUT.id, slice.id, Slice3dNode.INPUT_3D.id))
        addLink(Link(slice.id, Slice3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
    }
}
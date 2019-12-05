package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.NodeData
import xyz.rthqks.synapse.data.PortType

fun GraphData.toGraph(): Graph {
    return Graph(id, name)
}

fun NodeData.toNode(): Node = NodeMap[type]?.copy(graphId, id) ?: error("no mapping for type: $type")

val NodeMap = mapOf(
    Node.Type.Camera to Nodes.Camera,
    Node.Type.Microphone to Nodes.Microphone,
    Node.Type.MediaFile to Nodes.MediaFile,
    Node.Type.FrameDifference to Nodes.FrameDifference,
    Node.Type.GrayscaleFilter to Nodes.Grayscale,
    Node.Type.MultiplyAccumulate to Nodes.MultiplyAccumulate,
    Node.Type.OverlayFilter to Nodes.Overlay,
    Node.Type.BlurFilter to Nodes.Blur,
    Node.Type.AudioWaveform to Node(Node.Type.AudioWaveform),
    Node.Type.Image to Node(Node.Type.Image),
    Node.Type.AudioFile to Node(Node.Type.AudioFile),
    Node.Type.LutFilter to Nodes.Lut,
    Node.Type.ShaderFilter to Node(Node.Type.ShaderFilter),
    Node.Type.Speakers to Nodes.Speakers,
    Node.Type.Screen to Nodes.Screen,
    Node.Type.Creation to Node(Node.Type.Creation),
    Node.Type.Connection to Node(Node.Type.Connection)
)
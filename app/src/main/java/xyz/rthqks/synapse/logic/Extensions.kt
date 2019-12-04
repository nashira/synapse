package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.*

fun GraphData.toGraph(): Graph {
    return Graph(id, name)
}

fun NodeData.toNode(): Node {
    val type = when (type) {
        NodeType.Camera -> Nodes.Camera.copy(graphId, id)
        NodeType.VideoFile -> Nodes.MediaFile.copy(graphId, id)
        NodeType.Microphone -> Nodes.Microphone.copy(graphId, id)
        NodeType.FrameDifference -> Nodes.FrameDifference.copy(graphId, id)
        NodeType.GrayscaleFilter -> Nodes.Grayscale.copy(graphId, id)
        NodeType.MultiplyAccumulate -> Nodes.MultiplyAccumulate.copy(graphId, id)
        NodeType.OverlayFilter -> Nodes.Overlay.copy(graphId, id)
        NodeType.BlurFilter -> Nodes.Blur.copy(graphId, id)
//        NodeType.AudioWaveform -> Nodes.AudioWaveform.copy(graphId, id)
//        NodeType.Image -> Nodes.Image.copy(graphId, id)
//        NodeType.AudioFile -> Nodes.AudioFile.copy(graphId, id)
        NodeType.LutFilter -> Nodes.Lut.copy(graphId, id)
//        NodeType.ShaderFilter -> Nodes.ShaderFilter.copy(graphId, id)
        NodeType.Speakers -> Nodes.Speakers.copy(graphId, id)
        NodeType.Screen -> Nodes.Screen.copy(graphId, id)
        else -> Node(Node.Type.Image)
    }
    return type
}

fun Node.Type.toNodeType(): NodeType = when (this) {
    Node.Type.Camera -> NodeType.Camera
    Node.Type.Microphone -> NodeType.Microphone
    Node.Type.MediaFile -> NodeType.VideoFile
    Node.Type.FrameDifference -> NodeType.FrameDifference
    Node.Type.GrayscaleFilter -> NodeType.GrayscaleFilter
    Node.Type.MultiplyAccumulate -> NodeType.MultiplyAccumulate
    Node.Type.OverlayFilter -> NodeType.OverlayFilter
    Node.Type.BlurFilter -> NodeType.BlurFilter
    Node.Type.AudioWaveform -> NodeType.AudioWaveform
    Node.Type.Image -> NodeType.Image
    Node.Type.AudioFile -> NodeType.AudioFile
    Node.Type.LutFilter -> NodeType.LutFilter
    Node.Type.ShaderFilter -> NodeType.ShaderFilter
    Node.Type.Speakers -> NodeType.Speakers
    Node.Type.Screen -> NodeType.Screen
    Node.Type.Creation -> error("can't even")
    Node.Type.Connection -> error("can't even")
}

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

fun PortConfig.toPort(): Port {
    val type = when (type) {
        is PortType.Surface -> Port.Type.Video
        is PortType.Texture -> Port.Type.Video
        is PortType.AudioBuffer -> Port.Type.Audio
    }
    return Port(type, key.key, key.key, key.direction == PortType.OUTPUT)
}
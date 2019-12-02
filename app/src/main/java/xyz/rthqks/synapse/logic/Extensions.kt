package xyz.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
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

fun Node.Type.node(graphId: Int, id: Int): Node = when (this) {
    Node.Type.Camera -> Nodes.Camera.copy(graphId, id)
    Node.Type.Microphone -> Nodes.Microphone.copy(graphId, id)
    Node.Type.MediaFile -> Nodes.MediaFile.copy(graphId, id)
    Node.Type.FrameDifference -> Nodes.FrameDifference.copy(graphId, id)
    Node.Type.GrayscaleFilter -> Nodes.Grayscale.copy(graphId, id)
    Node.Type.MultiplyAccumulate -> Nodes.MultiplyAccumulate.copy(graphId, id)
    Node.Type.OverlayFilter -> Nodes.Overlay.copy(graphId, id)
    Node.Type.BlurFilter -> Nodes.Blur.copy(graphId, id)
    Node.Type.AudioWaveform -> TODO()
    Node.Type.Image -> TODO()
    Node.Type.AudioFile -> TODO()
    Node.Type.LutFilter -> Nodes.Lut.copy(graphId, id)
    Node.Type.ShaderFilter -> TODO()
    Node.Type.Speakers -> Nodes.Speakers.copy(graphId, id)
    Node.Type.Screen -> Nodes.Screen.copy(graphId, id)
    Node.Type.Connection -> Node(Node.Type.Connection)
}

fun PortConfig.toPort(): Port {
    val type = when (type){
        is PortType.Surface -> Port.Type.Video
        is PortType.Texture -> Port.Type.Video
        is PortType.AudioBuffer -> Port.Type.Audio
    }
    return Port(type, key.key, key.key, key.direction == PortType.OUTPUT)
}

object Nodes {

    val Camera = Node(Node.Type.Camera).apply {
        addPort(Port(Port.Type.Video, "video_1", "Video", true))
        setProperty("camera_facing", CameraCharacteristics.LENS_FACING_BACK)
    }

    val Microphone = Node(Node.Type.Microphone).apply {
        addPort(Port(Port.Type.Audio, "audio_1", "Audio", true))
    }

    val MediaFile = Node(Node.Type.MediaFile).apply {
        addPort(Port(Port.Type.Video, "video_1", "Video", true))
        addPort(Port(Port.Type.Audio, "audio_1", "Audio", true))
    }

    val FrameDifference = Node(Node.Type.FrameDifference).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
        addPort(Port(Port.Type.Video, "video_2", "Difference", true))
    }

    val Grayscale = Node(Node.Type.GrayscaleFilter).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
        addPort(Port(Port.Type.Video, "video_2", "Grayscale", true))
    }

    val MultiplyAccumulate = Node(Node.Type.MultiplyAccumulate).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
        addPort(Port(Port.Type.Video, "video_2", "Accumulated", true))
    }

    val Overlay = Node(Node.Type.OverlayFilter).apply {
        addPort(Port(Port.Type.Video, "video_1", "Content", false))
        addPort(Port(Port.Type.Video, "video_2", "Mask", false))
        addPort(Port(Port.Type.Video, "video_3", "Combined", true))
    }

    val Blur = Node(Node.Type.BlurFilter).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
        addPort(Port(Port.Type.Video, "video_2", "Blurred", true))
    }

    val Lut = Node(Node.Type.LutFilter).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
        addPort(Port(Port.Type.Video, "video_2", "Colored", true))
    }

    val Screen = Node(Node.Type.Screen).apply {
        addPort(Port(Port.Type.Video, "video_1", "Source", false))
    }

    val Speakers = Node(Node.Type.Speakers).apply {
        addPort(Port(Port.Type.Audio, "audio_1", "Audio", false))
    }
}
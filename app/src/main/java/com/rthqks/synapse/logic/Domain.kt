package com.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.exec_dep.node.ImageBlendNode
import com.rthqks.synapse.exec_dep.node.Lut2dNode
import com.rthqks.synapse.exec_dep.node.PhysarumNode
import com.rthqks.synapse.exec_dep.node.ShapeNode

val AudioSampleRate = Property.Key("audio_sample_rate", Int::class.java)
val AudioEncoding = Property.Key("audio_encoding", Int::class.java)
val AudioChannel = Property.Key("audio_channel", Int::class.java)
val AudioSource = Property.Key("audio_source", Int::class.java)
val CameraFacing = Property.Key("camera_facing", Int::class.java)
val VideoSize = Property.Key("video_size", Size::class.java)
val CropSize = Property.Key("crop_size", Size::class.java)
val HistorySize = Property.Key("history_size", Int::class.java)
val SliceDirection = Property.Key("slice_direction", Int::class.java)
val FrameRate = Property.Key("frame_rate", Int::class.java)
val Stabilize = Property.Key("stabilize", Boolean::class.java)
val Rotation = Property.Key("rotation", Int::class.java)
val RotationSpeed = Property.Key("rotation_speed", Float::class.java)
val BlurSize = Property.Key("blur_size", Int::class.java)
val NumPasses = Property.Key("num_passes", Int::class.java)
val ScaleFactor = Property.Key("scale_factor", Int::class.java)
val CropEnabled = Property.Key("crop_enabled", Boolean::class.java)
val GrayEnabled = Property.Key("gray_enabled", Boolean::class.java)
val LutStrength = Property.Key("lut_strength", Float::class.java)
val AccumulateFactor = Property.Key("accumulate_factor", Float::class.java)
val MultiplyFactor = Property.Key("multiply_factor", Float::class.java)
val MediaUri = Property.Key("media_uri", Uri::class.java)
val LutUri = Property.Key("lut_uri", Uri::class.java)
val CropToFit = Property.Key("crop_to_fit", Boolean::class.java)
val NetworkName = Property.Key("network_name", String::class.java)
val NumAgents = Property.Key("num_agents", Int::class.java)
val FixedWidth = Property.Key("fixed_width", Boolean::class.java)
val BlendMode = Property.Key("blend_mode", Int::class.java)
val Opacity = Property.Key("opacity", Float::class.java)
val SensorAngle = Property.Key("sensor_angle", Float::class.java)
val SensorDistance = Property.Key("sensor_distance", Float::class.java)
val TravelAngle = Property.Key("travel_angle", Float::class.java)
val TravelDistance = Property.Key("travel_distance", Float::class.java)
val SliceDepth = Property.Key("slice_depth", Float::class.java)
val Recording = Property.Key("recording", Boolean::class.java)
val NumElements = Property.Key("num_elements", FloatArray::class.java)

val Nodes = listOf(
    Node(NodeType.Camera).apply {
        add(Port(Port.Type.Video, "video_1", "Video", true))
        add(CameraFacing, CameraCharacteristics.LENS_FACING_BACK)
        add(FrameRate, 30)
        add(VideoSize, Size(1280, 720))
        add(Stabilize, value = true)
    },
    Node(NodeType.Microphone).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
        add(AudioSampleRate, 44100)
        add(AudioEncoding, AudioFormat.ENCODING_PCM_16BIT)
        add(AudioChannel, AudioFormat.CHANNEL_IN_DEFAULT)
        add(AudioSource, MediaRecorder.AudioSource.DEFAULT)
    },
    Node(NodeType.MediaFile).apply {
        add(Port(Port.Type.Video, "video_1", "Video", true))
        add(Port(Port.Type.Audio, "audio_1", "Audio", true))
        add(MediaUri, Uri.parse("none://"))
    },
    Node(NodeType.Image).apply {
        add(Port(Port.Type.Video, "image_1", "Image", true))
        add(MediaUri, Uri.parse("assets:///img/ic_launcher_web.png"))
    },
    Node(NodeType.CubeImport).apply {
        add(Port(Port.Type.Texture3D, CubeImportNode.OUTPUT.id, "LUT", true))
        add(LutUri, Uri.parse("assets:///cube/invert.cube"))
    },
    Node(NodeType.BCubeImport).apply {
        add(Port(Port.Type.Texture3D, BCubeImportNode.OUTPUT.id, "BLUT", true))
        add(LutUri, Uri.parse("assets:///cube/invert.bcube"))
    },
    Node(NodeType.FrameDifference).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Difference", true))
    },
    Node(NodeType.GrayscaleFilter).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Grayscale", true))
        add(ScaleFactor, 1)
    },
    Node(NodeType.MultiplyAccumulate).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(Port(Port.Type.Video, "video_2", "Accumulated", true))
        add(MultiplyFactor, 0.9f)
        add(AccumulateFactor, 0.9f)
    },
    Node(NodeType.BlurFilter).apply {
        add(Port(Port.Type.Video, BlurNode.INPUT.id, "Source", false))
        add(Port(Port.Type.Video, BlurNode.OUTPUT.id, "Blurred", true))
        add(ScaleFactor, 2)
        add(NumPasses, 1)
        add(BlurSize, 9)
        add(CropEnabled, false)
        add(GrayEnabled, true)
        add(CropSize, Size(320, 320))
    },
    Node(NodeType.Lut2d).apply {
        add(Port(Port.Type.Video, Lut2dNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, Lut2dNode.INPUT_LUT.id, "LUT", false))
        add(Port(Port.Type.Video, Lut2dNode.OUTPUT.id, "Output", true))
        add(FrameRate, 30)
        add(VideoSize, Size(720, 1280))
    },
    Node(NodeType.Lut3d).apply {
        add(Port(Port.Type.Video, Lut3dNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Texture3D, Lut3dNode.INPUT_LUT.id, "LUT", false))
        add(Port(Port.Type.Matrix, Lut3dNode.LUT_MATRIX.id, "LUT Matrix", false))
        add(Port(Port.Type.Video, Lut3dNode.OUTPUT.id, "Output", true))
        add(FrameRate, 30)
        add(VideoSize, Size(720, 1280))
        add(LutStrength, 1f)
    },
    Node(NodeType.CropResize).apply {
        add(Port(Port.Type.Video, CropResizeNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, CropResizeNode.OUTPUT.id, "Output", true))
        add(CropSize, Size(320, 320))
    },
    Node(NodeType.Screen).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(CropToFit, false)
    },
    Node(NodeType.TextureView).apply {
        add(Port(Port.Type.Video, "video_1", "Source", false))
        add(CropToFit, false)
    },
    Node(NodeType.Speakers).apply {
        add(Port(Port.Type.Audio, "audio_1", "Audio", false))
    },
    Node(NodeType.SlimeMold).apply {
        add(Port(Port.Type.Video, PhysarumNode.INPUT_ENV.id, "Environment", false))
        add(Port(Port.Type.Video, PhysarumNode.INPUT_AGENT.id, "Agent", false))
        add(Port(Port.Type.Video, PhysarumNode.OUTPUT_ENV.id, "Environment", true))
        add(Port(Port.Type.Video, PhysarumNode.OUTPUT_AGENT.id, "Agent", true))
        add(NumAgents, 10_000)
        add(FrameRate, 30)
        add(VideoSize, Size(720, 1280))
        add(SensorAngle, 2f)
        add(SensorDistance, 11f)
        add(TravelAngle, 4f)
        add(TravelDistance, 1.1f)
    },
    Node(NodeType.ImageBlend).apply {
        add(Port(Port.Type.Video, ImageBlendNode.INPUT_BASE.id, "Base", false))
        add(Port(Port.Type.Video, ImageBlendNode.INPUT_BLEND.id, "Blend", false))
        add(Port(Port.Type.Video, ImageBlendNode.OUTPUT.id, "Output", true))
        add(BlendMode, 1)
        add(FrameRate, 30)
        add(VideoSize, Size(720, 1280))
        add(Opacity, 1f)
    },
    Node(NodeType.Shape).apply {
        //        add(Port(Port.Type.Video, ShapeNode.INPUT_POS.id, "Positions", false))
        add(Port(Port.Type.Video, ShapeNode.OUTPUT.id, "Output", true))
        add(FrameRate, 30)
        add(VideoSize, Size(720, 1280))
    },
    Node(NodeType.RingBuffer).apply {
        add(Port(Port.Type.Video, RingBufferNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Texture3D, RingBufferNode.OUTPUT.id, "Output", true))
        add(HistorySize, 10)
        add(VideoSize, Size(720, 1280))
    },
    Node(NodeType.Slice3d).apply {
        add(Port(Port.Type.Texture3D, Slice3dNode.INPUT_3D.id, "Input", false))
        add(Port(Port.Type.Video, Slice3dNode.OUTPUT.id, "Output", true))
        add(SliceDepth, 0f)
        add(SliceDirection, 0)
        add(FrameRate, 30)
    },
    Node(NodeType.MediaEncoder).apply {
        add(Port(Port.Type.Video, EncoderNode.INPUT_VIDEO.id, "Video", false))
        add(Port(Port.Type.Audio, EncoderNode.INPUT_AUDIO.id, "Audio", false))
        add(FrameRate, 30)
        add(Recording, false)
        add(Rotation, 0)
    },
    Node(NodeType.RotateMatrix).apply {
        add(Port(Port.Type.Matrix, RotateMatrixNode.OUTPUT.id, "Matrix", true))
        add(FrameRate, 30)
        add(RotationSpeed, 1f)
    },
    Node(NodeType.CellAuto).apply {
        add(Port(Port.Type.Video, CellularAutoNode.OUTPUT.id, "Grid", true))
        add(FrameRate, 30)
        add(CellularAutoNode.GridSize, Size(180, 320))
    },
    Node(NodeType.Quantizer).apply {
        add(Port(Port.Type.Video, QuantizerNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, QuantizerNode.OUTPUT.id, "Output", true))
        add(CropSize, Size(180, 320))
        add(NumElements, floatArrayOf(6f, 6f, 6f))
    },
    Node(NodeType.Sobel).apply {
        add(Port(Port.Type.Video, SobelNode.INPUT.id, "Input", false))
        add(Port(Port.Type.Video, SobelNode.OUTPUT.id, "Output", true))
    },
    Node(NodeType.Properties).also { it.id = -2 },
    Node(NodeType.Connection).also { it.id = -3 },
    Node(NodeType.Creation).also { it.id = -4 }
)

val NodeMap = Nodes.map { it.type to it }.toMap()

val NodeTypes = listOf(
    NodeType.Camera,
    NodeType.Microphone,
    NodeType.MediaFile,
    NodeType.FrameDifference,
    NodeType.GrayscaleFilter,
    NodeType.MultiplyAccumulate,
    NodeType.BlurFilter,
    NodeType.Image,
    NodeType.CubeImport,
    NodeType.BCubeImport,
    NodeType.AudioFile,
    NodeType.Lut2d,
    NodeType.Lut3d,
    NodeType.ShaderFilter,
    NodeType.Speakers,
    NodeType.Screen,
    NodeType.TextureView,
    NodeType.SlimeMold,
    NodeType.ImageBlend,
    NodeType.CropResize,
    NodeType.Shape,
    NodeType.RingBuffer,
    NodeType.Slice3d,
    NodeType.MediaEncoder,
    NodeType.RotateMatrix,
    NodeType.Properties,
    NodeType.Creation,
    NodeType.Connection
).map { it.key to it }.toMap()

fun GetNode(type: NodeType) = NodeMap[type] ?: error("missing node $type")

fun NewNode(type: NodeType, id: Int = -1) =
    (NodeMap[type] ?: error("missing node $type")).copy(id = id)
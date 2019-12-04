package xyz.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

class Node(
    val type: Type
) {
    var graphId: Int = -1
    var id: Int = -1

    //    private val inputPorts = mutableMapOf<String, Port>()
//    private val outputPorts = mutableMapOf<String, Port>()
    val ports = mutableMapOf<String, Port>()
    val properties = mutableMapOf<String, Any?>()

    fun addPort(port: Port) {
        ports[port.id] = port

//        if (port.output) {
//            outputPorts[port.id] = port
//        } else {
//            inputPorts[port.id] = port
//        }
    }

    fun copy(graphId: Int = this.graphId, id: Int = this.id): Node = Node(type).also {
        it.graphId = graphId
        it.id = id
        it.ports.putAll(ports)
//        it.inputPorts.putAll(inputPorts)
//        it.outputPorts.putAll(outputPorts)
//        it.properties.putAll(properties)
    }

    inline fun <reified T> setProperty(key: String, value: T?) {
        properties[key] = value
    }

    inline fun <reified T> getProperty(key: String): T? {
        return properties[key] as T?
    }

    fun getPort(id: String): Port = ports[id]!!

    fun getPortIds(): Set<String> = ports.keys

    enum class Type(val key: String, @StringRes val title: Int, @DrawableRes val icon: Int) {
        Camera("camera", R.string.name_node_type_camera, R.drawable.ic_camera),
        Microphone("microphone", R.string.name_node_type_microphone, R.drawable.ic_mic),
        MediaFile("media_file", R.string.name_node_type_media_file, R.drawable.ic_movie),
        FrameDifference("frame_difference", R.string.name_node_type_frame_difference, R.drawable.ic_difference),
        GrayscaleFilter("grayscale", R.string.name_node_type_grayscale_filter, R.drawable.ic_tune),
        MultiplyAccumulate("multiply_accumulate", R.string.name_node_type_multiply_accumulate, R.drawable.ic_add),
        OverlayFilter("overlay", R.string.name_node_type_overlay_filter, R.drawable.ic_layers),
        BlurFilter("blur", R.string.name_node_type_blur_filter, R.drawable.ic_blur),
        AudioWaveform("audio_waveform", R.string.name_node_type_audio_waveform, R.drawable.ic_audio),
        Image("image", R.string.name_node_type_image, R.drawable.ic_image),
        AudioFile("audio", R.string.name_node_type_audio_file, R.drawable.ic_audio_file),
        LutFilter("lut", R.string.name_node_type_color_filter, R.drawable.ic_tune),
        ShaderFilter("shader", R.string.name_node_type_shader_filter, R.drawable.ic_texture),
        Speakers("speakers", R.string.name_node_type_speaker, R.drawable.ic_speaker),
        Screen("screen", R.string.name_node_type_screen, R.drawable.ic_display),
        Creation("creation", 0, 0),
        Connection("connection", 0, 0);

        companion object {
            private val byKey = values().map { it.key to it }.toMap()
            operator fun get(key: String) = byKey[key]
        }
    }

    companion object {

    }
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

    private val map = mapOf(
        Node.Type.Camera to Camera
    )
}
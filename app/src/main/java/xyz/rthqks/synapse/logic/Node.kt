package xyz.rthqks.synapse.logic

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.data.PropertyData
import xyz.rthqks.synapse.data.PropertyType
import xyz.rthqks.synapse.logic.Port.Type.Audio
import xyz.rthqks.synapse.logic.Port.Type.Video

class Node(
    val type: Type
) {
    var graphId: Int = -1
    var id: Int = -1

    val ports = mutableMapOf<String, Port>()
    val properties = mutableMapOf<String, Any?>()

    fun add(port: Port) {
        ports[port.id] = port
    }

    fun copy(graphId: Int = this.graphId, id: Int = this.id): Node = Node(type).also {
        it.graphId = graphId
        it.id = id
        it.ports.putAll(ports)
    }

    inline fun <reified T> setProperty(key: String, value: T?) {
        properties[key] = value
    }

    inline fun <reified T> getProperty(key: String): T? {
        return properties[key] as T?
    }

    fun getPort(id: String): Port = ports[id]!!

    fun getPortIds(): Set<String> = ports.keys

    val props = mutableMapOf<Key<*>, PropertyData>()

    operator fun <T : Any> get(key: Key<T>): T? {
        val p = props[key] ?: return null
        val type = PropertyType[key]
        return type.fromString(p.value)
    }

    enum class Type(val key: String, @StringRes val title: Int, @DrawableRes val icon: Int) {
        Camera("camera", R.string.name_node_type_camera, R.drawable.ic_camera),
        Microphone("microphone", R.string.name_node_type_microphone, R.drawable.ic_mic),
        MediaFile("media_file", R.string.name_node_type_media_file, R.drawable.ic_movie),
        FrameDifference(
            "frame_difference",
            R.string.name_node_type_frame_difference,
            R.drawable.ic_difference
        ),
        GrayscaleFilter("grayscale", R.string.name_node_type_grayscale_filter, R.drawable.ic_tune),
        MultiplyAccumulate(
            "multiply_accumulate",
            R.string.name_node_type_multiply_accumulate,
            R.drawable.ic_add
        ),
        OverlayFilter("overlay", R.string.name_node_type_overlay_filter, R.drawable.ic_layers),
        BlurFilter("blur", R.string.name_node_type_blur_filter, R.drawable.ic_blur),
//        AudioWaveform(
//            "audio_waveform",
//            R.string.name_node_type_audio_waveform,
//            R.drawable.ic_audio
//        ),
        Image("image", R.string.name_node_type_image, R.drawable.ic_image),
        AudioFile("audio", R.string.name_node_type_audio_file, R.drawable.ic_audio_file),
        LutFilter("lut", R.string.name_node_type_color_filter, R.drawable.ic_tune),
        ShaderFilter("shader", R.string.name_node_type_shader_filter, R.drawable.ic_texture),
        Speakers("speakers", R.string.name_node_type_speaker, R.drawable.ic_speaker),
        Screen("screen", R.string.name_node_type_screen, R.drawable.ic_display),
        Creation("creation", 0, 0),
        Connection("connection", 0, 0);

        fun node(): Node = toNode[this] ?: error("")

        companion object {
            private val byKey = values().map { it.key to it }.toMap()
            val toNode = All.map { it.type to it }.toMap()
            operator fun get(key: String) = byKey[key]
        }
    }

    companion object {
        val All = listOf(
            Node(Type.Camera).apply {
                add(Port(Video, "video_1", "Video", true))
                setProperty("camera_facing", CameraCharacteristics.LENS_FACING_BACK)
            },
            Node(Type.Microphone).apply {
                add(Port(Audio, "audio_1", "Audio", true))
            },
            Node(Type.MediaFile).apply {
                add(Port(Video, "video_1", "Video", true))
                add(Port(Audio, "audio_1", "Audio", true))
            },
            Node(Type.FrameDifference).apply {
                add(Port(Video, "video_1", "Source", false))
                add(Port(Video, "video_2", "Difference", true))
            },
            Node(Type.GrayscaleFilter).apply {
                add(Port(Video, "video_1", "Source", false))
                add(Port(Video, "video_2", "Grayscale", true))
            },
            Node(Type.MultiplyAccumulate).apply {
                add(Port(Video, "video_1", "Source", false))
                add(Port(Video, "video_2", "Accumulated", true))
            },
            Node(Type.OverlayFilter).apply {
                add(Port(Video, "video_1", "Content", false))
                add(Port(Video, "video_2", "Mask", false))
                add(Port(Video, "video_3", "Combined", true))
            },
            Node(Type.BlurFilter).apply {
                add(Port(Video, "video_1", "Source", false))
                add(Port(Video, "video_2", "Blurred", true))
            },
            Node(Type.LutFilter).apply {
                add(Port(Video, "video_1", "Source", false))
                add(Port(Video, "video_2", "Colored", true))
            },
            Node(Type.Screen).apply {
                add(Port(Video, "video_1", "Source", false))
            },
            Node(Type.Speakers).apply {
                add(Port(Audio, "audio_1", "Audio", false))
            },
            Node(Type.Connection),
            Node(Type.Creation)
        )
    }
}
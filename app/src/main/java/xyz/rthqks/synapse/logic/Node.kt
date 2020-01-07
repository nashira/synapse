package xyz.rthqks.synapse.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import xyz.rthqks.synapse.R

class Node(
    val type: Type
) {
    var graphId: Int = -1
    var id: Int = -1

    val ports = mutableMapOf<String, Port>()
    val properties = Properties()

    fun add(port: Port) {
        ports[port.id] = port
    }

    fun <T> add(property: Property<T>) {
        properties.put(property.key, property)
    }

    fun copy(graphId: Int = this.graphId, id: Int = this.id): Node = Node(type).also {
        it.graphId = graphId
        it.id = id
        it.ports.putAll(ports)
        properties.copyTo(it.properties)
    }

    fun getPort(id: String): Port = ports[id]!!

    fun getPortIds(): Set<String> = ports.keys

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
        Image("image", R.string.name_node_type_image, R.drawable.ic_image),
        AudioFile("audio", R.string.name_node_type_audio_file, R.drawable.ic_audio_file),
        LutFilter("lut", R.string.name_node_type_color_filter, R.drawable.ic_tune),
        ShaderFilter("shader", R.string.name_node_type_shader_filter, R.drawable.ic_texture),
        Speakers("speakers", R.string.name_node_type_speaker, R.drawable.ic_speaker),
        Screen("screen", R.string.name_node_type_screen, R.drawable.ic_display),
        Properties("properties", R.string.name_node_type_properties, R.drawable.ic_tune),
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
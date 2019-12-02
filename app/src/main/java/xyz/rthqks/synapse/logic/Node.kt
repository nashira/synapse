package xyz.rthqks.synapse.logic

class Node(
    val type: Type
) {
    var graphId: Int = -1
    var id: Int = -1

//    private val inputPorts = mutableMapOf<String, Port>()
//    private val outputPorts = mutableMapOf<String, Port>()
    private val ports = mutableMapOf<String, Port>()
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

    enum class Type(
        val key: String
    ) {
        Camera("camera"),
        Microphone("microphone"),
        MediaFile("media_file"),
        FrameDifference("frame_difference"),
        GrayscaleFilter("grayscale"),
        MultiplyAccumulate("multiply_accumulate"),
        OverlayFilter("overlay"),
        BlurFilter("blur"),
        AudioWaveform("audio_waveform"),
        Image("image"),
        AudioFile("audio"),
        LutFilter("lut"),
        ShaderFilter("shader"),
        Speakers("speakers"),
        Screen("screen"),
        Connection("connection");

        companion object {
            private val byKey = values().map { it.key to it }.toMap()
            operator fun get(key: String) = byKey[key]
        }
    }

    companion object {

    }
}
package xyz.rthqks.synapse.logic

class Port(
    val type: Type,
    val id: String,
    val name: String,
    val output: Boolean
) {

    enum class Type {
        Audio,
        Video
    }
}
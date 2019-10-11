package xyz.rthqks.synapse.data

import androidx.room.TypeConverter

class DbConverters {
    @TypeConverter
    fun fromNodeType(nodeType: NodeType): String =
        when (nodeType) {
            NodeType.Camera -> CAMERA
            NodeType.Microphone -> "microphone"
            NodeType.Image -> "image"
            NodeType.AudioFile -> "audio_file"
            NodeType.VideoFile -> "video_file"
            NodeType.ColorFilter -> "color_filter"
            NodeType.ShaderFilter -> "shader_filter"
            NodeType.Speakers -> "speakers"
            NodeType.Screen -> "screen"
        }

    @TypeConverter
    fun toNodeType(string: String): NodeType =
        when (string) {
            CAMERA -> NodeType.Camera
            "microphone" -> NodeType.Microphone
            "image" -> NodeType.Image
            "audio_file" -> NodeType.AudioFile
            "video_file" -> NodeType.VideoFile
            "color_filter" -> NodeType.ColorFilter
            "shader_filter" -> NodeType.ShaderFilter
            "speakers" -> NodeType.Speakers
            "screen" -> NodeType.Screen
            else -> throw IllegalArgumentException("unknown node type: $string")
        }

    companion object {
        private const val CAMERA = "camera"
    }
}
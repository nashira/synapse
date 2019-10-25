package xyz.rthqks.synapse.data

import androidx.room.TypeConverter

class DbConverters {
    @TypeConverter
    fun fromNodeType(nodeType: NodeType): String = nodeType.key

    @TypeConverter
    fun toNodeType(string: String): NodeType =
        when (string) {
            NodeType.Camera.key -> NodeType.Camera
            NodeType.Microphone.key -> NodeType.Microphone
            NodeType.Image.key -> NodeType.Image
            NodeType.AudioFile.key -> NodeType.AudioFile
            NodeType.VideoFile.key -> NodeType.VideoFile
            NodeType.LutFilter.key -> NodeType.LutFilter
            NodeType.ShaderFilter.key -> NodeType.ShaderFilter
            NodeType.Speakers.key -> NodeType.Speakers
            NodeType.Screen.key -> NodeType.Screen
            else -> throw IllegalArgumentException("unknown node type: $string")
        }

    companion object {
    }
}
package xyz.rthqks.synapse.data

import androidx.room.TypeConverter
import xyz.rthqks.synapse.logic.Node

class DbConverters {
    @TypeConverter
    fun fromNodeType(nodeType: Node.Type): String = nodeType.key

    @TypeConverter
    fun toNodeType(string: String): Node.Type = Node.Type[string] ?: error("Unknown Node.Type $string")

    companion object {
    }
}
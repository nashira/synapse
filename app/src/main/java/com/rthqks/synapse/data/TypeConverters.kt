package com.rthqks.synapse.data

import androidx.room.TypeConverter
import com.rthqks.synapse.logic.NodeType
import com.rthqks.synapse.logic.NodeTypes

class DbConverters {
    @TypeConverter
    fun fromNodeType(nodeType: NodeType): String = nodeType.key

    @TypeConverter
    fun toNodeType(string: String): NodeType = NodeTypes[string] ?: error("Unknown Node.Type $string")

    companion object {
    }
}
package xyz.rthqks.synapse.data

import androidx.room.*

@Entity(
    primaryKeys = ["graphId", "id"],
    indices = [
        Index("graphId", "id", unique = true)
    ]
)
data class NodeConfig(
    val id: Int,
    val graphId: Int,
    val type: NodeType
) {
    @Ignore
    val inputs = type.inputs.map { PortConfig(PortKey(id, it.key, it.direction), it) }
    @Ignore
    val outputs = type.outputs.map { PortConfig(PortKey(id, it.key, it.direction), it) }
    @Ignore
    val properties = mutableListOf<PropertyConfig>()
}
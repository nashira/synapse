package xyz.rthqks.synapse.data

import androidx.room.*

@Entity(
    indices = [
        Index("graphId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = GraphConfig::class,
            childColumns = ["graphId"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NodeConfig(
    @PrimaryKey val id: Int,
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
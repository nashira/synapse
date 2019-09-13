package xyz.rthqks.proc.data

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
    val inputs = mutableListOf<PortConfig>()
    @Ignore
    val outputs = mutableListOf<PortConfig>()

    fun createPorts(startId: Int): Int {
        var index = startId
        type.inputs.forEach{ dataType ->
            inputs.add(PortConfig(index++, graphId, id, PortConfig.DIRECTION_INPUT, dataType))
        }
        type.outputs.forEach { dataType ->
            outputs.add(PortConfig(index++, graphId, id, PortConfig.DIRECTION_OUTPUT, dataType))
        }
        return type.inputs.size + type.outputs.size
    }
}
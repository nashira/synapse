package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    indices = [
        Index("nodeId"),
        Index("graphId")
    ],
    primaryKeys = ["id", "nodeId", "graphId"],
    foreignKeys = [
        ForeignKey(
            entity = NodeConfig::class,
            childColumns = ["nodeId"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PortConfig(
    val id: Int,
    val graphId: Int,
    val nodeId: Int,
    val direction: Int,
    val dataType: DataType
) {
    companion object {
        const val DIRECTION_INPUT = 0
        const val DIRECTION_OUTPUT = 1
    }
}
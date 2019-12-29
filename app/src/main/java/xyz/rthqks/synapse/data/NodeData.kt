package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index
import xyz.rthqks.synapse.logic.Node

@Entity(
    tableName = "node",
    primaryKeys = ["graphId", "id"],
    indices = [
        Index("graphId", "id", unique = true)
    ]
)
data class NodeData(
    val id: Int,
    val graphId: Int,
    val type: Node.Type
)
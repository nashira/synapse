package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index
import com.rthqks.synapse.logic.NodeType

@Entity(
    tableName = "node",
    primaryKeys = ["networkId", "id"],
    indices = [
        Index("networkId", "id", unique = true)
    ]
)
data class NodeData(
    val id: Int,
    val networkId: Int,
    val type: NodeType
)
package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "edge",
    primaryKeys = ["graphId", "fromKey", "toKey", "fromNodeId", "toNodeId"],
    indices = [
        Index("graphId")
    ]
)
data class EdgeConfig(
    val graphId: Int,
    val fromNodeId: Int,
    val fromKey: String,
    val toNodeId: Int,
    val toKey: String
)
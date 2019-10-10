package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["fromId", "toId", "fromNodeId", "toNodeId"],
    indices = [
        Index("fromNodeId"),
        Index("toNodeId")
    ]
)
data class EdgeConfig(
    val fromNodeId: Int,
    val fromId: Int,
    val toNodeId: Int,
    val toId: Int
)
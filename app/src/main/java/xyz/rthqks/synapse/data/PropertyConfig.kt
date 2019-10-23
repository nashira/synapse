package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    indices = [
        Index("graphId", "nodeId", "type", unique = true)
    ],
    primaryKeys = ["graphId", "nodeId", "type"]
)
data class PropertyConfig(
    val graphId: Int,
    val nodeId: Int,
    val type: String,
    var value: String
)

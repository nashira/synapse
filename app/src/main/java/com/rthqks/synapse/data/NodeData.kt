package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "node",
    primaryKeys = ["networkId", "id"],
    indices = [
        Index("networkId", "id", unique = true)
    ]
)
data class NodeData(
    val id: Int,
    val networkId: String,
    val type: String
)
package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "property",
    indices = [
        Index("graphId", "nodeId", "type", unique = true)
    ],
    primaryKeys = ["graphId", "nodeId", "type"]
)
data class PropertyData(
    val graphId: Int,
    val nodeId: Int,
    val type: String,
    var value: String
)

package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "property",
    indices = [
        Index("graphId", "nodeId", "key", unique = true)
    ],
    primaryKeys = ["graphId", "nodeId", "key"]
)
data class PropertyData(
    val graphId: Int,
    val nodeId: Int,
    val key: String,
    var value: String?
)

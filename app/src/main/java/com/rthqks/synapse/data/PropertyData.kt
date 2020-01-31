package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "property",
    indices = [
        Index("networkId", "nodeId", "key", unique = true)
    ],
    primaryKeys = ["networkId", "nodeId", "key"]
)
data class PropertyData(
    val networkId: Int,
    val nodeId: Int,
    val key: String,
    var value: String?
)
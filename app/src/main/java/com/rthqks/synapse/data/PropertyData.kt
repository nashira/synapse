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
    val type: Int,
    val key: String,
    val value: String?,
    val exposed: Boolean
)

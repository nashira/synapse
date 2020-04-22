package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "port",
    indices = [
        Index("networkId", "nodeId", "id", unique = true)
    ],
    primaryKeys = ["networkId", "nodeId", "id"]
)
data class PortData(
    val networkId: Int,
    val nodeId: Int,
    val id: String,
    val type: Int,
    val name: String,
    val output: Boolean
)

package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "link",
    primaryKeys = ["networkId", "fromKey", "toKey", "fromNodeId", "toNodeId"],
    indices = [
        Index("networkId")
    ]
)
data class LinkData(
    val networkId: Int,
    val fromNodeId: Int,
    val fromKey: String,
    val toNodeId: Int,
    val toKey: String
)
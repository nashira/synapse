package xyz.rthqks.proc.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    indices = [
        Index("nodeId")
    ],
    primaryKeys = ["key", "nodeId"]
)
data class PropertyConfig(
    val key: String,
    val nodeId: Int,
    val value: String
)
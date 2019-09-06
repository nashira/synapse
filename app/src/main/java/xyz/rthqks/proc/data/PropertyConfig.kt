package xyz.rthqks.proc.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    indices = [
        Index("nodeId")
    ],
    primaryKeys = ["id", "nodeId"]
)
data class PropertyConfig(
    val id: Int,
    val nodeId: Int,
    val key: String,
    val value: String
)
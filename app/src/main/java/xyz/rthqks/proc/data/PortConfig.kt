package xyz.rthqks.proc.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    indices = [
        Index("nodeId")
    ],
    primaryKeys = ["graphId", "nodeId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = NodeConfig::class,
            childColumns = ["nodeId"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PortConfig(
    val id: Int,
    val graphId: Int,
    val nodeId: Int,
    val direction: Int,
    val dataType: DataType
)
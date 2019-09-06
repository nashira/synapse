package xyz.rthqks.proc.data

import androidx.room.*

@Entity(
    indices = [
        Index("graphId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = GraphConfig::class,
            childColumns = ["graphId"],
            parentColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NodeConfig(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val graphId: Int,
    val type: NodeType
) {
    @Ignore
    val inputs = mutableListOf<PortConfig>()
    @Ignore
    val outputs = mutableListOf<PortConfig>()
}
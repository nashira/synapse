package xyz.rthqks.proc.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
data class GraphConfig(
    @PrimaryKey(autoGenerate = true) var id: Int,
    var name: String
) {
    @Ignore
    val nodes = mutableListOf<NodeConfig>()
    @Ignore
    val edges = mutableListOf<EdgeConfig>()
}
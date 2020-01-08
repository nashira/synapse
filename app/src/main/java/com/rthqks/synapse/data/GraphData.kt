package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "graph")
data class GraphData(
    @PrimaryKey(autoGenerate = true) var id: Int,
    var name: String
) {
    @Ignore
    val nodes = mutableListOf<NodeData>()
    @Ignore
    val edges = mutableListOf<EdgeData>()

    override fun toString(): String {
        return "GraphData(id=$id, name='$name', nodes=$nodes, edges=$edges)"
    }
}
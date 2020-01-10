package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "network")
data class NetworkData(
    @PrimaryKey(autoGenerate = true) var id: Int,
    var name: String
) {
    @Ignore
    val nodes = mutableListOf<NodeData>()
    @Ignore
    val links = mutableListOf<LinkData>()

    override fun toString(): String {
        return "NetworkData(id=$id, name='$name', nodes=$nodes, links=$links)"
    }
}
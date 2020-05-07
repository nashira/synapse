package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "network")
data class NetworkData(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String
) {

    @Ignore
    var ports: List<PortData> = emptyList()
    @Ignore
    var links: List<LinkData> = emptyList()
    @Ignore
    var nodes: List<NodeData> = emptyList()
    @Ignore
    var properties: List<PropertyData> = emptyList()
}
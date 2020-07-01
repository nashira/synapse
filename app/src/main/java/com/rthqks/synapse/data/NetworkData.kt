package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "network")
data class NetworkData(
    @PrimaryKey val id: String,
    val creatorId: String,
    val name: String,
    val description: String
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
package com.rthqks.synapse.data

import androidx.room.*
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef

@Dao
abstract class SynapseDao {

    @Query("SELECT * FROM network WHERE id = :networkId")
    abstract suspend fun getNetwork(networkId: Int): NetworkData

    @Query("SELECT * FROM network")
    abstract suspend fun getNetworkData(): List<NetworkData>

    @Query("SELECT * FROM node WHERE networkId = :networkId")
    abstract suspend fun getNodes(networkId: Int): List<NodeData>

    @Query("SELECT * FROM link WHERE networkId = :networkId")
    abstract suspend fun getLinks(networkId: Int): List<LinkData>

    @Query("SELECT * FROM property WHERE networkId = :networkId AND nodeId = :nodeId")
    abstract suspend fun getProperties(networkId: Int, nodeId: Int): List<PropertyData>

    @Query("SELECT * FROM property WHERE networkId = :networkId")
    abstract suspend fun getProperties(networkId: Int): List<PropertyData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNetwork(network: NetworkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNode(node: NodeData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLink(linkData: LinkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperties(properties: Collection<PropertyData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperty(property: PropertyData)

    @Query("DELETE FROM network WHERE id = :networkId")
    abstract suspend fun deleteNetwork(networkId: Int)

    @Delete
    abstract suspend fun deleteNode(node: NodeData)

    @Query("DELETE FROM node WHERE networkId = :networkId AND id = :id")
    abstract suspend fun deleteNode(networkId: Int, id: Int)

    @Query("DELETE FROM node WHERE networkId = :networkId")
    abstract suspend fun deleteNodes(networkId: Int)

    @Query("DELETE FROM link WHERE networkId = :networkId")
    abstract suspend fun deleteLinks(networkId: Int)

    @Query("DELETE FROM link WHERE networkId = :networkId AND (fromNodeId = :nodeId OR toNodeId = :nodeId)")
    abstract suspend fun deleteLinksForNode(networkId: Int, nodeId: Int)

    @Query(
        """DELETE FROM link WHERE networkId = :networkId
        AND fromNodeId = :fromNode
        AND fromKey = :fromPort
        AND toNodeId = :toNode
        AND toKey = :toPort"""
    )
    abstract suspend fun deleteLink(
        networkId: Int,
        fromNode: Int,
        fromPort: String,
        toNode: Int,
        toPort: String
    )

    @Query("DELETE FROM property WHERE networkId = :networkId")
    abstract suspend fun deleteProperties(networkId: Int)

    @Transaction
    open suspend fun getFullNetwork(networkId: Int): Network {
        val networkData = getNetwork(networkId)
        val network = Network(networkData.id)
        val nodes = getNodes(networkId)
        val links = getLinks(networkId)
        val properties = getProperties(networkId)

        nodes.forEach {
            network.addNode(NodeDef[it.type].toNode(it.id))
        }

        links.map { Link(it.fromNodeId, it.fromKey, it.toNodeId, it.toKey) }.let(network::addLinks)

        properties.forEach {
            network.getNode(it.nodeId)?.properties?.fromString(it.type, it.key, it.value)
        }

        return network
    }

    @Transaction
    open suspend fun deleteFullNetwork(networkId: Int) {
        deleteProperties(networkId)
        deleteLinks(networkId)
        deleteNodes(networkId)
        deleteNetwork(networkId)
    }
}
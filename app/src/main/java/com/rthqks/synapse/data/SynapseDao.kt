package com.rthqks.synapse.data

import androidx.room.*

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

    @Query("SELECT * FROM port WHERE networkId = :networkId")
    abstract suspend fun getPorts(networkId: Int): List<PortData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPorts(portData: List<PortData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNetwork(network: NetworkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNode(node: NodeData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNodes(node: List<NodeData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLink(linkData: LinkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLinks(linkData: List<LinkData>)

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

    @Query("DELETE FROM port WHERE networkId = :networkId")
    abstract suspend fun deletePorts(networkId: Int)

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
    open suspend fun getFullNetwork(networkId: Int) =
        getNetwork(networkId).also { populateNetwork(it) }


    @Transaction
    open suspend fun getNetworks(): List<NetworkData> {
        return getNetworkData().also { list ->
            list.forEach { populateNetwork(it) }
        }
    }

    @Transaction
    open suspend fun insertFullNetwork(networkData: NetworkData) {
        insertNetwork(networkData)
        insertProperties(networkData.properties)
        insertPorts(networkData.ports)
        insertLinks(networkData.links)
        insertNodes(networkData.nodes)
    }

    private suspend fun populateNetwork(networkData: NetworkData) {
        val networkId = networkData.id
        networkData.nodes = getNodes(networkId)
        networkData.links = getLinks(networkId)
        networkData.properties = getProperties(networkId)
        networkData.ports = getPorts(networkId)
    }

    @Transaction
    open suspend fun deleteFullNetwork(networkId: Int) {
        deletePorts(networkId)
        deleteProperties(networkId)
        deleteLinks(networkId)
        deleteNodes(networkId)
        deleteNetwork(networkId)
    }

    @Query("SELECT COUNT(*) > 0 FROM network WHERE id = 100")
    abstract suspend fun hasNetwork0(): Boolean
}
package com.rthqks.synapse.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class SynapseDao {

    @Query("SELECT * FROM network WHERE id = :networkId")
    abstract suspend fun getNetwork(networkId: String): NetworkData

    @Query("SELECT * FROM network")
    abstract fun getNetworkData(): Flow<List<NetworkData>>

    @Query("SELECT * FROM node WHERE networkId = :networkId")
    abstract suspend fun getNodes(networkId: String): List<NodeData>

    @Query("SELECT * FROM link WHERE networkId = :networkId")
    abstract suspend fun getLinks(networkId: String): List<LinkData>

    @Query("SELECT * FROM property WHERE networkId = :networkId AND nodeId = :nodeId")
    abstract suspend fun getProperties(networkId: String, nodeId: Int): List<PropertyData>

    @Query("SELECT * FROM property WHERE networkId = :networkId")
    abstract suspend fun getProperties(networkId: String): List<PropertyData>

    @Query("SELECT * FROM port WHERE networkId = :networkId")
    abstract suspend fun getPorts(networkId: String): List<PortData>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUser(userData: UserData)

    @Query("DELETE FROM network WHERE id = :networkId")
    abstract suspend fun deleteNetwork(networkId: String)

    @Delete
    abstract suspend fun deleteNode(node: NodeData)

    @Query("DELETE FROM node WHERE networkId = :networkId AND id = :id")
    abstract suspend fun deleteNode(networkId: String, id: Int)

    @Query("DELETE FROM node WHERE networkId = :networkId")
    abstract suspend fun deleteNodes(networkId: String)

    @Query("DELETE FROM link WHERE networkId = :networkId")
    abstract suspend fun deleteLinks(networkId: String)

    @Query("DELETE FROM port WHERE networkId = :networkId")
    abstract suspend fun deletePorts(networkId: String)

    @Query("DELETE FROM link WHERE networkId = :networkId AND (fromNodeId = :nodeId OR toNodeId = :nodeId)")
    abstract suspend fun deleteLinksForNode(networkId: String, nodeId: Int)

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
    abstract suspend fun deleteProperties(networkId: String)

    @Transaction
    open suspend fun getFullNetwork(networkId: String) =
        getNetwork(networkId).also { populateNetwork(it) }


    @Transaction
    open fun getNetworks(): Flow<List<NetworkData>> {
        return getNetworkData().map { list ->
            list.forEach { populateNetwork(it) }
            list
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
    open suspend fun deleteFullNetwork(networkId: String) {
        deletePorts(networkId)
        deleteProperties(networkId)
        deleteLinks(networkId)
        deleteNodes(networkId)
        deleteNetwork(networkId)
    }

    @Query("SELECT COUNT(*) > 0 FROM network WHERE id = :id")
    abstract suspend fun hasNetwork(id: String): Boolean

    @Query("SELECT * FROM user WHERE current = 1 LIMIT 1")
    abstract suspend fun getCurrentUser(): UserData?

    @Query("UPDATE network SET name = :name WHERE id = :id")
    abstract fun setNetworkName(id: String, name: String)

    @Query("UPDATE network SET description = :description WHERE id = :id")
    abstract fun setNetworkDescription(id: String, description: String)
}
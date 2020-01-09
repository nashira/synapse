package com.rthqks.synapse.data

import androidx.room.*
import com.rthqks.synapse.logic.Graph
import com.rthqks.synapse.logic.NewNode

@Dao
abstract class SynapseDao {

    @Query("SELECT * FROM graph WHERE id = :graphId")
    abstract suspend fun getGraph(graphId: Int): GraphData

    @Query("SELECT * FROM graph")
    abstract suspend fun getGraphData(): List<GraphData>

    @Query("SELECT * FROM node WHERE graphId = :graphId")
    abstract suspend fun getNodes(graphId: Int): List<NodeData>

    @Query("SELECT * FROM edge WHERE graphId = :graphId")
    abstract suspend fun getEdges(graphId: Int): List<EdgeData>

    @Query("SELECT * FROM property WHERE graphId = :graphId AND nodeId = :nodeId")
    abstract suspend fun getProperties(graphId: Int, nodeId: Int): List<PropertyData>

    @Query("SELECT * FROM property WHERE graphId = :graphId")
    abstract suspend fun getProperties(graphId: Int): List<PropertyData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGraph(graph: GraphData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNode(node: NodeData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEdge(edgeData: EdgeData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperties(properties: Collection<PropertyData>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperty(property: PropertyData)

    @Query("DELETE FROM graph WHERE id = :graphId")
    abstract suspend fun deleteGraph(graphId: Int)

    @Delete
    abstract suspend fun deleteNode(node: NodeData)

    @Query("DELETE FROM node WHERE graphId = :graphId AND id = :id")
    abstract suspend fun deleteNode(graphId: Int, id: Int)

    @Query("DELETE FROM node WHERE graphId = :graphId")
    abstract suspend fun deleteNodes(graphId: Int)

    @Query("DELETE FROM edge WHERE graphId = :graphId")
    abstract suspend fun deleteEdges(graphId: Int)

    @Query("DELETE FROM edge WHERE graphId = :graphId AND (fromNodeId = :nodeId OR toNodeId = :nodeId)")
    abstract suspend fun deleteEdgesForNode(graphId: Int, nodeId: Int)

    @Query(
        """DELETE FROM edge WHERE graphId = :graphId
        AND fromNodeId = :fromNode
        AND fromKey = :fromPort
        AND toNodeId = :toNode
        AND toKey = :toPort"""
    )
    abstract suspend fun deleteEdge(
        graphId: Int,
        fromNode: Int,
        fromPort: String,
        toNode: Int,
        toPort: String
    )

    @Query("DELETE FROM property WHERE graphId = :graphId")
    abstract suspend fun deleteProperties(graphId: Int)

    suspend fun getGraphs(): List<Graph> {
        return getGraphData().map {
            Graph(it.id, it.name)
        }
    }

    @Transaction
    open suspend fun getFullGraph(graphId: Int): Graph {
        val graphData = getGraph(graphId)
        val graph = Graph(graphData.id, graphData.name)
        val nodes = getNodes(graphId)
        val edges = getEdges(graphId)
        val properties = getProperties(graphId)

        nodes.forEach {
            graph.addNode(NewNode(it.type, it.graphId, it.id))
        }

        edges.forEach {
            graph.addEdge(it.fromNodeId, it.fromKey, it.toNodeId, it.toKey)
        }

        properties.forEach {
            graph.getNode(it.nodeId)?.let { node ->
                node.properties[it.key] = it.value
            } ?: run {
                graph.properties[it.key] = it.value
            }
        }

        return graph
    }

    @Transaction
    open suspend fun deleteFullGraph(graphId: Int) {
        deleteProperties(graphId)
        deleteEdges(graphId)
        deleteNodes(graphId)
        deleteGraph(graphId)
    }
}
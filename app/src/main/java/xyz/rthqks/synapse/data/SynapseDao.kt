package xyz.rthqks.synapse.data

import androidx.room.*
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.logic.toGraph
import xyz.rthqks.synapse.logic.toNode

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

    @Delete
    abstract suspend fun deleteGraph(graph: GraphData)

    @Delete
    abstract suspend fun deleteNode(node: NodeData)

    @Delete
    abstract suspend fun deleteNodes(nodes: Collection<NodeData>)

    @Delete
    abstract suspend fun deleteEdges(edges: List<EdgeData>)

    @Delete
    abstract suspend fun deleteProperties(properties: Collection<PropertyData>)

    suspend fun getFullGraphData(graphId: Int): GraphData {
        val graph = getGraph(graphId)
        graph.nodes.addAll(getNodes(graphId))
        graph.edges.addAll(getEdges(graphId))
        graph.nodes.forEach {
            it.properties.putAll(getProperties(graphId, it.id).map { Key[it.type]!! to it })
        }
        return graph
    }

    suspend fun getGraphs(): List<Graph> {
        return getGraphData().map {
            it.toGraph()
        }
    }

    @Transaction
    open suspend fun getFullGraph(graphId: Int): Graph {
        val graphData = getGraph(graphId)
        val graph = graphData.toGraph()
        val nodes = getNodes(graphId)
        val edges = getEdges(graphId)
        val properties = getProperties(graphId)

        nodes.forEach {
            graph.addNode(it.toNode())
        }

        edges.forEach {
            graph.addEdge(it.fromNodeId, it.fromKey, it.toNodeId, it.toKey)
        }

        return graph
    }

    suspend fun deleteFullGraph(graph: GraphData) {
        graph.nodes.forEach {
            deleteProperties(it.properties.values)
        }
        deleteEdges(graph.edges)
        deleteNodes(graph.nodes)
        deleteGraph(graph)
    }
}
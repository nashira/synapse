package xyz.rthqks.synapse.data

import androidx.room.*

@Dao
abstract class SynapseDao {

    @Query("SELECT * FROM GraphConfig WHERE id = :graphId")
    abstract suspend fun getGraph(graphId: Int): GraphConfig

    @Query("SELECT * FROM GraphConfig")
    abstract suspend fun getGraphs(): List<GraphConfig>

    @Query("SELECT * FROM NodeConfig WHERE graphId = :graphId")
    abstract suspend fun getNodes(graphId: Int): List<NodeConfig>

    @Query("SELECT * FROM EdgeConfig WHERE graphId = :graphId")
    abstract suspend fun getEdges(graphId: Int): List<EdgeConfig>

    @Query("SELECT * FROM PropertyConfig WHERE graphId = :graphId AND nodeId = :nodeId")
    abstract suspend fun getProperties(graphId: Int, nodeId: Int): List<PropertyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGraph(graph: GraphConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNode(node: NodeConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEdge(edgeConfig: EdgeConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperties(properties: List<PropertyConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProperty(property: PropertyConfig)

    @Delete
    abstract suspend fun deleteGraph(graph: GraphConfig)

    @Delete
    abstract suspend fun deleteNodes(nodes: List<NodeConfig>)

    @Delete
    abstract suspend fun deleteEdges(edges: List<EdgeConfig>)

    @Delete
    abstract suspend fun deleteProperties(properties: List<PropertyConfig>)

    suspend fun getFullGraph(graphId: Int): GraphConfig {
        val graph = getGraph(graphId)
        graph.nodes.addAll(getNodes(graphId))
        graph.edges.addAll(getEdges(graphId))
        graph.nodes.forEach {
            it.properties.addAll(getProperties(graphId, it.id))
        }
        return graph
    }

    suspend fun deleteFullGraph(graph: GraphConfig) {
        graph.nodes.forEach {
            deleteProperties(it.properties)
        }
        deleteEdges(graph.edges)
        deleteNodes(graph.nodes)
        deleteGraph(graph)
    }
}
package xyz.rthqks.synapse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Query("SELECT * FROM PropertyConfig WHERE nodeId = :nodeId")
    abstract suspend fun getProperties(nodeId: Int): List<PropertyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertGraph(graph: GraphConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertNode(node: NodeConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProperties(properties: List<PropertyConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProperty(property: PropertyConfig)

    suspend fun getFullGraph(graphId: Int): GraphConfig {
        val graph = getGraph(graphId)
        graph.nodes.addAll(getNodes(graphId))
        graph.edges.addAll(getEdges(graphId))
        graph.nodes.forEach {
            it.properties.addAll(getProperties(it.id))
        }
        return graph
    }

}
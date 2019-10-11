package xyz.rthqks.synapse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class SynapseDao {

    @Query("SELECT * FROM GraphConfig")
    abstract suspend fun getGraphs(): List<GraphConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertNodes(nodes: List<NodeConfig>)


    fun saveGraph(graph: GraphConfig) {
        insertNodes(graph.nodes)
        graph.edges
    }
}
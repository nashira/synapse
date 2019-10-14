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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertGraph(graph: GraphConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertNode(node: NodeConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProperties(properties: List<PropertyConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertProperty(property: PropertyConfig)

}
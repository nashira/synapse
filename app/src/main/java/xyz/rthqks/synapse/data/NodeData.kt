package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import xyz.rthqks.synapse.logic.Node

@Entity(
    tableName = "node",
    primaryKeys = ["graphId", "id"],
    indices = [
        Index("graphId", "id", unique = true)
    ]
)
data class NodeData(
    val id: Int,
    val graphId: Int,
    val type: Node.Type
) {

    @Ignore
    val properties = mutableMapOf<Key<*>, PropertyData>()

    operator fun <T : Any> get(key: Key<T>): T {
        val p = properties[key]!!
        val type = PropertyType[key]
        return type.fromString(p.value)
    }
}
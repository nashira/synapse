package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index

@Entity(
    primaryKeys = ["graphId", "id"],
    indices = [
        Index("graphId", "id", unique = true)
    ]
)
data class NodeConfig(
    val id: Int,
    val graphId: Int,
    val type: NodeType
) {
    @Ignore
    val inputs = type.inputs.map { PortConfig(PortKey(id, it.key, it.direction), it) }
    @Ignore
    val outputs = type.outputs.map { PortConfig(PortKey(id, it.key, it.direction), it) }
    @Ignore
    val properties = mutableMapOf<Key<*>, PropertyConfig>()

    operator fun <T> get(key: Key<T>): T {
        val p = properties[key]!!
        val type = PropertyType[key]!!
        @Suppress("UNCHECKED_CAST")
        return type.fromString(p.value)
    }

    operator fun <T> set(key: Key<T>, value: T) {
        @Suppress("UNCHECKED_CAST")
        val p = properties[key] as PropertyConfig
        p.value = value.toString()
    }
}
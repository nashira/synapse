package xyz.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    indices = [
        Index("graphId", "nodeId", "key", unique = true)
    ],
    primaryKeys = ["graphId", "nodeId", "key"]
)
data class PropertyConfig(
    val graphId: Int,
    val nodeId: Int,
    val key: String,
    var value: String
) {
//    val typeKey: PropertyType<*> = PropertyType[Key[key]]
}

package xyz.rthqks.synapse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GraphData::class,
        NodeConfig::class,
        EdgeConfig::class,
        PropertyConfig::class
    ],
    version = SynapseDb.VERSION,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class SynapseDb : RoomDatabase() {

    abstract fun dao(): SynapseDao

    companion object {
        const val VERSION = 2
        const val NAME = "synapse.db"
    }
}
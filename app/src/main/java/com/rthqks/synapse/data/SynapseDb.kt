package com.rthqks.synapse.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserData::class,
        NetworkData::class,
        NodeData::class,
        PortData::class,
        LinkData::class,
        PropertyData::class
    ],
    version = SynapseDb.VERSION,
    exportSchema = false
)
//@TypeConverters(DbConverters::class)
abstract class SynapseDb : RoomDatabase() {

    abstract fun dao(): SynapseDao

    companion object {
        const val VERSION = 18
        const val NAME = "synapse.db"
    }
}
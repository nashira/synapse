package com.rthqks.synapse.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NetworkData::class,
        NodeData::class,
        LinkData::class,
        PropertyData::class
    ],
    version = SynapseDb.VERSION,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class SynapseDb : RoomDatabase() {

    abstract fun dao(): SynapseDao

    companion object {
        const val VERSION = 11
        const val NAME = "synapse.db"
    }
}
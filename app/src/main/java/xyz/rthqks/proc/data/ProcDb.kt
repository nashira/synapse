package xyz.rthqks.proc.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GraphConfig::class,
        NodeConfig::class,
        PortConfig::class,
        EdgeConfig::class,
        PropertyConfig::class
    ],
    version = ProcDb.VERSION,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class ProcDb : RoomDatabase() {
    companion object {
        const val VERSION = 1
    }
}
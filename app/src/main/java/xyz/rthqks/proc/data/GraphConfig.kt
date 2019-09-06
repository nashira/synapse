package xyz.rthqks.proc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class GraphConfig(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String
)
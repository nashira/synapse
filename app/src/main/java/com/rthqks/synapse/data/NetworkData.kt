package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network")
data class NetworkData(
    @PrimaryKey(autoGenerate = true) var id: Int
)
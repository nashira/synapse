package com.rthqks.synapse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserData(
    @PrimaryKey val id: String,
    val name: String,
    val current: Boolean = false
)
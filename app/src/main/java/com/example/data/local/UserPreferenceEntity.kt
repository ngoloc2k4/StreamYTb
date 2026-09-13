package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "tag_name")
    val tagName: String,

    @ColumnInfo(name = "affinity_score")
    val affinityScore: Double = 0.0,

    @ColumnInfo(name = "last_interacted")
    val lastInteracted: Long = System.currentTimeMillis()
)

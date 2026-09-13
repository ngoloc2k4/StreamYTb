package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {
    @Query("SELECT * FROM user_preferences ORDER BY affinity_score DESC")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences ORDER BY affinity_score DESC LIMIT :limit")
    suspend fun getTopPreferences(limit: Int): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE tag_name = :tag LIMIT 1")
    suspend fun getPreference(tag: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: UserPreferenceEntity)

    @Query("UPDATE user_preferences SET affinity_score = affinity_score + :delta, last_interacted = :now WHERE tag_name = :tag")
    suspend fun updateScore(tag: String, delta: Double, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_preferences")
    suspend fun clearAll()
}

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE is_audio_only = 1 ORDER BY timestamp DESC")
    fun getAudioWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE video_id = :videoId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecentByVideoId(videoId: String): WatchHistoryEntity?

    @Query("SELECT COUNT(*) FROM watch_history WHERE video_id = :videoId AND timestamp > :sinceTimestamp")
    suspend fun countPlaysInWindow(videoId: String, sinceTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistoryEntity): Long

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}

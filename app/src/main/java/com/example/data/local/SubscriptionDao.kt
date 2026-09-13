package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY subscribed_at DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE channel_id = :channelId LIMIT 1")
    suspend fun getSubscription(channelId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE channel_id = :channelId LIMIT 1")
    fun observeSubscription(channelId: String): Flow<SubscriptionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channel_id = :channelId")
    suspend fun deleteSubscription(channelId: String)

    @Query("SELECT DISTINCT custom_group FROM subscriptions WHERE custom_group IS NOT NULL")
    fun getCustomGroups(): Flow<List<String>>

    @Query("SELECT * FROM subscriptions WHERE custom_group = :group ORDER BY subscribed_at DESC")
    fun getSubscriptionsByGroup(group: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun getSubscriptionCount(): Flow<Int>
}

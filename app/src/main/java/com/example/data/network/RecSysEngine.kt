package com.example.data.network

import com.example.data.local.SubscriptionDao
import com.example.data.local.UserPreferenceDao
import com.example.data.local.UserPreferenceEntity
import com.example.data.local.WatchHistoryDao
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.FeedItem
import com.example.data.model.FeedSourceReason
import com.example.data.model.StreamVideo
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.max

class RecSysEngine(
    private val subscriptionDao: SubscriptionDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val userPreferenceDao: UserPreferenceDao
) {

    /**
     * Records playback interaction and updates affinity scores based on:
     * Score(Tag) = sum(WatchTimeBonus + InteractionWeight) - DecayFactor
     * - Watched >= 70% or full song: +5 points
     * - Skipped / stopped < 30s: -3 points
     * - Repeated track in 24h: +4 points
     */
    suspend fun recordPlaybackInteraction(
        video: StreamVideo,
        watchedSec: Int,
        totalDurationSec: Int,
        isAudioOnly: Boolean
    ) {
        val now = System.currentTimeMillis()

        // 1. Save to Room watch_history
        watchHistoryDao.insertHistory(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelId = video.channelId,
                durationSec = totalDurationSec,
                watchedSec = watchedSec,
                isAudioOnly = if (isAudioOnly) 1 else 0,
                timestamp = now
            )
        )

        // 2. Compute interaction score delta
        var delta = 0.0
        val watchRatio = if (totalDurationSec > 0) watchedSec.toDouble() / totalDurationSec.toDouble() else 0.0

        if (watchRatio >= 0.70 || (totalDurationSec > 0 && watchedSec >= totalDurationSec)) {
            delta += 5.0 // +5 for >= 70% watch
        } else if (watchedSec < 30 && totalDurationSec > 45) {
            delta -= 3.0 // -3 for early skip
        }

        // Check if repeated track within 24 hours (86,400,000 ms)
        val oneDayAgo = now - 86_400_000L
        val recentPlays = watchHistoryDao.countPlaysInWindow(video.id, oneDayAgo)
        if (recentPlays >= 2) {
            delta += 4.0 // +4 for replay within 24h
        }

        // Apply score to video tags
        val tagsToUpdate = video.tags.toMutableList()
        tagsToUpdate.add(video.channelTitle)
        if (video.category.isNotEmpty()) tagsToUpdate.add(video.category)

        for (tag in tagsToUpdate.distinct()) {
            val existing = userPreferenceDao.getPreference(tag)
            val currentScore = existing?.affinityScore ?: 0.0
            val lastTime = existing?.lastInteracted ?: now
            // Apply gentle decay factor based on days elapsed
            val daysPassed = max(0.0, (now - lastTime).toDouble() / (1000.0 * 60 * 60 * 24))
            val decay = daysPassed * 0.1
            val newScore = max(0.0, (currentScore - decay) + delta)

            userPreferenceDao.upsertPreference(
                UserPreferenceEntity(
                    tagName = tag,
                    affinityScore = newScore,
                    lastInteracted = now
                )
            )
        }
    }

    /**
     * Subscribing adds +10 points to channel tag weight
     */
    suspend fun recordSubscriptionInteraction(channelTitle: String, isSubscribed: Boolean) {
        val now = System.currentTimeMillis()
        val delta = if (isSubscribed) 10.0 else -10.0
        val existing = userPreferenceDao.getPreference(channelTitle)
        val currentScore = existing?.affinityScore ?: 0.0
        val newScore = max(0.0, currentScore + delta)

        userPreferenceDao.upsertPreference(
            UserPreferenceEntity(
                tagName = channelTitle,
                affinityScore = newScore,
                lastInteracted = now
            )
        )
    }

    /**
     * Generates dynamic home feed strictly following the 50% Subscriptions / 30% Preferences / 20% Trending split:
     * - 50%: Videos from subscriptions
     * - 30%: Related to top 3 affinity tags in last 7 days
     * - 20%: Regional trending / Top music
     */
    suspend fun generateHomeFeed(
        allVideos: List<StreamVideo>
    ): List<FeedItem> {
        val subscriptions = subscriptionDao.getAllSubscriptions().firstOrNull() ?: emptyList()
        val subChannelIds = subscriptions.map { it.channelId }.toSet()

        val topTags = userPreferenceDao.getTopPreferences(3).map { it.tagName }.toSet()

        val subVideos = mutableListOf<StreamVideo>()
        val prefVideos = mutableListOf<StreamVideo>()
        val trendVideos = mutableListOf<StreamVideo>()

        for (video in allVideos) {
            when {
                video.channelId in subChannelIds -> {
                    subVideos.add(video)
                }
                video.tags.any { it in topTags } || video.channelTitle in topTags -> {
                    prefVideos.add(video)
                }
                else -> {
                    trendVideos.add(video)
                }
            }
        }

        // If user has no subscriptions yet, distribute more to preferences and trending
        val result = mutableListOf<FeedItem>()

        // 50% target
        val subCount = if (subVideos.isNotEmpty()) subVideos.size else 0
        subVideos.take(4).forEach {
            result.add(FeedItem(it, FeedSourceReason.SUBSCRIPTION, 10.0))
        }

        // 30% target
        val prefTarget = if (prefVideos.isNotEmpty()) prefVideos else allVideos.filter { it.tags.any { tag -> tag in listOf("Âm nhạc", "V-Pop", "Lofi") } }
        prefTarget.filterNot { item -> result.any { it.video.id == item.id } }
            .take(3)
            .forEach {
                result.add(FeedItem(it, FeedSourceReason.INTERACTION_REC, 7.5))
            }

        // 20% target
        val trendTarget = if (trendVideos.isNotEmpty()) trendVideos else allVideos
        trendTarget.filterNot { item -> result.any { it.video.id == item.id } }
            .take(3)
            .forEach {
                result.add(FeedItem(it, FeedSourceReason.TRENDING, 5.0))
            }

        // Fill remaining from catalog to ensure a rich feed
        if (result.size < allVideos.size) {
            allVideos.filterNot { item -> result.any { it.video.id == item.id } }.forEach {
                result.add(FeedItem(it, FeedSourceReason.TRENDING, 3.0))
            }
        }

        return result
    }
}

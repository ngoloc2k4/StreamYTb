package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.ClientType
import com.example.data.model.FeedItem
import com.example.data.model.StreamChannel
import com.example.data.model.StreamVideo
import com.example.data.network.InnerTubeEngine
import com.example.data.network.RecSysEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MediaRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val subscriptionDao = db.subscriptionDao()
    private val watchHistoryDao = db.watchHistoryDao()
    private val userPreferenceDao = db.userPreferenceDao()

    val innerTubeEngine = InnerTubeEngine()
    val recSysEngine = RecSysEngine(subscriptionDao, watchHistoryDao, userPreferenceDao)

    val subscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()
    val watchHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllWatchHistory()
    val preferences: Flow<List<UserPreferenceEntity>> = userPreferenceDao.getAllPreferences()

    private var cachedMusicVideos: List<StreamVideo> = emptyList()

    fun getAllVideos(): List<StreamVideo> = innerTubeEngine.getSampleMediaCatalog()

    fun getAllChannels(): List<StreamChannel> = innerTubeEngine.getSampleChannels()

    suspend fun getMusicVideos(forceRefresh: Boolean = false): List<StreamVideo> {
        return withContext(Dispatchers.IO) {
            if (cachedMusicVideos.isNotEmpty() && !forceRefresh) {
                return@withContext cachedMusicVideos
            }
            val fetched = innerTubeEngine.fetchMusic()
            if (fetched.isNotEmpty()) {
                cachedMusicVideos = fetched
                fetched
            } else {
                getAllVideos().filter { it.isAudioOnly || it.category == "Âm nhạc" || it.category == "Lofi" }
            }
        }
    }

    suspend fun getHomeFeed(): List<FeedItem> {
        return withContext(Dispatchers.IO) {
            val trending = innerTubeEngine.fetchTrending()
            val baseVideos = if (trending.isNotEmpty()) trending else getAllVideos()
            recSysEngine.generateHomeFeed(baseVideos)
        }
    }

    suspend fun isSubscribed(channelId: String): Boolean {
        return withContext(Dispatchers.IO) {
            subscriptionDao.getSubscription(channelId) != null
        }
    }

    fun observeIsSubscribed(channelId: String): Flow<SubscriptionEntity?> {
        return subscriptionDao.observeSubscription(channelId)
    }

    suspend fun toggleSubscription(channel: StreamChannel, customGroup: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val existing = subscriptionDao.getSubscription(channel.id)
            if (existing != null) {
                subscriptionDao.deleteSubscription(channel.id)
                recSysEngine.recordSubscriptionInteraction(channel.title, false)
                false
            } else {
                subscriptionDao.insertSubscription(
                    SubscriptionEntity(
                        channelId = channel.id,
                        title = channel.title,
                        thumbnailUrl = channel.thumbnailUrl,
                        customGroup = customGroup ?: channel.customGroup,
                        subscribedAt = System.currentTimeMillis()
                    )
                )
                recSysEngine.recordSubscriptionInteraction(channel.title, true)
                true
            }
        }
    }

    suspend fun recordWatchProgress(
        video: StreamVideo,
        watchedSec: Int,
        totalSec: Int,
        isAudioOnly: Boolean
    ) {
        withContext(Dispatchers.IO) {
            recSysEngine.recordPlaybackInteraction(video, watchedSec, totalSec, isAudioOnly)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            watchHistoryDao.clearAllHistory()
        }
    }

    suspend fun clearPreferences() {
        withContext(Dispatchers.IO) {
            userPreferenceDao.clearAll()
        }
    }

    fun search(query: String): List<StreamVideo> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return getAllVideos()
        return getAllVideos().filter {
            it.title.lowercase().contains(trimmed) ||
                    it.channelTitle.lowercase().contains(trimmed) ||
                    it.tags.any { tag -> tag.lowercase().contains(trimmed) }
        }
    }

    suspend fun searchOnline(query: String): List<StreamVideo> {
        return withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext getAllVideos()
            innerTubeEngine.searchVideos(trimmed)
        }
    }

    suspend fun resolveStream(video: StreamVideo): StreamVideo {
        return withContext(Dispatchers.IO) {
            if (video.streamUrl.isEmpty() || !video.streamUrl.startsWith("http")) {
                val result = innerTubeEngine.fetchVideoStreams(video.id)
                val resolved = result.getOrNull()
                if (resolved != null && (resolved.streamUrl.isNotEmpty() || resolved.audioStreamUrl.isNotEmpty())) {
                    resolved.copy(
                        title = video.title.ifEmpty { resolved.title },
                        channelTitle = video.channelTitle.ifEmpty { resolved.channelTitle },
                        thumbnailUrl = video.thumbnailUrl.ifEmpty { resolved.thumbnailUrl },
                        tags = if (video.tags.isNotEmpty()) video.tags else resolved.tags,
                        isAudioOnly = video.isAudioOnly || resolved.isAudioOnly
                    )
                } else {
                    video
                }
            } else {
                video
            }
        }
    }

    /**
     * Export Subscriptions compatible with NewPipe JSON format
     */
    suspend fun exportNewPipeJson(): String {
        return withContext(Dispatchers.IO) {
            val subs = subscriptionDao.getAllSubscriptions().first()
            val root = JSONObject()
            root.put("app_version", "1.0")
            root.put("app_version_int", 1)
            val subArray = JSONArray()
            subs.forEach {
                val subObj = JSONObject()
                subObj.put("service_id", 0)
                subObj.put("url", "https://www.youtube.com/channel/${it.channelId}")
                subObj.put("name", it.title)
                subArray.put(subObj)
            }
            root.put("subscriptions", subArray)
            root.toString(2)
        }
    }

    /**
     * Import Subscriptions from JSON
     */
    suspend fun importSubscriptionsJson(jsonString: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonString)
                val subArray = root.optJSONArray("subscriptions") ?: JSONArray()
                var imported = 0
                for (i in 0 until subArray.length()) {
                    val item = subArray.getJSONObject(i)
                    val name = item.optString("name", "Channel")
                    val url = item.optString("url", "")
                    val channelId = url.substringAfterLast("/").ifEmpty { "channel_$i" }
                    subscriptionDao.insertSubscription(
                        SubscriptionEntity(
                            channelId = channelId,
                            title = name,
                            thumbnailUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200",
                            customGroup = "Đã nhập",
                            subscribedAt = System.currentTimeMillis()
                        )
                    )
                    imported++
                }
                imported
            } catch (e: Exception) {
                0
            }
        }
    }
}

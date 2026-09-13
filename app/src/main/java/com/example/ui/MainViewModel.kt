package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.ClientType
import com.example.data.model.FeedItem
import com.example.data.model.PlayerState
import com.example.data.model.StreamChannel
import com.example.data.model.StreamVideo
import com.example.data.repository.MediaRepository
import com.example.player.PlayerManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MediaRepository(application)
    val playerManager = PlayerManager(application, repository)

    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems.asStateFlow()

    private val _musicTracks = MutableStateFlow<List<StreamVideo>>(emptyList())
    val musicTracks: StateFlow<List<StreamVideo>> = _musicTracks.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isRecSysDialogOpen = MutableStateFlow(false)
    val isRecSysDialogOpen: StateFlow<Boolean> = _isRecSysDialogOpen.asStateFlow()

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _toastMessage = MutableSharedFlow<UiText>()
    val toastMessage: SharedFlow<UiText> = _toastMessage.asSharedFlow()

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<List<UserPreferenceEntity>> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState: StateFlow<PlayerState> = playerManager.playerState

    // Checks if current media channel is subscribed
    val isCurrentChannelSubscribed: StateFlow<Boolean> = combine(
        playerState,
        subscriptions
    ) { state, subs ->
        val currentMedia = state.currentMedia ?: return@combine false
        subs.any { it.channelId == currentMedia.channelId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        refreshFeed()
        refreshMusic()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val items = repository.getHomeFeed()
            _feedItems.value = items
            _isRefreshing.value = false
        }
    }

    fun refreshMusic(genreQuery: String = "nhạc trẻ vpop mới nhất") {
        viewModelScope.launch {
            val tracks = repository.getMusicVideos(forceRefresh = true)
            _musicTracks.value = tracks
        }
    }

    fun playVideo(video: StreamVideo, audioOnly: Boolean = false, queue: List<StreamVideo> = emptyList()) {
        val effectiveQueue = if (queue.isNotEmpty()) {
            queue
        } else {
            val currentFeedVideos = _feedItems.value.map { it.video }
            if (currentFeedVideos.any { it.id == video.id }) currentFeedVideos else listOf(video)
        }
        playerManager.playMedia(video, audioOnly, effectiveQueue)
    }

    fun playMusicTrack(video: StreamVideo, queue: List<StreamVideo> = emptyList()) {
        val effectiveQueue = if (queue.isNotEmpty()) {
            queue
        } else {
            if (_musicTracks.value.any { it.id == video.id }) _musicTracks.value else listOf(video)
        }
        playerManager.playMedia(video, true, effectiveQueue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun toggleAudioOnlyMode() {
        playerManager.toggleAudioOnlyMode()
    }

    fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun setClientSpoof(client: ClientType) {
        playerManager.switchClientSpoof(client)
        viewModelScope.launch {
            _toastMessage.emit(UiText.ResourceString(R.string.toast_client_changed, client.label))
        }
    }

    fun toggleSubscribeCurrent() {
        val media = playerState.value.currentMedia ?: return
        viewModelScope.launch {
            val channel = StreamChannel(
                id = media.channelId,
                title = media.channelTitle,
                thumbnailUrl = media.thumbnailUrl,
                subscriberCountText = "Channel"
            )
            val subbed = repository.toggleSubscription(channel)
            _toastMessage.emit(
                if (subbed) UiText.ResourceString(R.string.toast_subscribed, channel.title)
                else UiText.ResourceString(R.string.toast_unsubscribed, channel.title)
            )
            refreshFeed()
        }
    }

    fun toggleSubscribeChannel(channel: StreamChannel) {
        viewModelScope.launch {
            val subbed = repository.toggleSubscription(channel)
            _toastMessage.emit(
                if (subbed) UiText.ResourceString(R.string.toast_subscribed, channel.title)
                else UiText.ResourceString(R.string.toast_unsubscribed, channel.title)
            )
            refreshFeed()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _toastMessage.emit(UiText.ResourceString(R.string.toast_history_cleared))
        }
    }

    fun clearPreferences() {
        viewModelScope.launch {
            repository.clearPreferences()
            _toastMessage.emit(UiText.ResourceString(R.string.toast_recsys_reset))
            refreshFeed()
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            val json = repository.exportNewPipeJson()
            _toastMessage.emit(UiText.ResourceString(R.string.toast_backup_exported, subscriptions.value.size))
        }
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val count = repository.importSubscriptionsJson(jsonString)
            _toastMessage.emit(UiText.ResourceString(R.string.toast_backup_imported, count))
            refreshFeed()
        }
    }

    fun setSearchOpen(isOpen: Boolean) {
        _isSearchOpen.value = isOpen
    }

    fun setRecSysDialogOpen(isOpen: Boolean) {
        _isRecSysDialogOpen.value = isOpen
    }

    fun setFullPlayerExpanded(isExpanded: Boolean) {
        _isFullPlayerExpanded.value = isExpanded
    }

    fun dismissPlayer() {
        playerManager.seekTo(0)
        playerManager.togglePlayPause()
        _isFullPlayerExpanded.value = false
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

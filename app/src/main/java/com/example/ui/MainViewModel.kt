package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isRecSysDialogOpen = MutableStateFlow(false)
    val isRecSysDialogOpen: StateFlow<Boolean> = _isRecSysDialogOpen.asStateFlow()

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

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
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val items = repository.getHomeFeed()
            _feedItems.value = items
            _isRefreshing.value = false
        }
    }

    fun playVideo(video: StreamVideo, audioOnly: Boolean = false) {
        val queue = repository.getAllVideos()
        playerManager.playMedia(video, audioOnly, queue)
    }

    fun playMusicTrack(video: StreamVideo) {
        val queue = repository.getMusicVideos()
        playerManager.playMedia(video, true, queue)
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
            _toastMessage.emit("Đã đổi client giả lập: ${client.label}")
        }
    }

    fun toggleSubscribeCurrent() {
        val media = playerState.value.currentMedia ?: return
        viewModelScope.launch {
            val channel = StreamChannel(
                id = media.channelId,
                title = media.channelTitle,
                thumbnailUrl = media.thumbnailUrl,
                subscriberCountText = "Kênh"
            )
            val subbed = repository.toggleSubscription(channel)
            _toastMessage.emit(if (subbed) "Đã đăng ký kênh ${channel.title}" else "Đã hủy đăng ký ${channel.title}")
            refreshFeed()
        }
    }

    fun toggleSubscribeChannel(channel: StreamChannel) {
        viewModelScope.launch {
            val subbed = repository.toggleSubscription(channel)
            _toastMessage.emit(if (subbed) "Đã đăng ký kênh ${channel.title}" else "Đã hủy đăng ký ${channel.title}")
            refreshFeed()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _toastMessage.emit("Đã xóa toàn bộ lịch sử xem")
        }
    }

    fun clearPreferences() {
        viewModelScope.launch {
            repository.clearPreferences()
            _toastMessage.emit("Đã đặt lại điểm thuật toán gợi ý")
            refreshFeed()
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            val json = repository.exportNewPipeJson()
            _toastMessage.emit("Đã sao lưu ${subscriptions.value.size} kênh (định dạng NewPipe)")
        }
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val count = repository.importSubscriptionsJson(jsonString)
            _toastMessage.emit("Đã nhập thành công $count kênh từ dữ liệu NewPipe")
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

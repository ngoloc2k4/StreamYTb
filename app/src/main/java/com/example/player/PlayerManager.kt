package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.model.ClientType
import com.example.data.model.PlayerState
import com.example.data.model.StreamVideo
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerManager(
    private val context: Context,
    private val repository: MediaRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 2

    private var mediaController: MediaController? = null
    private var pendingPlayTask: (() -> Unit)? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                setupPlayerListener(controller)

                // Execute any pending play command that was queued during connection
                pendingPlayTask?.invoke()
                pendingPlayTask = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.update { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        retryCount = 0
                        _playerState.update {
                            it.copy(
                                isBuffering = false,
                                durationMs = player.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playerState.update { it.copy(isBuffering = false, isPlaying = false) }
                        onTrackFinished()
                    }
                    Player.STATE_IDLE -> {
                        _playerState.update { it.copy(isBuffering = false) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < maxRetries) {
                    retryCount++
                    val fallbackClient = repository.innerTubeEngine.triggerFallback()
                    _playerState.update { it.copy(isBuffering = true, activeClient = fallbackClient) }

                    val current = _playerState.value.currentMedia
                    if (current != null) {
                        coroutineScope.launch {
                            val resolved = repository.resolveStream(current)
                            val targetUrl = if (_playerState.value.isAudioOnlyMode) {
                                resolved.audioStreamUrl.ifEmpty { resolved.streamUrl }
                            } else {
                                resolved.streamUrl.ifEmpty { resolved.audioStreamUrl }
                            }
                            if (targetUrl.isNotEmpty()) {
                                player.setMediaItem(MediaItem.fromUri(targetUrl))
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                } else {
                    retryCount = 0
                    _playerState.update { it.copy(isBuffering = false, isPlaying = false) }
                }
            }
        })
    }

    fun getPlayer(): Player? = mediaController

    // Retained for backward compatibility with UI components
    fun getExoPlayer(): Player? = mediaController

    fun playMedia(video: StreamVideo, audioOnly: Boolean = false, queue: List<StreamVideo> = emptyList()) {
        val controller = mediaController
        if (controller == null) {
            // Queue play until service connection completes
            pendingPlayTask = { playMedia(video, audioOnly, queue) }
            return
        }

        retryCount = 0

        // Record progress for the previous track
        val prevVideo = _playerState.value.currentMedia
        if (prevVideo != null) {
            val watchedSec = (_playerState.value.currentPositionMs / 1000).toInt()
            val totalSec = (_playerState.value.durationMs / 1000).toInt()
            coroutineScope.launch {
                repository.recordWatchProgress(prevVideo, watchedSec, totalSec, _playerState.value.isAudioOnlyMode)
            }
        }

        val resolvedQueue = if (queue.isNotEmpty()) queue else listOf(video)
        val currentIndex = resolvedQueue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)

        _playerState.update {
            it.copy(
                currentMedia = video,
                isAudioOnlyMode = audioOnly || video.isAudioOnly,
                queue = resolvedQueue,
                currentIndex = currentIndex,
                currentPositionMs = 0L,
                durationMs = (video.durationSec * 1000L),
                activeClient = repository.innerTubeEngine.getActiveClient(),
                isBuffering = true
            )
        }

        coroutineScope.launch {
            // Resolve stream URL if needed (e.g. from YouTube search or trending)
            val resolvedVideo = repository.resolveStream(video)
            _playerState.update { it.copy(currentMedia = resolvedVideo) }

            val mediaUrl = if (audioOnly || resolvedVideo.isAudioOnly) {
                resolvedVideo.audioStreamUrl.ifEmpty { resolvedVideo.streamUrl }
            } else {
                resolvedVideo.streamUrl.ifEmpty { resolvedVideo.audioStreamUrl }
            }

            if (mediaUrl.isEmpty()) {
                _playerState.update { it.copy(isBuffering = false) }
                return@launch
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(resolvedVideo.title)
                .setArtist(resolvedVideo.channelTitle)
                .setArtworkUri(Uri.parse(resolvedVideo.thumbnailUrl))
                .setDescription(resolvedVideo.description)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(resolvedVideo.id)
                .setUri(Uri.parse(mediaUrl))
                .setMediaMetadata(metadata)
                .build()

            // Configure audio-only mode on the service
            sendAudioOnlyModeCommand(audioOnly || resolvedVideo.isAudioOnly)

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    private fun sendAudioOnlyModeCommand(audioOnly: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply { putBoolean("audioOnly", audioOnly) }
        controller.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_AUDIO_ONLY, Bundle.EMPTY), args)
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekRelative(offsetMs: Long) {
        val player = mediaController ?: return
        val target = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
        _playerState.update { it.copy(speed = speed) }
    }

    fun toggleAudioOnlyMode() {
        val current = _playerState.value.currentMedia ?: return
        val newMode = !_playerState.value.isAudioOnlyMode
        val currentPos = mediaController?.currentPosition ?: 0L

        sendAudioOnlyModeCommand(newMode)
        _playerState.update { it.copy(isAudioOnlyMode = newMode) }

        val mediaUrl = if (newMode) current.audioStreamUrl.ifEmpty { current.streamUrl } else current.streamUrl.ifEmpty { current.audioStreamUrl }
        if (mediaUrl.isNotEmpty()) {
            mediaController?.setMediaItem(MediaItem.fromUri(mediaUrl), currentPos)
            mediaController?.prepare()
            mediaController?.play()
        }
    }

    fun switchClientSpoof(client: ClientType) {
        repository.innerTubeEngine.setClient(client)
        _playerState.update { it.copy(activeClient = client) }

        // Re-resolve current video with the new client
        val current = _playerState.value.currentMedia ?: return
        coroutineScope.launch {
            val resolved = repository.resolveStream(current)
            _playerState.update { it.copy(currentMedia = resolved) }
        }
    }

    fun playNext() {
        val state = _playerState.value
        if (state.queue.isNotEmpty() && state.currentIndex < state.queue.size - 1) {
            val nextVideo = state.queue[state.currentIndex + 1]
            playMedia(nextVideo, state.isAudioOnlyMode, state.queue)
        }
    }

    fun playPrevious() {
        val state = _playerState.value
        if (state.queue.isNotEmpty() && state.currentIndex > 0) {
            val prevVideo = state.queue[state.currentIndex - 1]
            playMedia(prevVideo, state.isAudioOnlyMode, state.queue)
        }
    }

    private fun onTrackFinished() {
        val state = _playerState.value
        val finishedVideo = state.currentMedia
        if (finishedVideo != null) {
            val totalSec = finishedVideo.durationSec
            coroutineScope.launch {
                repository.recordWatchProgress(finishedVideo, totalSec, totalSec, state.isAudioOnlyMode)
            }
        }
        playNext()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = coroutineScope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    _playerState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = if (dur > 0L) dur else it.durationMs
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        mediaController?.release()
        mediaController = null
    }
}

package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

    private val trackSelector = DefaultTrackSelector(context)
    private var exoPlayer: ExoPlayer? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        initPlayer()
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

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
                    // Fallback client simulation on stream error (e.g. 403)
                    val fallbackClient = repository.innerTubeEngine.triggerFallback()
                    _playerState.update { it.copy(isBuffering = true, activeClient = fallbackClient) }
                    val current = _playerState.value.currentMedia
                    if (current != null) {
                        // Fallback to audio stream if video stream failed or alternative
                        val fallbackUrl = if (!_playerState.value.isAudioOnlyMode) current.audioStreamUrl else current.streamUrl
                        player.setMediaItem(MediaItem.fromUri(fallbackUrl))
                        player.prepare()
                        player.play()
                    }
                } else {
                    retryCount = 0
                    _playerState.update { it.copy(isBuffering = false, isPlaying = false) }
                }
            }
        })

        exoPlayer = player
    }

    fun playMedia(video: StreamVideo, audioOnly: Boolean = false, queue: List<StreamVideo> = emptyList()) {
        val player = exoPlayer ?: return
        retryCount = 0

        // Record previous track watched progress if switching
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
                activeClient = repository.innerTubeEngine.getActiveClient()
            )
        }

        // Apply audio-only optimization (disable video track to save RAM < 90MB)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, audioOnly || video.isAudioOnly)
            .build()

        val mediaUrl = if (audioOnly || video.isAudioOnly) video.audioStreamUrl else video.streamUrl
        val mediaItem = MediaItem.fromUri(mediaUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekRelative(offsetMs: Long) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _playerState.update { it.copy(speed = speed) }
    }

    fun toggleAudioOnlyMode() {
        val current = _playerState.value.currentMedia ?: return
        val newMode = !_playerState.value.isAudioOnlyMode
        val currentPos = exoPlayer?.currentPosition ?: 0L

        trackSelector.parameters = trackSelector.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, newMode)
            .build()

        _playerState.update { it.copy(isAudioOnlyMode = newMode) }

        val mediaUrl = if (newMode) current.audioStreamUrl else current.streamUrl
        exoPlayer?.setMediaItem(MediaItem.fromUri(mediaUrl), currentPos)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    fun switchClientSpoof(client: ClientType) {
        repository.innerTubeEngine.setClient(client)
        _playerState.update { it.copy(activeClient = client) }
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
                exoPlayer?.let { player ->
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
        exoPlayer?.release()
        exoPlayer = null
    }
}

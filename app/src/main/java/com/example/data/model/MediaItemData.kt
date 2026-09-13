package com.example.data.model

enum class ClientType(val label: String, val userAgent: String) {
    IOS(
        label = "iOS Mock",
        userAgent = "com.google.ios.youtube/19.20.1 (iPhone14,5; U; CPU iOS 17_5 like Mac OS X; vi_VN)"
    ),
    WEB(
        label = "Web Client",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    ),
    ANDROID(
        label = "Android Client",
        userAgent = "com.google.android.youtube/19.20.35 (Linux; U; Android 14; vi_VN)"
    ),
    TVHTML5(
        label = "TVHTML5 Fallback",
        userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko)"
    )
}

data class StreamVideo(
    val id: String,
    val title: String,
    val channelTitle: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val viewCountText: String,
    val publishedText: String,
    val description: String = "",
    val streamUrl: String,
    val audioStreamUrl: String,
    val tags: List<String> = emptyList(),
    val isAudioOnly: Boolean = false,
    val category: String = "All"
) {
    val durationFormatted: String
        get() {
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

data class StreamChannel(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val subscriberCountText: String,
    val description: String = "",
    val customGroup: String? = null
)

data class PlayerState(
    val currentMedia: StreamVideo? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isAudioOnlyMode: Boolean = false,
    val isBuffering: Boolean = false,
    val speed: Float = 1.0f,
    val queue: List<StreamVideo> = emptyList(),
    val currentIndex: Int = 0,
    val activeClient: ClientType = ClientType.IOS
) {
    val progress: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val positionFormatted: String
        get() {
            val sec = (currentPositionMs / 1000).toInt()
            val m = sec / 60
            val s = sec % 60
            return String.format("%d:%02d", m, s)
        }

    val durationFormatted: String
        get() {
            val sec = (durationMs / 1000).toInt()
            val m = sec / 60
            val s = sec % 60
            return String.format("%d:%02d", m, s)
        }
}

data class FeedItem(
    val video: StreamVideo,
    val sourceReason: FeedSourceReason,
    val recScore: Double = 0.0
)

enum class FeedSourceReason(val label: String, val badgeColorHex: Long) {
    SUBSCRIPTION("Kênh đăng ký (50%)", 0xFFFF334B),
    INTERACTION_REC("Gợi ý từ sở thích (30%)", 0xFF8A2BE2),
    TRENDING("Thịnh hành khu vực VN (20%)", 0xFF00B4D8),
    MUSIC_CHARTS("Bảng xếp hạng Âm nhạc", 0xFF10B981)
}

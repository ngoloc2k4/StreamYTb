package com.example.data.network

import com.example.data.model.ClientType
import com.example.data.model.StreamChannel
import com.example.data.model.StreamVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class InnerTubeEngine {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var activeClientType: ClientType = ClientType.IOS

    fun getActiveClient(): ClientType = activeClientType

    fun setClient(clientType: ClientType) {
        activeClientType = clientType
    }

    /**
     * Builds request headers simulating the specified client platform.
     */
    fun buildClientHeaders(client: ClientType): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = client.userAgent
        headers["Accept-Language"] = "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
        headers["Origin"] = "https://www.youtube.com"

        when (client) {
            ClientType.IOS -> {
                headers["X-YouTube-Client-Name"] = "5"
                headers["X-YouTube-Client-Version"] = "19.20.1"
                headers["X-YouTube-Device"] = "iPhone14,5"
            }
            ClientType.WEB -> {
                headers["X-YouTube-Client-Name"] = "1"
                headers["X-YouTube-Client-Version"] = "2.20240501.01.00"
            }
            ClientType.ANDROID -> {
                headers["X-YouTube-Client-Name"] = "3"
                headers["X-YouTube-Client-Version"] = "19.20.35"
            }
            ClientType.TVHTML5 -> {
                headers["X-YouTube-Client-Name"] = "85"
                headers["X-YouTube-Client-Version"] = "7.20240501.08.00"
            }
        }
        return headers
    }

    /**
     * Fallback resolution when encountering 403 or playback restrictions.
     */
    fun triggerFallback(): ClientType {
        activeClientType = when (activeClientType) {
            ClientType.IOS -> ClientType.TVHTML5
            ClientType.TVHTML5 -> ClientType.ANDROID
            ClientType.ANDROID -> ClientType.WEB
            ClientType.WEB -> ClientType.IOS
        }
        return activeClientType
    }

    /**
     * Curated catalog of media videos and songs.
     * Includes real streaming streams (HLS/AAC/MP4/MP3) so the player functions reliably.
     */
    fun getSampleMediaCatalog(): List<StreamVideo> {
        return listOf(
            StreamVideo(
                id = "vn_music_01",
                title = "Đưa Nhau Đi Trốn - Chill Acoustic Live Session",
                channelTitle = "Den Vau Official",
                channelId = "den_vau_channel",
                thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                durationSec = 245,
                viewCountText = "48M lượt xem",
                publishedText = "2 ngày trước",
                description = "Bản thu thanh mộc acoustic với đàn guitar và âm thanh tự nhiên của núi rừng Tây Bắc.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                tags = listOf("Âm nhạc", "V-Pop", "Acoustic", "Chill", "Rap Việt"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "vn_music_02",
                title = "See Tình - Speed Up Tropical Remix",
                channelTitle = "Hoàng Thùy Linh Records",
                channelId = "htl_channel",
                thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
                durationSec = 192,
                viewCountText = "120M lượt xem",
                publishedText = "1 tuần trước",
                description = "Giai điệu lôi cuốn kết hợp nhạc cụ dân tộc hiện đại, lan tỏa khắp châu Á.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                tags = listOf("Âm nhạc", "V-Pop", "Dance", "Remix", "Trending"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "lofi_01",
                title = "Lofi Hip Hop Beats to Relax / Study to [24/7 Deep Focus]",
                channelTitle = "Lofi Girl Vietnam",
                channelId = "lofigirl_vn",
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
                durationSec = 360,
                viewCountText = "2.4M lượt xem",
                publishedText = "Hôm nay",
                description = "Không gian âm thanh thư giãn giúp tập trung làm việc, học tập ban đêm và giảm căng thẳng.",
                streamUrl = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                tags = listOf("Lofi", "Study", "Chill", "Beats", "Thư giãn"),
                isAudioOnly = true,
                category = "Lofi"
            ),
            StreamVideo(
                id = "tech_01",
                title = "Kiến trúc Media3 & Jetpack Compose: Tối ưu 60FPS trên thiết bị RAM thấp",
                channelTitle = "Android Dev VN",
                channelId = "android_dev_vn",
                thumbnailUrl = "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&auto=format&fit=crop&q=80",
                durationSec = 480,
                viewCountText = "35K lượt xem",
                publishedText = "3 ngày trước",
                description = "Phân tích kỹ thuật tách rời ExoPlayer vào Background Service, giảm thiểu Recomposition và tránh OOM.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-25s.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                tags = listOf("Lập trình", "Android", "Kotlin", "Công nghệ", "Tối ưu"),
                isAudioOnly = false,
                category = "Lập trình"
            ),
            StreamVideo(
                id = "podcast_01",
                title = "Hành trình Xây dựng Sản phẩm Open-Source Độc lập",
                channelTitle = "The Saigon Podcast",
                channelId = "saigon_podcast",
                thumbnailUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=600&auto=format&fit=crop&q=80",
                durationSec = 520,
                viewCountText = "98K lượt xem",
                publishedText = "5 ngày trước",
                description = "Trò chuyện cùng các kỹ sư phần mềm về quyền riêng tư dữ liệu, tự do mã nguồn mở và tương lai media.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                tags = listOf("Podcast", "Công nghệ", "Khởi nghiệp", "Tri thức"),
                isAudioOnly = true,
                category = "Podcast"
            ),
            StreamVideo(
                id = "vn_music_03",
                title = "Nấu Ăn Cho Em - Bản Thu Đồng Quê Mộc",
                channelTitle = "Den Vau Official",
                channelId = "den_vau_channel",
                thumbnailUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
                durationSec = 260,
                viewCountText = "65M lượt xem",
                publishedText = "3 tuần trước",
                description = "Âm nhạc sưởi ấm tâm hồn dành cho các em nhỏ vùng cao Điện Biên.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                tags = listOf("Âm nhạc", "V-Pop", "Ý nghĩa", "Acoustic"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "coding_02",
                title = "Xây dựng Thuật toán Recommendation Engine Cục bộ với SQLite",
                channelTitle = "Android Dev VN",
                channelId = "android_dev_vn",
                thumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop&q=80",
                durationSec = 390,
                viewCountText = "22K lượt xem",
                publishedText = "1 tuần trước",
                description = "Giải thuật tính trọng số WatchTime, tỷ lệ bỏ qua và hệ số phân rã thời gian Decay Factor.",
                streamUrl = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                tags = listOf("Lập trình", "Thuật toán", "Database", "Room", "AI Cục bộ"),
                isAudioOnly = false,
                category = "Lập trình"
            ),
            StreamVideo(
                id = "music_trend_04",
                title = "Cắt Đôi Nỗi Sầu - Vinahouse Club Mix 2026",
                channelTitle = "Tăng Duy Tân Records",
                channelId = "tangduytan_channel",
                thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
                durationSec = 210,
                viewCountText = "82M lượt xem",
                publishedText = "2 tuần trước",
                description = "Bản phối sôi động dẫn đầu các bảng xếp hạng âm nhạc điện tử và vũ trường.",
                streamUrl = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
                audioStreamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
                tags = listOf("Âm nhạc", "Vinahouse", "EDM", "Remix", "Trending"),
                isAudioOnly = true,
                category = "Âm nhạc"
            )
        )
    }

    fun getSampleChannels(): List<StreamChannel> {
        return listOf(
            StreamChannel(
                id = "den_vau_channel",
                title = "Den Vau Official",
                thumbnailUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
                subscriberCountText = "5.1M người đăng ký",
                description = "Kênh phát hành âm nhạc chính thức của Đen.",
                customGroup = "Âm nhạc"
            ),
            StreamChannel(
                id = "htl_channel",
                title = "Hoàng Thùy Linh Records",
                thumbnailUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200&auto=format&fit=crop&q=80",
                subscriberCountText = "1.8M người đăng ký",
                description = "Kênh chính thức phát hành các dự án âm nhạc đương đại.",
                customGroup = "Âm nhạc"
            ),
            StreamChannel(
                id = "android_dev_vn",
                title = "Android Dev VN",
                thumbnailUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop&q=80",
                subscriberCountText = "150K người đăng ký",
                description = "Kênh chia sẻ kỹ thuật lập trình Android hiện đại, Clean Architecture và Media3.",
                customGroup = "Lập trình"
            ),
            StreamChannel(
                id = "lofigirl_vn",
                title = "Lofi Girl Vietnam",
                thumbnailUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200&auto=format&fit=crop&q=80",
                subscriberCountText = "850K người đăng ký",
                description = "Những bản nhạc không lời nhẹ nhàng dành cho tâm hồn và sự tập trung.",
                customGroup = "Thư giãn"
            ),
            StreamChannel(
                id = "saigon_podcast",
                title = "The Saigon Podcast",
                thumbnailUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop&q=80",
                subscriberCountText = "320K người đăng ký",
                description = "Góc nhìn công nghệ, đời sống và văn hóa số.",
                customGroup = "Tin tức"
            )
        )
    }
}

package com.example.data.network

import com.example.data.model.ClientType
import com.example.data.model.StreamChannel
import com.example.data.model.StreamVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
     * Builds InnerTube context payload for the given client platform.
     */
    fun buildClientContext(client: ClientType): JSONObject {
        val clientObj = JSONObject()
        when (client) {
            ClientType.IOS -> {
                clientObj.put("clientName", "IOS")
                clientObj.put("clientVersion", "19.20.1")
                clientObj.put("deviceModel", "iPhone14,5")
                clientObj.put("userAgent", client.userAgent)
                clientObj.put("osName", "iOS")
                clientObj.put("osVersion", "17.5.1.21F90")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
            ClientType.WEB -> {
                clientObj.put("clientName", "WEB")
                clientObj.put("clientVersion", "2.20240501.01.00")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
            ClientType.ANDROID -> {
                clientObj.put("clientName", "ANDROID")
                clientObj.put("clientVersion", "19.20.35")
                clientObj.put("androidSdkVersion", 34)
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
            ClientType.TVHTML5 -> {
                clientObj.put("clientName", "TVHTML5")
                clientObj.put("clientVersion", "7.20240501.08.00")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
        }
        return JSONObject().apply { put("client", clientObj) }
    }

    /**
     * Builds request headers simulating the specified client platform.
     */
    fun buildClientHeaders(client: ClientType): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = client.userAgent
        headers["Accept-Language"] = "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
        headers["Origin"] = "https://www.youtube.com"
        headers["Content-Type"] = "application/json"

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
     * Fetches real video streams and metadata from InnerTube /player endpoint.
     * Uses iOS client by default for clean HLS / AAC streams, with automatic fallback.
     */
    suspend fun fetchVideoStreams(
        videoId: String,
        client: ClientType = activeClientType
    ): Result<StreamVideo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("context", buildClientContext(client))
                put("videoId", videoId)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    buildClientHeaders(client).forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (client == ClientType.IOS) {
                    return@withContext fetchVideoStreams(videoId, ClientType.TVHTML5)
                }
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != "OK") {
                if (client == ClientType.IOS) {
                    return@withContext fetchVideoStreams(videoId, ClientType.TVHTML5)
                }
                val reason = playabilityStatus?.optString("reason", "Playability status: $status")
                return@withContext Result.failure(Exception(reason))
            }

            val videoDetails = json.optJSONObject("videoDetails") ?: JSONObject()
            val title = videoDetails.optString("title", "Video")
            val author = videoDetails.optString("author", "Kênh YouTube")
            val channelId = videoDetails.optString("channelId", "")
            val lengthSec = videoDetails.optString("lengthSeconds", "0").toIntOrNull() ?: 0
            val viewCount = videoDetails.optString("viewCount", "0")
            val description = videoDetails.optString("shortDescription", "")

            val thumbnails = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
                thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
            } else {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            val streamingData = json.optJSONObject("streamingData")
            val hlsUrl = streamingData?.optString("hlsManifestUrl", "") ?: ""

            var chosenAudioUrl = ""
            var chosenVideoUrl = ""

            val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                var highestAudioBitrate = 0
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mime = fmt.optString("mimeType", "")
                    val url = fmt.optString("url", "")
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (url.isNotEmpty()) {
                        if (mime.startsWith("audio/") && bitrate > highestAudioBitrate) {
                            highestAudioBitrate = bitrate
                            chosenAudioUrl = url
                        } else if (mime.startsWith("video/") && chosenVideoUrl.isEmpty()) {
                            chosenVideoUrl = url
                        }
                    }
                }
            }

            val formats = streamingData?.optJSONArray("formats")
            if (formats != null && chosenVideoUrl.isEmpty()) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url = fmt.optString("url", "")
                    if (url.isNotEmpty()) {
                        chosenVideoUrl = url
                        if (chosenAudioUrl.isEmpty()) chosenAudioUrl = url
                        break
                    }
                }
            }

            val finalStreamUrl = hlsUrl.ifEmpty { chosenVideoUrl.ifEmpty { chosenAudioUrl } }
            val finalAudioUrl = chosenAudioUrl.ifEmpty { hlsUrl.ifEmpty { finalStreamUrl } }

            if (finalStreamUrl.isEmpty() && finalAudioUrl.isEmpty()) {
                if (client == ClientType.IOS) {
                    return@withContext fetchVideoStreams(videoId, ClientType.TVHTML5)
                }
                return@withContext Result.failure(Exception("No direct stream URL available"))
            }

            Result.success(
                StreamVideo(
                    id = videoId,
                    title = title,
                    channelTitle = author,
                    channelId = channelId,
                    thumbnailUrl = thumbUrl,
                    durationSec = lengthSec,
                    viewCountText = formatViewCount(viewCount),
                    publishedText = "Gần đây",
                    description = description,
                    streamUrl = finalStreamUrl,
                    audioStreamUrl = finalAudioUrl,
                    tags = listOf("YouTube", author),
                    isAudioOnly = false,
                    category = "Âm nhạc"
                )
            )
        } catch (e: Exception) {
            if (client == ClientType.IOS) {
                fetchVideoStreams(videoId, ClientType.TVHTML5)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Searches YouTube videos via /search endpoint.
     */
    suspend fun searchVideos(query: String): List<StreamVideo> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext getSampleMediaCatalog()

        try {
            val requestBody = JSONObject().apply {
                put("context", buildClientContext(ClientType.WEB))
                put("query", trimmed)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    buildClientHeaders(ClientType.WEB).forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext filterSampleCatalog(trimmed)

            val body = response.body?.string() ?: return@withContext filterSampleCatalog(trimmed)
            val json = JSONObject(body)

            val results = mutableListOf<StreamVideo>()
            extractVideoRenderers(json, results)

            if (results.isNotEmpty()) results else filterSampleCatalog(trimmed)
        } catch (e: Exception) {
            filterSampleCatalog(trimmed)
        }
    }

    /**
     * Fetches trending or home feed videos via /browse endpoint.
     */
    suspend fun fetchTrending(browseId: String = "FEtrending"): List<StreamVideo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("context", buildClientContext(ClientType.WEB))
                put("browseId", browseId)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    buildClientHeaders(ClientType.WEB).forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext getSampleMediaCatalog()

            val body = response.body?.string() ?: return@withContext getSampleMediaCatalog()
            val json = JSONObject(body)

            val results = mutableListOf<StreamVideo>()
            extractVideoRenderers(json, results)

            if (results.isNotEmpty()) results else getSampleMediaCatalog()
        } catch (e: Exception) {
            getSampleMediaCatalog()
        }
    }

    /**
     * Recursively traverses JSON structures to extract videoRenderer objects.
     */
    private fun extractVideoRenderers(json: Any?, results: MutableList<StreamVideo>) {
        when (json) {
            is JSONObject -> {
                if (json.has("videoRenderer")) {
                    val vr = json.getJSONObject("videoRenderer")
                    val id = vr.optString("videoId")
                    if (id.isNotEmpty()) {
                        val title = parseRuns(vr.optJSONObject("title"))
                        val owner = vr.optJSONObject("ownerText") ?: vr.optJSONObject("shortBylineText")
                        val channel = parseRuns(owner)
                        val channelId = owner?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId", "") ?: ""
                        val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumb = if (thumbs != null && thumbs.length() > 0) {
                            thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        } else "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        val length = vr.optJSONObject("lengthText")?.optString("simpleText", "0:00") ?: "0:00"
                        val views = vr.optJSONObject("viewCountText")?.optString("simpleText", "") ?: ""
                        val published = vr.optJSONObject("publishedTimeText")?.optString("simpleText", "") ?: ""

                        results.add(
                            StreamVideo(
                                id = id,
                                title = title.ifEmpty { "Video" },
                                channelTitle = channel.ifEmpty { "Kênh YouTube" },
                                channelId = channelId,
                                thumbnailUrl = thumb,
                                durationSec = parseDurationToSeconds(length),
                                viewCountText = views,
                                publishedText = published,
                                description = "",
                                streamUrl = "",
                                audioStreamUrl = "",
                                tags = listOf("YouTube", channel.ifEmpty { "Âm nhạc" }),
                                isAudioOnly = false,
                                category = "All"
                            )
                        )
                    }
                } else {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        extractVideoRenderers(json.opt(keys.next()), results)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    extractVideoRenderers(json.opt(i), results)
                }
            }
        }
    }

    private fun parseRuns(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotEmpty()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.getJSONObject(i).optString("text", ""))
        }
        return sb.toString()
    }

    private fun parseDurationToSeconds(text: String): Int {
        val parts = text.trim().split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }

    private fun formatViewCount(countStr: String): String {
        val count = countStr.toLongOrNull() ?: return countStr
        return when {
            count >= 1_000_000 -> String.format("%.1fM lượt xem", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK lượt xem", count / 1_000.0)
            else -> "$count lượt xem"
        }
    }

    private fun filterSampleCatalog(query: String): List<StreamVideo> {
        val q = query.lowercase()
        return getSampleMediaCatalog().filter {
            it.title.lowercase().contains(q) ||
                    it.channelTitle.lowercase().contains(q) ||
                    it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    /**
     * Curated catalog of media videos and songs used for offline resilience.
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

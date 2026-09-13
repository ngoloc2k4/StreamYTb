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

    companion object {
        const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val BASE_URL = "https://www.youtube.com/youtubei/v1"
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var activeClientType: ClientType = ClientType.ANDROID_VR

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
            ClientType.ANDROID_VR -> {
                clientObj.put("clientName", "ANDROID_VR")
                clientObj.put("clientVersion", "1.56.21")
                clientObj.put("deviceMake", "Oculus")
                clientObj.put("deviceModel", "Quest 3")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
            ClientType.TVHTML5 -> {
                clientObj.put("clientName", "TVHTML5")
                clientObj.put("clientVersion", "7.20240501.08.00")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
            ClientType.WEB -> {
                clientObj.put("clientName", "WEB")
                clientObj.put("clientVersion", "2.20260911.01.00")
                clientObj.put("hl", "vi")
                clientObj.put("gl", "VN")
            }
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
            ClientType.ANDROID -> {
                clientObj.put("clientName", "ANDROID")
                clientObj.put("clientVersion", "19.20.35")
                clientObj.put("androidSdkVersion", 34)
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
            ClientType.ANDROID_VR -> {
                headers["X-YouTube-Client-Name"] = "28"
                headers["X-YouTube-Client-Version"] = "1.56.21"
            }
            ClientType.TVHTML5 -> {
                headers["X-YouTube-Client-Name"] = "85"
                headers["X-YouTube-Client-Version"] = "7.20240501.08.00"
            }
            ClientType.WEB -> {
                headers["X-YouTube-Client-Name"] = "1"
                headers["X-YouTube-Client-Version"] = "2.20260911.01.00"
            }
            ClientType.IOS -> {
                headers["X-YouTube-Client-Name"] = "5"
                headers["X-YouTube-Client-Version"] = "19.20.1"
                headers["X-YouTube-Device"] = "iPhone14,5"
            }
            ClientType.ANDROID -> {
                headers["X-YouTube-Client-Name"] = "3"
                headers["X-YouTube-Client-Version"] = "19.20.35"
            }
        }
        return headers
    }

    /**
     * Fallback resolution when encountering playback restrictions.
     */
    fun triggerFallback(): ClientType {
        activeClientType = when (activeClientType) {
            ClientType.ANDROID_VR -> ClientType.TVHTML5
            ClientType.TVHTML5 -> ClientType.WEB
            ClientType.WEB -> ClientType.ANDROID_VR
            ClientType.IOS -> ClientType.ANDROID_VR
            ClientType.ANDROID -> ClientType.ANDROID_VR
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
                .url("$BASE_URL/player?key=$INNERTUBE_API_KEY")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    buildClientHeaders(client).forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (client != ClientType.TVHTML5) {
                    return@withContext fetchVideoStreams(videoId, ClientType.TVHTML5)
                }
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(responseBody)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != "OK") {
                if (client != ClientType.TVHTML5) {
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

            // Combined progressive formats (Video + Audio muxed together, e.g. itag 18 360p / 22 720p)
            val formats = streamingData?.optJSONArray("formats")
            if (formats != null) {
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

            // Adaptive formats (Highest quality audio AAC / Opus, and HD video)
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

            val finalStreamUrl = chosenVideoUrl.ifEmpty { hlsUrl.ifEmpty { chosenAudioUrl } }
            val finalAudioUrl = chosenAudioUrl.ifEmpty { hlsUrl.ifEmpty { finalStreamUrl } }

            if (finalStreamUrl.isEmpty() && finalAudioUrl.isEmpty()) {
                if (client != ClientType.TVHTML5) {
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
            if (client != ClientType.TVHTML5) {
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
                .url("$BASE_URL/search?key=$INNERTUBE_API_KEY")
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
     * Automatically falls back to popular Vietnamese search query if browse endpoint fails.
     */
    suspend fun fetchTrending(browseId: String = "FEtrending"): List<StreamVideo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("context", buildClientContext(ClientType.WEB))
                put("browseId", browseId)
            }

            val request = Request.Builder()
                .url("$BASE_URL/browse?key=$INNERTUBE_API_KEY")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    buildClientHeaders(ClientType.WEB).forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val results = mutableListOf<StreamVideo>()
                    extractVideoRenderers(json, results)
                    if (results.isNotEmpty()) return@withContext results
                }
            }

            // Fallback: search popular trending videos
            val fallbackResults = searchVideos("trending việt nam")
            if (fallbackResults.isNotEmpty()) fallbackResults else getSampleMediaCatalog()
        } catch (e: Exception) {
            val fallbackResults = searchVideos("trending việt nam")
            if (fallbackResults.isNotEmpty()) fallbackResults else getSampleMediaCatalog()
        }
    }

    /**
     * Fetches real YouTube music tracks for Music screen.
     */
    suspend fun fetchMusic(genreQuery: String = "nhạc trẻ vpop mới nhất"): List<StreamVideo> = withContext(Dispatchers.IO) {
        val tracks = searchVideos(genreQuery)
        if (tracks.isNotEmpty()) {
            tracks.map { it.copy(isAudioOnly = true, category = "Âm nhạc") }
        } else {
            getSampleMediaCatalog().filter { it.isAudioOnly || it.category == "Âm nhạc" || it.category == "Lofi" }
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
     * Curated catalog of media videos and songs with real YouTube IDs for offline resilience.
     */
    fun getSampleMediaCatalog(): List<StreamVideo> {
        return listOf(
            StreamVideo(
                id = "2N4_cW7ZlI8",
                title = "Đen - Nấu ăn cho em ft. PiaLinh (M/V)",
                channelTitle = "Đen Vâu Official",
                channelId = "UCm22OUnhwi5hGqSg_F3i5gQ",
                thumbnailUrl = "https://i.ytimg.com/vi/2N4_cW7ZlI8/hqdefault.jpg",
                durationSec = 265,
                viewCountText = "72M lượt xem",
                publishedText = "1 năm trước",
                description = "Bản thu âm thanh bình dị dành tặng các em nhỏ vùng cao.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Âm nhạc", "V-Pop", "Acoustic", "Đen Vâu"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "b09U5R_M63s",
                title = "Sơn Tùng M-TP | ĐỪNG LÀM TRÁI TIM ANH ĐAU | OFFICIAL MUSIC VIDEO",
                channelTitle = "Sơn Tùng M-TP Official",
                channelId = "UClyArs3IZKA_5pOG_tB_e9w",
                thumbnailUrl = "https://i.ytimg.com/vi/b09U5R_M63s/hqdefault.jpg",
                durationSec = 330,
                viewCountText = "95M lượt xem",
                publishedText = "3 tháng trước",
                description = "Ca khúc âm nhạc mang giai điệu tươi sáng, ngọt ngào của Sơn Tùng M-TP.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Âm nhạc", "V-Pop", "Trending", "Sơn Tùng M-TP"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "jfKfPfyJRdk",
                title = "lofi hip hop radio 📚 - beats to relax/study to",
                channelTitle = "Lofi Girl",
                channelId = "UCSJ4gkVC6NrvII8umztf0Ow",
                thumbnailUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
                durationSec = 360,
                viewCountText = "68M lượt xem",
                publishedText = "Trực tiếp",
                description = "Giai điệu lofi thư giãn giúp tập trung làm việc, học tập ban đêm và giảm căng thẳng.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Lofi", "Study", "Chill", "Beats", "Thư giãn"),
                isAudioOnly = true,
                category = "Lofi"
            ),
            StreamVideo(
                id = "H5v3kku4y6Q",
                title = "Mascara - Chillies x B Ray (Official Music Video)",
                channelTitle = "Chillies",
                channelId = "UCm_xO8wB8BwD5U9a2N4e0Aw",
                thumbnailUrl = "https://i.ytimg.com/vi/H5v3kku4y6Q/hqdefault.jpg",
                durationSec = 285,
                viewCountText = "60M lượt xem",
                publishedText = "2 năm trước",
                description = "Ca khúc đầy cảm xúc kết hợp giữa Chillies và B Ray.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Âm nhạc", "V-Pop", "Chillies", "Indie"),
                isAudioOnly = false,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "7C2z4GqqS5E",
                title = "Bước Qua Nhau - Vũ. (Official MV)",
                channelTitle = "Vũ. Official",
                channelId = "UCvV7U5FwF6_rT4s7E9sT8xA",
                thumbnailUrl = "https://i.ytimg.com/vi/7C2z4GqqS5E/hqdefault.jpg",
                durationSec = 257,
                viewCountText = "110M lượt xem",
                publishedText = "2 năm trước",
                description = "Bản tình ca da diết về những ký ức tuổi trẻ của Hoàng tử Indie Vũ.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Âm nhạc", "Ballad", "Indie", "Vũ"),
                isAudioOnly = true,
                category = "Âm nhạc"
            ),
            StreamVideo(
                id = "dQw4w9WgXcQ",
                title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                channelTitle = "Rick Astley",
                channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                durationSec = 212,
                viewCountText = "1.5B lượt xem",
                publishedText = "14 năm trước",
                description = "The official video for Never Gonna Give You Up by Rick Astley.",
                streamUrl = "",
                audioStreamUrl = "",
                tags = listOf("Music", "Pop", "Classic", "80s"),
                isAudioOnly = false,
                category = "All"
            )
        )
    }

    fun getSampleChannels(): List<StreamChannel> {
        return listOf(
            StreamChannel(
                id = "UCm22OUnhwi5hGqSg_F3i5gQ",
                title = "Đen Vâu Official",
                thumbnailUrl = "https://i.ytimg.com/vi/2N4_cW7ZlI8/hqdefault.jpg",
                subscriberCountText = "5.2M người đăng ký",
                description = "Kênh phát hành âm nhạc chính thức của Đen.",
                customGroup = "Âm nhạc"
            ),
            StreamChannel(
                id = "UClyArs3IZKA_5pOG_tB_e9w",
                title = "Sơn Tùng M-TP Official",
                thumbnailUrl = "https://i.ytimg.com/vi/b09U5R_M63s/hqdefault.jpg",
                subscriberCountText = "10.4M người đăng ký",
                description = "Kênh YouTube chính thức của Sơn Tùng M-TP.",
                customGroup = "Âm nhạc"
            ),
            StreamChannel(
                id = "UCSJ4gkVC6NrvII8umztf0Ow",
                title = "Lofi Girl",
                thumbnailUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
                subscriberCountText = "14.2M người đăng ký",
                description = "Peaceful lofi hip hop radio beats to relax, study and chill.",
                customGroup = "Thư giãn"
            ),
            StreamChannel(
                id = "UCm_xO8wB8BwD5U9a2N4e0Aw",
                title = "Chillies",
                thumbnailUrl = "https://i.ytimg.com/vi/H5v3kku4y6Q/hqdefault.jpg",
                subscriberCountText = "1.1M người đăng ký",
                description = "Official YouTube Channel of Chillies Band Vietnam.",
                customGroup = "Âm nhạc"
            )
        )
    }
}

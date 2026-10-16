package com.mahirlabib.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ApprovedChannel(
    val channelId: String,
    val title: String = channelId,
    val avatarUrl: String = "",
    val subscriberCount: String = ""
)

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val publishedAt: String,
    val description: String = "",
    val channelId: String = "",
    val channelAvatarUrl: String = "",
    val viewCount: String = ""
)

data class YouTubeComment(
    val author: String,
    val text: String,
    val publishedAt: String,
    val authorAvatarUrl: String = "",
    val likeCount: Int = 0
)

internal fun isValidYouTubeChannelId(value: String): Boolean =
    Regex("^UC[A-Za-z0-9_-]{22}$").matches(value.trim())

fun formatRelativeTime(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val clean = isoDate.substringBefore(".Z").substringBefore("Z")
        val date = format.parse(clean) ?: return isoDate
        val diff = System.currentTimeMillis() - date.time
        val seconds = (diff / 1000).coerceAtLeast(0)
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365
        when {
            years > 0 -> "${years}y ago"
            months > 0 -> "${months}mo ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }.getOrDefault(isoDate)
}

object ApprovedChannelStore {
    private const val PREFS = "youtube_local_library"
    private const val KEY = "approved_channels_json"
    private const val LEGACY_KEY = "approved_channel_ids"

    val DEFAULT_CHANNELS = listOf(
        ApprovedChannel("UC_x5XG1OV2P6uZZ5FSM9Ttw", "Google for Developers", "https://yt3.googleusercontent.com/ytc/AIdro_k60j2uF-1200=s88-c-k-c0x00ffffff-no-rj"),
        ApprovedChannel("UCVHFbqXqoYvEWM1Ddxl0QDg", "Android Developers", "https://yt3.googleusercontent.com/ytc/AIdro_n3d25w-1200=s88-c-k-c0x00ffffff-no-rj"),
        ApprovedChannel("UCsTcErHg8oDvUnTzoqsYeNw", "Veritasium", "https://yt3.googleusercontent.com/ytc/AIdro_m9955w-1200=s88-c-k-c0x00ffffff-no-rj"),
        ApprovedChannel("UCBcRF18aGmBrDflS45305iw", "The Verge", "https://yt3.googleusercontent.com/ytc/AIdro_lj55aw-1200=s88-c-k-c0x00ffffff-no-rj")
    )

    fun load(context: Context): List<ApprovedChannel> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw != null) {
            return runCatching {
                val array = JSONArray(raw)
                buildList<ApprovedChannel> {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("channelId")
                        if (isValidYouTubeChannelId(id) && none { it.channelId == id }) {
                            add(
                                ApprovedChannel(
                                    channelId = id,
                                    title = obj.optString("title", id),
                                    avatarUrl = obj.optString("avatarUrl", ""),
                                    subscriberCount = obj.optString("subscriberCount", "")
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        // Migrate legacy string array if exists
        val legacyRaw = prefs.getString(LEGACY_KEY, null)
        if (legacyRaw != null) {
            val migrated = runCatching {
                val array = JSONArray(legacyRaw)
                buildList<ApprovedChannel> {
                    for (i in 0 until array.length()) {
                        val id = array.optString(i)
                        if (isValidYouTubeChannelId(id)) {
                            add(ApprovedChannel(id))
                        }
                    }
                }
            }.getOrDefault(emptyList())
            if (migrated.isNotEmpty()) {
                save(context, migrated)
                return migrated
            }
        }

        // Default seeds for first run
        save(context, DEFAULT_CHANNELS)
        return DEFAULT_CHANNELS
    }

    fun add(context: Context, channelId: String, title: String = channelId, avatarUrl: String = ""): List<ApprovedChannel> {
        val normalized = channelId.trim()
        require(isValidYouTubeChannelId(normalized)) {
            "Enter a valid YouTube channel ID (starts with UC and has 24 characters)."
        }
        val existing = load(context)
        if (existing.any { it.channelId == normalized }) {
            // Update title/avatar if previously had placeholder
            if (title != normalized && existing.first { it.channelId == normalized }.title == normalized) {
                val updated = existing.map {
                    if (it.channelId == normalized) it.copy(title = title, avatarUrl = avatarUrl) else it
                }
                save(context, updated)
                return updated
            }
            return existing
        }
        require(existing.size < 100) { "You can save up to 100 channel IDs on this device." }
        val updated = existing + ApprovedChannel(normalized, title = title, avatarUrl = avatarUrl)
        save(context, updated)
        return updated
    }

    fun updateChannel(context: Context, channel: ApprovedChannel): List<ApprovedChannel> {
        val existing = load(context)
        val updated = existing.map {
            if (it.channelId == channel.channelId) channel else it
        }
        save(context, updated)
        return updated
    }

    fun remove(context: Context, channelId: String): List<ApprovedChannel> {
        val updated = load(context).filterNot { it.channelId == channelId }
        save(context, updated)
        return updated
    }

    fun resetToDefaults(context: Context): List<ApprovedChannel> {
        save(context, DEFAULT_CHANNELS)
        return DEFAULT_CHANNELS
    }

    private fun save(context: Context, channels: List<ApprovedChannel>) {
        val array = JSONArray()
        channels.forEach { channel ->
            array.put(
                JSONObject()
                    .put("channelId", channel.channelId)
                    .put("title", channel.title)
                    .put("avatarUrl", channel.avatarUrl)
                    .put("subscriberCount", channel.subscriberCount)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}

fun isApiKeyConfigured(apiKey: String): Boolean =
    apiKey.isNotBlank() &&
        apiKey != "your_api_key_here" &&
        apiKey != "YOUR_API_KEY_HERE"

object YouTubeDataRepository {

    suspend fun fetchChannelDetails(apiKey: String, channelId: String): ApprovedChannel =
        withContext(Dispatchers.IO) {
            if (!isApiKeyConfigured(apiKey)) {
                return@withContext ApprovedChannel(channelId, channelId)
            }
            runCatching {
                val url = "https://www.googleapis.com/youtube/v3/channels" +
                    "?part=snippet,statistics&id=${encode(channelId)}&key=${encode(apiKey)}"
                val json = getJson(url)
                val items = json.optJSONArray("items") ?: JSONArray()
                if (items.length() > 0) {
                    val item = items.getJSONObject(0)
                    val snippet = item.optJSONObject("snippet")
                    val title = snippet?.optString("title").orEmpty().ifBlank { channelId }
                    val avatarUrl = snippet?.optJSONObject("thumbnails")
                        ?.optJSONObject("default")
                        ?.optString("url").orEmpty()
                    val stats = item.optJSONObject("statistics")
                    val subs = stats?.optString("subscriberCount").orEmpty()
                    ApprovedChannel(channelId, title = title, avatarUrl = avatarUrl, subscriberCount = subs)
                } else {
                    ApprovedChannel(channelId, channelId)
                }
            }.getOrDefault(ApprovedChannel(channelId, channelId))
        }

    suspend fun latestVideos(context: Context, apiKey: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            require(isApiKeyConfigured(apiKey)) {
                "YouTube Data API key is not configured. Add YOUTUBE_API_KEY in the Secrets panel in AI Studio or in the .env file."
            }
            val channels = ApprovedChannelStore.load(context)
            if (channels.isEmpty()) return@withContext emptyList()

            val videos = mutableListOf<YouTubeVideo>()
            for (channel in channels) {
                runCatching {
                    val channelJson = getJson(
                        "https://www.googleapis.com/youtube/v3/channels" +
                            "?part=contentDetails,snippet&id=${encode(channel.channelId)}&key=${encode(apiKey)}"
                    )
                    val channelItems = channelJson.optJSONArray("items") ?: JSONArray()
                    if (channelItems.length() > 0) {
                        val channelItem = channelItems.getJSONObject(0)
                        val snippetObj = channelItem.optJSONObject("snippet")
                        val channelTitle = snippetObj?.optString("title").orEmpty().ifBlank { channel.title }
                        val avatarUrl = snippetObj?.optJSONObject("thumbnails")
                            ?.optJSONObject("default")?.optString("url").orEmpty()

                        if (channel.title == channel.channelId || channel.avatarUrl.isBlank()) {
                            ApprovedChannelStore.updateChannel(
                                context,
                                channel.copy(title = channelTitle, avatarUrl = avatarUrl)
                            )
                        }

                        val uploadsId = channelItem.optJSONObject("contentDetails")
                            ?.optJSONObject("relatedPlaylists")
                            ?.optString("uploads").orEmpty()

                        if (uploadsId.isNotBlank()) {
                            val playlistJson = getJson(
                                "https://www.googleapis.com/youtube/v3/playlistItems" +
                                    "?part=snippet&playlistId=${encode(uploadsId)}&maxResults=50&key=${encode(apiKey)}"
                            )
                            val items = playlistJson.optJSONArray("items") ?: JSONArray()
                            for (i in 0 until items.length()) {
                                val itemSnippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                                val resource = itemSnippet.optJSONObject("resourceId") ?: continue
                                val id = resource.optString("videoId")
                                if (id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
                                    videos += YouTubeVideo(
                                        videoId = id,
                                        title = itemSnippet.optString("title", "YouTube video"),
                                        channelTitle = channelTitle,
                                        publishedAt = itemSnippet.optString("publishedAt", ""),
                                        description = itemSnippet.optString("description", ""),
                                        channelId = channel.channelId,
                                        channelAvatarUrl = avatarUrl
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val result = videos.distinctBy { it.videoId }.sortedByDescending { it.publishedAt }
            LocalVideoStore.save(context, result)
            result
        }

    suspend fun searchChannelVideos(context: Context, apiKey: String, query: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            require(isApiKeyConfigured(apiKey)) {
                "YouTube Data API key is not configured. Add YOUTUBE_API_KEY in the Secrets panel in AI Studio or in the .env file."
            }
            val normalizedQuery = query.trim()
            require(normalizedQuery.isNotBlank()) { "Enter a search term." }
            val channels = ApprovedChannelStore.load(context)
            if (channels.isEmpty()) return@withContext emptyList()

            val results = mutableListOf<YouTubeVideo>()
            for (channel in channels) {
                runCatching {
                    val url = "https://www.googleapis.com/youtube/v3/search" +
                        "?part=snippet&type=video&channelId=${encode(channel.channelId)}" +
                        "&q=${encode(normalizedQuery)}&maxResults=25&order=relevance&key=${encode(apiKey)}"
                    val response = getJson(url)
                    val items = response.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                        if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
                        val snippet = item.optJSONObject("snippet") ?: continue
                        results += YouTubeVideo(
                            videoId = id,
                            title = snippet.optString("title", "YouTube video"),
                            channelTitle = snippet.optString("channelTitle", channel.title),
                            publishedAt = snippet.optString("publishedAt", ""),
                            description = snippet.optString("description", ""),
                            channelId = channel.channelId,
                            channelAvatarUrl = channel.avatarUrl
                        )
                    }
                }
            }
            results.distinctBy { it.videoId }.sortedByDescending { it.publishedAt }
        }

    suspend fun publicComments(videoId: String, apiKey: String): List<YouTubeComment> =
        withContext(Dispatchers.IO) {
            require(isApiKeyConfigured(apiKey)) {
                "YouTube Data API key is not configured. Add YOUTUBE_API_KEY in the Secrets panel in AI Studio or in the .env file."
            }
            require(Regex("^[A-Za-z0-9_-]{11}$").matches(videoId)) { "Invalid YouTube video ID." }
            val url = "https://www.googleapis.com/youtube/v3/commentThreads" +
                "?part=snippet&videoId=${encode(videoId)}&maxResults=25&order=relevance&textFormat=plainText&key=${encode(apiKey)}"
            val response = getJson(url)
            val items = response.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val thread = items.optJSONObject(i) ?: continue
                    val snippet = thread.optJSONObject("snippet")?.optJSONObject("topLevelComment")?.optJSONObject("snippet") ?: continue
                    add(
                        YouTubeComment(
                            author = snippet.optString("authorDisplayName", "YouTube user"),
                            text = snippet.optString("textDisplay", ""),
                            publishedAt = snippet.optString("publishedAt", ""),
                            authorAvatarUrl = snippet.optString("authorProfileImageUrl", ""),
                            likeCount = snippet.optInt("likeCount", 0)
                        )
                    )
                }
            }
        }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
                    .getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "YouTube API request failed (HTTP $status)." })
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

/** Persists public video metadata for offline browsing of the local channel list. */
object LocalVideoStore {
    private const val PREFS = "youtube_local_library"
    private const val KEY = "approved_channel_video_cache"

    fun load(context: Context): List<YouTubeVideo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("videoId")
                    if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
                    add(
                        YouTubeVideo(
                            videoId = id,
                            title = item.optString("title", "YouTube video"),
                            channelTitle = item.optString("channelTitle", "Unknown channel"),
                            publishedAt = item.optString("publishedAt", ""),
                            description = item.optString("description", ""),
                            channelId = item.optString("channelId", ""),
                            channelAvatarUrl = item.optString("channelAvatarUrl", ""),
                            viewCount = item.optString("viewCount", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, videos: List<YouTubeVideo>) {
        val array = JSONArray()
        videos.distinctBy { it.videoId }.take(500).forEach { video ->
            array.put(
                JSONObject()
                    .put("videoId", video.videoId)
                    .put("title", video.title)
                    .put("channelTitle", video.channelTitle)
                    .put("publishedAt", video.publishedAt)
                    .put("description", video.description)
                    .put("channelId", video.channelId)
                    .put("channelAvatarUrl", video.channelAvatarUrl)
                    .put("viewCount", video.viewCount)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}

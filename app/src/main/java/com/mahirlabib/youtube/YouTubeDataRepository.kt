package com.mahirlabib.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ApprovedChannel(
    val channelId: String,
    val title: String = channelId
)

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val publishedAt: String
)

internal fun isValidYouTubeChannelId(value: String): Boolean =
    Regex("^UC[A-Za-z0-9_-]{22}$").matches(value.trim())

object ApprovedChannelStore {
    private const val PREFS = "youtube_local_library"
    private const val KEY = "approved_channel_ids"

    fun load(context: Context): List<ApprovedChannel> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList<ApprovedChannel> {
                for (i in 0 until array.length()) {
                    val id = array.optString(i)
                    if (isValidYouTubeChannelId(id) && none { channel -> channel.channelId == id }) {
                        add(ApprovedChannel(id))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, channelId: String): List<ApprovedChannel> {
        val normalized = channelId.trim()
        require(isValidYouTubeChannelId(normalized)) {
            "Enter a valid YouTube channel ID (starts with UC and has 24 characters)."
        }
        val existing = load(context)
        if (existing.any { it.channelId == normalized }) return existing
        require(existing.size < 100) { "You can save up to 100 channel IDs on this device." }
        val updated = existing + ApprovedChannel(normalized)
        save(context, updated)
        return updated
    }

    fun remove(context: Context, channelId: String): List<ApprovedChannel> {
        val updated = load(context).filterNot { it.channelId == channelId }
        save(context, updated)
        return updated
    }

    private fun save(context: Context, channels: List<ApprovedChannel>) {
        val array = JSONArray()
        channels.forEach { array.put(it.channelId) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}

/** Uses the official YouTube Data API v3; API key is supplied at build time, never hard-coded. */
object YouTubeDataRepository {
    suspend fun latestVideos(context: Context, apiKey: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) {
                "YouTube Data API key is not configured. Add YOUTUBE_API_KEY as a GitHub Actions secret and rebuild."
            }
            val channels = ApprovedChannelStore.load(context)
            if (channels.isEmpty()) return@withContext emptyList()

            val videos = mutableListOf<YouTubeVideo>()
            for (channel in channels) {
                val channelJson = getJson(
                    "https://www.googleapis.com/youtube/v3/channels" +
                        "?part=contentDetails,snippet&id=${encode(channel.channelId)}&key=${encode(apiKey)}"
                )
                val channelItems = channelJson.optJSONArray("items") ?: JSONArray()
                if (channelItems.length() == 0) continue
                val channelItem = channelItems.getJSONObject(0)
                val channelTitle = channelItem.optJSONObject("snippet")
                    ?.optString("title").orEmpty().ifBlank { channel.channelId }
                val uploadsId = channelItem.optJSONObject("contentDetails")
                    ?.optJSONObject("relatedPlaylists")
                    ?.optString("uploads").orEmpty()
                if (uploadsId.isBlank()) continue

                val playlistJson = getJson(
                    "https://www.googleapis.com/youtube/v3/playlistItems" +
                        "?part=snippet&playlistId=${encode(uploadsId)}&maxResults=50&key=${encode(apiKey)}"
                )
                val items = playlistJson.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                    val resource = snippet.optJSONObject("resourceId") ?: continue
                    val id = resource.optString("videoId")
                    if (id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
                        videos += YouTubeVideo(
                            videoId = id,
                            title = snippet.optString("title", "YouTube video"),
                            channelTitle = channelTitle,
                            publishedAt = snippet.optString("publishedAt", "")
                        )
                    }
                }
            }
            val result = videos.distinctBy { it.videoId }.sortedByDescending { it.publishedAt }
            LocalVideoStore.save(context, result)
            result
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
                    add(YouTubeVideo(
                        videoId = id,
                        title = item.optString("title", "YouTube video"),
                        channelTitle = item.optString("channelTitle", "Unknown channel"),
                        publishedAt = item.optString("publishedAt", "")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, videos: List<YouTubeVideo>) {
        val array = JSONArray()
        videos.distinctBy { it.videoId }.take(500).forEach { video ->
            array.put(JSONObject()
                .put("videoId", video.videoId)
                .put("title", video.title)
                .put("channelTitle", video.channelTitle)
                .put("publishedAt", video.publishedAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}

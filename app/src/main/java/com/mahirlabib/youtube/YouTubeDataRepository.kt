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

object ApprovedChannelStore {
    private const val PREFS = "youtube_local_library"
    private const val KEY = "approved_channel_ids"
    private val channelIdPattern = Regex("^UC[A-Za-z0-9_-]{22}$")

    fun load(context: Context): List<ApprovedChannel> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val id = array.optString(i)
                    if (channelIdPattern.matches(id) && none { it.channelId == id }) {
                        add(ApprovedChannel(id))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, channelId: String): List<ApprovedChannel> {
        val normalized = channelId.trim()
        require(channelIdPattern.matches(normalized)) {
            "Enter a valid YouTube channel ID (starts with UC and has 24 characters)."
        }
        val updated = (load(context).filterNot { it.channelId == normalized } +
            ApprovedChannel(normalized)).take(100)
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
                "YouTube Data API key is not configured. Set YOUTUBE_API_KEY in GitHub Actions secrets or local Gradle properties."
            }
            val channels = ApprovedChannelStore.load(context)
            if (channels.isEmpty()) return@withContext emptyList()

            channels.flatMap { channel ->
                runCatching {
                    val channelJson = getJson(
                        "https://www.googleapis.com/youtube/v3/channels" +
                            "?part=contentDetails,snippet&id=${encode(channel.channelId)}&key=${encode(apiKey)}"
                    )
                    val channelItems = channelJson.optJSONArray("items") ?: JSONArray()
                    if (channelItems.length() == 0) return@runCatching emptyList()
                    val channelItem = channelItems.getJSONObject(0)
                    val channelTitle = channelItem.optJSONObject("snippet")
                        ?.optString("title").orEmpty().ifBlank { channel.channelId }
                    val uploadsId = channelItem.optJSONObject("contentDetails")
                        ?.optJSONObject("relatedPlaylists")
                        ?.optString("uploads").orEmpty()
                    if (uploadsId.isBlank()) return@runCatching emptyList()

                    val playlistJson = getJson(
                        "https://www.googleapis.com/youtube/v3/playlistItems" +
                            "?part=snippet&playlistId=${encode(uploadsId)}&maxResults=10&key=${encode(apiKey)}"
                    )
                    val items = playlistJson.optJSONArray("items") ?: JSONArray()
                    buildList {
                        for (i in 0 until items.length()) {
                            val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                            val resource = snippet.optJSONObject("resourceId") ?: continue
                            val id = resource.optString("videoId")
                            if (id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
                                add(
                                    YouTubeVideo(
                                        videoId = id,
                                        title = snippet.optString("title", "YouTube video"),
                                        channelTitle = channelTitle,
                                        publishedAt = snippet.optString("publishedAt", "")
                                    )
                                )
                            }
                        }
                    }
                }.getOrElse { emptyList() }
            }.distinctBy { it.videoId }.sortedByDescending { it.publishedAt }
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

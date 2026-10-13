package com.mahirlabib.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores recently opened videos on this device; no account or server is required. */
data class WatchedVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val watchedAt: Long
)

object WatchHistoryStore {
    private const val PREFS_NAME = "youtube_local_library"
    private const val HISTORY_KEY = "watch_history"
    private const val MAX_ITEMS = 50

    fun load(context: Context): List<WatchedVideo> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("videoId")
                    if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
                    add(
                        WatchedVideo(
                            videoId = id,
                            title = item.optString("title", "YouTube video"),
                            channel = item.optString("channel", "Unknown channel"),
                            watchedAt = item.optLong("watchedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, videoId: String, title: String, channel: String): List<WatchedVideo> {
        require(videoId.matches(Regex("^[A-Za-z0-9_-]{11}$"))) { "Invalid YouTube video ID" }
        val updated = (listOf(WatchedVideo(videoId, title, channel, System.currentTimeMillis())) +
            load(context).filterNot { it.videoId == videoId })
            .take(MAX_ITEMS)
        val array = JSONArray()
        updated.forEach { video ->
            array.put(
                JSONObject()
                    .put("videoId", video.videoId)
                    .put("title", video.title)
                    .put("channel", video.channel)
                    .put("watchedAt", video.watchedAt)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, array.toString())
            .apply()
        return updated
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(HISTORY_KEY)
            .apply()
    }
}

package com.mahirlabib.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores recently opened videos and user playlists on this device; persistent across restarts. */
data class WatchedVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val watchedAt: Long,
    val description: String = ""
)

object WatchHistoryStore {
    private const val PREFS_NAME = "youtube_local_library"
    private const val HISTORY_KEY = "watch_history"
    private const val LIKED_KEY = "liked_video_ids"
    private const val WATCHLIST_KEY = "saved_watchlist"
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
                            watchedAt = item.optLong("watchedAt", 0L),
                            description = item.optString("description", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, videoId: String, title: String, channel: String, description: String = ""): List<WatchedVideo> {
        require(videoId.matches(Regex("^[A-Za-z0-9_-]{11}$"))) { "Invalid YouTube video ID" }
        val updated = (listOf(WatchedVideo(videoId, title, channel, System.currentTimeMillis(), description)) +
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
                    .put("description", video.description)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, array.toString())
            .apply()
        return updated
    }

    fun getLastWatched(context: Context): WatchedVideo? = load(context).firstOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(HISTORY_KEY)
            .apply()
    }

    // Liked videos local persistence
    fun isLiked(context: Context, videoId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(LIKED_KEY, emptySet()) ?: emptySet()
        return set.contains(videoId)
    }

    fun toggleLike(context: Context, videoId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = (prefs.getStringSet(LIKED_KEY, emptySet()) ?: emptySet()).toMutableSet()
        val isNowLiked = if (set.contains(videoId)) {
            set.remove(videoId)
            false
        } else {
            set.add(videoId)
            true
        }
        prefs.edit().putStringSet(LIKED_KEY, set).apply()
        return isNowLiked
    }

    // Watchlist / Save for Later persistence
    fun loadWatchlist(context: Context): List<WatchedVideo> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(WATCHLIST_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("videoId")
                    if (id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
                        add(
                            WatchedVideo(
                                videoId = id,
                                title = obj.optString("title", "YouTube video"),
                                channel = obj.optString("channel", "Unknown"),
                                watchedAt = obj.optLong("watchedAt", 0L),
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun isInWatchlist(context: Context, videoId: String): Boolean {
        return loadWatchlist(context).any { it.videoId == videoId }
    }

    fun toggleWatchlist(context: Context, video: WatchedVideo): Boolean {
        val existing = loadWatchlist(context).toMutableList()
        val exists = existing.any { it.videoId == video.videoId }
        if (exists) {
            existing.removeAll { it.videoId == video.videoId }
        } else {
            existing.add(0, video)
        }
        val array = JSONArray()
        existing.take(50).forEach { item ->
            array.put(
                JSONObject()
                    .put("videoId", item.videoId)
                    .put("title", item.title)
                    .put("channel", item.channel)
                    .put("watchedAt", item.watchedAt)
                    .put("description", item.description)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(WATCHLIST_KEY, array.toString())
            .apply()
        return !exists
    }
}

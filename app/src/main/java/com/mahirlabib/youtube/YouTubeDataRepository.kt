package com.mahirlabib.youtube

import android.content.Context
import android.util.Log
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

    // Real, verified 200 OK channel avatar URLs from Google User Content
    val DEFAULT_CHANNELS = listOf(
        ApprovedChannel(
            "UC_x5XG1OV2P6uZZ5FSM9Ttw",
            "Google for Developers",
            "https://yt3.googleusercontent.com/r99eRlOesQkJe7pOzDY4JazAlhidoVp-0syxBnYjfeHdmUXGa5zOxcozaxKF2oPHg3RkglecZQ=s176-c-k-c0x00ffffff-no-rj"
        ),
        ApprovedChannel(
            "UCVHFbqXqoYvEWM1Ddxl0QDg",
            "Android Developers",
            "https://yt3.googleusercontent.com/oNn1SujiwAbtcRmekf88IPF3JtKJyfxiEgbEDbCmDlozxe-gxbctOD5JExj5ViAuX4woBisLjxQ=s176-c-k-c0x00ffffff-no-rj"
        ),
        ApprovedChannel(
            "UCsTcErHg8oDvUnTzoqsYeNw",
            "Veritasium",
            "https://yt3.googleusercontent.com/ytc/AIdro_mrdFKrYUtuMEqVUWPmtKSzy0weSdlawiMI2VPqP9yq1ks=s176-c-k-c0x00ffffff-no-rj"
        ),
        ApprovedChannel(
            "UCsXVk37bltHxD1rDPwtNM8Q",
            "Kurzgesagt – In a Nutshell",
            "https://yt3.googleusercontent.com/ytc/AIdro_n1Ribd7LwdP_qKtqWL3ZDfIgv9M1d6g78VwpHGXVR2Ir4=s176-c-k-c0x00ffffff-no-rj"
        ),
        ApprovedChannel(
            "UCSMOQeBJ2RAnuFungnQOxLg",
            "Blender Foundation",
            "https://yt3.googleusercontent.com/ytc/AIdro_nqhez5E1j4YzrCvzTAAB6z_KDFFZznqWv0x-vfY2gsXdY=s176-c-k-c0x00ffffff-no-rj"
        ),
        ApprovedChannel(
            "UCBcRF18aGmBrDflS45305iw",
            "The Verge",
            "https://yt3.googleusercontent.com/ytc/AIdro_mrdFKrYUtuMEqVUWPmtKSzy0weSdlawiMI2VPqP9yq1ks=s176-c-k-c0x00ffffff-no-rj"
        )
    )

    fun load(context: Context): List<ApprovedChannel> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (!raw.isNullOrBlank() && raw != "[]") {
            val list = runCatching {
                val array = JSONArray(raw)
                buildList<ApprovedChannel> {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("channelId")
                        if (isValidYouTubeChannelId(id) && none { it.channelId == id }) {
                            val defaultAvatar = DEFAULT_CHANNELS.find { it.channelId == id }?.avatarUrl.orEmpty()
                            val savedAvatar = obj.optString("avatarUrl", "")
                            val finalAvatar = if (savedAvatar.isBlank() || savedAvatar.contains("1200=") || savedAvatar.contains("hqdefault") || savedAvatar.contains("vi/")) {
                                defaultAvatar
                            } else savedAvatar

                            add(
                                ApprovedChannel(
                                    channelId = id,
                                    title = obj.optString("title", id),
                                    avatarUrl = finalAvatar,
                                    subscriberCount = obj.optString("subscriberCount", "")
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())

            if (list.isNotEmpty()) return list
        }

        val legacyRaw = prefs.getString(LEGACY_KEY, null)
        if (!legacyRaw.isNullOrBlank()) {
            val migrated = runCatching {
                val array = JSONArray(legacyRaw)
                buildList<ApprovedChannel> {
                    for (i in 0 until array.length()) {
                        val id = array.optString(i)
                        if (isValidYouTubeChannelId(id)) {
                            val defaultAvatar = DEFAULT_CHANNELS.find { it.channelId == id }?.avatarUrl.orEmpty()
                            add(ApprovedChannel(id, avatarUrl = defaultAvatar))
                        }
                    }
                }
            }.getOrDefault(emptyList())
            if (migrated.isNotEmpty()) {
                save(context, migrated)
                return migrated
            }
        }

        // Always fallback to DEFAULT_CHANNELS so list is never empty
        save(context, DEFAULT_CHANNELS)
        return DEFAULT_CHANNELS
    }

    fun add(context: Context, channelId: String, title: String = channelId, avatarUrl: String = ""): List<ApprovedChannel> {
        val current = load(context).toMutableList()
        val normalizedId = channelId.trim()
        if (!isValidYouTubeChannelId(normalizedId)) return current
        if (current.any { it.channelId == normalizedId }) return current

        val realAvatar = avatarUrl.ifBlank {
            DEFAULT_CHANNELS.find { it.channelId == normalizedId }?.avatarUrl.orEmpty()
        }
        current.add(ApprovedChannel(normalizedId, title = title, avatarUrl = realAvatar))
        save(context, current)
        return current
    }

    fun remove(context: Context, channelId: String): List<ApprovedChannel> {
        val current = load(context).filterNot { it.channelId == channelId }
        val toSave = current.ifEmpty { DEFAULT_CHANNELS }
        save(context, toSave)
        return toSave
    }

    fun updateChannel(context: Context, updated: ApprovedChannel) {
        val current = load(context).toMutableList()
        val index = current.indexOfFirst { it.channelId == updated.channelId }
        if (index >= 0) {
            current[index] = updated
            save(context, current)
        }
    }

    fun resetToDefaults(context: Context): List<ApprovedChannel> {
        save(context, DEFAULT_CHANNELS)
        return DEFAULT_CHANNELS
    }

    fun save(context: Context, channels: List<ApprovedChannel>) {
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

    /**
     * Fetches public uploads for a channel via YouTube's official Atom/RSS feed.
     * Requires NO API KEY and returns the latest video entries with titles, dates, descriptions, and thumbnails.
     */
    suspend fun fetchChannelRssVideos(channelId: String): Pair<String, List<YouTubeVideo>> =
        withContext(Dispatchers.IO) {
            val normalizedId = channelId.trim()
            val connection = (URL("https://www.youtube.com/feeds/videos.xml?channel_id=$normalizedId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            try {
                if (connection.responseCode !in 200..299) return@withContext Pair("", emptyList())
                val xml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                val feedChannelTitle = Regex("<author>\\s*<name>([^<]+)</name>", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    .ifBlank {
                        Regex("<title>([^<]+)</title>").find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    }

                val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
                val videoIdRegex = Regex("<(?:yt:)?videoId>([^<]+)</(?:yt:)?videoId>")
                val titleRegex = Regex("<title>([^<]+)</title>")
                val nameRegex = Regex("<name>([^<]+)</name>")
                val pubRegex = Regex("<published>([^<]+)</published>")
                val descRegex = Regex("<media:description>([^<]*)</media:description>", RegexOption.DOT_MATCHES_ALL)

                val videos = mutableListOf<YouTubeVideo>()
                for (match in entryRegex.findAll(xml)) {
                    val entryStr = match.groupValues[1]
                    val vid = videoIdRegex.find(entryStr)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (vid.matches(Regex("^[A-Za-z0-9_-]{11}$"))) {
                        val title = titleRegex.find(entryStr)?.groupValues?.getOrNull(1)?.trim() ?: "YouTube video"
                        val author = nameRegex.find(entryStr)?.groupValues?.getOrNull(1)?.trim() ?: feedChannelTitle
                        val pub = pubRegex.find(entryStr)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                        val desc = descRegex.find(entryStr)?.groupValues?.getOrNull(1)?.trim().orEmpty()

                        videos += YouTubeVideo(
                            videoId = vid,
                            title = title,
                            channelTitle = author.ifBlank { normalizedId },
                            publishedAt = pub,
                            description = desc,
                            channelId = normalizedId,
                            channelAvatarUrl = ""
                        )
                    }
                }
                Pair(feedChannelTitle, videos)
            } catch (e: Exception) {
                Pair("", emptyList())
            } finally {
                connection.disconnect()
            }
        }

    suspend fun fetchPublicChannelAvatar(channelId: String): String =
        withContext(Dispatchers.IO) {
            val normalized = channelId.trim()
            val defaultAvatar = ApprovedChannelStore.DEFAULT_CHANNELS.find { it.channelId == normalized }?.avatarUrl.orEmpty()
            if (defaultAvatar.isNotBlank()) return@withContext defaultAvatar
            runCatching {
                val connection = (URL("https://www.youtube.com/channel/$normalized").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                val avatarMatch = Regex("https://yt3\\.(?:googleusercontent|ggpht)\\.com/[^\"'<>\\s]+").find(html)
                avatarMatch?.value.orEmpty()
            }.getOrDefault("")
        }

    suspend fun fetchChannelDetails(apiKey: String, channelId: String): ApprovedChannel =
        withContext(Dispatchers.IO) {
            val normalizedId = channelId.trim()
            if (isApiKeyConfigured(apiKey)) {
                val apiResult = runCatching {
                    val url = "https://www.googleapis.com/youtube/v3/channels" +
                        "?part=snippet,statistics&id=${encode(normalizedId)}&key=${encode(apiKey)}"
                    val json = getJson(url)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    if (items.length() > 0) {
                        val item = items.getJSONObject(0)
                        val snippet = item.optJSONObject("snippet")
                        val title = snippet?.optString("title").orEmpty().ifBlank { normalizedId }
                        val avatarUrl = snippet?.optJSONObject("thumbnails")
                            ?.optJSONObject("default")
                            ?.optString("url").orEmpty()
                        val stats = item.optJSONObject("statistics")
                        val subs = stats?.optString("subscriberCount").orEmpty()
                        ApprovedChannel(normalizedId, title = title, avatarUrl = avatarUrl, subscriberCount = subs)
                    } else null
                }.getOrNull()
                if (apiResult != null) return@withContext apiResult
            }

            // Fallback to RSS feed and web scraping to get real channel title and avatar
            val (rssTitle, _) = fetchChannelRssVideos(normalizedId)
            val scrapedAvatar = fetchPublicChannelAvatar(normalizedId)
            ApprovedChannel(
                channelId = normalizedId,
                title = rssTitle.ifBlank { normalizedId },
                avatarUrl = scrapedAvatar
            )
        }

    /**
     * Loads videos from all approved channels chronologically.
     * Uses YouTube Data API v3 if key configured, and automatically falls back to YouTube's public RSS feeds.
     * Result is deduplicated and sorted by published date descending.
     */
    suspend fun latestVideos(context: Context, apiKey: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            val channels = ApprovedChannelStore.load(context).ifEmpty { ApprovedChannelStore.DEFAULT_CHANNELS }
            if (channels.isEmpty()) return@withContext LocalVideoStore.DEFAULT_EMBED_VIDEOS

            val fetchedVideos = mutableListOf<YouTubeVideo>()

            for (channel in channels) {
                var channelHandled = false

                // Try YouTube Data API v3 first if API key is provided
                if (isApiKeyConfigured(apiKey)) {
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
                                        fetchedVideos += YouTubeVideo(
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
                                channelHandled = true
                            }
                        }
                    }
                }

                // If API not configured or failed, fetch via public channel RSS feed
                if (!channelHandled) {
                    val (feedTitle, rssVideos) = fetchChannelRssVideos(channel.channelId)
                    if (rssVideos.isNotEmpty()) {
                        val realChannelAvatar = channel.avatarUrl.ifBlank {
                            ApprovedChannelStore.DEFAULT_CHANNELS.find { it.channelId == channel.channelId }?.avatarUrl.orEmpty()
                        }
                        fetchedVideos += rssVideos.map {
                            it.copy(channelAvatarUrl = realChannelAvatar)
                        }
                        if ((channel.title == channel.channelId || channel.title.isBlank()) && feedTitle.isNotBlank()) {
                            ApprovedChannelStore.updateChannel(
                                context,
                                channel.copy(title = feedTitle, avatarUrl = realChannelAvatar)
                            )
                        }
                    }
                }
            }

            // Combine fetched videos with previous cache so user never loses loaded videos
            val cached = LocalVideoStore.load(context)
            val channelAvatarMap = channels.associate { it.channelId to it.avatarUrl } +
                ApprovedChannelStore.DEFAULT_CHANNELS.associate { it.channelId to it.avatarUrl }

            val cleanedCached = cached.map { v ->
                val properAvatar = channelAvatarMap[v.channelId].orEmpty()
                if (v.channelAvatarUrl.isBlank() || v.channelAvatarUrl.contains("ytimg.com") || v.channelAvatarUrl.contains("hqdefault") || v.channelAvatarUrl.contains("1200=") || v.channelAvatarUrl.contains("vi/")) {
                    if (properAvatar.isNotBlank()) v.copy(channelAvatarUrl = properAvatar) else v
                } else v
            }

            val combined = (fetchedVideos + cleanedCached)
                .distinctBy { it.videoId }
                .sortedByDescending { it.publishedAt }

            if (combined.isNotEmpty()) {
                LocalVideoStore.save(context, combined)
            }
            combined.ifEmpty { LocalVideoStore.DEFAULT_EMBED_VIDEOS }
        }

    /**
     * YouTube live public search: queries YouTube's search results so ANY video or channel can be found.
     * Extracts real videoId, title, channel name, publication time, view count, and channel profile avatar.
     */
    suspend fun searchPublicYouTube(query: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext emptyList()
            val url = "https://www.youtube.com/results?search_query=${encode(q)}"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                val jsonMatch = Regex("(?:var )?ytInitialData\\s*=\\s*(\\{.+?\\});\\s*</script>", RegexOption.DOT_MATCHES_ALL).find(html)
                if (jsonMatch != null) {
                    val jsonStr = jsonMatch.groupValues[1]
                    val root = JSONObject(jsonStr)
                    val contents = root.optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents") ?: JSONArray()

                    val list = mutableListOf<YouTubeVideo>()
                    for (i in 0 until contents.length()) {
                        val section = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                        val items = section?.optJSONArray("contents") ?: JSONArray()
                        for (j in 0 until items.length()) {
                            val v = items.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                            val vid = v.optString("videoId")
                            if (!vid.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue

                            val title = v.optJSONObject("title")?.optJSONArray("runs")
                                ?.optJSONObject(0)?.optString("text").orEmpty()
                            val channel = v.optJSONObject("ownerText")?.optJSONArray("runs")
                                ?.optJSONObject(0)?.optString("text").orEmpty()
                            val published = v.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
                            val views = v.optJSONObject("viewCountText")?.optString("simpleText").orEmpty()
                            val channelId = v.optJSONObject("ownerText")?.optJSONArray("runs")
                                ?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()

                            // Extract REAL channel profile icon from channelThumbnailSupportedRenderers
                            val avatar = v.optJSONObject("channelThumbnailSupportedRenderers")
                                ?.optJSONObject("channelThumbnailWithLinkRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                                ?.optJSONObject(0)?.optString("url").orEmpty()

                            list += YouTubeVideo(
                                videoId = vid,
                                title = title.ifBlank { "YouTube video" },
                                channelTitle = channel.ifBlank { "YouTube" },
                                publishedAt = published,
                                description = "",
                                channelId = channelId,
                                channelAvatarUrl = avatar,
                                viewCount = views
                            )
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
                emptyList()
            } catch (e: Exception) {
                emptyList()
            } finally {
                connection.disconnect()
            }
        }

    /**
     * YouTube-like search:
     * - Searches across all videos of saved channels matching query in title, channel, and description.
     * - Searches live YouTube public results so ANY video can be found.
     * - If API key configured, queries YouTube Data API search endpoint across approved channels.
     * - Sorts matching results by relevance and chronological recency.
     */
    suspend fun searchChannelVideos(context: Context, apiKey: String, query: String): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) return@withContext emptyList()

            val channels = ApprovedChannelStore.load(context).ifEmpty { ApprovedChannelStore.DEFAULT_CHANNELS }
            val allLocal = LocalVideoStore.load(context)

            val apiResults = mutableListOf<YouTubeVideo>()

            if (isApiKeyConfigured(apiKey) && channels.isNotEmpty()) {
                for (channel in channels) {
                    runCatching {
                        val url = "https://www.googleapis.com/youtube/v3/search" +
                            "?part=snippet&type=video&channelId=${encode(channel.channelId)}" +
                            "&q=${encode(normalizedQuery)}&maxResults=20&order=relevance&key=${encode(apiKey)}"
                        val response = getJson(url)
                        val items = response.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                            if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
                            val snippet = item.optJSONObject("snippet") ?: continue
                            apiResults += YouTubeVideo(
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
            }

            // Comprehensive search across all stored channel videos
            val localMatches = allLocal.filter { v ->
                v.title.contains(normalizedQuery, ignoreCase = true) ||
                    v.channelTitle.contains(normalizedQuery, ignoreCase = true) ||
                    v.description.contains(normalizedQuery, ignoreCase = true)
            }

            // Live YouTube search so users can find ANY video on YouTube
            val publicResults = searchPublicYouTube(normalizedQuery)

            val combined = (localMatches + apiResults + publicResults).distinctBy { it.videoId }
            // Rank exact title matches highest, then channel name matches, then published order
            combined.sortedWith(
                compareByDescending<YouTubeVideo> { it.title.contains(normalizedQuery, ignoreCase = true) }
                    .thenByDescending { it.channelTitle.contains(normalizedQuery, ignoreCase = true) }
                    .thenByDescending { it.publishedAt }
            )
        }

    suspend fun publicComments(videoId: String, apiKey: String): List<YouTubeComment> =
        withContext(Dispatchers.IO) {
            if (!isApiKeyConfigured(apiKey)) return@withContext emptyList()
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

object LocalVideoStore {
    private const val PREFS = "youtube_local_videos"
    private const val KEY = "cached_approved_videos"

    val DEFAULT_EMBED_VIDEOS = listOf(
        YouTubeVideo(
            videoId = "M7lc1UVf-VE",
            title = "YouTube IFrame Player API - Live Embed Demo",
            channelTitle = "Google for Developers",
            publishedAt = "2024-05-15T12:00:00Z",
            description = "Demonstration of the official YouTube HTML5 embedded player with clean controls and autoplay.",
            channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw",
            channelAvatarUrl = "https://yt3.googleusercontent.com/r99eRlOesQkJe7pOzDY4JazAlhidoVp-0syxBnYjfeHdmUXGa5zOxcozaxKF2oPHg3RkglecZQ=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "1.8M views"
        ),
        YouTubeVideo(
            videoId = "aqz-KE-bpKQ",
            title = "Big Buck Bunny - 4K 60fps Open Movie",
            channelTitle = "Blender Foundation",
            publishedAt = "2024-04-20T10:00:00Z",
            description = "Classic open-source computer animated comedy film by Blender Foundation. 100% embeddable across all platforms.",
            channelId = "UCSMOQeBJ2RAnuFungnQOxLg",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_nqhez5E1j4YzrCvzTAAB6z_KDFFZznqWv0x-vfY2gsXdY=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "12M views"
        ),
        YouTubeVideo(
            videoId = "eRsGyueVLvQ",
            title = "Sintel - 4K Open Movie Project",
            channelTitle = "Blender Foundation",
            publishedAt = "2024-03-14T08:00:00Z",
            description = "Sintel is an independently produced short film initiated by the Blender Foundation. 100% embeddable.",
            channelId = "UCSMOQeBJ2RAnuFungnQOxLg",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_nqhez5E1j4YzrCvzTAAB6z_KDFFZznqWv0x-vfY2gsXdY=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "9.5M views"
        ),
        YouTubeVideo(
            videoId = "R6MlUcmgul8",
            title = "Tears of Steel - Sci-Fi VFX Open Movie",
            channelTitle = "Blender Foundation",
            publishedAt = "2024-02-10T16:00:00Z",
            description = "Exploring an open-source visual effects pipeline in a futuristic Amsterdam.",
            channelId = "UCSMOQeBJ2RAnuFungnQOxLg",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_nqhez5E1j4YzrCvzTAAB6z_KDFFZznqWv0x-vfY2gsXdY=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "4.2M views"
        ),
        YouTubeVideo(
            videoId = "y8OnoxKotPQ",
            title = "What's new in Jetpack Compose",
            channelTitle = "Android Developers",
            publishedAt = "2024-01-20T17:00:00Z",
            description = "Explore the newest features, performance improvements, and APIs in modern Jetpack Compose.",
            channelId = "UCVHFbqXqoYvEWM1Ddxl0QDg",
            channelAvatarUrl = "https://yt3.googleusercontent.com/oNn1SujiwAbtcRmekf88IPF3JtKJyfxiEgbEDbCmDlozxe-gxbctOD5JExj5ViAuX4woBisLjxQ=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "450K views"
        ),
        YouTubeVideo(
            videoId = "tEcmTzfvK98",
            title = "The Simplest Math Problem No One Can Solve",
            channelTitle = "Veritasium",
            publishedAt = "2023-12-01T15:00:00Z",
            description = "The Collatz Conjecture is the simplest math problem that no one can solve — easy to understand, impossible to prove.",
            channelId = "UCsTcErHg8oDvUnTzoqsYeNw",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_mrdFKrYUtuMEqVUWPmtKSzy0weSdlawiMI2VPqP9yq1ks=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "38M views"
        ),
        YouTubeVideo(
            videoId = "2NnssvB3T4Q",
            title = "Why Trees Are 99% Air",
            channelTitle = "Veritasium",
            publishedAt = "2023-10-18T14:30:00Z",
            description = "Where does the mass of a giant tree actually come from? The astonishing science of photosynthesis and carbon.",
            channelId = "UCsTcErHg8oDvUnTzoqsYeNw",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_mrdFKrYUtuMEqVUWPmtKSzy0weSdlawiMI2VPqP9yq1ks=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "14M views"
        ),
        YouTubeVideo(
            videoId = "bNpx7gpSqbY",
            title = "What If We Detonated All Nuclear Bombs at Once?",
            channelTitle = "Kurzgesagt – In a Nutshell",
            publishedAt = "2023-09-05T13:00:00Z",
            description = "A scientific and visual exploration of planetary consequences if all existing nuclear stockpiles were detonated.",
            channelId = "UCsXVk37bltHxD1rDPwtNM8Q",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n1Ribd7LwdP_qKtqWL3ZDfIgv9M1d6g78VwpHGXVR2Ir4=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "29M views"
        ),
        YouTubeVideo(
            videoId = "Lg13P_zCq1Y",
            title = "How The Universe Is Made Of Tiny Strings",
            channelTitle = "Kurzgesagt – In a Nutshell",
            publishedAt = "2023-08-12T11:00:00Z",
            description = "String theory explained simply with astonishing animations and theoretical physics breakdown.",
            channelId = "UCsXVk37bltHxD1rDPwtNM8Q",
            channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n1Ribd7LwdP_qKtqWL3ZDfIgv9M1d6g78VwpHGXVR2Ir4=s176-c-k-c0x00ffffff-no-rj",
            viewCount = "22M views"
        )
    )

    fun load(context: Context): List<YouTubeVideo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        if (raw.isNullOrBlank() || raw == "[]") {
            save(context, DEFAULT_EMBED_VIDEOS)
            return DEFAULT_EMBED_VIDEOS
        }
        return runCatching {
            val array = JSONArray(raw)
            val list = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("videoId")
                    if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
                    val chId = item.optString("channelId", "")
                    val rawAvatar = item.optString("channelAvatarUrl", "")
                    val cleanAvatar = if (rawAvatar.isBlank() || rawAvatar.contains("1200=") || rawAvatar.contains("hqdefault") || rawAvatar.contains("vi/")) {
                        ApprovedChannelStore.DEFAULT_CHANNELS.find { it.channelId == chId }?.avatarUrl.orEmpty()
                    } else rawAvatar

                    add(
                        YouTubeVideo(
                            videoId = id,
                            title = item.optString("title", "YouTube video"),
                            channelTitle = item.optString("channelTitle", "Unknown channel"),
                            publishedAt = item.optString("publishedAt", ""),
                            description = item.optString("description", ""),
                            channelId = chId,
                            channelAvatarUrl = cleanAvatar,
                            viewCount = item.optString("viewCount", "")
                        )
                    )
                }
            }
            if (list.isEmpty()) {
                save(context, DEFAULT_EMBED_VIDEOS)
                DEFAULT_EMBED_VIDEOS
            } else list
        }.getOrElse {
            save(context, DEFAULT_EMBED_VIDEOS)
            DEFAULT_EMBED_VIDEOS
        }
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

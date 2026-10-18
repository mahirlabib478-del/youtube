package com.mahirlabib.youtube

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object ThemeStore {
    private const val PREFS = "youtube_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode"

    fun isDark(context: Context, defaultSystemDark: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_DARK_MODE)) {
            prefs.getBoolean(KEY_DARK_MODE, defaultSystemDark)
        } else {
            defaultSystemDark
        }
    }

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, dark)
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeApp()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unified continuous flow: When user leaves the app, seamlessly continue playback
        // using the exact same shared player in Floating Window (Idea 1) if overlay permission is granted,
        // or background audio service otherwise.
        val video = SharedPlayerManager.currentVideo
        if (video != null && SharedPlayerManager.isPlaying) {
            SharedPlayerManager.detachFromContainer()
            if (OverlayPermissionHelper.hasPermission(this)) {
                FloatingPlayerService.start(this, video.videoId, video.title, video.channelTitle)
            } else {
                BackgroundAudioService.start(this, video.videoId, video.title, video.channelTitle)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // When returning to the app, dismiss the floating overlay or background notification
        // so the in-app presentation resumes seamlessly at the exact same millisecond.
        if (FloatingPlayerState.isRunning) {
            FloatingPlayerService.stop(this)
        }
        if (BackgroundAudioState.isRunning) {
            BackgroundAudioService.stop(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(ThemeStore.isDark(context, systemDark)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var lastHomeTapTime by remember { mutableLongStateOf(0L) }
    var activeVideo by remember { mutableStateOf<YouTubeVideo?>(null) }
    var isMiniPlayer by remember { mutableStateOf(false) }
    // Hoist feed scroll position above the player/feed branch so closing the mini player
    // does not recreate HomeFeed at the top of the list.
    val homeFeedListState = rememberLazyListState()

    var selectedChannel by remember { mutableStateOf<ChannelDetailState?>(null) }

    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    var approvedVideos by remember(context) { mutableStateOf(LocalVideoStore.load(context)) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var isFeedLoadingMore by remember { mutableStateOf(false) }
    var feedErrorMessage by remember { mutableStateOf<String?>(null) }
    var feedPageTokens by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var feedFailedChannelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var feedHasMore by remember { mutableStateOf(false) }
    var feedRefreshVersion by remember { mutableIntStateOf(0) }

    // Keep refresh-triggered scroll reset outside HomeFeed so toggling the mini-player
    // cannot re-run it when the feed composable is re-created.
    LaunchedEffect(feedRefreshVersion) {
        if (feedRefreshVersion > 0) {
            homeFeedListState.scrollToItem(0)
        }
    }
    var channelListRefreshKey by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Sync activeVideo when returning from floating window or background notification
    LaunchedEffect(SharedPlayerManager.currentVideo) {
        val current = SharedPlayerManager.currentVideo
        if (current != null && (activeVideo == null || activeVideo?.videoId != current.videoId)) {
            activeVideo = current
        }
    }

    val activity = context as? ComponentActivity

    fun playVideo(video: YouTubeVideo) {
        // Leaving the channel detail route is required because it has higher
        // rendering priority than PlayerScreen in the main content branch.
        selectedChannel = null
        history = WatchHistoryStore.record(
            context,
            video.videoId,
            video.title,
            video.channelTitle,
            video.description
        )
        activeVideo = video
        isMiniPlayer = false
    }

    fun openChannel(channelId: String, channelTitle: String, channelAvatarUrl: String) {
        val resolvedId = if (channelId.isNotBlank()) channelId else {
            approvedVideos.find { it.channelTitle.equals(channelTitle, ignoreCase = true) }?.channelId
                ?: ApprovedChannelStore.DEFAULT_CHANNELS.find { it.title.equals(channelTitle, ignoreCase = true) }?.channelId
                ?: ""
        }
        val resolvedAvatar = if (channelAvatarUrl.isNotBlank()) channelAvatarUrl else {
            approvedVideos.find { it.channelTitle.equals(channelTitle, ignoreCase = true) }?.channelAvatarUrl
                ?: ApprovedChannelStore.DEFAULT_CHANNELS.find { it.title.equals(channelTitle, ignoreCase = true) }?.avatarUrl
                ?: ""
        }
        selectedChannel = ChannelDetailState(
            channelId = resolvedId,
            title = channelTitle.ifBlank { "Channel" },
            avatarUrl = resolvedAvatar
        )
    }

    fun mixChannelVideos(
        pages: List<List<YouTubeVideo>>,
        previousChannelId: String = "",
        reshuffle: Boolean = false
    ): List<YouTubeVideo> {
        // Balance the first screen across channels while keeping each channel newest-first.
        return balanceVideosAcrossChannels(pages.flatten())
    }

    fun refreshFeed() {
        if (isFeedLoading || isFeedLoadingMore) return
        scope.launch {
            isFeedLoading = true
            try {
                feedErrorMessage = null
                val channels = ApprovedChannelStore.load(context)
                if (channels.isEmpty()) {
                    approvedVideos = emptyList()
                    LocalVideoStore.save(context, emptyList())
                    feedPageTokens = emptyMap()
                    feedFailedChannelIds = emptySet()
                    feedHasMore = false
                } else {
                    // Fetch channel first pages concurrently so a slow channel does not
                    // delay every other subscribed channel from appearing in the feed.
                    val results = coroutineScope {
                        channels.map { channel ->
                            async {
                                channel to runCatching {
                                    YouTubeDataRepository.fetchChannelVideosPage(channel.channelId, channel.avatarUrl)
                                }
                            }
                        }.awaitAll()
                    }
                    val successfulPages = results.mapNotNull { (_, result) -> result.getOrNull() }
                    val failedIds = results.filter { it.second.isFailure }.map { it.first.channelId }.toSet()
                    val freshVideos = successfulPages.flatMap { it.videos }
                    // Preserve cached items only for channels that failed this refresh.
                    val cachedFailedChannelVideos = approvedVideos.filter { it.channelId in failedIds }
                    val refreshedVideos = refreshBalancedVideos(
                        freshVideos = freshVideos,
                        previousVideos = approvedVideos,
                        rotationStep = feedRefreshVersion + 1
                    )
                    val merged = interleaveChannelQueuesPreservingOrder(
                        refreshedVideos + cachedFailedChannelVideos
                    )
                    if (merged.isNotEmpty() || failedIds.isEmpty()) {
                        approvedVideos = merged
                        LocalVideoStore.save(context, merged)
                    }
                    val successfulTokens = results.mapNotNull { (channel, result) ->
                        result.getOrNull()?.let { channel.channelId to it.continuationToken }
                    }.toMap()
                    feedPageTokens = channels.associate { channel ->
                        channel.channelId to if (channel.channelId in successfulTokens) {
                            successfulTokens[channel.channelId]
                        } else {
                            feedPageTokens[channel.channelId]
                        }
                    }
                    feedFailedChannelIds = failedIds
                    feedHasMore = feedPageTokens.values.any { !it.isNullOrBlank() } || failedIds.isNotEmpty()
                    feedRefreshVersion++
                    feedErrorMessage = if (failedIds.isNotEmpty()) {
                        "Some subscribed channels couldn't refresh. They'll be retried while loading more."
                    } else if (merged.isEmpty()) {
                        "No public videos found for your saved channels."
                    } else null
                }
            } catch (err: Exception) {
                // Keep cached feed visible when a refresh fails globally.
                feedErrorMessage = err.message ?: "Couldn't refresh subscribed channels."
            } finally {
                isFeedLoading = false
            }
        }
    }

    fun loadMoreFeed() {
        if (isFeedLoading || isFeedLoadingMore || !feedHasMore) return
        scope.launch {
            isFeedLoadingMore = true
            try {
                val channels = ApprovedChannelStore.load(context)
                val attempted = coroutineScope {
                    channels.mapNotNull { channel ->
                        val retryFirstPage = channel.channelId in feedFailedChannelIds
                        val token = feedPageTokens[channel.channelId]
                        if (!retryFirstPage && token.isNullOrBlank()) return@mapNotNull null
                        async {
                            val result = runCatching {
                                channel to YouTubeDataRepository.fetchChannelVideosPage(
                                    channelId = channel.channelId,
                                    channelAvatarUrl = channel.avatarUrl,
                                    continuationToken = if (retryFirstPage) null else token
                                )
                            }
                            channel.channelId to result
                        }
                    }.awaitAll()
                }
                val successes = attempted.mapNotNull { (_, result) -> result.getOrNull() }
                val successfulIds = successes.map { it.first.channelId }.toSet()
                val failedIds = attempted.filter { it.second.isFailure }.map { it.first }.toSet()
                val nextTokens = feedPageTokens.toMutableMap()
                successes.forEach { (channel, page) -> nextTokens[channel.channelId] = page.continuationToken }
                // If a failed channel is retried from its first page, replace its stale
                // cached entries with the current first page. Other channels are appended.
                val retriedIds = successfulIds.intersect(feedFailedChannelIds)
                val retainedVideos = approvedVideos.filterNot { it.channelId in retriedIds }
                val merged = balanceVideosAcrossChannels(retainedVideos + successes.flatMap { it.second.videos })
                approvedVideos = merged
                LocalVideoStore.save(context, merged)
                feedPageTokens = channels.associate { channel ->
                    channel.channelId to if (channel.channelId in successfulIds) {
                        nextTokens[channel.channelId]
                    } else {
                        feedPageTokens[channel.channelId]
                    }
                }
                feedFailedChannelIds = (feedFailedChannelIds - successfulIds) + failedIds
                feedHasMore = feedPageTokens.values.any { !it.isNullOrBlank() } || feedFailedChannelIds.isNotEmpty()
                feedErrorMessage = if (feedFailedChannelIds.isNotEmpty()) {
                    "Some subscribed channels are still unavailable. Scroll to retry."
                } else null
            } catch (err: Exception) {
                feedErrorMessage = err.message ?: "Couldn't load more subscribed videos."
            } finally {
                isFeedLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Load cached videos immediately, then refresh subscribed channels on startup.
        refreshFeed()
    }

    // Device Back Navigation handler
    BackHandler(enabled = selectedChannel != null || activeVideo != null || selectedTab != 0) {
        when {
            selectedChannel != null -> {
                selectedChannel = null
            }
            activeVideo != null -> {
                // Smoothly collapse to In-App MiniPlayer just like actual YouTube!
                isMiniPlayer = true
            }
            activeVideo != null && isMiniPlayer -> {
                // Dismiss miniplayer
                activeVideo = null
                isMiniPlayer = false
            }
            selectedTab != 0 -> selectedTab = 0
        }
    }

    val youtubeLightColors = lightColorScheme(
        primary = Color(0xFFFF0033),
        onPrimary = Color.White,
        secondary = Color(0xFF606060),
        background = Color.White,
        surface = Color.White,
        onSurface = Color(0xFF0F0F0F),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFF606060),
        surfaceContainer = Color(0xFFF8F8F8),
        surfaceContainerHigh = Color(0xFFEEEEEE)
    )

    val youtubeDarkColors = darkColorScheme(
        primary = Color(0xFFFF0033),
        onPrimary = Color.White,
        secondary = Color(0xFFAAAAAA),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF0F0F0F),
        onSurface = Color(0xFFF1F1F1),
        surfaceVariant = Color(0xFF272727),
        onSurfaceVariant = Color(0xFFAAAAAA),
        surfaceContainer = Color(0xFF212121),
        surfaceContainerHigh = Color(0xFF2A2A2A)
    )

    MaterialTheme(colorScheme = if (isDarkMode) youtubeDarkColors else youtubeLightColors) {
        AppUpdatePrompt()
        // Keep the existing PlayerScreen/WebView composed during Android PiP.
        // Creating a second YouTubeEmbedPlayer here restarted playback on PiP entry.

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if ((activeVideo == null || isMiniPlayer) && selectedChannel == null) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.testTag("youtube_logo_button")
                            ) {
                                Surface(
                                    color = Color(0xFFFF0033),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "▶",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "YouTube",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                        },
                        actions = {
                            // Dark / Light Theme Toggle
                            IconButton(
                                onClick = {
                                    val newTheme = !isDarkMode
                                    isDarkMode = newTheme
                                    ThemeStore.setDark(context, newTheme)
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme"
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (activeVideo == null || isMiniPlayer) {
                    Column {
                        // Background Audio Player Bar (Shown when background audio is actively playing outside or in feeds)
                        if (BackgroundAudioState.isRunning && activeVideo == null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val found = approvedVideos.find { it.videoId == BackgroundAudioState.currentVideoId }
                                        if (found != null) {
                                            playVideo(found)
                                        } else if (!BackgroundAudioState.currentVideoId.isNullOrBlank()) {
                                            playVideo(
                                                YouTubeVideo(
                                                    videoId = BackgroundAudioState.currentVideoId!!,
                                                    title = BackgroundAudioState.currentVideoTitle,
                                                    channelTitle = BackgroundAudioState.currentChannelTitle,
                                                    publishedAt = ""
                                                )
                                            )
                                        }
                                    }
                                    .testTag("bg_audio_running_bar")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFF0033),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Headphones,
                                                contentDescription = "Background Audio",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (BackgroundAudioState.currentVideoTitle.isNotBlank()) BackgroundAudioState.currentVideoTitle else "Background Audio",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (BackgroundAudioState.isPlaying) "Playing in background" else "Paused in background",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (BackgroundAudioState.isPlaying) {
                                                BackgroundAudioService.pause(context)
                                            } else {
                                                BackgroundAudioService.resume(context)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (BackgroundAudioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (BackgroundAudioState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { BackgroundAudioService.stop(context) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Stop",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Navigation Bar
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0 && selectedChannel == null,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    val isRapidRepeatTap = lastHomeTapTime != 0L && now - lastHomeTapTime <= 500L
                                    selectedTab = 0
                                    selectedChannel = null
                                    if (isRapidRepeatTap) {
                                        refreshFeed()
                                        lastHomeTapTime = 0L
                                    } else {
                                        lastHomeTapTime = now
                                    }
                                },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                modifier = Modifier.testTag("nav_home_tab")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1 && selectedChannel == null,
                                onClick = {
                                    selectedTab = 1
                                    selectedChannel = null
                                },
                                icon = { Icon(Icons.Default.Subscriptions, contentDescription = "Channels") },
                                label = { Text("Channels") },
                                modifier = Modifier.testTag("nav_channels_tab")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2 && selectedChannel == null,
                                onClick = {
                                    selectedTab = 2
                                    selectedChannel = null
                                    history = WatchHistoryStore.load(context)
                                },
                                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                                label = { Text("Library") },
                                modifier = Modifier.testTag("nav_library_tab")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3 && selectedChannel == null,
                                onClick = {
                                    selectedTab = 3
                                    selectedChannel = null
                                },
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                label = { Text("Search") },
                                modifier = Modifier.testTag("nav_search_tab")
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when {
                selectedChannel != null -> {
                    ChannelDetailScreen(
                        channel = selectedChannel!!,
                        allVideos = approvedVideos,
                        onPlayVideo = { video ->
                            playVideo(video)
                        },
                        onBack = { selectedChannel = null },
                        onSubscriptionChanged = { channel, subscribed ->
                            if (channel.channelId.isNotBlank()) {
                                if (subscribed) {
                                    ApprovedChannelStore.add(context, channel.channelId, channel.title, channel.avatarUrl)
                                } else {
                                    ApprovedChannelStore.remove(context, channel.channelId)
                                    val remainingVideos = approvedVideos.filterNot {
                                        it.channelId == channel.channelId ||
                                            it.channelTitle.equals(channel.title, ignoreCase = true)
                                    }
                                    approvedVideos = remainingVideos
                                    LocalVideoStore.save(context, remainingVideos)
                                    feedPageTokens = feedPageTokens - channel.channelId
                                    feedHasMore = feedPageTokens.values.any { !it.isNullOrBlank() }
                                }
                                channelListRefreshKey++
                                refreshFeed()
                            }
                        }
                    )
                }

                activeVideo != null -> {
                    // Keep the mini-player above Scaffold's bottom navigation bar.
                    Box(
                        // Keep the full Scaffold content bounds available to the feed.
                        // Each screen applies Scaffold padding itself; shrinking this Box too
                        // caused the bottom inset to be applied twice and left a blank strip.
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isMiniPlayer) {
                            when (selectedTab) {
                                0 -> HomeFeed(
                                    padding = padding, videos = approvedVideos,
                                    isLoading = isFeedLoading, errorMessage = feedErrorMessage,
                                    onPlayVideo = { playVideo(it) }, onRefresh = { refreshFeed() },
                                    onLoadMore = { loadMoreFeed() }, isLoadingMore = isFeedLoadingMore,
                                    refreshVersion = feedRefreshVersion,
                                    feedListState = homeFeedListState,
                                    onOpenChannel = { chId, chTitle, chAvatar -> openChannel(chId, chTitle, chAvatar) }
                                )
                                1 -> ChannelManagerScreen(
                                    padding = padding, onPlay = { playVideo(it) },
                                    onVideosLoaded = { approvedVideos = it },
                                    channelListRefreshKey = channelListRefreshKey,
                                    onOpenChannel = { chId, chTitle, chAvatar -> openChannel(chId, chTitle, chAvatar) }
                                )
                                2 -> LibraryScreen(
                                    history = history, padding = padding,
                                    onPlayWatched = { watched ->
                                        val video = approvedVideos.find { it.videoId == watched.videoId }
                                            ?: YouTubeVideo(videoId = watched.videoId, title = watched.title,
                                                channelTitle = watched.channel, publishedAt = "",
                                                description = watched.description)
                                        playVideo(video)
                                    },
                                    onClearHistory = { WatchHistoryStore.clear(context); history = emptyList() }
                                )
                                else -> SearchFeed(
                                    padding = padding, cachedVideos = approvedVideos, query = searchQuery,
                                    onQueryChange = { searchQuery = it }, onPlay = { playVideo(it) },
                                    onOpenChannel = { chId, chTitle, chAvatar -> openChannel(chId, chTitle, chAvatar) }
                                )
                            }
                        }
                        PlayerScreen(
                            video = activeVideo!!,
                            padding = padding,
                            relatedVideos = approvedVideos,
                            onClose = {
                                if (isMiniPlayer) { activeVideo = null; isMiniPlayer = false }
                                else isMiniPlayer = true
                            },
                            onPlayRelated = { playVideo(it) },
                            onOpenChannel = { chId, chTitle, chAvatar ->
                                isMiniPlayer = true
                                openChannel(chId, chTitle, chAvatar)
                            },
                            onSubscriptionChanged = { video, subscribed ->
                                val channelId = video.channelId.ifBlank {
                                    approvedVideos.find {
                                        it.channelTitle.equals(video.channelTitle, ignoreCase = true)
                                    }?.channelId ?: ApprovedChannelStore.DEFAULT_CHANNELS.find {
                                        it.title.equals(video.channelTitle, ignoreCase = true)
                                    }?.channelId.orEmpty()
                                }
                                if (channelId.isNotBlank()) {
                                    if (subscribed) {
                                        ApprovedChannelStore.add(
                                            context, channelId, video.channelTitle, video.channelAvatarUrl
                                        )
                                    } else {
                                        ApprovedChannelStore.remove(context, channelId)
                                        val remainingVideos = approvedVideos.filterNot {
                                            it.channelId == channelId ||
                                                it.channelTitle.equals(video.channelTitle, ignoreCase = true)
                                        }
                                        approvedVideos = remainingVideos
                                        LocalVideoStore.save(context, remainingVideos)
                                        feedPageTokens = feedPageTokens - channelId
                                        feedHasMore = feedPageTokens.values.any { !it.isNullOrBlank() }
                                    }
                                    channelListRefreshKey++
                                    refreshFeed()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Couldn't identify this channel. Open its channel page and try again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            miniMode = isMiniPlayer,
                            modifier = if (isMiniPlayer) {
                                Modifier.align(Alignment.BottomEnd).padding(
                                    end = 8.dp,
                                    bottom = padding.calculateBottomPadding() + 8.dp
                                )
                            } else Modifier,
                            onExpandMiniPlayer = { isMiniPlayer = false }
                        )
                    }
                }

                selectedTab == 0 -> {
                    HomeFeed(
                        padding = padding,
                        videos = approvedVideos,
                        isLoading = isFeedLoading,
                        errorMessage = feedErrorMessage,
                        onPlayVideo = { playVideo(it) },
                        onRefresh = { refreshFeed() },
                        onLoadMore = { loadMoreFeed() },
                        isLoadingMore = isFeedLoadingMore,
                        refreshVersion = feedRefreshVersion,
                                    feedListState = homeFeedListState,
                        onOpenChannel = { chId, chTitle, chAvatar ->
                            openChannel(chId, chTitle, chAvatar)
                        }
                    )
                }

                selectedTab == 1 -> {
                    ChannelManagerScreen(
                        padding = padding,
                        onPlay = { video -> playVideo(video) },
                        onVideosLoaded = { approvedVideos = it },
                        onOpenChannel = { chId, chTitle, chAvatar ->
                            openChannel(chId, chTitle, chAvatar)
                        }
                    )
                }

                selectedTab == 2 -> {
                    LibraryScreen(
                        history = history,
                        padding = padding,
                        onPlayWatched = { watched ->
                            val video = approvedVideos.find { it.videoId == watched.videoId }
                                ?: YouTubeVideo(
                                    videoId = watched.videoId,
                                    title = watched.title,
                                    channelTitle = watched.channel,
                                    publishedAt = "",
                                    description = watched.description
                                )
                            playVideo(video)
                        },
                        onClearHistory = {
                            WatchHistoryStore.clear(context)
                            history = emptyList()
                        }
                    )
                }

                else -> {
                    SearchFeed(
                        padding = padding,
                        cachedVideos = approvedVideos,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onPlay = { playVideo(it) },
                        onOpenChannel = { chId, chTitle, chAvatar ->
                            openChannel(chId, chTitle, chAvatar)
                        }
                    )
                }
            }
        }

    }
}
}

/**
 * YouTube-style In-App MiniPlayer.
 * Floats immediately above the bottom navigation bar when user navigates back while playing video.
 */
@Composable
private fun InAppMiniPlayer(
    video: YouTubeVideo,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onExpand() }
            .testTag("in_app_mini_player"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keep the mini-player preview passive: creating a second WebView here
            // starts a competing YouTube player and can interrupt the main player.
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                contentDescription = "Thumbnail for ${video.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .testTag("mini_player_thumbnail")
            )

            Spacer(Modifier.width(10.dp))

            // Video Title & Channel metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = video.channelTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expand Button
            IconButton(
                onClick = onExpand,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("mini_player_expand_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand video",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Close / Dismiss Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("mini_player_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close video",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

package com.mahirlabib.youtube

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(ThemeStore.isDark(context, systemDark)) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeVideo by remember { mutableStateOf<YouTubeVideo?>(null) }
    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    var approvedVideos by remember(context) { mutableStateOf(LocalVideoStore.load(context)) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var feedErrorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Fullscreen video state
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun playVideo(video: YouTubeVideo) {
        history = WatchHistoryStore.record(
            context,
            video.videoId,
            video.title,
            video.channelTitle,
            video.description
        )
        activeVideo = video
    }

    fun refreshFeed() {
        scope.launch {
            isFeedLoading = true
            feedErrorMessage = null
            runCatching {
                YouTubeDataRepository.latestVideos(context, BuildConfig.YOUTUBE_API_KEY)
            }.onSuccess { vids ->
                approvedVideos = vids
                feedErrorMessage = if (vids.isEmpty()) "No public videos found for the saved channels." else null
            }.onFailure { err ->
                feedErrorMessage = err.message ?: "Failed to refresh feed."
            }
            isFeedLoading = false
        }
    }

    // Auto-load on initial startup if approvedVideos is empty and API key configured
    LaunchedEffect(Unit) {
        if (approvedVideos.isEmpty() && isApiKeyConfigured(BuildConfig.YOUTUBE_API_KEY)) {
            refreshFeed()
        }
    }

    // Device Back Navigation handler
    BackHandler(enabled = customFullscreenView != null || activeVideo != null || selectedTab != 0) {
        when {
            customFullscreenView != null -> {
                customViewCallback?.onCustomViewHidden()
                customFullscreenView = null
                customViewCallback = null
            }
            activeVideo != null -> activeVideo = null
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
        surfaceContainer = Color(0xFFF8F8F8)
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
        surfaceContainer = Color(0xFF212121)
    )

    MaterialTheme(colorScheme = if (isDarkMode) youtubeDarkColors else youtubeLightColors) {
        // True Fullscreen Video Overlay when triggered by YouTube player
        if (customFullscreenView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("fullscreen_video_overlay")
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        (customFullscreenView?.parent as? ViewGroup)?.removeView(customFullscreenView)
                        customFullscreenView ?: View(it)
                    }
                )

                IconButton(
                    onClick = {
                        customViewCallback?.onCustomViewHidden()
                        customFullscreenView = null
                        customViewCallback = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color(0x88000000), CircleShape)
                        .testTag("exit_fullscreen_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White
                    )
                }
            }
            return@MaterialTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (activeVideo == null) {
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
                                    contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = { /* Cast */ }) {
                                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { /* Notifications */ }) {
                                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { selectedTab = 3 }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (activeVideo == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        // Channels tab directly replaces Shorts tab
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Subscriptions, contentDescription = "Channels") },
                            label = { Text("Channels") },
                            modifier = Modifier.testTag("nav_channels_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = {
                                selectedTab = 2
                                history = WatchHistoryStore.load(context)
                            },
                            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                            label = { Text("Library") },
                            modifier = Modifier.testTag("nav_library_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            modifier = Modifier.testTag("nav_search_tab")
                        )
                    }
                }
            }
        ) { padding ->
            when {
                activeVideo != null -> {
                    PlayerScreen(
                        video = activeVideo!!,
                        padding = padding,
                        relatedVideos = approvedVideos,
                        onClose = { activeVideo = null },
                        onPlayRelated = { playVideo(it) },
                        onShowCustomView = { view, callback ->
                            customFullscreenView = view
                            customViewCallback = callback
                        },
                        onHideCustomView = {
                            customViewCallback?.onCustomViewHidden()
                            customFullscreenView = null
                            customViewCallback = null
                        }
                    )
                }

                selectedTab == 0 -> {
                    HomeFeed(
                        padding = padding,
                        videos = approvedVideos,
                        isLoading = isFeedLoading,
                        errorMessage = feedErrorMessage,
                        onPlayVideo = { playVideo(it) },
                        onRefresh = { refreshFeed() }
                    )
                }

                // Channels tab directly accessible without secret PIN gate
                selectedTab == 1 -> {
                    ChannelManagerScreen(
                        padding = padding,
                        onPlay = { video -> playVideo(video) },
                        onVideosLoaded = { approvedVideos = it }
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
                        onPlay = { playVideo(it) }
                    )
                }
            }
        }
    }
}

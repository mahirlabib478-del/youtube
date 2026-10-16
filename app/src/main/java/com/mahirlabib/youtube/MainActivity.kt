package com.mahirlabib.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlinx.coroutines.launch

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastLogoTapAt by remember { mutableStateOf(0L) }
    var showAdminGate by remember { mutableStateOf(false) }
    var adminLoggedIn by remember { mutableStateOf(false) }
    var activeVideo by remember { mutableStateOf<YouTubeVideo?>(null) }
    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    var approvedVideos by remember(context) { mutableStateOf(LocalVideoStore.load(context)) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var feedErrorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

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

    // Auto-load on initial startup if approvedVideos is empty
    LaunchedEffect(Unit) {
        if (approvedVideos.isEmpty() && isApiKeyConfigured(BuildConfig.YOUTUBE_API_KEY)) {
            refreshFeed()
        }
    }

    // Device Back Navigation handler
    BackHandler(enabled = showAdminGate || adminLoggedIn || activeVideo != null || selectedTab != 0) {
        when {
            showAdminGate -> showAdminGate = false
            adminLoggedIn -> adminLoggedIn = false
            activeVideo != null -> activeVideo = null
            selectedTab != 0 -> selectedTab = 0
        }
    }

    val youtubeColors = lightColorScheme(
        primary = Color(0xFFFF0033),
        onPrimary = Color.White,
        secondary = Color(0xFF606060),
        background = Color.White,
        surface = Color.White,
        onSurface = Color(0xFF0F0F0F),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFF606060)
    )

    MaterialTheme(colorScheme = youtubeColors) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                if (activeVideo == null && !showAdminGate && !adminLoggedIn) {
                    TopAppBar(
                        title = {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        val now = System.currentTimeMillis()
                                        logoTapCount = if (now - lastLogoTapAt <= 1_500L) logoTapCount + 1 else 1
                                        lastLogoTapAt = now
                                        if (logoTapCount >= 7) {
                                            logoTapCount = 0
                                            showAdminGate = true
                                        }
                                    }
                                    .testTag("youtube_logo_button"),
                                verticalAlignment = Alignment.CenterVertically
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
                                    color = Color(0xFF0F0F0F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* Cast indicator */ }) {
                                Icon(Icons.Default.Cast, contentDescription = "Cast", modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { /* Notifications */ }) {
                                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { selectedTab = 3 }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(22.dp))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (activeVideo == null && !showAdminGate && !adminLoggedIn) {
                    NavigationBar(
                        containerColor = Color.White,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Shorts") },
                            label = { Text("Shorts") },
                            modifier = Modifier.testTag("nav_shorts_tab")
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
                showAdminGate -> {
                    AdminGateScreen(
                        onSuccess = {
                            showAdminGate = false
                            adminLoggedIn = true
                        },
                        onCancel = { showAdminGate = false }
                    )
                }

                adminLoggedIn -> {
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { adminLoggedIn = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = "Channel Admin",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { adminLoggedIn = false }) {
                                Text("Logout")
                            }
                        }
                        ChannelManagerScreen(
                            padding = PaddingValues(0.dp),
                            onPlay = { video -> playVideo(video) },
                            onVideosLoaded = { approvedVideos = it }
                        )
                    }
                }

                activeVideo != null -> {
                    PlayerScreen(
                        video = activeVideo!!,
                        padding = padding,
                        relatedVideos = approvedVideos,
                        onClose = { activeVideo = null },
                        onPlayRelated = { playVideo(it) }
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

                selectedTab == 1 -> {
                    ShortsFeed(
                        padding = padding,
                        approvedVideos = approvedVideos,
                        onOpenWatchPage = { playVideo(it) }
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

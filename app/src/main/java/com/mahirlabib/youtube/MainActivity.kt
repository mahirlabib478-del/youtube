package com.mahirlabib.youtube

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
    companion object {
        var isPipActiveState = mutableStateOf(false)
        var currentActiveVideoState = mutableStateOf<YouTubeVideo?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeApp(
                isPipActive = isPipActiveState.value,
                onActiveVideoChanged = { currentActiveVideoState.value = it }
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val video = currentActiveVideoState.value
        // Only the Floating Video mode should enter PiP. Audio-only mode cannot
        // be guaranteed by a YouTube iframe once Android backgrounds the WebView.
        if (currentActiveVideoState.value != null && PipSettingsStore.isPipVideoEnabled(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val builder = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                    // Android 12+ can enter PiP as part of the Home gesture, avoiding
                    // a separate onUserLeaveHint transition animation.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setAutoEnterEnabled(true)
                    }
                    enterPictureInPictureMode(builder.build())
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to enter Picture-in-Picture mode", e)
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipActiveState.value = isInPictureInPictureMode
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeApp(
    isPipActive: Boolean = false,
    onActiveVideoChanged: (YouTubeVideo?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(ThemeStore.isDark(context, systemDark)) }
    var playbackMode by remember { mutableStateOf(PipSettingsStore.getPlaybackMode(context)) }
    val isPipEnabled = playbackMode != PipSettingsStore.MODE_DISABLED
    var showPipSettingsDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeVideo by remember { mutableStateOf<YouTubeVideo?>(null) }
    var isMiniPlayer by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<ChannelDetailState?>(null) }

    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    var approvedVideos by remember(context) { mutableStateOf(LocalVideoStore.load(context)) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var feedErrorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Sync active video with Activity for PiP
    LaunchedEffect(activeVideo) {
        onActiveVideoChanged(activeVideo)
    }

    // Fullscreen video state
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val activity = context as? ComponentActivity

    // Register PiP auto-enter parameters before the Home gesture on Android 12+.
    LaunchedEffect(activeVideo, playbackMode, activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(
                    activeVideo != null && playbackMode == PipSettingsStore.MODE_VIDEO
                )
            }
            runCatching { activity.setPictureInPictureParams(builder.build()) }
                .onFailure { android.util.Log.w("MainActivity", "Unable to update PiP parameters", it) }
        }
    }

    LaunchedEffect(customFullscreenView) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (customFullscreenView != null) {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    fun playVideo(video: YouTubeVideo) {
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

    // Auto-load latest videos from channels on startup
    LaunchedEffect(Unit) {
        if (approvedVideos.size <= 4) {
            refreshFeed()
        }
    }

    // Device Back Navigation handler
    BackHandler(enabled = customFullscreenView != null || selectedChannel != null || activeVideo != null || selectedTab != 0) {
        when {
            customFullscreenView != null -> {
                customViewCallback?.onCustomViewHidden()
                customFullscreenView = null
                customViewCallback = null
            }
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
        // True PiP Window Mode: Clean Edge-to-Edge Player
        if (isPipActive && activeVideo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                YouTubeEmbedPlayer(
                    videoId = activeVideo!!.videoId,
                    modifier = Modifier.fillMaxSize(),
                    autoplay = true
                )
            }
            return@MaterialTheme
        }

        // True Fullscreen Video Overlay when triggered by WebChromeClient
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

        // Background playback mode settings.
        if (showPipSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showPipSettingsDialog = false },
                title = { Text("Background Playback") },
                text = {
                    Column {
                        Text(
                            text = "Choose what should happen when you leave the app. Audio-only playback may not continue because YouTube embedded playback does not guarantee background audio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        listOf(
                            PipSettingsStore.MODE_VIDEO to ("Floating Video" to "Continue in a small picture-in-picture window"),
                            PipSettingsStore.MODE_AUDIO to ("Background Audio Only" to "Attempts audio-only background playback; not guaranteed"),
                            PipSettingsStore.MODE_DISABLED to ("Disabled" to "Do not enter picture-in-picture automatically")
                        ).forEach { (mode, details) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    playbackMode = mode
                                    PipSettingsStore.setPlaybackMode(context, mode)
                                    Toast.makeText(context, details.first + " selected", Toast.LENGTH_SHORT).show()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = playbackMode == mode,
                                    onClick = {
                                        playbackMode = mode
                                        PipSettingsStore.setPlaybackMode(context, mode)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(details.first, fontWeight = FontWeight.SemiBold)
                                    Text(details.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showPipSettingsDialog = false }) { Text("Done") } }
            )
        }
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
                            // Background PiP Toggle Option Dialog
                            IconButton(
                                onClick = { showPipSettingsDialog = true },
                                modifier = Modifier.testTag("pip_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Background Playback Settings",
                                    tint = if (isPipEnabled) Color(0xFFFF0033) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                        // In-App Miniplayer Bar (Sits directly above bottom navigation bar, exactly like actual YouTube)
                        if (activeVideo != null && isMiniPlayer) {
                            InAppMiniPlayer(
                                video = activeVideo!!,
                                onExpand = { isMiniPlayer = false },
                                onClose = {
                                    activeVideo = null
                                    isMiniPlayer = false
                                }
                            )
                        }

                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0 && selectedChannel == null,
                                onClick = {
                                    selectedTab = 0
                                    selectedChannel = null
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
                        onBack = { selectedChannel = null }
                    )
                }

                activeVideo != null -> {
                    PlayerScreen(
                        video = activeVideo!!,
                        padding = padding,
                        relatedVideos = approvedVideos,
                        onClose = { isMiniPlayer = true },
                        onPlayRelated = { playVideo(it) },
                        onShowCustomView = { view, callback ->
                            customFullscreenView = view
                            customViewCallback = callback
                        },
                        onHideCustomView = {
                            customViewCallback?.onCustomViewHidden()
                            customFullscreenView = null
                            customViewCallback = null
                        },
                        onOpenChannel = { chId, chTitle, chAvatar ->
                            isMiniPlayer = true
                            openChannel(chId, chTitle, chAvatar)
                        },
                        miniMode = isMiniPlayer
                    )
                }

                selectedTab == 0 -> {
                    HomeFeed(
                        padding = padding,
                        videos = approvedVideos,
                        isLoading = isFeedLoading,
                        errorMessage = feedErrorMessage,
                        onPlayVideo = { playVideo(it) },
                        onRefresh = { refreshFeed() },
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

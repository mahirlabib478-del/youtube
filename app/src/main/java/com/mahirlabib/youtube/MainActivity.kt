package com.mahirlabib.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YouTubeApp() }
    }
}

private data class VideoCardData(
    val title: String,
    val channel: String,
    val duration: String,
    val description: String,
    val videoId: String? = null
)

private val demoVideos = listOf(
    VideoCardData(
        "YouTube IFrame API sample",
        "YouTube Developers",
        "DEMO",
        "A playback prototype using the official YouTube embedded player.",
        videoId = "M7lc1UVf-VE"
    ),
    VideoCardData("Build your own watchlist", "Product preview", "PLANNED", "Playlist support will be added in a later milestone."),
    VideoCardData("Videos from approved channels", "Channel feed", "API", "Add channel IDs in Channels to load recent uploads.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastLogoTapAt by remember { mutableStateOf(0L) }
    var showAdminGate by remember { mutableStateOf(false) }
    var adminLoggedIn by remember { mutableStateOf(false) }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    var approvedVideos by remember(context) { mutableStateOf(LocalVideoStore.load(context)) }
    var searchQuery by remember { mutableStateOf("") }
    val tabs = listOf("Home", "Shorts", "Library", "Search")

    fun playVideo(videoId: String, title: String, channel: String) {
        history = WatchHistoryStore.record(context, videoId, title, channel)
        activeVideoId = videoId
    }

    val youtubeColors = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFFF0033),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFF606060),
        background = androidx.compose.ui.graphics.Color.White,
        surface = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color(0xFF0F0F0F),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF2F2F2),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF606060)
    )
    MaterialTheme(colorScheme = youtubeColors) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.White,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.clickable {
                                val now = System.currentTimeMillis()
                                logoTapCount = if (now - lastLogoTapAt <= 1_500L) logoTapCount + 1 else 1
                                lastLogoTapAt = now
                                if (logoTapCount >= 7) {
                                    logoTapCount = 0
                                    showAdminGate = true
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("▶", color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            Text("YouTube", color = androidx.compose.ui.graphics.Color(0xFF0F0F0F), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; activeVideoId = null },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text(tabs[0]) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; activeVideoId = null },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Shorts preview") },
                        label = { Text(tabs[1]) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; activeVideoId = null },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                        label = { Text(tabs[2]) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; activeVideoId = null },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Channels") },
                        label = { Text(tabs[3]) }
                    )
                }
            }
        ) { padding ->
            when {
                showAdminGate -> AdminGateScreen(
                    onSuccess = { showAdminGate = false; adminLoggedIn = true },
                    onCancel = { showAdminGate = false }
                )
                adminLoggedIn -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Admin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { adminLoggedIn = false }) { Text("Logout") }
                    }
                    ChannelManagerScreen(
                        padding = PaddingValues(0.dp),
                        onPlay = { video -> playVideo(video.videoId, video.title, video.channelTitle) },
                        onVideosLoaded = { approvedVideos = it }
                    )
                }
                activeVideoId != null -> PlayerScreen(
                    videoId = activeVideoId!!,
                    padding = padding,
                    onClose = { activeVideoId = null }
                )
                selectedTab == 0 -> HomeFeed(
                    padding = padding,
                    approvedVideos = approvedVideos,
                    onPlayApproved = { video -> playVideo(video.videoId, video.title, video.channelTitle) },
                    onPlayDemo = { video -> video.videoId?.let { playVideo(it, video.title, video.channel) } }
                )
                selectedTab == 1 -> PlaceholderScreen(
                    "Shorts-style feed",
                    "Vertical video feed is planned. Playback will use official YouTube embeds and must follow YouTube's player rules.",
                    padding
                )
                selectedTab == 2 -> LibraryScreen(
                    history = history,
                    padding = padding,
                    onPlay = { item -> playVideo(item.videoId, item.title, item.channel) },
                    onClear = {
                        WatchHistoryStore.clear(context)
                        history = emptyList()
                    }
                )
                else -> SearchFeed(
                    padding = padding,
                    videos = approvedVideos,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onPlay = { video -> playVideo(video.videoId, video.title, video.channelTitle) }
                )
            }
        }
    }
}


@Composable
private fun VideoThumbnail(videoId: String, title: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg",
        contentDescription = title + " thumbnail",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun HomeFeed(
    padding: PaddingValues,
    approvedVideos: List<YouTubeVideo>,
    onPlayApproved: (YouTubeVideo) -> Unit,
    onPlayDemo: (VideoCardData) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Explore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (approvedVideos.isEmpty()) "Add channels in hidden Local Admin, then load their latest public uploads." else "Latest videos from your saved channels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (approvedVideos.isNotEmpty()) {
            items(approvedVideos, key = { it.videoId }) { video ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        VideoThumbnail(videoId = video.videoId, title = video.title, modifier = Modifier.fillMaxWidth().height(210.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(video.channelTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPlayApproved(video) }) { Text("Play video") }
                    }
                }
            }
        } else {
            items(demoVideos, key = { it.title }) { video ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        if (video.videoId != null) VideoThumbnail(videoId = video.videoId, title = video.title, modifier = Modifier.fillMaxWidth().height(210.dp)) else Surface(modifier = Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("▶", fontSize = 34.sp, color = MaterialTheme.colorScheme.error)
                                Text(video.duration, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(video.channel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(video.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (video.videoId != null) { Spacer(Modifier.height(8.dp)); Button(onClick = { onPlayDemo(video) }) { Text("Play demo") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFeed(
    padding: PaddingValues,
    videos: List<YouTubeVideo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlay: (YouTubeVideo) -> Unit
) {
    val filtered = videos.filter { it.title.contains(query, ignoreCase = true) || it.channelTitle.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), label = { Text("Search approved-channel videos") }, singleLine = true)
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (videos.isEmpty()) "No channel videos saved yet. Tap the YouTube logo 7 times to open Local Admin, then load videos." else "No videos match your search.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.videoId }) { video ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VideoThumbnail(videoId = video.videoId, title = video.title, modifier = Modifier.weight(1f).height(88.dp))
                            Column(Modifier.weight(1.4f)) {
                                Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                                Spacer(Modifier.height(4.dp))
                                Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                TextButton(onClick = { onPlay(video) }) { Text("Watch") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelManagerScreen(
    padding: PaddingValues,
    onPlay: (YouTubeVideo) -> Unit,
    onVideosLoaded: (List<YouTubeVideo>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channelId by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(ApprovedChannelStore.load(context)) }
    var videos by remember { mutableStateOf(LocalVideoStore.load(context)) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Add YouTube channel IDs, then load their latest public uploads.") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Channels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Prototype: this list is stored only on this device. It is not server-side admin approval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = channelId,
                onValueChange = { channelId = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube channel ID") },
                placeholder = { Text("UCxxxxxxxxxxxxxxxxxxxxxx") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    runCatching { ApprovedChannelStore.add(context, channelId) }
                        .onSuccess {
                            channels = it
                            channelId = ""
                            message = "Channel ID saved on this device."
                        }
                        .onFailure { message = it.message ?: "Could not add channel." }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add channel") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        message = "Loading latest public uploads…"
                        runCatching { YouTubeDataRepository.latestVideos(context, BuildConfig.YOUTUBE_API_KEY) }
                            .onSuccess {
                                videos = it
                                onVideosLoaded(it)
                                message = if (it.isEmpty()) "No public uploads found for these channels." else "Loaded ${it.size} videos."
                            }
                            .onFailure { message = it.message ?: "Could not load videos." }
                        loading = false
                    }
                },
                enabled = !loading && channels.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "Loading…" else "Load latest videos") }
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Saved channel IDs (${channels.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (channels.isEmpty()) {
            item { Text("No channel IDs added yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(channels, key = { it.channelId }) { channel ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(channel.channelId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        channels = ApprovedChannelStore.remove(context, channel.channelId)
                        videos = emptyList()
                        LocalVideoStore.save(context, emptyList())
                        onVideosLoaded(emptyList())
                    }) { Text("Remove") }
                }
            }
        }

        if (videos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Latest uploads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(videos, key = { it.videoId }) { video ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPlay(video) }) { Text("Play video") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    history: List<WatchedVideo>,
    padding: PaddingValues,
    onPlay: (WatchedVideo) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        PlaceholderScreen(
            "Your library",
            "Videos you open will appear here as watch history. History is saved locally on this device.",
            padding
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Watch history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${history.size} recently opened video(s)", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        items(history, key = { it.videoId }) { video ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.height(64.dp).weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) { Text("▶", color = MaterialTheme.colorScheme.error, fontSize = 24.sp) }
                    }
                    Column(modifier = Modifier.weight(2f)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { onPlay(video) }) { Text("Watch again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(videoId: String, padding: PaddingValues, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Top
    ) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = 8.dp)) {
            Text("← Back to feed")
        }
        YouTubeEmbedPlayer(
            videoId = videoId,
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
        Column(Modifier.padding(16.dp)) {
            Text("Official YouTube player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Playback controls and availability are provided by YouTube. Videos are played using YouTube's embedded player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

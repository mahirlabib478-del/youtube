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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    VideoCardData("Videos from approved channels", "Channel feed", "PLANNED", "The admin-managed channel list and YouTube Data API are not connected yet.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var history by remember(context) { mutableStateOf(WatchHistoryStore.load(context)) }
    val tabs = listOf("Home", "Shorts", "Library")

    fun playVideo(videoId: String, title: String, channel: String) {
        history = WatchHistoryStore.record(context, videoId, title, channel)
        activeVideoId = videoId
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", color = MaterialTheme.colorScheme.error, fontSize = 24.sp)
                            Text(" YouTube", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
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
                }
            }
        ) { padding ->
            when {
                activeVideoId != null -> PlayerScreen(
                    videoId = activeVideoId!!,
                    padding = padding,
                    onClose = { activeVideoId = null }
                )
                selectedTab == 0 -> HomeFeed(padding) { video ->
                    video.videoId?.let { playVideo(it, video.title, video.channel) }
                }
                selectedTab == 1 -> PlaceholderScreen(
                    "Shorts-style feed",
                    "Vertical video feed is planned. Playback will use official YouTube embeds and must follow YouTube's player rules.",
                    padding
                )
                else -> LibraryScreen(
                    history = history,
                    padding = padding,
                    onPlay = { item -> playVideo(item.videoId, item.title, item.channel) },
                    onClear = {
                        WatchHistoryStore.clear(context)
                        history = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeFeed(padding: PaddingValues, onPlay: (VideoCardData) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Explore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Playback prototype · channel API integration is next",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(demoVideos) { video ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(174.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                    if (video.videoId != null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPlay(video) }) {
                            Text("Play demo")
                        }
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
                "Playback controls and availability are provided by YouTube. This is a prototype video, not yet the admin-approved channel feed.",
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

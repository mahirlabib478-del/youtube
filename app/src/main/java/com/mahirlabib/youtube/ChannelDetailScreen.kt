package com.mahirlabib.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

data class ChannelDetailState(
    val channelId: String,
    val title: String,
    val avatarUrl: String = "",
    val subscriberCount: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channel: ChannelDetailState,
    allVideos: List<YouTubeVideo>,
    onPlayVideo: (YouTubeVideo) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    var isSubscribed by remember { mutableStateOf(false) }
    var channelVideos by remember(channel.channelId) {
        val initial = allVideos.filter {
            (channel.channelId.isNotBlank() && it.channelId == channel.channelId) ||
                it.channelTitle.equals(channel.title, ignoreCase = true)
        }
        mutableStateOf(initial)
    }
    var isLoadingOnlineUploads by remember { mutableStateOf(false) }
    var initialLoadRetryKey by remember(channel.channelId) { mutableIntStateOf(0) }
    var uploadsError by remember(channel.channelId) { mutableStateOf<String?>(null) }
    var continuationToken by remember(channel.channelId) { mutableStateOf<String?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreVideos by remember(channel.channelId) { mutableStateOf(true) }
    var allowLoadMore by remember(channel.channelId) { mutableStateOf(true) }
    var loadMoreRetryKey by remember(channel.channelId) { mutableIntStateOf(0) }

    // Initial load: Fetch first rich page of videos + continuation token
    LaunchedEffect(channel.channelId, initialLoadRetryKey) {
        if (channel.channelId.isNotBlank()) {
            isLoadingOnlineUploads = true
            uploadsError = null
            try {
                val page = YouTubeDataRepository.fetchChannelVideosPage(
                    channelId = channel.channelId,
                    channelAvatarUrl = channel.avatarUrl
                )
                if (page.videos.isNotEmpty()) {
                    val combined = (page.videos + channelVideos)
                        .distinctBy { it.videoId }
                    channelVideos = combined
                }
                continuationToken = page.continuationToken
                hasMoreVideos = page.continuationToken != null
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                uploadsError = error.message ?: "Could not load channel uploads. Check your internet connection and YouTube API key."
            } finally {
                isLoadingOnlineUploads = false
            }
        }
    }

    // Infinite scroll detection: When scrolling near the bottom, load the next page automatically
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = lazyListState.layoutInfo.totalItemsCount
            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(channel.channelId, loadMoreRetryKey) {
        // Observe scroll position and pagination state in one long-lived collector.
        // A normal scroll/layout change must not cancel an in-flight network request.
        snapshotFlow {
            // Include loading state so the collector wakes up after a page finishes.
            // Otherwise a token update that arrives while loading can be skipped, leaving
            // the list stuck even when the viewport is still near the bottom.
            listOf(
                shouldLoadMore,
                continuationToken,
                hasMoreVideos && allowLoadMore,
                isLoadingMore
            )
        }.distinctUntilChanged().collect { (nearEnd, token, canLoadMore, loading) ->
            if (nearEnd && canLoadMore && token != null && !loading) {
                isLoadingMore = true
                try {
                    val nextPage = YouTubeDataRepository.fetchChannelVideosPage(
                        channelId = channel.channelId,
                        channelAvatarUrl = channel.avatarUrl,
                        continuationToken = token
                    )
                    if (nextPage.videos.isNotEmpty()) {
                        channelVideos = (channelVideos + nextPage.videos).distinctBy { it.videoId }
                    }
                    continuationToken = nextPage.continuationToken
                    hasMoreVideos = nextPage.continuationToken != null
                    if (nextPage.videos.isEmpty() && nextPage.continuationToken != null) {
                        // Prevent an endless loop if the API returns an empty page with a token.
                        allowLoadMore = false
                        uploadsError = "No videos were returned for this page. Tap retry to continue."
                    } else if (nextPage.continuationToken == null) {
                        hasMoreVideos = false
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    uploadsError = error.message ?: "Could not load more videos. Try again later."
                    allowLoadMore = false
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = channel.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("channel_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("channel_detail_screen"),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Channel Decorative Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF0033),
                                    Color(0xFF8E24AA),
                                    Color(0xFF1E88E5)
                                )
                            )
                        )
                )
            }

            // Channel Profile Information
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(
                            channelTitle = channel.title,
                            avatarUrl = channel.avatarUrl,
                            size = 72.dp
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Channel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "@${channel.title.replace(" ", "").lowercase()} • ${if (channel.subscriberCount.isNotBlank()) channel.subscriberCount else "Official Channel"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${channelVideos.size} videos uploaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // YouTube Subscribe Button
                    Button(
                        onClick = { isSubscribed = !isSubscribed },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("channel_subscribe_button"),
                        shape = RoundedCornerShape(21.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0F0F0F),
                            contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        )
                    ) {
                        if (isSubscribed) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Subscribed", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Subscribe", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Tab Indicator Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Uploads (${channelVideos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoadingOnlineUploads) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Surface initial pagination/API errors even when cached videos exist,
            // so a cached 50-video list cannot silently look like the end of the channel.
            if (uploadsError != null && continuationToken == null && !isLoadingOnlineUploads) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uploadsError ?: "Could not load channel uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                uploadsError = null
                                initialLoadRetryKey++
                            },
                            modifier = Modifier.testTag("channel_initial_load_retry")
                        ) {
                            Text("Retry loading channel videos")
                        }
                    }
                }
            }

            // Channel Videos List
            if (channelVideos.isEmpty() && !isLoadingOnlineUploads) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uploadsError ?: "No public uploads found for this channel.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(channelVideos, key = { it.videoId }) { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onPlayVideo(video) }
                            .testTag("channel_video_${video.videoId}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                                    contentDescription = video.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(44.dp),
                                    shape = CircleShape,
                                    color = Color(0xCC000000)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatRelativeTime(video.publishedAt)}${if (video.viewCount.isNotBlank()) " • ${video.viewCount}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isLoadingMore || (uploadsError != null && hasMoreVideos && continuationToken != null)) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFFF0033),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = uploadsError ?: "Could not load more videos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        uploadsError = null
                                        allowLoadMore = true
                                        loadMoreRetryKey++
                                    },
                                    modifier = Modifier.testTag("channel_load_more_retry")
                                ) {
                                    Text("Retry loading videos")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

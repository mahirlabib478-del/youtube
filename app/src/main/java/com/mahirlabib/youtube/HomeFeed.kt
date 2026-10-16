package com.mahirlabib.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val DEMO_VIDEO = YouTubeVideo(
    videoId = "M7lc1UVf-VE",
    title = "YouTube IFrame API sample",
    channelTitle = "YouTube Developers",
    publishedAt = "2024-01-01T00:00:00Z",
    description = "A playback prototype using the official YouTube embedded player.",
    channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_k60j2uF-1200=s88-c-k-c0x00ffffff-no-rj"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeed(
    padding: PaddingValues,
    videos: List<YouTubeVideo>,
    isLoading: Boolean,
    errorMessage: String?,
    onPlayVideo: (YouTubeVideo) -> Unit,
    onRefresh: () -> Unit
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("All", "Tech", "Science", "Tutorials", "Podcasts", "News")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Pinned Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEachIndexed { index, label ->
                val isSelected = selectedCategoryIndex == index
                Surface(
                    onClick = { selectedCategoryIndex = index },
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("category_chip_$index")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home_pull_to_refresh")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_feed_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {

        // Error message banner if any
        if (errorMessage != null && videos.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButtonAction(Icons.Default.Refresh, "Retry", onRefresh)
                    }
                }
            }
        }

        // Loading state with empty list
        if (isLoading && videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF0033))
                        Spacer(Modifier.height(12.dp))
                        Text("Fetching latest uploads from your channels…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Empty state
        if (!isLoading && videos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = Color(0xFFFF0033)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Welcome to YouTube Feed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (errorMessage != null) errorMessage
                            else "Add YouTube channel IDs in Admin or try our embedded demo video below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onPlayVideo(DEMO_VIDEO) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033))
                            ) {
                                Text("Play Demo Video")
                            }
                            OutlinedButton(onClick = onRefresh) {
                                Text("Refresh Feed")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "💡 Tip: Tap the Channels tab below to add and manage your favorite channels anytime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Filtered Video Feed
        val filteredVideos = if (selectedCategoryIndex == 0) videos else {
            val filterTerm = categories[selectedCategoryIndex].lowercase()
            videos.filter {
                it.title.lowercase().contains(filterTerm) ||
                    it.channelTitle.lowercase().contains(filterTerm) ||
                    it.description.lowercase().contains(filterTerm)
            }.ifEmpty { videos }
        }

        items(filteredVideos, key = { it.videoId }) { video ->
            VideoCardItem(
                video = video,
                onClick = { onPlayVideo(video) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
}
}

@Composable
private fun VideoCardItem(
    video: YouTubeVideo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_card_${video.videoId}")
    ) {
        // Thumbnail with 16:9 aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF202020))
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
                    .size(48.dp),
                shape = CircleShape,
                color = Color(0x99000000)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel Avatar
            if (video.channelAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = video.channelAvatarUrl,
                    contentDescription = video.channelTitle,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = video.channelTitle.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title and Channel metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.channelTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = formatRelativeTime(video.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IconButtonAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

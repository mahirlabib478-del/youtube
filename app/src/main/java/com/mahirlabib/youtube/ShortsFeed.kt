package com.mahirlabib.youtube

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val SHORTS_PREVIEW_LIST = listOf(
    YouTubeVideo("M7lc1UVf-VE", "Official YouTube Player API - Embed Showcase", "YouTube Developers", "2024-01-01T00:00:00Z", "YouTube iframe test", channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_k60j2uF-1200=s88-c-k-c0x00ffffff-no-rj"),
    YouTubeVideo("aqz-KE-bpKQ", "Big Buck Bunny - Animated Short Excerpt", "Blender Foundation", "2023-01-01T00:00:00Z", "Animation excerpt", channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n3d25w-1200=s88-c-k-c0x00ffffff-no-rj"),
    YouTubeVideo("eRsGyueVLvQ", "Sintel - 4K Epic Film Sequence", "Blender Foundation", "2023-05-01T00:00:00Z", "Open movie excerpt", channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n3d25w-1200=s88-c-k-c0x00ffffff-no-rj"),
    YouTubeVideo("R6MlUcmgul8", "Tears of Steel - Sci-Fi Action Scene", "Blender Foundation", "2023-03-01T00:00:00Z", "Sci-fi VFX showcase", channelAvatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n3d25w-1200=s88-c-k-c0x00ffffff-no-rj")
)

@Composable
fun ShortsFeed(
    padding: PaddingValues,
    approvedVideos: List<YouTubeVideo>,
    onOpenWatchPage: (YouTubeVideo) -> Unit
) {
    val context = LocalContext.current
    val allShorts = remember(approvedVideos) {
        if (approvedVideos.isNotEmpty()) (approvedVideos.take(10) + SHORTS_PREVIEW_LIST).distinctBy { it.videoId }
        else SHORTS_PREVIEW_LIST
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentVideo = allShorts.getOrElse(currentIndex) { SHORTS_PREVIEW_LIST.first() }
    var isLiked by remember(currentVideo.videoId) { mutableStateOf(WatchHistoryStore.isLiked(context, currentVideo.videoId)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.Black)
            .testTag("shorts_feed_screen")
    ) {
        // Player Area taking primary center
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            YouTubeEmbedPlayer(
                videoId = currentVideo.videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                autoplay = true
            )
        }

        // Overlay Navigation Controls (Top Right Up/Down)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (currentIndex > 0) currentIndex-- },
                enabled = currentIndex > 0,
                modifier = Modifier
                    .background(Color(0x88000000), CircleShape)
                    .testTag("shorts_prev_button")
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Short", tint = Color.White)
            }
            IconButton(
                onClick = { if (currentIndex < allShorts.size - 1) currentIndex++ },
                enabled = currentIndex < allShorts.size - 1,
                modifier = Modifier
                    .background(Color(0x88000000), CircleShape)
                    .testTag("shorts_next_button")
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Short", tint = Color.White)
            }
        }

        // Overlay Right Action Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Like
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        isLiked = WatchHistoryStore.toggleLike(context, currentVideo.videoId)
                    },
                    modifier = Modifier
                        .background(Color(0x88000000), CircleShape)
                        .testTag("shorts_like_button")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like Short",
                        tint = if (isLiked) Color(0xFFFF0033) else Color.White
                    )
                }
                Text("Like", color = Color.White, fontSize = 12.sp)
            }

            // Share
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${currentVideo.videoId}")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share short"))
                    },
                    modifier = Modifier
                        .background(Color(0x88000000), CircleShape)
                        .testTag("shorts_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Short", tint = Color.White)
                }
                Text("Share", color = Color.White, fontSize = 12.sp)
            }

            // Watch full video
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onOpenWatchPage(currentVideo) },
                    modifier = Modifier
                        .background(Color(0x88000000), CircleShape)
                        .testTag("shorts_open_watch_button")
                ) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "Full Watch Page", tint = Color.White)
                }
                Text("Full", color = Color.White, fontSize = 12.sp)
            }
        }

        // Overlay Bottom Video Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 24.dp)
                .background(Color(0x88000000), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = Color(0xFFFF0033)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(currentVideo.channelTitle.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "@${currentVideo.channelTitle.replace(" ", "")}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = currentVideo.title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Short ${currentIndex + 1} of ${allShorts.size}",
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp
            )
        }
    }
}

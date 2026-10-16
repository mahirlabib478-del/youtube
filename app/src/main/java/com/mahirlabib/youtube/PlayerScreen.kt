package com.mahirlabib.youtube

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import android.view.View
import android.webkit.WebChromeClient
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(
    video: YouTubeVideo,
    padding: PaddingValues,
    relatedVideos: List<YouTubeVideo>,
    onClose: () -> Unit,
    onPlayRelated: (YouTubeVideo) -> Unit,
    onShowCustomView: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideCustomView: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val videoId = video.videoId
    var isFullscreenExpanded by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(isFullscreenExpanded) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreenExpanded) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    BackHandler {
        if (isFullscreenExpanded) {
            isFullscreenExpanded = false
        } else {
            onClose()
        }
    }

    var descriptionExpanded by remember(videoId) { mutableStateOf(false) }
    var comments by remember(videoId) { mutableStateOf<List<YouTubeComment>>(emptyList()) }
    var commentsLoading by remember(videoId) { mutableStateOf(true) }
    var commentsMessage by remember(videoId) { mutableStateOf("") }
    var isLiked by remember(videoId) { mutableStateOf(WatchHistoryStore.isLiked(context, videoId)) }
    var isSaved by remember(videoId) { mutableStateOf(WatchHistoryStore.isInWatchlist(context, videoId)) }
    var isSubscribed by remember(video.channelTitle) { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        commentsLoading = true
        commentsMessage = ""
        runCatching {
            YouTubeDataRepository.publicComments(videoId, BuildConfig.YOUTUBE_API_KEY)
        }.onSuccess {
            comments = it
            commentsMessage = if (it.isEmpty()) "No public comments returned by YouTube." else ""
        }.onFailure {
            comments = emptyList()
            commentsMessage = it.message ?: "Comments unavailable."
        }
        commentsLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isFullscreenExpanded) Modifier.background(Color.Black)
                else Modifier.padding(padding).background(MaterialTheme.colorScheme.background)
            )
            .testTag("player_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreenExpanded) {
                // Top Back Navigation bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to feed"
                        )
                    }
                    Text(
                        text = "Playing Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isFullscreenExpanded = true },
                        modifier = Modifier.testTag("top_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen"
                        )
                    }
                }
            }

            // Embedded Player Container - Seamlessly transitions between 16:9 and Fullscreen
            Box(
                modifier = if (isFullscreenExpanded) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag("player_fullscreen_mode")
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                }
            ) {
                YouTubeEmbedPlayer(
                    videoId = videoId,
                    modifier = Modifier.fillMaxSize(),
                    autoplay = true,
                    onShowCustomView = onShowCustomView,
                    onHideCustomView = onHideCustomView
                )

                if (isFullscreenExpanded) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color(0x99000000))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { isFullscreenExpanded = false },
                                modifier = Modifier.testTag("fullscreen_back_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = video.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { isFullscreenExpanded = false },
                            modifier = Modifier.testTag("exit_fullscreen_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (!isFullscreenExpanded) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Video Title & Meta
            item {
                Column {
                    Text(
                        text = video.title.ifBlank { "YouTube video" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (video.viewCount.isNotBlank()) {
                            Text(
                                text = video.viewCount,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = formatRelativeTime(video.publishedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Channel Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ChannelAvatar(
                            channelTitle = video.channelTitle,
                            avatarUrl = video.channelAvatarUrl,
                            size = 40.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = video.channelTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Verified Channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { isSubscribed = !isSubscribed },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0F0F0F),
                            contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("subscribe_button")
                    ) {
                        Text(if (isSubscribed) "Subscribed" else "Subscribe")
                    }
                }
            }

            // Action Buttons (Like, Share, Save)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isLiked = WatchHistoryStore.toggleLike(context, videoId)
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("like_button")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFFFF0033) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLiked) "Liked" else "Like", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, video.title)
                                putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share video"))
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val watched = WatchedVideo(
                                videoId = video.videoId,
                                title = video.title,
                                channel = video.channelTitle,
                                watchedAt = System.currentTimeMillis(),
                                description = video.description
                            )
                            isSaved = WatchHistoryStore.toggleWatchlist(context, watched)
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_watchlist_button")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (isSaved) Color(0xFFFF0033) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isSaved) "Saved" else "Save", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { isFullscreenExpanded = true },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Full", maxLines = 1)
                    }
                }
            }

            // Description Card (Expandable)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { descriptionExpanded = !descriptionExpanded }
                        .testTag("description_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (descriptionExpanded) "Show less ▲" else "Show more ▼",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFF0033)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (descriptionExpanded) {
                                video.description.ifBlank { "No description was provided for this video." }
                            } else {
                                video.description.take(120).ifBlank { "Tap to view description." } +
                                    if (video.description.length > 120) "…" else ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Comments Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Comments (${comments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (commentsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // Comments List / Status
            if (comments.isNotEmpty()) {
                items(comments, key = { it.author + it.publishedAt }) { comment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (comment.authorAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = comment.authorAvatarUrl,
                                    contentDescription = comment.author,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = comment.author.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = comment.author,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatRelativeTime(comment.publishedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            } else if (!commentsLoading) {
                item {
                    Text(
                        text = commentsMessage.ifBlank { "No comments available for this video." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // More Videos / Up Next
            if (relatedVideos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "More to Watch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(relatedVideos.filterNot { it.videoId == videoId }.take(5), key = { it.videoId }) { rel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayRelated(rel) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = "https://i.ytimg.com/vi/${rel.videoId}/hqdefault.jpg",
                                contentDescription = rel.title,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(65.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rel.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = rel.channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

package com.mahirlabib.youtube

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
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
    onHideCustomView: (() -> Unit)? = null,
    onOpenChannel: (channelId: String, channelTitle: String, channelAvatarUrl: String) -> Unit = { _, _, _ -> },
    miniMode: Boolean = false,
    modifier: Modifier = Modifier,
    onExpandMiniPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val videoId = video.videoId

    var isFullscreenExpanded by remember(videoId) { mutableStateOf(false) }
    var isFullscreenControlsVisible by remember(videoId, isFullscreenExpanded) { mutableStateOf(true) }

    // Auto-hide fullscreen controls after 3.5 seconds
    LaunchedEffect(isFullscreenExpanded, isFullscreenControlsVisible) {
        if (isFullscreenExpanded && isFullscreenControlsVisible) {
            delay(3500)
            isFullscreenControlsVisible = false
        }
    }

    LaunchedEffect(isFullscreenExpanded) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreenExpanded) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isFullscreenControlsVisible = true
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
    var nextPageToken by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoadingMoreComments by remember(videoId) { mutableStateOf(false) }

    var isLiked by remember(videoId) { mutableStateOf(WatchHistoryStore.isLiked(context, videoId)) }
    var isDisliked by remember(videoId) { mutableStateOf(false) }
    var isSaved by remember(videoId) { mutableStateOf(WatchHistoryStore.isInWatchlist(context, videoId)) }
    var isSubscribed by remember(video.channelTitle) { mutableStateOf(false) }

    fun loadInitialComments() {
        commentsLoading = true
        commentsMessage = ""
        nextPageToken = null
        scope.launch {
            runCatching {
                YouTubeDataRepository.publicCommentsPage(videoId, BuildConfig.YOUTUBE_API_KEY)
            }.onSuccess { page ->
                comments = page.comments
                nextPageToken = page.nextPageToken
                commentsMessage = if (page.comments.isEmpty()) "No public comments returned by YouTube." else ""
            }.onFailure { err ->
                comments = emptyList()
                commentsMessage = err.message ?: "Comments unavailable."
            }
            commentsLoading = false
        }
    }

    fun loadMoreComments() {
        val token = nextPageToken ?: return
        if (isLoadingMoreComments) return
        isLoadingMoreComments = true
        scope.launch {
            runCatching {
                YouTubeDataRepository.publicCommentsPage(videoId, BuildConfig.YOUTUBE_API_KEY, token)
            }.onSuccess { page ->
                comments = (comments + page.comments).distinctBy { it.author + it.publishedAt + it.text }
                nextPageToken = page.nextPageToken
            }
            isLoadingMoreComments = false
        }
    }

    LaunchedEffect(videoId) {
        loadInitialComments()
    }

    Box(
        modifier = if (miniMode) {
            modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .testTag("player_screen_mini_host")
        } else {
            modifier
                .fillMaxSize()
                .then(
                    if (isFullscreenExpanded) Modifier.background(Color.Black)
                    else Modifier.padding(padding).background(MaterialTheme.colorScheme.background)
                )
                .testTag("player_screen")
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreenExpanded && !miniMode) {
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
                modifier = if (isFullscreenExpanded || miniMode) {
                    Modifier.fillMaxSize().background(Color.Black)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                }
            ) {
                if (miniMode) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable { onExpandMiniPlayer() }
                    ) {
                        YouTubeEmbedPlayer(
                            videoId = videoId,
                            modifier = Modifier.fillMaxSize(),
                            autoplay = true,
                            onShowCustomView = onShowCustomView,
                            onHideCustomView = onHideCustomView
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                                .background(Color(0x99000000), CircleShape)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Close mini player", tint = Color.White)
                        }
                    }
                } else {
                    YouTubeEmbedPlayer(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize(),
                        autoplay = true,
                        onShowCustomView = onShowCustomView,
                        onHideCustomView = onHideCustomView
                    )
                }

                if (isFullscreenExpanded) {
                    // Tap detector overlay to toggle controls visibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                isFullscreenControlsVisible = !isFullscreenControlsVisible
                            }
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isFullscreenControlsVisible,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xCC000000))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
            }

            if (!isFullscreenExpanded && !miniMode) {
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onOpenChannel(video.channelId, video.channelTitle, video.channelAvatarUrl)
                                    }
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
                                        text = "Verified Channel • View Profile",
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

                    // YouTube-style Action Pill Bar (No text cutting, smooth horizontal scroll)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Combined Like & Dislike Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.testTag("like_dislike_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    // Like Section
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                isLiked = WatchHistoryStore.toggleLike(context, videoId)
                                                if (isLiked) isDisliked = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                            contentDescription = "Like",
                                            tint = if (isLiked) Color(0xFFFF0033) else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isLiked) "Liked" else "Like",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Divider
                                    Box(
                                        modifier = Modifier
                                            .height(18.dp)
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                    )

                                    // Dislike Section
                                    Box(
                                        modifier = Modifier
                                            .clickable {
                                                isDisliked = !isDisliked
                                                if (isDisliked && isLiked) {
                                                    isLiked = WatchHistoryStore.toggleLike(context, videoId)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                            contentDescription = "Dislike",
                                            tint = if (isDisliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 2. Share Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, video.title)
                                        putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$videoId")
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share video"))
                                },
                                modifier = Modifier.testTag("share_pill_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                    Text("Share", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 3. Save Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                modifier = Modifier.testTag("save_pill_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Save",
                                        tint = if (isSaved) Color(0xFFFF0033) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isSaved) "Saved" else "Save",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // 4. Fullscreen Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { isFullscreenExpanded = true },
                                modifier = Modifier.testTag("fullscreen_pill_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", modifier = Modifier.size(20.dp))
                                    Text("Full screen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 5. Copy Link Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("YouTube Link", "https://www.youtube.com/watch?v=$videoId")
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_link_pill_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(18.dp))
                                    Text("Copy link", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
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
                                    text = "Comments (${comments.size}${if (nextPageToken != null) "+" else ""})",
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
                        items(comments, key = { it.author + it.publishedAt + it.text.hashCode() }) { comment ->
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

                        // Load More Comments button if pagination available
                        if (nextPageToken != null) {
                            item {
                                Button(
                                    onClick = { loadMoreComments() },
                                    enabled = !isLoadingMoreComments,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("load_more_comments_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    if (isLoadingMoreComments) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Loading more comments…")
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Load More Comments (${comments.size} loaded)")
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

package com.mahirlabib.youtube

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlin.math.absoluteValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
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
    onSubscriptionChanged: (video: YouTubeVideo, subscribed: Boolean) -> Unit = { _, _ -> },
    miniMode: Boolean = false,
    modifier: Modifier = Modifier,
    onExpandMiniPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val videoId = video.videoId

    var isFullscreenExpanded by remember(videoId) { mutableStateOf(false) }
    var resumeTrigger by remember(videoId) { mutableIntStateOf(0) }
    var customFullscreenView by remember(videoId) { mutableStateOf<View?>(null) }
    var customViewCallback by remember(videoId) { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var showControls by remember(videoId) { mutableStateOf(true) }

    val exitFullscreen: () -> Unit = {
        val cb = customViewCallback
        val v = customFullscreenView
        customFullscreenView = null
        customViewCallback = null
        isFullscreenExpanded = false
        showControls = true
        if (cb != null) {
            try {
                cb.onCustomViewHidden()
            } catch (e: Exception) {
                Log.w("PlayerScreen", "Error dismissing customViewCallback", e)
            }
        }
        if (v != null) {
            try {
                (v.parent as? ViewGroup)?.removeView(v)
            } catch (e: Exception) {
                Log.w("PlayerScreen", "Error removing customFullscreenView", e)
            }
        }
    }

    val enterFullscreen: () -> Unit = {
        isFullscreenExpanded = true
        resumeTrigger++
        showControls = true
    }

    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    val toggleControls = {
        val now = System.currentTimeMillis()
        if (now - lastInteractionTime > 350) {
            lastInteractionTime = now
            showControls = !showControls
        }
    }

    // Auto-hide controls together after 3.8 seconds of inactivity
    LaunchedEffect(showControls, videoId, isFullscreenExpanded) {
        if (showControls) {
            delay(3800)
            showControls = false
        }
    }

    // 1:1 synchronization with YouTube player's touch interaction
    DisposableEffect(videoId) {
        SharedPlayerManager.onUserInteraction = {
            toggleControls()
        }
        onDispose {
            SharedPlayerManager.onUserInteraction = null
        }
    }

    LaunchedEffect(isFullscreenExpanded) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        showControls = true
        if (isFullscreenExpanded) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        delay(60)
        resumeTrigger++
        delay(200)
        resumeTrigger++
    }

    DisposableEffect(activity, videoId) {
        onDispose {
            customFullscreenView?.let { v ->
                (v.parent as? ViewGroup)?.removeView(v)
            }
            customViewCallback?.onCustomViewHidden()
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    BackHandler {
        if (isFullscreenExpanded || customFullscreenView != null) {
            exitFullscreen()
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
    var isSubscribed by remember(video.channelId, video.channelTitle) {
        mutableStateOf(
            ApprovedChannelStore.load(context).any { channel ->
                (video.channelId.isNotBlank() && channel.channelId == video.channelId) ||
                    channel.title.equals(video.channelTitle, ignoreCase = true)
            }
        )
    }

    val isThisVideoInBg = BackgroundAudioState.isRunning && BackgroundAudioState.currentVideoId == videoId

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        BackgroundAudioService.start(context, video.videoId, video.title, video.channelTitle)
        Toast.makeText(context, "Background Audio Active: Sound will play when screen is off or app is closed!", Toast.LENGTH_LONG).show()
    }

    fun toggleBackgroundAudio() {
        if (isThisVideoInBg) {
            BackgroundAudioService.stopForegroundOnly(context)
            Toast.makeText(context, "Background audio disabled", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    BackgroundAudioService.start(context, video.videoId, video.title, video.channelTitle)
                    Toast.makeText(context, "Background Audio Active: Sound will play when screen is off or app is closed!", Toast.LENGTH_LONG).show()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                BackgroundAudioService.start(context, video.videoId, video.title, video.channelTitle)
                Toast.makeText(context, "Background Audio Active: Sound will play when screen is off or app is closed!", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(video.videoId) {
        showControls = true
        if (SharedPlayerManager.currentVideo?.videoId != video.videoId) {
            SharedPlayerManager.playVideo(context, video)
        }
    }

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
                .clipToBounds()
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
            // Embedded Player Container - Seamlessly transitions between 16:9 and Fullscreen
            Box(
                modifier = if (isFullscreenExpanded || miniMode) {
                    Modifier.fillMaxSize()
                        .clip(if (miniMode) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp))
                        .clipToBounds()
                        .background(Color.Black)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                }
            ) {
                // Video player layer with gesture detection for tapping and swiping down
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!miniMode) {
                                Modifier.pointerInput(isFullscreenExpanded) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                        var hasSwipedDown = false
                                        var hasMoved = false
                                        while (true) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            val deltaY = change.position.y - down.position.y
                                            val deltaX = change.position.x - down.position.x
                                            if (deltaX.absoluteValue > 18f || deltaY.absoluteValue > 18f) {
                                                hasMoved = true
                                            }
                                            if (!isFullscreenExpanded && deltaY > 65f && deltaY > deltaX.absoluteValue * 1.4f) {
                                                hasSwipedDown = true
                                                onClose()
                                                break
                                            }
                                            if (!change.pressed) {
                                                if (!hasSwipedDown && !hasMoved) {
                                                    toggleControls()
                                                }
                                                break
                                            }
                                        }
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    YouTubeEmbedPlayer(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize(),
                        autoplay = !isThisVideoInBg,
                        resumeTrigger = resumeTrigger,
                        onShowCustomView = { view, callback ->
                            customFullscreenView = view
                            customViewCallback = callback
                            isFullscreenExpanded = true
                            showControls = true
                        },
                        onHideCustomView = {
                            val v = customFullscreenView
                            customFullscreenView = null
                            customViewCallback = null
                            isFullscreenExpanded = false
                            showControls = true
                            if (v != null) {
                                try {
                                    (v.parent as? ViewGroup)?.removeView(v)
                                } catch (e: Exception) {
                                    Log.w("PlayerScreen", "Error removing custom view on hide", e)
                                }
                            }
                        }
                    )

                    if (customFullscreenView != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                                (customFullscreenView?.parent as? ViewGroup)?.removeView(customFullscreenView)
                                customFullscreenView ?: View(it)
                            }
                        )
                    }
                }

                if (miniMode) {
                    // Transparent tap target and close control sit above the same WebView.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onExpandMiniPlayer() }
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                                .background(Color(0x99000000), CircleShape)
                                .testTag("mini_player_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close mini player", tint = Color.White)
                        }
                    }
                }

                if (isFullscreenExpanded || customFullscreenView != null) {
                    // Fullscreen Top-left Back button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        IconButton(
                            onClick = exitFullscreen,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(46.dp)
                                .background(Color(0x77000000), CircleShape)
                                .testTag("fullscreen_top_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Bottom-right floating exit button when fullscreen controls are revealed
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        IconButton(
                            onClick = exitFullscreen,
                            modifier = Modifier
                                .padding(18.dp)
                                .size(48.dp)
                                .background(Color(0x77000000), CircleShape)
                                .testTag("fullscreen_bottom_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Portrait 16:9 Controls overlay: Synchronized back and fullscreen buttons
                if (!isFullscreenExpanded && !miniMode) {
                    // Top-left corner Back button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(44.dp)
                                .background(Color(0x77000000), CircleShape)
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Bottom-right corner Fullscreen button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        IconButton(
                            onClick = enterFullscreen,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(44.dp)
                                .background(Color(0x77000000), CircleShape)
                                .testTag("player_corner_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
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
                                onClick = {
                    val nextSubscribed = !isSubscribed
                    isSubscribed = nextSubscribed
                    onSubscriptionChanged(video, nextSubscribed)
                },
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

                            // 2. Background Audio Pill (Plays even when exiting app)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isThisVideoInBg) Color(0xFFFF0033) else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { toggleBackgroundAudio() },
                                modifier = Modifier.testTag("background_audio_pill_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isThisVideoInBg) Icons.Default.Stop else Icons.Default.Headphones,
                                        contentDescription = "Background audio",
                                        tint = if (isThisVideoInBg) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isThisVideoInBg) "Playing in BG" else "Play in BG",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isThisVideoInBg) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // PiP remains available as an explicit action, not on every Home press.
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { (activity as? MainActivity)?.enterPipMode() },
                                modifier = Modifier.testTag("pip_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in picture",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("PiP", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            // 3. Share Pill
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
                                onClick = {
                                    isFullscreenExpanded = true
                                },
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

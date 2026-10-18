package com.mahirlabib.youtube

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * YouTube Embed Player Composable:
 * Attaches the unified, continuous SharedPlayerManager player into Compose.
 * Preserves the exact playing position and state across orientation changes,
 * full-screen transitions, and background audio transitions without reloading.
 */
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    resumeTrigger: Int = 0,
    onPlaybackError: ((String) -> Unit)? = null,
    onShowCustomView: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideCustomView: (() -> Unit)? = null,
    onControlsVisibilityChanged: ((Boolean) -> Unit)? = null
) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID: $videoId" }

    val context = LocalContext.current

    LaunchedEffect(onShowCustomView, onHideCustomView) {
        SharedPlayerManager.onShowCustomViewCallback = onShowCustomView
        SharedPlayerManager.onHideCustomViewCallback = onHideCustomView
    }

    LaunchedEffect(SharedPlayerManager.isControlsVisible) {
        onControlsVisibilityChanged?.invoke(SharedPlayerManager.isControlsVisible)
    }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger > 0) {
            SharedPlayerManager.resume()
        }
    }

    val errorMessage = SharedPlayerManager.playbackError
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            onPlaybackError?.invoke(errorMessage)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!BackgroundAudioState.isRunning) {
                SharedPlayerManager.detachFromContainer()
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("youtube_embed_player_container")
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    SharedPlayerManager.attachToContainer(this)
                }
            },
            update = { container ->
                SharedPlayerManager.attachToContainer(container)
            }
        )

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0101010))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val cur = SharedPlayerManager.currentVideo
                            if (cur != null) {
                                SharedPlayerManager.playVideo(context, cur)
                            } else {
                                SharedPlayerManager.playVideo(
                                    context,
                                    YouTubeVideo(videoId, "YouTube Video", "", "")
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

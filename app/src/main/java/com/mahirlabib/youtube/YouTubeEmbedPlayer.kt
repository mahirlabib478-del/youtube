package com.mahirlabib.youtube

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Reliable playback fallback: show the real YouTube thumbnail in-app and open
 * the official YouTube watch page for playback. This avoids a blank WebView
 * when YouTube's embedded player rejects the app with Error 153.
 */
@Composable
fun YouTubeEmbedPlayer(videoId: String, modifier: Modifier = Modifier) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID" }
    val context = LocalContext.current

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = "YouTube video thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(16.dp)
        ) {
            Text(
                "Play this video on YouTube",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = {
                    val watchUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                    val intent = Intent(Intent.ACTION_VIEW, watchUri)
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("▶  Play video")
            }
            Text(
                "Opens the official YouTube app or website",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

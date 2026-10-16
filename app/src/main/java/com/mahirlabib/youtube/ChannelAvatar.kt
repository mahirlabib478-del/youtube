package com.mahirlabib.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

/**
 * YouTube-style Channel Avatar with guaranteed colorful fallback.
 * Ensures channel profile icons NEVER render as empty or black circles,
 * even when network is offline, image URLs are invalid, or avatar fails to load.
 */
@Composable
fun ChannelAvatar(
    channelTitle: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val cleanTitle = channelTitle.trim().ifBlank { "YouTube" }

    // Consistent distinct vibrant background color per channel name
    val avatarBgColor = remember(cleanTitle) {
        val colors = listOf(
            Color(0xFFE53935), // Red
            Color(0xFF1E88E5), // Blue
            Color(0xFF43A047), // Green
            Color(0xFFFB8C00), // Orange
            Color(0xFF8E24AA), // Purple
            Color(0xFF00ACC1), // Cyan/Teal
            Color(0xFFD81B60), // Pink
            Color(0xFF3949AB), // Indigo
            Color(0xFF00897B), // Mint/Teal
            Color(0xFFF4511E)  // Deep Orange
        )
        val hash = (cleanTitle.hashCode() and 0x7FFFFFFF) % colors.size
        colors[hash]
    }

    val initial = remember(cleanTitle) {
        cleanTitle.take(1).uppercase()
    }

    val context = LocalContext.current
    val normalizedUrl = avatarUrl.trim()
    val isValidAvatarUrl = normalizedUrl.isNotBlank() &&
        !normalizedUrl.contains("hqdefault") &&
        !normalizedUrl.contains("vi/") &&
        !normalizedUrl.contains("1200=") &&
        normalizedUrl.startsWith("http")

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarBgColor),
        contentAlignment = Alignment.Center
    ) {
        // High-contrast initial letter badge
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.44f).sp
        )

        // Remote image layered on top; if it loads successfully, it smoothly overlays the letter badge
        if (isValidAvatarUrl) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(normalizedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = cleanTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(avatarBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.44f).sp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(avatarBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.44f).sp
                        )
                    }
                },
                success = {
                    SubcomposeAsyncImageContent()
                }
            )
        }
    }
}

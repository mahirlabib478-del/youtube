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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * YouTube-style search feed that searches across all videos and channels.
 * Features:
 * - Real-time filtering as you type, plus Enter/Search keyboard action.
 * - Matching channel header card for quick channel discovery.
 * - Comprehensive multi-field matching (title, channel, description).
 * - Direct embedded playback on tap.
 */
@Composable
fun SearchFeed(
    padding: PaddingValues,
    cachedVideos: List<YouTubeVideo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlay: (YouTubeVideo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val channels = remember(context) { ApprovedChannelStore.load(context) }

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(cachedVideos) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filterChips = listOf("All", "Android", "Google", "Science", "Blender", "Tutorial", "Tech")

    fun performSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) {
            searchResults = cachedVideos
            return
        }

        scope.launch {
            isSearching = true
            runCatching {
                YouTubeDataRepository.searchChannelVideos(context, BuildConfig.YOUTUBE_API_KEY, trimmed)
            }.onSuccess { list ->
                searchResults = if (list.isNotEmpty()) list else {
                    cachedVideos.filter {
                        it.title.contains(trimmed, ignoreCase = true) ||
                            it.channelTitle.contains(trimmed, ignoreCase = true) ||
                            it.description.contains(trimmed, ignoreCase = true)
                    }
                }
            }.onFailure {
                searchResults = cachedVideos.filter {
                    it.title.contains(trimmed, ignoreCase = true) ||
                        it.channelTitle.contains(trimmed, ignoreCase = true) ||
                        it.description.contains(trimmed, ignoreCase = true)
                }
            }
            isSearching = false
        }
    }

    // Live search when query changes
    // Live search when query changes - instant local match + debounced online YouTube search
    LaunchedEffect(query, cachedVideos) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchResults = cachedVideos
            return@LaunchedEffect
        }
        // 1. Instant local filter
        val localFiltered = cachedVideos.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.channelTitle.contains(trimmed, ignoreCase = true) ||
                it.description.contains(trimmed, ignoreCase = true)
        }
        searchResults = localFiltered

        // 2. Debounced online YouTube search
        kotlinx.coroutines.delay(650)
        isSearching = true
        runCatching {
            YouTubeDataRepository.searchChannelVideos(context, BuildConfig.YOUTUBE_API_KEY, trimmed)
        }.onSuccess { list ->
            if (list.isNotEmpty()) {
                searchResults = list
            }
        }
        isSearching = false
    }

    // Find any channel matching current search query
    val matchingChannel = if (query.isNotBlank()) {
        channels.find { it.title.contains(query, ignoreCase = true) }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("search_feed_screen")
    ) {
        // Search Input Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_input"),
                placeholder = { Text("Search any video or channel on YouTube…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        performSearch(query)
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )

            // Dedicated Search Button
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    performSearch(query)
                },
                modifier = Modifier
                    .background(Color(0xFFFF0033), CircleShape)
                    .size(46.dp)
                    .testTag("search_submit_button")
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Quick Topic Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterChips.forEach { chip ->
                val isSelected = if (chip == "All") query.isBlank() else query.equals(chip, ignoreCase = true)
                Surface(
                    onClick = {
                        if (chip == "All") {
                            onQueryChange("")
                            searchResults = cachedVideos
                        } else {
                            onQueryChange(chip)
                            performSearch(chip)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Results Count Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (query.isBlank()) "All Videos (${searchResults.size})"
                else "Results for \"$query\" (${searchResults.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Search Results List
        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "No videos found for \"$query\"",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Try searching for tech, science, or channel names like Google or Veritasium.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // If a channel matches, render a dedicated YouTube Channel header
                if (matchingChannel != null) {
                    item(key = "matching_channel_${matchingChannel.channelId}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ChannelAvatar(
                                    channelTitle = matchingChannel.title,
                                    avatarUrl = matchingChannel.avatarUrl,
                                    size = 56.dp
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = matchingChannel.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Saved Channel • Videos below",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Video Result Items styled like actual YouTube
                items(searchResults, key = { it.videoId }) { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(video) }
                            .testTag("search_result_${video.videoId}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            // Thumbnail 16:9
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color(0xFF202020))
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            ) {
                                AsyncImage(
                                    model = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                                    contentDescription = video.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Direct play overlay badge
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(46.dp),
                                    shape = CircleShape,
                                    color = Color(0xCC000000)
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

                            // Video Information Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                ChannelAvatar(
                                    channelTitle = video.channelTitle,
                                    avatarUrl = video.channelAvatarUrl,
                                    size = 36.dp
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "${video.channelTitle} • ${formatRelativeTime(video.publishedAt)}",
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

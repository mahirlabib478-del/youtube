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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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
    var searchResults by remember { mutableStateOf(cachedVideos) }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf("Search across all saved channels using YouTube Data API v3.") }
    val recentKeywords = listOf("Android", "Kotlin", "Compose", "AI", "Google", "Tutorial", "Review")

    fun executeSearch(searchTerm: String) {
        val q = searchTerm.trim()
        if (q.isBlank()) return
        scope.launch {
            isSearching = true
            searchStatus = "Searching saved channels on YouTube…"
            runCatching {
                YouTubeDataRepository.searchChannelVideos(context, BuildConfig.YOUTUBE_API_KEY, q)
            }.onSuccess {
                searchResults = it
                searchStatus = if (it.isEmpty()) "No public videos found matching \"$q\"."
                else "Found ${it.size} video(s) for \"$q\"."
            }.onFailure {
                // If API fails or is unconfigured, filter locally from cache
                val localMatches = cachedVideos.filter { v ->
                    v.title.contains(q, ignoreCase = true) ||
                        v.channelTitle.contains(q, ignoreCase = true) ||
                        v.description.contains(q, ignoreCase = true)
                }
                searchResults = localMatches
                searchStatus = (it.message ?: "Search API request failed.") +
                    if (localMatches.isNotEmpty()) " Showing ${localMatches.size} cached result(s)." else ""
            }
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("search_feed_screen")
    ) {
        // Search Input Field
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("search_text_input"),
            label = { Text("Search videos in saved channels") },
            placeholder = { Text("Enter title, topic, or keyword…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true
        )

        // Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentKeywords.forEach { tag ->
                Surface(
                    onClick = {
                        onQueryChange(tag)
                        executeSearch(tag)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Search Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { executeSearch(query) },
                enabled = !isSearching && query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033)),
                modifier = Modifier.testTag("search_action_button")
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching…")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search")
                }
            }
            Text(
                text = "Saved channels only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status / Helper text
        Text(
            text = searchStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

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
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (query.isBlank()) "Search any keyword to find videos from your channels."
                        else "No videos found. Try a different query.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(searchResults, key = { it.videoId }) { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(video) }
                            .testTag("search_result_${video.videoId}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF202020))
                            ) {
                                AsyncImage(
                                    model = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                                    contentDescription = video.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    lineHeight = 18.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = video.channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatRelativeTime(video.publishedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onPlay(video) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Watch", tint = Color(0xFFFF0033))
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.mahirlabib.youtube

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun ChannelManagerScreen(
    padding: PaddingValues,
    onPlay: (YouTubeVideo) -> Unit,
    onVideosLoaded: (List<YouTubeVideo>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputChannelId by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(ApprovedChannelStore.load(context)) }
    var loadedVideos by remember { mutableStateOf(LocalVideoStore.load(context)) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var isAddingChannel by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Manage saved YouTube channels on this device.") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("channel_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Add Channel Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFFFF0033))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add YouTube Channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Enter a 24-character YouTube Channel ID (starts with UC). Real channel title and avatar will be fetched automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputChannelId,
                        onValueChange = { inputChannelId = it.trim() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_id_input"),
                        label = { Text("Channel ID (e.g. UC_x5XG1OV2P6uZZ5FSM9Ttw)") },
                        placeholder = { Text("UCxxxxxxxxxxxxxxxxxxxxxx") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val id = inputChannelId.trim()
                            if (!isValidYouTubeChannelId(id)) {
                                statusMessage = "Invalid ID. Channel ID must start with 'UC' and contain 24 characters."
                                return@Button
                            }
                            scope.launch {
                                isAddingChannel = true
                                statusMessage = "Fetching channel details…"
                                val details = YouTubeDataRepository.fetchChannelDetails(BuildConfig.YOUTUBE_API_KEY, id)
                                channels = ApprovedChannelStore.add(context, id, details.title, details.avatarUrl)
                                inputChannelId = ""
                                statusMessage = "Added \"${details.title}\" to approved channels!"
                                isAddingChannel = false
                            }
                        },
                        enabled = !isAddingChannel && inputChannelId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_add_channel_button")
                    ) {
                        if (isAddingChannel) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Adding Channel…")
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Channel")
                        }
                    }
                }
            }
        }

        // Actions: Load uploads & Reset
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoadingFeed = true
                            statusMessage = "Loading public uploads from all ${channels.size} channel(s)…"
                            runCatching {
                                YouTubeDataRepository.latestVideos(context, BuildConfig.YOUTUBE_API_KEY)
                            }.onSuccess { vids ->
                                loadedVideos = vids
                                onVideosLoaded(vids)
                                channels = ApprovedChannelStore.load(context)
                                statusMessage = if (vids.isEmpty()) "No public videos found for these channels."
                                else "Successfully loaded ${vids.size} videos from your saved channels!"
                            }.onFailure { err ->
                                statusMessage = err.message ?: "Failed to load uploads. Check API key & internet."
                            }
                            isLoadingFeed = false
                        }
                    },
                    enabled = !isLoadingFeed && channels.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_load_videos_button")
                ) {
                    if (isLoadingFeed) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching Uploads…")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fetch Latest Videos From Channels")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        channels = ApprovedChannelStore.resetToDefaults(context)
                        statusMessage = "Reset to recommended developer & science channels."
                    }) {
                        Text("Reset Defaults")
                    }
                }
            }
        }

        // Channels List Header
        item {
            Text(
                text = "Saved Channels (${channels.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (channels.isEmpty()) {
            item {
                Text(
                    text = "No channels saved yet. Add a channel ID above or tap 'Reset Defaults'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Channel Cards
        items(channels, key = { it.channelId }) { channel ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (channel.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.avatarUrl,
                            contentDescription = channel.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = Color(0xFFFF0033)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(channel.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = channel.channelId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            channels = ApprovedChannelStore.remove(context, channel.channelId)
                            val remaining = loadedVideos.filterNot { it.channelId == channel.channelId }
                            loadedVideos = remaining
                            LocalVideoStore.save(context, remaining)
                            onVideosLoaded(remaining)
                            statusMessage = "Removed channel ${channel.title}."
                        },
                        modifier = Modifier.testTag("remove_channel_${channel.channelId}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Cached Videos Preview
        if (loadedVideos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cached Feed Videos (${loadedVideos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(loadedVideos.take(10), key = { it.videoId }) { video ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { onPlay(video) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}

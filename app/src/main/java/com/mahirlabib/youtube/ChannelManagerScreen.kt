package com.mahirlabib.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    onVideosLoaded: (List<YouTubeVideo>) -> Unit,
    onOpenChannel: (channelId: String, channelTitle: String, channelAvatarUrl: String) -> Unit = { _, _, _ -> }
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
                        text = "Enter any @handle, channel name, YouTube link, or channel ID. Real channel title and avatar will be fetched automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputChannelId,
                        onValueChange = { inputChannelId = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_id_input"),
                        label = { Text("Channel name, @handle, URL, or ID") },
                        placeholder = { Text("e.g. @Veritasium, @mkbhd, Fireship, or URL") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Veritasium" to "@Veritasium",
                            "MKBHD" to "@mkbhd",
                            "Fireship" to "@Fireship",
                            "Google Devs" to "@GoogleDevelopers",
                            "Android Devs" to "@AndroidDevelopers",
                            "The Verge" to "@TheVerge",
                            "Kurzgesagt" to "@Kurzgesagt",
                            "Blender" to "@BlenderOfficial"
                        ).forEach { (name, handle) ->
                            Surface(
                                onClick = { inputChannelId = handle },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "+ $name",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val input = inputChannelId.trim()
                            if (input.isBlank()) {
                                statusMessage = "Please enter a channel name, @handle, or URL."
                                return@Button
                            }
                            scope.launch {
                                isAddingChannel = true
                                statusMessage = "Searching and resolving channel \"$input\"…"
                                runCatching {
                                    YouTubeDataRepository.resolveChannelInput(context, BuildConfig.YOUTUBE_API_KEY, input)
                                }.onSuccess { details ->
                                    channels = ApprovedChannelStore.add(context, details.channelId, details.title, details.avatarUrl)
                                    inputChannelId = ""
                                    statusMessage = "Added \"${details.title}\"! Updating feed…"
                                    runCatching {
                                        YouTubeDataRepository.latestVideos(context, BuildConfig.YOUTUBE_API_KEY)
                                    }.onSuccess { vids ->
                                        loadedVideos = vids
                                        onVideosLoaded(vids)
                                        statusMessage = "Added \"${details.title}\" and updated feed with ${vids.size} videos!"
                                    }.onFailure {
                                        statusMessage = "Added \"${details.title}\" to saved channels."
                                    }
                                }.onFailure { error ->
                                    statusMessage = error.message ?: "Failed to find channel. Try entering the @handle or full channel URL."
                                }
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
                            Text("Finding Channel…")
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
                    ChannelAvatar(
                        channelTitle = channel.title,
                        avatarUrl = channel.avatarUrl,
                        size = 44.dp
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onOpenChannel(channel.channelId, channel.title, channel.avatarUrl)
                            }
                    ) {
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

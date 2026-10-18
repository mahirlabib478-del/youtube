package com.mahirlabib.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * State representing Background Audio playback and its foreground service.
 */
object BackgroundAudioState {
    var isRunning by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentVideoId by mutableStateOf<String?>(null)
    var currentVideoTitle by mutableStateOf("")
    var currentChannelTitle by mutableStateOf("")
}

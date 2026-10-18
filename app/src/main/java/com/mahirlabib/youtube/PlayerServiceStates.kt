package com.mahirlabib.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * State representing Background Audio foreground service.
 */
object BackgroundAudioState {
    var isRunning by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentVideoId by mutableStateOf<String?>(null)
    var currentVideoTitle by mutableStateOf("")
    var currentChannelTitle by mutableStateOf("")
}

/**
 * State representing Floating Window Player (SYSTEM_ALERT_WINDOW) service.
 */
object FloatingPlayerState {
    var isRunning by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentVideoId by mutableStateOf<String?>(null)
    var currentVideoTitle by mutableStateOf("")
    var currentChannelTitle by mutableStateOf("")
}

/**
 * Helper to check and request "Display over other apps" (SYSTEM_ALERT_WINDOW) permission.
 */
object OverlayPermissionHelper {
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

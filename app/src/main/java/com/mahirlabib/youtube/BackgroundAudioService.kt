package com.mahirlabib.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service that keeps playback active in the background and provides
 * persistent notification controls (Play/Pause, Stop).
 * Delegates directly to SharedPlayerManager to maintain a single continuous playback flow.
 */
class BackgroundAudioService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID)
                    ?: SharedPlayerManager.currentVideo?.videoId.orEmpty()
                val title = intent?.getStringExtra(EXTRA_TITLE)
                    ?: SharedPlayerManager.currentVideo?.title.orEmpty()
                val channel = intent?.getStringExtra(EXTRA_CHANNEL)
                    ?: SharedPlayerManager.currentVideo?.channelTitle.orEmpty()

                if (videoId.isNotBlank()) {
                    BackgroundAudioState.isRunning = true
                    BackgroundAudioState.isPlaying = SharedPlayerManager.isPlaying
                    BackgroundAudioState.currentVideoId = videoId
                    BackgroundAudioState.currentVideoTitle = title
                    BackgroundAudioState.currentChannelTitle = channel

                    startInForeground()

                    // Ensure webview timers are running and player continues without interruption
                    SharedPlayerManager.ensureActive()
                    if (!SharedPlayerManager.isPlaying) {
                        SharedPlayerManager.resume()
                    }
                }
            }
            ACTION_RESUME -> {
                SharedPlayerManager.resume()
                updateNotification()
            }
            ACTION_PAUSE -> {
                SharedPlayerManager.pause()
                updateNotification()
            }
            ACTION_STOP_FOREGROUND_ONLY -> {
                stopForegroundOnlyAndSelf()
            }
            ACTION_STOP -> {
                stopPlaybackAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Error starting foreground service", e)
        }
    }

    fun updateNotification() {
        if (!BackgroundAudioState.isRunning) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val videoId = BackgroundAudioState.currentVideoId.orEmpty()
        val videoTitle = BackgroundAudioState.currentVideoTitle
        val channelTitle = BackgroundAudioState.currentChannelTitle
        val isPlaying = SharedPlayerManager.isPlaying

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("active_video_id", videoId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val displayTitle: CharSequence = if (videoTitle.isNotBlank()) videoTitle else "YouTube Audio"
        val displayText: CharSequence = if (channelTitle.isNotBlank()) channelTitle else "Playing in background"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_youtube_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText("Background Playback")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for YouTube background playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Called when returning to the app or disabling background mode:
     * Removes the background notification without stopping or destroying the playback!
     */
    private fun stopForegroundOnlyAndSelf() {
        BackgroundAudioState.isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * Called when user explicitly taps "Stop" on the notification:
     * Completely stops playback and tears down the service.
     */
    private fun stopPlaybackAndSelf() {
        BackgroundAudioState.isRunning = false
        BackgroundAudioState.isPlaying = false
        BackgroundAudioState.currentVideoId = null
        SharedPlayerManager.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (activeService == this) {
            activeService = null
        }
        BackgroundAudioState.isRunning = false
        super.onDestroy()
    }

    companion object {
        var activeService: BackgroundAudioService? = null

        const val CHANNEL_ID = "youtube_background_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.mahirlabib.youtube.action.START"
        const val ACTION_PAUSE = "com.mahirlabib.youtube.action.PAUSE"
        const val ACTION_RESUME = "com.mahirlabib.youtube.action.RESUME"
        const val ACTION_STOP = "com.mahirlabib.youtube.action.STOP"
        const val ACTION_STOP_FOREGROUND_ONLY = "com.mahirlabib.youtube.action.STOP_FOREGROUND_ONLY"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"

        fun start(context: Context, videoId: String, title: String, channel: String) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHANNEL, channel)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Dismisses notification and stops service without stopping ongoing playback.
         */
        fun stopForegroundOnly(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_STOP_FOREGROUND_ONLY
            }
            context.startService(intent)
        }

        /**
         * Fully stops playback and dismisses notification.
         */
        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }
}

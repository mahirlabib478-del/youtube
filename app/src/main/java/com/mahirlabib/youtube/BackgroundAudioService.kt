package com.mahirlabib.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service that keeps playback active in the background and provides
 * persistent notification controls (Play/Pause, Rewind, Fast Forward, Stop).
 * Delegates directly to SharedPlayerManager to maintain continuous background audio.
 */
class BackgroundAudioService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (BackgroundAudioState.isRunning) {
                if (BackgroundAudioState.isPlaying) {
                    SharedPlayerManager.ensureActive()
                    SharedPlayerManager.checkAndResumeIfPaused()
                }
                heartbeatHandler.postDelayed(this, 1500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
        initMediaSession()
    }

    private fun startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.post(heartbeatRunnable)
    }

    private fun stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun initMediaSession() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            mediaSession = MediaSession(this, "YouTubeBackgroundAudio").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        SharedPlayerManager.resume()
                        updateNotification()
                    }
                    override fun onPause() {
                        SharedPlayerManager.pause()
                        updateNotification()
                    }
                    override fun onFastForward() {
                        SharedPlayerManager.seekBy(10)
                    }
                    override fun onRewind() {
                        SharedPlayerManager.seekBy(-10)
                    }
                    override fun onStop() {
                        stopPlaybackAndSelf()
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error initializing MediaSession", e)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                SharedPlayerManager.pause()
                                updateNotification()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                SharedPlayerManager.resume()
                                updateNotification()
                            }
                        }
                    }
                    .build()
                audioFocusRequest = afr
                am.requestAudioFocus(afr) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            SharedPlayerManager.pause()
                            updateNotification()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error requesting audio focus", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error abandoning audio focus", e)
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouTube:BackgroundAudioLock")?.apply {
                    setReferenceCounted(false)
                    acquire(8 * 60 * 60 * 1000L) // 8 hours max
                }
            }
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YouTube:WifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error acquiring WakeLock/WifiLock", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error releasing WakeLock/WifiLock", e)
        }
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
                    BackgroundAudioState.isPlaying = true
                    BackgroundAudioState.currentVideoId = videoId
                    BackgroundAudioState.currentVideoTitle = title
                    BackgroundAudioState.currentChannelTitle = channel

                    acquireLocks()
                    requestAudioFocus()
                    startInForeground()
                    startHeartbeat()

                    // Ensure webview timers are running and player continues without interruption
                    SharedPlayerManager.ensureActive()
                    SharedPlayerManager.resume()
                }
            }
            ACTION_RESUME -> {
                acquireLocks()
                requestAudioFocus()
                SharedPlayerManager.resume()
                BackgroundAudioState.isPlaying = true
                startHeartbeat()
                updateNotification()
            }
            ACTION_PAUSE -> {
                SharedPlayerManager.pause()
                BackgroundAudioState.isPlaying = false
                updateNotification()
            }
            ACTION_SEEK_BACK -> {
                SharedPlayerManager.seekBy(-10)
            }
            ACTION_SEEK_FORWARD -> {
                SharedPlayerManager.seekBy(10)
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
        val isPlaying = BackgroundAudioState.isPlaying || SharedPlayerManager.isPlaying

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

        val rewIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_SEEK_BACK
        }
        val rewPendingIntent = PendingIntent.getService(
            this,
            10,
            rewIntent,
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

        val ffIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_SEEK_FORWARD
        }
        val ffPendingIntent = PendingIntent.getService(
            this,
            11,
            ffIntent,
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
            .addAction(android.R.drawable.ic_media_rew, "-10s", rewPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_ff, "+10s", ffPendingIntent)
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
     * Removes the background notification without stopping or destroying the playback.
     */
    private fun stopForegroundOnlyAndSelf() {
        stopHeartbeat()
        releaseLocks()
        abandonAudioFocus()
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
        stopHeartbeat()
        releaseLocks()
        abandonAudioFocus()
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
        stopHeartbeat()
        releaseLocks()
        abandonAudioFocus()
        mediaSession?.release()
        mediaSession = null
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
        const val ACTION_SEEK_BACK = "com.mahirlabib.youtube.action.SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "com.mahirlabib.youtube.action.SEEK_FORWARD"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"

        fun start(context: Context, videoId: String, title: String, channel: String) {
            try {
                val intent = Intent(context, BackgroundAudioService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_VIDEO_ID, videoId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_CHANNEL, channel)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("BackgroundAudioService", "Error starting background service", e)
            }
        }

        fun stopForegroundOnly(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_STOP_FOREGROUND_ONLY
            }
            context.startService(intent)
        }

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

        fun seekBack(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_SEEK_BACK
            }
            context.startService(intent)
        }

        fun seekForward(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_SEEK_FORWARD
            }
            context.startService(intent)
        }
    }
}

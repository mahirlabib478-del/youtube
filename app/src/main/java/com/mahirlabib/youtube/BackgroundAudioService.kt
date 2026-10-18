package com.mahirlabib.youtube

import android.annotation.SuppressLint
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
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Observable state holder for background audio playback so Compose UI can react in real time.
 */
object BackgroundAudioState {
    var isRunning by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var currentVideoId by mutableStateOf<String?>(null)
    var currentVideoTitle by mutableStateOf("")
    var currentChannelTitle by mutableStateOf("")
}

/**
 * Foreground Service that plays YouTube audio via an offscreen WebView in the Service context.
 * Enables uninterrupted background playback even when the user minimizes the app,
 * locks the screen, or uses another application.
 */
class BackgroundAudioService : Service() {

    private var webView: WebView? = null
    private var videoId: String = ""
    private var videoTitle: String = ""
    private var channelTitle: String = ""
    private var isPlaying: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        try {
            webView = WebView(applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    allowFileAccess = false
                    allowContentAccess = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    val ua = userAgentString
                    userAgentString = ua.replace("; wv", "").replace("Version/4.0 ", "")
                }
                val wv = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(wv, true)
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        if (request != null && !request.isForMainFrame) return false
                        val url = request?.url?.toString().orEmpty()
                        if (url.startsWith("https://www.youtube.com/embed") ||
                            url.startsWith("https://www.youtube-nocookie.com/embed") ||
                            url.startsWith("about:") ||
                            url.startsWith("data:")
                        ) return false
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Error initializing WebView", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val newVideoId = intent?.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
                val newTitle = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
                val newChannel = intent?.getStringExtra(EXTRA_CHANNEL).orEmpty()

                if (newVideoId.isNotBlank()) {
                    val isDifferent = newVideoId != videoId
                    videoId = newVideoId
                    videoTitle = newTitle
                    channelTitle = newChannel
                    isPlaying = true

                    updateSharedState()
                    startInForeground()

                    if (isDifferent) {
                        loadVideoInWebView(videoId)
                    } else {
                        resumePlayback()
                    }
                }
            }
            ACTION_RESUME -> {
                isPlaying = true
                updateSharedState()
                resumePlayback()
                updateNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updateSharedState()
                pausePlayback()
                updateNotification()
            }
            ACTION_STOP -> {
                stopPlaybackAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun loadVideoInWebView(id: String) {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin:0; padding:0; background:#000; overflow:hidden; }
                    iframe { width:100vw; height:100vh; border:none; }
                </style>
            </head>
            <body>
                <iframe id="yt-bg-embed"
                    src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&controls=0&rel=0&enablejsapi=1&origin=https://www.youtube-nocookie.com"
                    allow="autoplay; encrypted-media">
                </iframe>
            </body>
            </html>
        """.trimIndent()
        webView?.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            htmlContent,
            "text/html",
            "UTF-8",
            "https://www.youtube-nocookie.com"
        )
    }

    private fun pausePlayback() {
        val js = """
            (function() {
                var iframe = document.getElementById('yt-bg-embed');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                        event: 'command',
                        func: 'pauseVideo',
                        args: []
                    }), '*');
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun resumePlayback() {
        val js = """
            (function() {
                var iframe = document.getElementById('yt-bg-embed');
                if (iframe && iframe.contentWindow) {
                    iframe.contentWindow.postMessage(JSON.stringify({
                        event: 'command',
                        func: 'playVideo',
                        args: []
                    }), '*');
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
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

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_youtube_launcher)
            .setContentTitle(videoTitle.ifBlank { "YouTube Audio" })
            .setContentText(channelTitle.ifBlank { "Playing in background" })
            .setSubText("Background Audio")
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
                description = "Controls for YouTube background audio playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateSharedState() {
        BackgroundAudioState.isRunning = true
        BackgroundAudioState.isPlaying = isPlaying
        BackgroundAudioState.currentVideoId = videoId
        BackgroundAudioState.currentVideoTitle = videoTitle
        BackgroundAudioState.currentChannelTitle = channelTitle
    }

    private fun stopPlaybackAndSelf() {
        isPlaying = false
        BackgroundAudioState.isRunning = false
        BackgroundAudioState.isPlaying = false
        BackgroundAudioState.currentVideoId = null

        try {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
        } catch (e: Exception) {
            Log.w("BackgroundAudioService", "Error destroying webView", e)
        }
        webView = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopPlaybackAndSelf()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "youtube_background_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.mahirlabib.youtube.action.START"
        const val ACTION_PAUSE = "com.mahirlabib.youtube.action.PAUSE"
        const val ACTION_RESUME = "com.mahirlabib.youtube.action.RESUME"
        const val ACTION_STOP = "com.mahirlabib.youtube.action.STOP"

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

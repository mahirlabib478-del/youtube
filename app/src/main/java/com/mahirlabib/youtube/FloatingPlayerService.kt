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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Floating Window Player Service:
 * Implements "Display over other apps" (SYSTEM_ALERT_WINDOW) overlay window.
 * Moves the unified SharedPlayerManager continuous player into a draggable,
 * floating mini player that hovers over all other applications.
 *
 * Ensures seamless, 100% uninterrupted video and audio flow when leaving
 * and returning to the application.
 */
class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingRootView: FrameLayout? = null
    private var videoContainer: FrameLayout? = null
    private var controlsOverlay: FrameLayout? = null
    private var playPauseButton: ImageButton? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        controlsOverlay?.visibility = View.GONE
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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

                if (!OverlayPermissionHelper.hasPermission(this)) {
                    Log.w(TAG, "Cannot start floating window: SYSTEM_ALERT_WINDOW permission missing")
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (videoId.isNotBlank()) {
                    FloatingPlayerState.isRunning = true
                    FloatingPlayerState.isPlaying = SharedPlayerManager.isPlaying
                    FloatingPlayerState.currentVideoId = videoId
                    FloatingPlayerState.currentVideoTitle = title
                    FloatingPlayerState.currentChannelTitle = channel

                    startInForeground()
                    showFloatingWindow(title)

                    // Ensure video keeps playing smoothly
                    if (!SharedPlayerManager.isPlaying) {
                        SharedPlayerManager.resume()
                    }
                }
            }
            ACTION_PAUSE -> {
                SharedPlayerManager.pause()
                updatePlayPauseIcon()
                updateNotification()
            }
            ACTION_RESUME -> {
                SharedPlayerManager.resume()
                updatePlayPauseIcon()
                updateNotification()
            }
            ACTION_STOP -> {
                stopFloatingWindowAndSelf()
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
            Log.e(TAG, "Error starting floating player foreground service", e)
        }
    }

    private fun buildNotification(): Notification {
        val title = FloatingPlayerState.currentVideoTitle.ifBlank { "YouTube Video" }
        val channel = FloatingPlayerState.currentChannelTitle.ifBlank { "Floating Window Mode" }
        val isPlaying = SharedPlayerManager.isPlaying

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("active_video_id", FloatingPlayerState.currentVideoId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, FloatingPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            11,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingPlayerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            12,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_youtube_launcher)
            .setContentTitle(title as CharSequence)
            .setContentText(channel as CharSequence)
            .setSubText("Floating Window Active")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow(videoTitle: String) {
        if (floatingRootView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val widthPx = (240 * density).toInt()
        val heightPx = (135 * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * density).toInt()
            y = (100 * density).toInt()
        }
        windowLayoutParams = params

        // Root floating view with rounded corners and dark styling
        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.BLACK)
                setStroke((1.5f * density).toInt(), Color.parseColor("#44FFFFFF"))
            }
            clipToOutline = true
        }

        // 1. Video Container where SharedPlayerManager continuous player is attached
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(container)
        videoContainer = container

        // Move continuous player into this floating window container
        SharedPlayerManager.attachToContainer(container)

        // 2. Controls Overlay (Title, Drag indicator, Play/Pause, Open in App, Close)
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            visibility = View.VISIBLE
        }

        // Top bar in overlay
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        val titleTv = TextView(this).apply {
            text = videoTitle.ifBlank { "YouTube" }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        }
        topBar.addView(titleTv)

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
            setOnClickListener {
                stopFloatingWindowAndSelf()
            }
        }
        topBar.addView(closeBtn)
        overlay.addView(topBar)

        // Center Action Bar (Expand to Full App & Play/Pause)
        val centerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // Play/Pause button
        val ppBtn = ImageButton(this).apply {
            val isPlaying = SharedPlayerManager.isPlaying
            setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                rightMargin = (16 * density).toInt()
            }
            setOnClickListener {
                if (SharedPlayerManager.isPlaying) {
                    SharedPlayerManager.pause()
                } else {
                    SharedPlayerManager.resume()
                }
                updatePlayPauseIcon()
                updateNotification()
                rescheduleHideControls()
            }
        }
        playPauseButton = ppBtn
        centerBar.addView(ppBtn)

        // Expand / Open App button
        val expandBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop) // standard maximize/expand icon
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
            setOnClickListener {
                reopenApp()
            }
        }
        centerBar.addView(expandBtn)
        overlay.addView(centerBar)

        root.addView(overlay)
        controlsOverlay = overlay

        // Smooth Drag & Touch handling on the entire floating window
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasDragged = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > 10) {
                        hasDragged = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(root, params)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error moving floating window", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasDragged) {
                        toggleControls()
                    } else {
                        rescheduleHideControls()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(root, params)
            floatingRootView = root
            rescheduleHideControls()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view to WindowManager", e)
        }
    }

    private fun toggleControls() {
        val overlay = controlsOverlay ?: return
        if (overlay.visibility == View.VISIBLE) {
            overlay.visibility = View.GONE
            mainHandler.removeCallbacks(hideControlsRunnable)
        } else {
            overlay.visibility = View.VISIBLE
            updatePlayPauseIcon()
            rescheduleHideControls()
        }
    }

    private fun rescheduleHideControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 3500)
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = SharedPlayerManager.isPlaying
        playPauseButton?.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun reopenApp() {
        val videoId = FloatingPlayerState.currentVideoId.orEmpty()
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("active_video_id", videoId)
        }
        startActivity(appIntent)
        stopFloatingWindowAndSelf()
    }

    private fun stopFloatingWindowAndSelf() {
        FloatingPlayerState.isRunning = false
        FloatingPlayerState.currentVideoId = null

        mainHandler.removeCallbacks(hideControlsRunnable)

        // Detach player without stopping or destroying
        SharedPlayerManager.detachFromContainer()

        val root = floatingRootView
        if (root != null) {
            try {
                if (root.isAttachedToWindow) {
                    windowManager?.removeView(root)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating window", e)
            }
            floatingRootView = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        FloatingPlayerState.isRunning = false
        stopFloatingWindowAndSelf()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Window Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for floating popup video player"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "FloatingPlayerService"
        const val CHANNEL_ID = "youtube_floating_player_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.mahirlabib.youtube.action.START_FLOATING"
        const val ACTION_PAUSE = "com.mahirlabib.youtube.action.PAUSE_FLOATING"
        const val ACTION_RESUME = "com.mahirlabib.youtube.action.RESUME_FLOATING"
        const val ACTION_STOP = "com.mahirlabib.youtube.action.STOP_FLOATING"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"

        fun start(context: Context, videoId: String, title: String, channel: String) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CHANNEL, channel)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }
}

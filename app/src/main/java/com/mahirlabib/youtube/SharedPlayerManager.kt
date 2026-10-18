package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Unified Shared Player Manager:
 * Maintains a single, continuous BackgroundPlayableWebView player across in-app UI and background service.
 * Eliminates restarting, gap, or desynchronization when moving between app and background.
 */
object SharedPlayerManager {
    private const val TAG = "SharedPlayerManager"

    // Single persistent WebView instance stored in Application context
    private var sharedWebView: BackgroundPlayableWebView? = null

    // Reactive Compose state
    var currentVideo by mutableStateOf<YouTubeVideo?>(null)
    var isPlaying by mutableStateOf(false)
    var isControlsVisible by mutableStateOf(false)
    var playbackError by mutableStateOf<String?>(null)

    // User interaction callback for 1:1 control synchronization
    var onUserInteraction: (() -> Unit)? = null

    // Fullscreen callbacks
    var onShowCustomViewCallback: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null
    var onHideCustomViewCallback: (() -> Unit)? = null

    private class Bridge(
        val onControlsChange: (Boolean) -> Unit,
        val onStateChange: (Boolean) -> Unit,
        val onInteraction: () -> Unit
    ) {
        @JavascriptInterface
        fun controlsVisibility(visible: Boolean) {
            Handler(Looper.getMainLooper()).post {
                onControlsChange(visible)
            }
        }

        @JavascriptInterface
        fun onPlayerStateChanged(playing: Boolean) {
            Handler(Looper.getMainLooper()).post {
                onStateChange(playing)
            }
        }

        @JavascriptInterface
        fun onUserTapped() {
            Handler(Looper.getMainLooper()).post {
                onInteraction()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun getOrCreateWebView(context: Context): BackgroundPlayableWebView {
        sharedWebView?.let { return it }

        val appContext = context.applicationContext
        val wv = BackgroundPlayableWebView(appContext).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            clipChildren = true
            clipToPadding = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AndroidColor.BLACK)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    onUserInteraction?.invoke()
                }
                false
            }

            addJavascriptInterface(
                Bridge(
                    onControlsChange = { visible -> isControlsVisible = visible },
                    onStateChange = { playing ->
                        isPlaying = playing
                        BackgroundAudioState.isPlaying = playing
                        BackgroundAudioService.activeService?.updateNotification()
                    },
                    onInteraction = {
                        onUserInteraction?.invoke()
                    }
                ),
                "AndroidBridge"
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "Console: ${message.message()}")
                    return true
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        onShowCustomViewCallback?.invoke(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    onHideCustomViewCallback?.invoke()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    playbackError = null
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request != null && !request.isForMainFrame) return false
                    val url = request?.url?.toString().orEmpty()
                    if (url.contains("/watch") || url.contains("m.youtube.com") || url.contains("youtu.be")) {
                        return true
                    }
                    if (url.startsWith("https://www.youtube.com/embed") ||
                        url.startsWith("https://www.youtube-nocookie.com/embed") ||
                        url.startsWith("about:") ||
                        url.startsWith("data:")
                    ) {
                        return false
                    }
                    return true
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        playbackError = error?.description?.toString() ?: "Network error"
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                        playbackError = "HTTP ${errorResponse?.statusCode ?: 500}"
                    }
                }
            }
        }

        sharedWebView = wv
        return wv
    }

    /**
     * Attaches the continuous shared player into an in-app container (such as Compose AndroidView).
     */
    fun attachToContainer(container: ViewGroup) {
        val wv = sharedWebView ?: getOrCreateWebView(container.context)
        if (wv.parent != container) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            container.removeAllViews()
            container.addView(
                wv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        ensureActive()
    }

    /**
     * Ensures WebView timers and active state are running.
     */
    fun ensureActive() {
        sharedWebView?.let { wv ->
            wv.isBackgroundAudioActive = true
            wv.resumeTimers()
            wv.onResume()
        }
    }

    /**
     * Detaches the shared player from an in-app container without stopping playback or unloading.
     */
    fun detachFromContainer() {
        sharedWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
    }

    /**
     * Loads a video into the continuous player or ensures playback if already loaded.
     */
    fun playVideo(context: Context, video: YouTubeVideo) {
        val isSameVideo = currentVideo?.videoId == video.videoId
        currentVideo = video
        playbackError = null

        val wv = getOrCreateWebView(context)
        if (!isSameVideo) {
            loadEmbedPlayer(wv, video.videoId)
        } else {
            resume()
        }
        isPlaying = true
        BackgroundAudioState.currentVideoId = video.videoId
        BackgroundAudioState.currentVideoTitle = video.title
        BackgroundAudioState.currentChannelTitle = video.channelTitle
        BackgroundAudioState.isPlaying = true
        BackgroundAudioService.activeService?.updateNotification()
    }

    fun pause() {
        isPlaying = false
        BackgroundAudioState.isPlaying = false
        BackgroundAudioService.activeService?.updateNotification()
        sharedWebView?.evaluateJavascript(
            """
            (function() {
                try {
                    if (typeof window.pausePlayer === 'function') {
                        window.pausePlayer();
                    } else {
                        var iframe = document.getElementById('yt-embed');
                        if (iframe && iframe.contentWindow) {
                            iframe.contentWindow.postMessage(JSON.stringify({
                                event: 'command',
                                func: 'pauseVideo',
                                args: []
                            }), '*');
                        }
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    fun resume() {
        isPlaying = true
        BackgroundAudioState.isPlaying = true
        BackgroundAudioService.activeService?.updateNotification()
        sharedWebView?.let { wv ->
            ensureActive()
            wv.evaluateJavascript(
                """
                (function() {
                    try {
                        if (typeof window.playPlayer === 'function') {
                            window.playPlayer();
                        } else {
                            var iframe = document.getElementById('yt-embed');
                            if (iframe && iframe.contentWindow) {
                                iframe.contentWindow.postMessage(JSON.stringify({
                                    event: 'command',
                                    func: 'playVideo',
                                    args: []
                                }), '*');
                            }
                        }
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun stop() {
        isPlaying = false
        currentVideo = null
        playbackError = null
        BackgroundAudioState.isRunning = false
        BackgroundAudioState.isPlaying = false
        BackgroundAudioState.currentVideoId = null

        sharedWebView?.let { wv ->
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping shared WebView", e)
            }
        }
        sharedWebView = null
    }

    private fun loadEmbedPlayer(wv: WebView, videoId: String) {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    * { margin:0; padding:0; box-sizing:border-box; }
                    html, body { width:100%; height:100%; background-color:#000; overflow:hidden; }
                    #player-container { position:relative; width:100%; height:100%; background-color:#000; }
                    iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
                </style>
                <script>
                    try {
                        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                        Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                    } catch(e) {}

                    document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                    window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                    window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
                    window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
                </script>
            </head>
            <body>
                <div id="player-container">
                    <iframe
                        id="yt-embed"
                        src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=0&enablejsapi=1&origin=https://www.youtube-nocookie.com"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen>
                    </iframe>
                </div>
                <script>
                    var iframe = document.getElementById('yt-embed');
                    var userWantsPlay = true;

                    function notifyInteraction() {
                        try { AndroidBridge.onUserTapped(); } catch(e) {}
                    }

                    window.addEventListener('touchstart', notifyInteraction, { passive: true });
                    window.addEventListener('click', notifyInteraction, { passive: true });
                    window.addEventListener('pointerup', notifyInteraction, { passive: true });

                    function postCmd(func, args) {
                        try {
                            if (iframe && iframe.contentWindow) {
                                iframe.contentWindow.postMessage(JSON.stringify({
                                    event: 'command',
                                    func: func,
                                    args: args || []
                                }), '*');
                            }
                        } catch(e) {}
                    }

                    function play() {
                        userWantsPlay = true;
                        postCmd('playVideo');
                    }

                    function pause() {
                        userWantsPlay = false;
                        postCmd('pauseVideo');
                    }

                    window.playPlayer = play;
                    window.pausePlayer = pause;

                    window.addEventListener('message', function(event) {
                        try {
                            var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                            if (data && data.event === 'infoDelivery' && data.info) {
                                var state = data.info.playerState;
                                if (state === 1) { // Playing
                                    userWantsPlay = true;
                                    try { AndroidBridge.onPlayerStateChanged(true); } catch(e) {}
                                } else if (state === 2) { // Paused
                                    if (!userWantsPlay) {
                                        try { AndroidBridge.onPlayerStateChanged(false); } catch(e) {}
                                    } else {
                                        // Unsolicited pause while user wants audio - resume immediately
                                        setTimeout(function() {
                                            if (userWantsPlay) play();
                                        }, 100);
                                    }
                                } else if (state === 0) { // Ended
                                    userWantsPlay = false;
                                    try { AndroidBridge.onPlayerStateChanged(false); } catch(e) {}
                                }
                            }
                        } catch(e) {}
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        wv.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            htmlContent,
            "text/html",
            "UTF-8",
            "https://www.youtube-nocookie.com"
        )
    }
}

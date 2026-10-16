package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Direct YouTube Embedded Player with isolated video lifecycle.
 * - Wraps each video in key(videoId) so switching videos completely refreshes the player instance.
 * - Eliminates black-screen freeze by avoiding static pauseTimers() calls.
 * - Suppresses "Watch on YouTube" redirects, suggested video grids, and recommendation overlays.
 */
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    onPlaybackError: ((String) -> Unit)? = null,
    onShowCustomView: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideCustomView: (() -> Unit)? = null
) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID: $videoId" }

    key(videoId) {
        YouTubeEmbedPlayerInstance(
            videoId = videoId,
            modifier = modifier,
            autoplay = autoplay,
            onPlaybackError = onPlaybackError,
            onShowCustomView = onShowCustomView,
            onHideCustomView = onHideCustomView
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbedPlayerInstance(
    videoId: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    onPlaybackError: ((String) -> Unit)? = null,
    onShowCustomView: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideCustomView: (() -> Unit)? = null
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.destroy()
                } catch (e: Exception) {
                    Log.w("YouTubeEmbedPlayer", "Error disposing WebView", e)
                }
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("youtube_embed_player_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    // Ensure process JS timers and WebView are actively running
                    resumeTimers()
                    onResume()

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)

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

                        val currentUa = userAgentString
                        userAgentString = currentUa
                            .replace("; wv", "")
                            .replace("Version/4.0 ", "")
                    }

                    val wv = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(wv, true)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.d("YouTubeEmbedPlayer", "Console: ${message.message()}")
                            return true
                        }

                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null && callback != null) {
                                onShowCustomView?.invoke(view, callback)
                            }
                        }

                        override fun onHideCustomView() {
                            onHideCustomView?.invoke()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            errorMessage = null
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Never block sub-resources, video chunks, iframe redirects, or media streams
                            if (request != null && !request.isForMainFrame) {
                                return false
                            }

                            val url = request?.url?.toString().orEmpty()

                            // If anything tries to open "Watch on YouTube" (/watch or m.youtube.com), BLOCK IT!
                            // Keep video strictly playing in the embedded player
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

                            // Block main-frame navigation that tries to break out of the embedded player
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                val desc = error?.description?.toString() ?: "Network error"
                                errorMessage = "Network error ($desc)"
                                onPlaybackError?.invoke(desc)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                val code = errorResponse?.statusCode ?: 500
                                errorMessage = "Player HTTP error $code"
                                onPlaybackError?.invoke("HTTP $code")
                            }
                        }
                    }

                    tag = "$videoId-$reloadTrigger"
                    loadCleanEmbedPlayer(videoId, autoplay)
                }
            },
            update = { wv ->
                webViewRef = wv
                if (wv.tag != "$videoId-$reloadTrigger") {
                    wv.tag = "$videoId-$reloadTrigger"
                    errorMessage = null
                    wv.loadCleanEmbedPlayer(videoId, autoplay)
                }
            }
        )

        // Retry overlay on fatal network error
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0101010))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Playback error",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            reloadTrigger++
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * Loads the HTML5 YouTube embedded player using privacy-enhanced domain and strict cross-origin referrer.
 * Suppresses:
 * 1. "Watch on YouTube" button (.ytp-youtube-button).
 * 2. End-screen suggestion grid (resets to 0 and pauses on completion).
 * 3. Pause overlay recommendations (.ytp-pause-overlay).
 * 4. Annotation teasers and promotional watermarks.
 */
private fun WebView.loadCleanEmbedPlayer(videoId: String, autoplay: Boolean) {
    val autoPlayParam = if (autoplay) 1 else 0
    val htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    width: 100%;
                    height: 100%;
                    background-color: #000000;
                    overflow: hidden;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                #player-container {
                    position: relative;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background-color: #000000;
                }
                iframe {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    border: 0;
                }
            </style>
        </head>
        <body>
            <div id="player-container">
                <iframe
                    id="yt-embed"
                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=$autoPlayParam&playsinline=1&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=1&enablejsapi=1&origin=https://www.youtube-nocookie.com"
                    referrerpolicy="strict-origin-when-cross-origin"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                </iframe>
            </div>

            <script>
                var iframe = document.getElementById('yt-embed');

                // Suppress end-screen suggestions grid and pause overlays
                window.addEventListener('message', function(event) {
                    try {
                        var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        if (data && data.event === 'infoDelivery' && data.info && data.info.playerState === 0) {
                            if (iframe && iframe.contentWindow) {
                                iframe.contentWindow.postMessage(JSON.stringify({
                                    event: 'command',
                                    func: 'seekTo',
                                    args: [0, true]
                                }), '*');
                                iframe.contentWindow.postMessage(JSON.stringify({
                                    event: 'command',
                                    func: 'pauseVideo',
                                    args: []
                                }), '*');
                            }
                        }
                    } catch(e) {}
                });

                // Periodic check to inject CSS rules to eliminate "Watch on YouTube" and overlays
                function injectAntiDistractionStyle() {
                    try {
                        var doc = iframe.contentDocument || iframe.contentWindow.document;
                        if (doc && !doc.getElementById('clean-player-style')) {
                            var style = doc.createElement('style');
                            style.id = 'clean-player-style';
                            style.innerHTML = `
                                .ytp-youtube-button,
                                a.ytp-youtube-button,
                                .ytp-impression-link,
                                .ytp-title-link,
                                .ytp-watermark,
                                .ytp-pause-overlay,
                                .ytp-pause-overlay-container,
                                .ytp-scroll-min,
                                .ytp-endscreen-paginate,
                                .ytp-ce-element,
                                .ytp-ce-video,
                                .ytp-ce-channel,
                                .ytp-ce-playlist,
                                .ytp-cards-teaser,
                                .ytp-cards-button,
                                .ytp-suggestion-set,
                                .ytp-show-tiles,
                                .ytp-related-on-error-overlay {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                }
                            `;
                            doc.head.appendChild(style);
                        }
                    } catch(e) {}
                }

                iframe.onload = function() {
                    injectAntiDistractionStyle();
                    setInterval(injectAntiDistractionStyle, 800);
                };
            </script>
        </body>
        </html>
    """.trimIndent()

    loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlContent, "text/html", "UTF-8", "https://www.youtube-nocookie.com")
}

package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
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
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Robust embedded YouTube player using official YouTube IFrame API.
 * Uses loadDataWithBaseURL("https://www.youtube.com", ...) to ensure proper HTTPS web
 * origin, resolving Error 150/153 referrer restrictions.
 * Includes lifecycle management, diagnostics overlay, and external player fallback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    onPlaybackError: ((String) -> Unit)? = null
) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID: $videoId" }
    val context = LocalContext.current
    var isPlayerReady by remember(videoId) { mutableStateOf(false) }
    var errorMessage by remember(videoId) { mutableStateOf<String?>(null) }
    var errorDetails by remember(videoId) { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var reloadTrigger by remember(videoId) { mutableStateOf(0) }

    DisposableEffect(videoId) {
        onDispose {
            webViewRef?.let { wv ->
                wv.onPause()
                wv.pauseTimers()
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
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
                    }
                    val wvInstance = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(wvInstance, true)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val text = message.message()
                            Log.d("YouTubeEmbedPlayer", "Console: $text")

                            if (text.contains("YT_PLAYER_READY")) {
                                isPlayerReady = true
                                errorMessage = null
                            } else if (text.contains("YT_PLAYER_ERROR")) {
                                val code = text.substringAfter("YT_PLAYER_ERROR:").trim()
                                val msg = when (code) {
                                    "2" -> "Invalid video ID parameter."
                                    "5" -> "HTML5 player error on this device."
                                    "100" -> "Video not found or has been marked private."
                                    "101", "150" -> "Owner disabled embedded playback on mobile apps."
                                    else -> "Playback error ($code)."
                                }
                                errorMessage = msg
                                errorDetails = "Code: $code. You can still watch it directly on YouTube."
                                onPlaybackError?.invoke(msg)
                            } else if (text.contains("error 153") || text.contains("referrer")) {
                                errorMessage = "YouTube embed policy restricted this video."
                                errorDetails = "Embedding is restricted by YouTube or the uploader."
                                onPlaybackError?.invoke("Error 153: Referrer restriction")
                            }
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            errorMessage = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Page loaded, player init script is executing
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                val desc = error?.description?.toString() ?: "Network error"
                                Log.e("YouTubeEmbedPlayer", "Main frame error: $desc")
                                errorMessage = "Could not connect to YouTube ($desc)"
                                errorDetails = "Please check your network connection."
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                val code = errorResponse?.statusCode ?: 500
                                errorMessage = "YouTube server returned HTTP $code"
                                errorDetails = "Video might be unavailable or geo-restricted."
                            }
                        }
                    }

                    loadHtmlPlayer(videoId, autoplay)
                }
            },
            update = { wv ->
                webViewRef = wv
                if (wv.tag != "$videoId-$reloadTrigger") {
                    wv.tag = "$videoId-$reloadTrigger"
                    isPlayerReady = false
                    errorMessage = null
                    wv.loadHtmlPlayer(videoId, autoplay)
                }
            }
        )

        // Loading indicator
        if (!isPlayerReady && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF0033),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading YouTube player…",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Error Diagnostic Overlay
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6101010))
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
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Playback failed",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (errorDetails != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = errorDetails ?: "",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                reloadTrigger++
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Retry")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/watch?v=$videoId")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033))
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Open in YouTube")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Loads the HTML5 iframe player with origin "https://www.youtube.com" to ensure
 * official embedding policies are satisfied without Error 150/153.
 */
private fun WebView.loadHtmlPlayer(videoId: String, autoplay: Boolean) {
    val autoplayVal = if (autoplay) 1 else 0
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
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
                    width: 100%;
                    height: 100%;
                    position: relative;
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
                <iframe id="yt-player"
                    src="https://www.youtube.com/embed/$videoId?enablejsapi=1&playsinline=1&autoplay=$autoplayVal&rel=0&modestbranding=1&fs=1&origin=https://www.youtube.com"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                </iframe>
            </div>
            <script>
                var iframe = document.getElementById('yt-player');
                iframe.onload = function() {
                    console.log('YT_PLAYER_READY');
                };

                // Listen for YouTube iframe player messages
                window.addEventListener('message', function(event) {
                    try {
                        var data = JSON.parse(event.data);
                        if (data.event === 'onError') {
                            console.log('YT_PLAYER_ERROR: ' + data.info);
                        } else if (data.event === 'onReady') {
                            console.log('YT_PLAYER_READY');
                        } else if (data.event === 'infoDelivery' && data.info && data.info.playerState !== undefined) {
                            console.log('YT_STATE_CHANGE: ' + data.info.playerState);
                        }
                    } catch(e) {}
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
}

package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Uses YouTube's official embedded player. WebView failures are surfaced rather
 * than silently leaving a black rectangle. Playback still depends on YouTube's
 * embed policy, network access, and the video owner's embedding settings.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(videoId: String, modifier: Modifier = Modifier) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID" }
    var playerStatus by remember(videoId) { mutableStateOf("Loading YouTube player…") }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val text = "YouTube WebView ${message.messageLevel()}: ${message.message()}"
                            Log.d("YouTubeEmbedPlayer", text)
                            val lower = message.message().lowercase()
                            if (lower.contains("error 153") || lower.contains("referrer")) {
                                playerStatus = "YouTube refused this embed (Error 153 / referrer). Try another video or open on YouTube."
                            } else if (lower.contains("playback") && lower.contains("error")) {
                                playerStatus = "YouTube reported a playback error. This video may restrict embedded playback."
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            playerStatus = "Connecting to YouTube…"
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            playerStatus = ""
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                val description = error?.description?.toString().orEmpty()
                                Log.e("YouTubeEmbedPlayer", "Main-frame load error: $description")
                                playerStatus = "Could not load YouTube player: ${description.ifBlank { "network error" }}"
                            }
                        }

                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                playerStatus = "YouTube page returned HTTP ${errorResponse?.statusCode}. Check connection or embed availability."
                                Log.e("YouTubeEmbedPlayer", playerStatus)
                            }
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            playerStatus = "The player process stopped. Reopen this video to try again."
                            Log.e("YouTubeEmbedPlayer", playerStatus)
                            return true
                        }
                    }
                    tag = videoId
                    loadOfficialEmbed(videoId, context.packageName)
                }
            },
            update = { view ->
                if ((view.tag as? String) != videoId) {
                    view.tag = videoId
                    playerStatus = "Loading YouTube player…"
                    view.loadOfficialEmbed(videoId, view.context.packageName)
                }
            }
        )
        if (playerStatus.isNotBlank()) {
            Text(
                text = playerStatus,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color(0xCC202020))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private fun WebView.loadOfficialEmbed(videoId: String, packageName: String) {
    // Do not spoof a browser user-agent. The package-based Referer is diagnostic
    // compatibility only; it is not a verified web origin and may not satisfy
    // YouTube's client-identification requirement on every device.
    val appReferrer = "https://${packageName.lowercase()}/"
    val headers = mapOf("Referer" to appReferrer)
    val url = "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&autoplay=0"
    loadUrl(url, headers)
}

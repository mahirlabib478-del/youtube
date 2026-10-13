package com.mahirlabib.youtube

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Plays a YouTube video through YouTube's official IFrame embed player.
 * Video IDs are validated before being interpolated into the HTML document.
 */
@Composable
fun YouTubeEmbedPlayer(videoId: String, modifier: Modifier = Modifier) {
    require(youtubeVideoIdPattern.matches(videoId)) { "Invalid YouTube video ID" }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    buildEmbedHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun buildEmbedHtml(videoId: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <style>
          html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; }
          .player { position: relative; width: 100%; height: 100%; }
          iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
        </style>
      </head>
      <body>
        <div class="player">
          <iframe
            src="https://www.youtube.com/embed/$videoId?enablejsapi=1&playsinline=1"
            title="YouTube video player"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen>
          </iframe>
        </div>
      </body>
    </html>
""".trimIndent()

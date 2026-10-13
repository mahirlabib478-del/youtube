package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Uses YouTube's official embedded player. Playback remains subject to YouTube
 * embedding permissions, video availability, network connectivity and account restrictions.
 */
@SuppressLint("SetJavaScriptEnabled")
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
                settings.loadsImagesAutomatically = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                tag = videoId
                loadPlayerHtml(videoId)
            }
        },
        update = { view ->
            val current = view.tag as? String
            if (current != videoId) {
                view.tag = videoId
                view.loadPlayerHtml(videoId)
            }
        }
    )
}

private fun WebView.loadPlayerHtml(videoId: String) {
    // Give the official player a real HTTPS base URL so its requests carry a
    // Referer and an explicit origin, required by some YouTube player configurations.
    val origin = "https://www.youtube.com"
    val encodedOrigin = URLEncoder.encode(origin, StandardCharsets.UTF_8.name())
    val html = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
          <style>
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
            iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
          </style>
        </head>
        <body>
          <iframe
            src="https://www.youtube.com/embed/$videoId?playsinline=1&amp;rel=0&amp;autoplay=0&amp;origin=$encodedOrigin"
            title="YouTube video player"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
    loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
}

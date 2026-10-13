package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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
                // Let YouTube handle its player/CDN redirects; filtering navigation can block playback.
                webViewClient = WebViewClient()
                tag = videoId
                loadDataWithBaseURL(
                    "https://www.youtube.com/",
                    buildEmbedHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { view ->
            val current = view.tag as? String
            if (current != videoId) {
                view.tag = videoId
                view.loadDataWithBaseURL(
                    "https://www.youtube.com/",
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
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
          html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
          .player { position: relative; width: 100%; height: 100%; }
          iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
        </style>
      </head>
      <body>
        <div class="player">
          <iframe
            src="https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
            title="YouTube video player"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen>
          </iframe>
        </div>
      </body>
    </html>
""".trimIndent()

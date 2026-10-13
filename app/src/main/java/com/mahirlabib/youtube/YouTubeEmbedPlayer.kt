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
                // Load the official embed URL directly so WebView sends a normal HTTPS
                // navigation/referrer instead of wrapping the player in generated HTML.
                loadUrl(embedUrl(videoId))
            }
        },
        update = { view ->
            val current = view.tag as? String
            if (current != videoId) {
                view.tag = videoId
                view.loadUrl(embedUrl(videoId))
            }
        }
    )
}


private fun embedUrl(videoId: String): String =
    "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&autoplay=0"

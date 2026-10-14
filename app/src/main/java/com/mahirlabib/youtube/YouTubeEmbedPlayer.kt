package com.mahirlabib.youtube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Loads YouTube's official embedded player inside the app.
 * YouTube requires an HTTP Referer (or equivalent client identification);
 * Android WebView does not reliably provide one for direct embed navigation.
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
                loadOfficialEmbed(videoId, context.packageName)
            }
        },
        update = { view ->
            val current = view.tag as? String
            if (current != videoId) {
                view.tag = videoId
                view.loadOfficialEmbed(videoId, view.context.packageName)
            }
        }
    )
}

private fun WebView.loadOfficialEmbed(videoId: String, packageName: String) {
    // Per YouTube's documented WebView integration guidance, explicitly identify
    // the embedding app using Referer. Do not use a fake browser user-agent.
    val appReferrer = "https://${packageName.lowercase()}/"
    val headers = mapOf("Referer" to appReferrer)
    val url = "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&autoplay=0"
    loadUrl(url, headers)
}

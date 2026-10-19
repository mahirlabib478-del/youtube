package com.mahirlabib.youtube

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that intercepts window visibility, window focus, view hierarchy state,
 * and pause signals so Chromium's internal media pipeline does NOT pause audio when the app is
 * minimized, detached, the screen is turned off, or the Activity loses focus.
 */
class BackgroundPlayableWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var isBackgroundAudioActive: Boolean = false

    override fun getWindowVisibility(): Int {
        return if (isBackgroundAudioActive) View.VISIBLE else super.getWindowVisibility()
    }

    override fun isShown(): Boolean {
        return if (isBackgroundAudioActive) true else super.isShown()
    }

    override fun hasWindowFocus(): Boolean {
        return if (isBackgroundAudioActive) true else super.hasWindowFocus()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundAudioActive) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundAudioActive) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.dispatchWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (isBackgroundAudioActive) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (isBackgroundAudioActive) {
            super.onWindowFocusChanged(true)
        } else {
            super.onWindowFocusChanged(hasWindowFocus)
        }
    }

    override fun onPause() {
        if (!isBackgroundAudioActive) {
            super.onPause()
        }
    }

    override fun pauseTimers() {
        if (!isBackgroundAudioActive) {
            super.pauseTimers()
        }
    }
}

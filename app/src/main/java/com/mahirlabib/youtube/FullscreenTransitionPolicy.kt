package com.mahirlabib.youtube

/**
 * Pure UI policy: let the Android orientation change finish before switching
 * the persistent WebView host between portrait 16:9 and fullscreen layouts.
 */
internal object FullscreenTransitionPolicy {
    fun useFullscreenLayout(
        fullscreenRequested: Boolean,
        waitingForPortrait: Boolean,
        isLandscape: Boolean
    ): Boolean = isLandscape && (fullscreenRequested || waitingForPortrait)

    fun hideSystemBars(fullscreenRequested: Boolean, isLandscape: Boolean): Boolean =
        fullscreenRequested && isLandscape

    fun showSystemBars(fullscreenRequested: Boolean, isLandscape: Boolean): Boolean =
        !fullscreenRequested && !isLandscape
}

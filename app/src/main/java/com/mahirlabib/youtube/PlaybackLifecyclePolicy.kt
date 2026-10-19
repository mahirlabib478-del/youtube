package com.mahirlabib.youtube

/**
 * Foreground playback, opt-in background audio and explicit PiP are separate features.
 * Keep these decisions independent from the Android/WebView UI lifecycle.
 */
internal object PlaybackLifecyclePolicy {
    fun shouldPauseWhenActivityStops(backgroundAudioActive: Boolean, inPictureInPicture: Boolean): Boolean =
        !backgroundAudioActive && !inPictureInPicture

    fun shouldKeepWebViewActive(backgroundAudioActive: Boolean): Boolean = backgroundAudioActive

    fun shouldRecoverPlayback(backgroundAudioActive: Boolean, activityInBackground: Boolean): Boolean =
        backgroundAudioActive && activityInBackground
}

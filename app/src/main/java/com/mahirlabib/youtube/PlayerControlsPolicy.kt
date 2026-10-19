package com.mahirlabib.youtube

/** Visibility policy for the app's own buttons over the cross-origin YouTube iframe. */
internal object PlayerControlsPolicy {
    /** Before the first actual playback, allow native buttons to remain visible. */
    fun waitForFirstPlayback(playbackStarted: Boolean, userInteracted: Boolean): Boolean =
        !playbackStarted && !userInteracted
}

package com.mahirlabib.youtube

/** Visibility policy for the app's own buttons over the cross-origin YouTube iframe. */
internal object PlayerControlsPolicy {
    // Best-effort approximation; the embedded YouTube controls have no public
    // show/hide signal. Keep this shorter than the old 3.8s native timeout.
    const val AUTO_HIDE_DELAY_MS = 2400L

    /** Native corner buttons auto-hide only when playback is actively running. */
    fun shouldAutoHide(isPlaying: Boolean, isVisible: Boolean): Boolean =
        isPlaying && isVisible

    /** A pause may reveal buttons once, unless already hidden by a paused tap. */
    fun shouldRevealOnPause(playbackStarted: Boolean, manuallyHiddenWhilePaused: Boolean): Boolean =
        playbackStarted && !manuallyHiddenWhilePaused

    /** Before the first actual playback, allow native buttons to remain visible. */
    fun waitForFirstPlayback(playbackStarted: Boolean, userInteracted: Boolean): Boolean =
        !playbackStarted && !userInteracted

    /** One accepted tap = one visibility transition; avoid duplicate touch sources. */
    fun shouldToggle(nowMs: Long, lastTapMs: Long): Boolean =
        nowMs >= lastTapMs && (lastTapMs == 0L || nowMs - lastTapMs >= 250L)

    /** Moving/holding a finger is a swipe or gesture, not a controls toggle. */
    fun isSimpleTap(deltaX: Float, deltaY: Float, durationMs: Long, slopPx: Float): Boolean =
        durationMs in 0L..650L && kotlin.math.abs(deltaX) <= slopPx &&
            kotlin.math.abs(deltaY) <= slopPx
}

package com.mahirlabib.youtube

/** Visibility policy for the app's own buttons over the cross-origin YouTube iframe. */
internal object PlayerControlsPolicy {
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

    /** Avoid repeat clicks while portrait/landscape view hierarchy is rotating. */
    fun shouldHandleFullscreenButtonTap(nowMs: Long, previousMs: Long): Boolean =
        nowMs >= previousMs && (previousMs == 0L || nowMs - previousMs >= 650L)
}

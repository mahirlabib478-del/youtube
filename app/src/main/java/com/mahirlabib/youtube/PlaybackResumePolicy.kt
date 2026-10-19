package com.mahirlabib.youtube

/**
 * Decides if ordinary (non-background-service) playback may resume when the
 * activity re-enters the foreground. The lifecycle's own pause is not user intent.
 */
internal object PlaybackResumePolicy {
    fun shouldRestoreOnReturn(
        wasPlaying: Boolean,
        playbackStarted: Boolean,
        pausedByUser: Boolean,
        backgroundAudioRunning: Boolean,
        inPictureInPicture: Boolean
    ): Boolean =
        wasPlaying && playbackStarted && !pausedByUser &&
            !backgroundAudioRunning && !inPictureInPicture
}

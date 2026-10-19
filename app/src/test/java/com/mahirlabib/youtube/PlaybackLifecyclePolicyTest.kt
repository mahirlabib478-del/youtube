package com.mahirlabib.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLifecyclePolicyTest {
    @Test
    fun ordinaryPlaybackPausesOutsideOfApp() {
        assertTrue(PlaybackLifecyclePolicy.shouldPauseWhenActivityStops(false, false))
        assertFalse(PlaybackLifecyclePolicy.shouldKeepWebViewActive(false))
        assertFalse(PlaybackLifecyclePolicy.shouldRecoverPlayback(false, true))
    }

    @Test
    fun explicitlyEnabledBackgroundAudioKeepsPlayingOutsideOfApp() {
        assertFalse(PlaybackLifecyclePolicy.shouldPauseWhenActivityStops(true, false))
        assertTrue(PlaybackLifecyclePolicy.shouldKeepWebViewActive(true))
        assertTrue(PlaybackLifecyclePolicy.shouldRecoverPlayback(true, true))
        assertFalse(PlaybackLifecyclePolicy.shouldRecoverPlayback(true, false))
    }

    @Test
    fun pictureInPictureWorksWithoutBackgroundAudioService() {
        assertFalse(PlaybackLifecyclePolicy.shouldPauseWhenActivityStops(false, true))
        assertFalse(PlaybackLifecyclePolicy.shouldRecoverPlayback(false, false))
    }
}

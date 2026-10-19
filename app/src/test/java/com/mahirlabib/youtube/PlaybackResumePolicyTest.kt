package com.mahirlabib.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumePolicyTest {
    @Test
    fun restoresOnlyAConfirmedPlayingVideo() {
        assertTrue(PlaybackResumePolicy.shouldRestoreOnReturn(true, true, false, false, false))
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(true, false, false, false, false))
    }

    @Test
    fun neverRestartsVideoExplicitlyPausedByUser() {
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(true, true, true, false, false))
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(false, true, true, false, false))
    }

    @Test
    fun backgroundAudioServiceControlsItsOwnPlayback() {
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(true, true, false, true, false))
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(false, true, true, true, false))
    }

    @Test
    fun pipAndAlreadyStoppedPlaybackDoNotTriggerForegroundResume() {
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(true, true, false, false, true))
        assertFalse(PlaybackResumePolicy.shouldRestoreOnReturn(false, true, false, false, false))
    }
}

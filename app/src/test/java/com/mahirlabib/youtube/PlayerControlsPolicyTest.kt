package com.mahirlabib.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsPolicyTest {
    @Test
    fun keepCornerButtonsVisibleDuringSlowInitialPlayback() {
        assertTrue(PlayerControlsPolicy.waitForFirstPlayback(false, false))
    }

    @Test
    fun firstPlaybackStartsTheNormalInactivityTimer() {
        assertFalse(PlayerControlsPolicy.waitForFirstPlayback(true, false))
    }

    @Test
    fun userCanDismissRevealedButtonsEvenIfVideoHasNotStarted() {
        assertFalse(PlayerControlsPolicy.waitForFirstPlayback(false, true))
    }
}

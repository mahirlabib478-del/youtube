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

    @Test
    fun aNewTapTogglesControlsButDuplicateEventsDoNot() {
        assertTrue(PlayerControlsPolicy.shouldToggle(1000L, 0L))
        assertFalse(PlayerControlsPolicy.shouldToggle(1120L, 1000L))
        assertTrue(PlayerControlsPolicy.shouldToggle(1480L, 1000L))
    }

    @Test
    fun horizontalAndVerticalSwipesDoNotTogglePlayerButtons() {
        assertFalse(PlayerControlsPolicy.isSimpleTap(45f, 1f, 200L, 24f))
        assertFalse(PlayerControlsPolicy.isSimpleTap(0f, 70f, 200L, 24f))
        assertFalse(PlayerControlsPolicy.isSimpleTap(0f, 0f, 1000L, 24f))
        assertTrue(PlayerControlsPolicy.isSimpleTap(2f, 3f, 110L, 24f))
    }
}

package com.mahirlabib.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenTransitionPolicyTest {
    @Test
    fun enterFullscreenWaitsForLandscapeBeforeExpandingPlayer() {
        assertFalse(FullscreenTransitionPolicy.useFullscreenLayout(true, false, false))
        assertTrue(FullscreenTransitionPolicy.useFullscreenLayout(true, false, true))
    }

    @Test
    fun exitFullscreenKeepsLargePlayerUntilPortraitArrives() {
        assertTrue(FullscreenTransitionPolicy.useFullscreenLayout(false, true, true))
        assertFalse(FullscreenTransitionPolicy.useFullscreenLayout(false, true, false))
        assertFalse(FullscreenTransitionPolicy.useFullscreenLayout(false, false, true))
    }

    @Test
    fun systemBarsOnlySwitchInDestinationOrientation() {
        assertFalse(FullscreenTransitionPolicy.hideSystemBars(true, false))
        assertTrue(FullscreenTransitionPolicy.hideSystemBars(true, true))
        assertFalse(FullscreenTransitionPolicy.showSystemBars(false, true))
        assertTrue(FullscreenTransitionPolicy.showSystemBars(false, false))
    }
}
